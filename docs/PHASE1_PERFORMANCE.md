# Dymith — Performance/UI pass 1

## Mục tiêu thiết bị
HONOR WOD-LX2, MT6765, Android 14, WebView 157, 90 Hz, khoảng 3.7 GiB RAM.

## Phát hiện từ source
1. `GlassBackdrop` đăng ký `OnPreDrawListener`, và khi bật blur ghi lại `source.draw(canvas)` toàn bộ WebView rồi áp RenderEffect blur/color filter. Đây là thiết kế đắt đỏ và không phù hợp với đường cuộn mượt trên SoC tầm trung.
2. Trang chủ trước đó có nhiều gradient, shadow, pseudo-elements, nhiều card, placeholder tin tức và emoji thay ảnh thật.
3. Nút trang chủ trước đó có nhiều `<a href="#">` không có hành vi.
4. WebView được tạo đồng bộ ngay trong `onCreate`, trước khi giao diện có thể được vẽ.

## Thay đổi
- Xóa lớp GlassBackdrop khỏi đường chạy và project; thanh địa chỉ dùng nền opaque nhẹ.
- Dùng WebView asynchronous startup API từ AndroidX WebKit 1.16.0. App hiển thị shell native trước, đợi callback rồi mới tạo WebView và gọi WebView APIs.
- Tạo icon Canvas tĩnh cho toolbar native và canvas 2D cho dashboard. Không chạy animation hoặc vẽ theo requestAnimationFrame.
- Giảm CSS xuống ít lớp; bỏ hero image lớn, bỏ widget thời tiết giả, bỏ bóng đổ dày và hiệu ứng kính.
- Nút công cụ gọi `WebMessageListener` có allowlist đúng origin `https://start.dymith.invalid`; không expose `addJavascriptInterface` cho website bên ngoài. History dùng back-forward list của phiên WebView, bookmarks dùng SharedPreferences, downloads dùng DownloadManager.
- RSS chạy trên executor nền, có cache 15 phút, lấy metadata + thumbnail từ VnExpress/BBC.

## Giới hạn đã biết
- RSS feed và cấu trúc ảnh có thể thay đổi; parser có fallback không ảnh và cache.
- Lịch sử hiện lấy từ back-forward list của phiên WebView, chưa phải lịch sử lâu dài trong Room.
- Chưa có UI quản lý/thêm/xóa shortcut tùy chỉnh; mục bên cạnh Truy cập nhanh là lưu trang hiện tại.
- Hình nền lớn được loại bỏ; dịp đặc biệt chỉ hiện nhãn sự kiện và accent, chưa tải bộ ảnh lễ hội.
- Không chỉnh Rust vì source hiện tại không cho thấy JNI là nút thắt frame; cần profile Perfetto trước.

## Benchmark bắt buộc sau build
- Cold start: process start -> splash -> first browser content.
- Warm start: app background/foreground.
- Home scroll: 10 lượt cuộn liên tục.
- News image decode: cache cold và warm.
- Heavy page scroll: một trang có nhiều DOM/ads/video.
- Record Perfetto FrameTimeline, `FrameMetrics`, PSS, CPU time, WebView renderer process.
- So sánh p50/p90/p99, dropped frames và thời gian tạo WebView trước/sau.
