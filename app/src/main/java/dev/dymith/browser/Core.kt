package dev.dymith.browser

import android.net.Uri
import android.util.Log
import java.util.concurrent.atomic.AtomicLong

/**
 * Cầu nối sang lõi Rust (libdymith_core.so).
 * Nếu thư viện không nạp được, ứng dụng vẫn chạy với bản dự phòng bằng Kotlin
 * và màn hình chẩn đoán sẽ hiển thị lý do.
 */
object Core {
    private const val TAG = "DymithCore"
    private const val DEFAULT_SEARCH = "https://www.google.com/search?q=%s"

    @Volatile
    var loaded: Boolean = false
        private set

    @Volatile
    var loadError: String? = null
        private set

    @Volatile
    var loadNanos: Long = 0L
        private set

    // Số liệu đo đường nóng (mỗi request con đi qua JNI một lần).
    val requestsSeen = AtomicLong()
    val requestsBlocked = AtomicLong()
    val matchNanos = AtomicLong()

    fun load() {
        if (loaded) return
        val t0 = System.nanoTime()
        try {
            System.loadLibrary("dymith_core")
            loaded = true
        } catch (t: Throwable) {
            loadError = t.javaClass.simpleName + ": " + (t.message ?: "")
            Log.e(TAG, "Không nạp được lõi Rust", t)
        } finally {
            loadNanos = System.nanoTime() - t0
        }
    }

    fun version(): String {
        if (!loaded) return "CHƯA NẠP (" + (loadError ?: "?") + ")"
        return try {
            nativeVersion()
        } catch (t: Throwable) {
            "lỗi: " + (t.message ?: t.javaClass.simpleName)
        }
    }

    fun resolve(input: String, template: String = DEFAULT_SEARCH): String {
        if (loaded) {
            try {
                return nativeResolve(input, template)
            } catch (t: Throwable) {
                Log.e(TAG, "resolve lỗi", t)
            }
        }
        return fallbackResolve(input, template)
    }

    fun shouldBlock(url: String): Boolean {
        if (!loaded) return false
        val t0 = System.nanoTime()
        val blocked = try {
            nativeShouldBlock(url)
        } catch (t: Throwable) {
            false
        }
        matchNanos.addAndGet(System.nanoTime() - t0)
        requestsSeen.incrementAndGet()
        if (blocked) requestsBlocked.incrementAndGet()
        return blocked
    }

    private fun fallbackResolve(input: String, template: String): String {
        val s = input.trim()
        if (s.isEmpty()) return "about:blank"
        if (s.startsWith("http://") || s.startsWith("https://")) return s
        return template.replace("%s", Uri.encode(s))
    }

    @JvmStatic
    external fun nativeVersion(): String

    @JvmStatic
    external fun nativeResolve(input: String, template: String): String

    @JvmStatic
    external fun nativeShouldBlock(url: String): Boolean
}
