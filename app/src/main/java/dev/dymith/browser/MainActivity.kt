package dev.dymith.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.app.DownloadManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewOutcomeReceiver
import androidx.webkit.WebViewStartUpConfig
import androidx.webkit.WebViewStartUpResult
import androidx.webkit.WebViewStartupException
import androidx.webkit.WebViewFeature
import androidx.webkit.WebMessageCompat
import java.util.concurrent.Executors
import java.io.ByteArrayInputStream
import java.util.Locale

/**
 * Dymith Phase 0: một tab, WebView hệ thống + lõi Rust + thanh công cụ Canvas nhẹ.
 * Vỏ dùng View thuần (không AppCompat/Compose) để APK nhỏ và khởi động nhanh.
 */
class MainActivity : Activity() {

    private companion object {
        const val SEARCH_TEMPLATE = "https://www.google.com/search?q=%s"
        const val PREF_KB_TEXT = "kb_text"
        const val PREF_UA_CLEAN = "ua_clean"
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var root: FrameLayout
    private lateinit var web: WebView
    private lateinit var progressBar: View
    private lateinit var bar: FrameLayout
    private lateinit var tint: View
    private lateinit var omnibox: EditText
    private lateinit var btnBack: ImageButton
    private lateinit var btnReload: ImageButton
    private lateinit var btnMenu: ImageButton

    private val webStartupExecutor = Executors.newSingleThreadExecutor()
    private var frameStats: FrameStats? = null

    private var defaultUa: String = ""
    private var loading = false
    private var deferredIntent: Intent? = null
    private var hostResumed = false
    private var barHidden = false
    private var insetTop = 0
    private var insetBottom = 0
    private var insetLeft = 0
    private var insetRight = 0

    private var firstDrawMs = -1L
    private var onCreateMs = 0.0
    private var webViewCreateMs = 0.0
    private var webStartupMs = 0.0
    private var webStartupStartedNs = 0L

    // ------------------------------------------------------------------ vòng đời

    override fun onCreate(savedInstanceState: Bundle?) {
        val t0 = SystemClock.elapsedRealtimeNanos()
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("dymith", MODE_PRIVATE)
        Core.load()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true

        // Vẽ vỏ native ngay lập tức; tuyệt đối chưa chạm WebView trước async startup.
        setContentView(createStartupView())
        onCreateMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000.0

        webStartupStartedNs = SystemClock.elapsedRealtimeNanos()
        val startupConfig = WebViewStartUpConfig.Builder(webStartupExecutor).build()
        WebViewCompat.startUpWebView(
            applicationContext,
            startupConfig,
            object : WebViewOutcomeReceiver<WebViewStartUpResult, WebViewStartupException> {
                override fun onResult(result: WebViewStartUpResult) {
                    webStartupMs = (SystemClock.elapsedRealtimeNanos() - webStartupStartedNs) / 1_000_000.0
                    if (!isFinishing && !isDestroyed) initializeBrowser(savedInstanceState)
                }

                override fun onError(error: WebViewStartupException) {
                    webStartupMs = (SystemClock.elapsedRealtimeNanos() - webStartupStartedNs) / 1_000_000.0
                    android.util.Log.e("Dymith", "WebView async startup thất bại; dùng khởi tạo dự phòng", error)
                    if (!isFinishing && !isDestroyed) initializeBrowser(savedInstanceState)
                }
            }
        )
    }

    private fun createStartupView(): View {
        val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        return TextView(this).apply {
            text = "Dymith\nĐang khởi động…"
            textSize = 19f
            setTextColor(if (night) Color.WHITE else Color.BLACK)
            gravity = Gravity.CENTER
            setBackgroundColor(if (night) Color.BLACK else Color.WHITE)
            typeface = Typeface.create("sans-serif", Typeface.MEDIUM)
        }
    }

    private fun initializeBrowser(savedInstanceState: Bundle?) {
        buildUi()
        setContentView(root)

        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true

        applyUserAgentMode()

        val stats = FrameStats(window)
        stats.budgetUs = budgetFromDisplay()
        stats.start()
        frameStats = stats

        root.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                root.viewTreeObserver.removeOnPreDrawListener(this)
                firstDrawMs = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
                return true
            }
        })

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState)
        } else if (!handleIntent(deferredIntent ?: intent)) {
            loadStartPage()
        }
        deferredIntent = null
        if (hostResumed) web.onResume()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (::web.isInitialized) handleIntent(intent) else deferredIntent = intent
    }

    override fun onResume() {
        super.onResume()
        hostResumed = true
        if (::web.isInitialized) web.onResume()
    }

    override fun onPause() {
        hostResumed = false
        if (::web.isInitialized) web.onPause()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::web.isInitialized) web.saveState(outState)
    }

    override fun onDestroy() {
        frameStats?.stop()
        webStartupExecutor.shutdown()
        if (::root.isInitialized && ::web.isInitialized) root.removeView(web)
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!::web.isInitialized) {
            super.onBackPressed()
        } else if (omnibox.hasFocus()) {
            omnibox.clearFocus()
            hideKeyboard()
        } else if (web.canGoBack()) {
            web.goBack()
        } else {
            super.onBackPressed()
        }
    }

    private fun handleIntent(i: Intent?): Boolean {
        if (i == null || i.action != Intent.ACTION_VIEW) return false
        val data = i.data ?: return false
        web.loadUrl(data.toString())
        return true
    }

    // ------------------------------------------------------------------ giao diện

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun budgetFromDisplay(): Int {
        @Suppress("DEPRECATION")
        val hz = windowManager.defaultDisplay.refreshRate
        return if (hz > 1f) (1_000_000f / hz).toInt() else 16_667
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildUi() {
        root = FrameLayout(this)

        val tw = SystemClock.elapsedRealtimeNanos()
        web = WebView(this)
        webViewCreateMs = (SystemClock.elapsedRealtimeNanos() - tw) / 1_000_000.0
        configureWebView()
        installStartPageBridge()
        root.addView(web, FrameLayout.LayoutParams(MATCH, MATCH))

        progressBar = View(this)
        progressBar.setBackgroundColor(0xFF6B5CE7.toInt())
        progressBar.pivotX = 0f
        progressBar.scaleX = 0f
        progressBar.visibility = View.INVISIBLE
        val progressParams = FrameLayout.LayoutParams(MATCH, dp(3))
        progressParams.gravity = Gravity.TOP
        root.addView(progressBar, progressParams)

        // Thanh công cụ dạng viên thuốc, nổi ở đáy màn hình.
        bar = FrameLayout(this)
        bar.clipToOutline = true
        bar.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, view.height / 2f)
            }
        }

        tint = View(this)
        tint.background = tintDrawable()
        bar.addView(tint, FrameLayout.LayoutParams(MATCH, MATCH))

        btnBack = iconButton(R.drawable.ic_search, "Tìm kiếm") {
            omnibox.requestFocus()
            omnibox.setSelection(omnibox.text.length)
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(omnibox, InputMethodManager.SHOW_IMPLICIT)
        }
        btnReload = iconButton(R.drawable.ic_refresh, "Tải lại") {
            if (loading) web.stopLoading() else web.reload()
        }
        btnMenu = iconButton(R.drawable.ic_more, "Menu") { anchor -> showMenu(anchor) }
        omnibox = buildOmnibox()

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(8), 0, dp(8), 0)
        row.addView(btnBack, LinearLayout.LayoutParams(dp(42), dp(44)))
        row.addView(omnibox, LinearLayout.LayoutParams(0, dp(44), 1f))
        row.addView(btnReload, LinearLayout.LayoutParams(dp(40), dp(44)))
        row.addView(btnMenu, LinearLayout.LayoutParams(dp(40), dp(44)))
        bar.addView(row, FrameLayout.LayoutParams(MATCH, MATCH))

        val barParams = FrameLayout.LayoutParams(MATCH, dp(60))
        barParams.gravity = Gravity.BOTTOM
        barParams.setMargins(dp(14), 0, dp(14), dp(10))
        bar.elevation = dp(10).toFloat()
        root.addView(bar, barParams)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val sys = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            insetTop = sys.top
            insetLeft = sys.left
            insetRight = sys.right
            insetBottom = maxOf(sys.bottom, ime.bottom)
            applyInsets()
            insets
        }
        updateBackButton()
    }

    private fun applyInsets() {
        (web.layoutParams as FrameLayout.LayoutParams).topMargin = insetTop
        web.requestLayout()
        (progressBar.layoutParams as FrameLayout.LayoutParams).topMargin = insetTop
        progressBar.requestLayout()
        val lp = bar.layoutParams as FrameLayout.LayoutParams
        lp.leftMargin = dp(12) + insetLeft
        lp.rightMargin = dp(12) + insetRight
        lp.bottomMargin = dp(12) + insetBottom
        bar.requestLayout()
    }

    private fun iconButton(res: Int, desc: String, onClick: (View) -> Unit): ImageButton {
        val b = ImageButton(this)
        val iconName = when (res) {
            R.drawable.ic_search -> "search"
            R.drawable.ic_refresh -> "refresh"
            else -> "more"
        }
        b.setImageDrawable(CanvasIconDrawable(iconName, 0xFF26365D.toInt()))
        b.setBackgroundColor(Color.TRANSPARENT)
        b.scaleType = ImageView.ScaleType.CENTER_INSIDE
        b.setPadding(dp(11), dp(11), dp(11), dp(11))
        b.contentDescription = desc
        b.setOnClickListener { v -> onClick(v) }
        return b
    }

    private fun omniboxInputType(): Int {
        return if (prefs.getBoolean(PREF_KB_TEXT, false)) {
            InputType.TYPE_CLASS_TEXT
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
    }

    private fun buildOmnibox(): EditText {
        val e = EditText(this)
        e.setSingleLine(true)
        e.hint = "Tìm kiếm hoặc nhập địa chỉ web"
        e.setHintTextColor(0x99000000.toInt())
        e.setTextColor(0xFF111111.toInt())
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        e.background = null
        e.gravity = Gravity.CENTER_VERTICAL
        e.setPadding(dp(8), 0, dp(8), 0)
        e.imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        e.inputType = omniboxInputType()
        e.setSelectAllOnFocus(true)
        e.setOnEditorActionListener { v, actionId, event ->
            val enter = event != null &&
                event.keyCode == KeyEvent.KEYCODE_ENTER &&
                event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE || enter) {
                go(v.text.toString())
                true
            } else {
                false
            }
        }
        e.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) showBar()
        }
        return e
    }

    private fun tintDrawable(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(30).toFloat()
            setColor(0xFFF8F9FF.toInt())
            setStroke(dp(1), 0x1F6656DF)
        }
    }

    private fun hideBar() {
        if (barHidden) return
        barHidden = true
        val lp = bar.layoutParams as FrameLayout.LayoutParams
        bar.animate().translationY((bar.height + lp.bottomMargin + dp(8)).toFloat()).setDuration(180).start()
    }

    private fun showBar() {
        if (!barHidden) return
        barHidden = false
        bar.animate().translationY(0f).setDuration(180).start()
    }

    private fun onWebScrolled(dy: Int) {
        // Trang chủ tự sở hữu dock tìm kiếm ở đáy; không để thanh native cũ trồi lên khi cuộn.
        if (web.url?.startsWith(StartPage.URL) == true || omnibox.hasFocus()) return
        if (dy > dp(4)) {
            hideBar()
        } else if (dy < -dp(4)) {
            showBar()
        }
    }

    private fun updateBackButton() {
        btnBack.alpha = 1f
    }

    private fun setLoadingUi(isLoading: Boolean) {
        loading = isLoading
        btnReload.setImageDrawable(CanvasIconDrawable(
            if (isLoading) "close" else "refresh",
            0xFF26365D.toInt()
        ))
        if (!isLoading) progressBar.visibility = View.INVISIBLE
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(omnibox.windowToken, 0)
    }

    // ------------------------------------------------------------------ WebView

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.setSupportMultipleWindows(false)
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.mediaPlaybackRequiresUserGesture = true
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        s.safeBrowsingEnabled = true
        defaultUa = s.userAgentString ?: ""
        val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        web.setBackgroundColor(if (night) Color.BLACK else Color.WHITE)

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val scheme = request?.url?.scheme?.lowercase(Locale.ROOT) ?: return false
                return if (scheme == "http" || scheme == "https" || scheme == "about" ||
                    scheme == "data" || scheme == "blob"
                ) {
                    false
                } else {
                    toast("Phase 0 chưa mở liên kết kiểu " + scheme)
                    true
                }
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                if (request == null || request.isForMainFrame) return null
                val url = request.url.toString()
                if (!url.startsWith("http")) return null
                return if (Core.shouldBlock(url)) emptyResponse() else null
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                setLoadingUi(true)
                if (url != null) {
                    if (url.startsWith(StartPage.URL)) hideBar() else showBar()
                    if (!omnibox.hasFocus()) omnibox.setText(displayUrl(url))
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                setLoadingUi(false)
                if (url != null) {
                    if (url.startsWith(StartPage.URL)) hideBar() else showBar()
                }
                updateBackButton()
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                updateBackButton()
                if (url != null && !omnibox.hasFocus()) omnibox.setText(displayUrl(url))
            }

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                toast("Tiến trình hiển thị đã dừng, đang khởi động lại")
                recreate()
                return true
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.INVISIBLE
                progressBar.scaleX = newProgress / 100f
            }
        }

        web.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            try {
                val request = DownloadManager.Request(android.net.Uri.parse(url))
                request.setMimeType(mimeType)
                request.addRequestHeader("User-Agent", userAgent)
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                request.setDestinationInExternalPublicDir(
                    android.os.Environment.DIRECTORY_DOWNLOADS,
                    android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType)
                )
                (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                toast("Đang tải xuống")
            } catch (t: Throwable) {
                toast("Không thể bắt đầu tải xuống: ${t.message ?: "lỗi"}")
            }
        }

        web.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            onWebScrolled(scrollY - oldScrollY)
        }
    }

    private fun emptyResponse(): WebResourceResponse {
        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
    }

    private fun displayUrl(url: String): String {
        if (url.startsWith(StartPage.URL) || url == "about:blank") return ""
        return url.removePrefix("https://").removePrefix("http://").removeSuffix("/")
    }

    private fun loadStartPage() {
        hideBar()
        web.loadDataWithBaseURL(StartPage.URL, StartPage.HTML, "text/html", "utf-8", null)
        omnibox.setText("")
    }

    private fun navigateFromStartPage(rawUrl: String) {
        val uri = try { android.net.Uri.parse(rawUrl) } catch (_: Throwable) { null }
        val scheme = uri?.scheme?.lowercase(Locale.ROOT)
        if (uri == null || (scheme != "https" && scheme != "http") || uri.host.isNullOrBlank()) {
            toast("Địa chỉ không hợp lệ")
            return
        }
        showBar()
        web.loadUrl(uri.toString())
    }

    private fun go(raw: String) {
        val url = Core.resolve(raw, SEARCH_TEMPLATE)
        omnibox.clearFocus()
        hideKeyboard()
        if (url == "about:blank") loadStartPage() else web.loadUrl(url)
    }

    private fun cleanUserAgent(ua: String): String {
        var out = ua.replace("; wv)", ")")
        out = out.replace(Regex("Version/\\d+(\\.\\d+)*\\s"), "")
        return out + " Dymith/0.0.1"
    }

    private fun applyUserAgentMode() {
        val clean = prefs.getBoolean(PREF_UA_CLEAN, false)
        web.settings.userAgentString = if (clean) cleanUserAgent(defaultUa) else defaultUa
    }

    private fun installStartPageBridge() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            android.util.Log.w("Dymith", "WebMessageListener non supporté par ce fournisseur WebView")
            return
        }
        // L'objet JS n'est injecté que dans l'origine locale de la page d'accueil.
        // Aucun site visité ne reçoit d'API native privilégiée.
        WebViewCompat.addWebMessageListener(
            web,
            "Dymith",
            setOf("https://start.dymith.invalid"),
            object : WebViewCompat.WebMessageListener {
                override fun onPostMessage(
                    view: WebView,
                    message: WebMessageCompat,
                    sourceOrigin: android.net.Uri,
                    isMainFrame: Boolean,
                    replyProxy: androidx.webkit.JavaScriptReplyProxy
                ) {
                    if (!isMainFrame || sourceOrigin.host != "start.dymith.invalid") return
                    val payload = try { org.json.JSONObject(message.data) } catch (_: Throwable) { return }
                    when (payload.optString("action")) {
                        "panel" -> showBrowserPanel(payload.optString("name"))
                        "bookmark" -> saveCurrentBookmark()
                        "navigate" -> navigateFromStartPage(payload.optString("url"))
                        "address" -> {
                            omnibox.requestFocus()
                            omnibox.setSelection(omnibox.text.length)
                            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                                .showSoftInput(omnibox, InputMethodManager.SHOW_IMPLICIT)
                        }
                    }
                }
            }
        )
    }

    private fun saveCurrentBookmark() {
        val url = web.url ?: return
        if (url.startsWith(StartPage.URL) || url == "about:blank") {
            toast("Hãy mở một trang web trước")
            return
        }
        val list = prefs.getStringSet("bookmarks", emptySet())?.toMutableSet() ?: mutableSetOf()
        list.add(url)
        prefs.edit().putStringSet("bookmarks", list).apply()
        toast("Đã lưu dấu trang")
    }

    private fun showBrowserPanel(name: String) {
        when (name) {
            "downloads" -> {
                try { startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) }
                catch (_: Throwable) { toast("Không mở được mục tải xuống trên thiết bị") }
            }
            "bookmarks" -> {
                val items = prefs.getStringSet("bookmarks", emptySet())?.toList().orEmpty()
                if (items.isEmpty()) {
                    AlertDialog.Builder(this).setTitle("Dấu trang")
                        .setMessage("Chưa có dấu trang. Khi đang ở một trang web, chọn Lưu trang.")
                        .setPositiveButton("Đóng", null).show()
                } else {
                    AlertDialog.Builder(this).setTitle("Dấu trang")
                        .setItems(items.toTypedArray()) { _, which -> web.loadUrl(items[which]) }
                        .setNegativeButton("Đóng", null).show()
                }
            }
            "history" -> {
                val history = web.copyBackForwardList()
                val entries = (0 until history.size).map { history.getItemAtIndex(history.size - 1 - it) }
                    .filter { it.url != StartPage.URL && it.url != "about:blank" }.take(20)
                if (entries.isEmpty()) {
                    AlertDialog.Builder(this).setTitle("Lịch sử").setMessage("Chưa có lịch sử duyệt web.")
                        .setPositiveButton("Đóng", null).show()
                } else {
                    AlertDialog.Builder(this).setTitle("Lịch sử")
                        .setItems(entries.map { it.title?.takeIf(String::isNotBlank) ?: it.url }.toTypedArray()) { _, which ->
                            web.loadUrl(entries[which].url)
                        }.setNegativeButton("Đóng", null).show()
                }
            }
            "settings" -> {
                val options = arrayOf(
                    "Bàn phím ô địa chỉ: ${if (prefs.getBoolean(PREF_KB_TEXT, false)) "Văn bản" else "URL"}",
                    "User-Agent chuẩn trình duyệt: ${if (prefs.getBoolean(PREF_UA_CLEAN, false)) "Bật" else "Tắt"}",
                    "Chẩn đoán hiệu năng"
                )
                AlertDialog.Builder(this).setTitle("Cài đặt Dymith").setItems(options) { _, which ->
                    when (which) {
                        0 -> toggleKeyboardMode()
                        1 -> toggleUserAgent()
                        2 -> showDiagnostics()
                    }
                }.setNegativeButton("Đóng", null).show()
            }
        }
    }

    // ------------------------------------------------------------------ menu và chẩn đoán

    private fun showMenu(anchor: View) {
        val pm = PopupMenu(this, anchor)
        val m = pm.menu
        m.add(0, 1, 0, "Trang chủ")
        val kbItem = m.add(0, 3, 1, "Bàn phím ô địa chỉ: văn bản")
        kbItem.isCheckable = true
        kbItem.isChecked = prefs.getBoolean(PREF_KB_TEXT, false)
        val uaItem = m.add(0, 4, 2, "UA chuẩn trình duyệt (bỏ wv)")
        uaItem.isCheckable = true
        uaItem.isChecked = prefs.getBoolean(PREF_UA_CLEAN, false)
        m.add(0, 5, 3, "Chẩn đoán và báo cáo")
        m.add(0, 6, 4, "Dấu trang")
        m.add(0, 7, 5, "Lịch sử")
        m.add(0, 8, 6, "Lưu trang hiện tại")

        pm.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> loadStartPage()
                3 -> toggleKeyboardMode()
                4 -> toggleUserAgent()
                5 -> showDiagnostics()
                6 -> showBrowserPanel("bookmarks")
                7 -> showBrowserPanel("history")
                8 -> saveCurrentBookmark()
            }
            true
        }
        pm.show()
    }

    private fun toggleKeyboardMode() {
        val now = !prefs.getBoolean(PREF_KB_TEXT, false)
        prefs.edit().putBoolean(PREF_KB_TEXT, now).apply()
        omnibox.inputType = omniboxInputType()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.restartInput(omnibox)
        toast(if (now) "Bàn phím văn bản" else "Bàn phím địa chỉ (URL)")
    }

    private fun toggleUserAgent() {
        val now = !prefs.getBoolean(PREF_UA_CLEAN, false)
        prefs.edit().putBoolean(PREF_UA_CLEAN, now).apply()
        applyUserAgentMode()
        web.reload()
        toast(if (now) "UA chuẩn trình duyệt: bật" else "UA mặc định của WebView")
    }

    private fun showDiagnostics() {
        val glassText = "tắt; không chụp/làm mờ WebView theo từng khung hình"
        val firstDraw = if (firstDrawMs >= 0) firstDrawMs.toString() + " ms" else "chưa đo"

        val extras = ArrayList<Pair<String, String>>()
        extras.add(Pair("Khung đầu tiên (tính từ lúc tiến trình bắt đầu)", firstDraw))
        extras.add(Pair("Activity.onCreate", String.format(Locale.US, "%.1f ms", onCreateMs)))
        extras.add(Pair("WebView async startup", String.format(Locale.US, "%.1f ms", webStartupMs)))
        extras.add(Pair("Tạo WebView", String.format(Locale.US, "%.1f ms", webViewCreateMs)))
        extras.add(Pair("Hiệu ứng nền", glassText))
        extras.add(Pair("UA chuẩn trình duyệt", prefs.getBoolean(PREF_UA_CLEAN, false).toString()))
        extras.add(Pair("Bàn phím ô địa chỉ", if (prefs.getBoolean(PREF_KB_TEXT, false)) "văn bản" else "URL"))
        extras.add(Pair("User-Agent", web.settings.userAgentString ?: "?"))

        val text = Diagnostics.report(this, frameStats, extras)

        val tv = TextView(this)
        tv.text = text
        tv.typeface = Typeface.MONOSPACE
        tv.textSize = 11f
        tv.setTextIsSelectable(true)
        tv.setPadding(dp(16), dp(8), dp(16), dp(8))
        val sv = ScrollView(this)
        sv.addView(tv)

        AlertDialog.Builder(this)
            .setTitle("Chẩn đoán Dymith")
            .setView(sv)
            .setPositiveButton("Sao chép") { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Dymith report", text))
                toast("Đã sao chép báo cáo")
            }
            .setNegativeButton("Đóng", null)
            .show()
    }
}
