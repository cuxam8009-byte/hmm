# Dymith Browser

Dymith là trình duyệt Android nhẹ, dùng WebView hệ thống, vỏ Kotlin và lõi Rust.

## Trang chủ mới

`StartPage.kt` đã được thay hoàn toàn bằng giao diện HTML/CSS/JS tối giản do người dùng cung cấp và tinh chỉnh cho WebView Android:

- Bố cục đen/trắng, đồng hồ lớn, ngày tháng, lời chào theo giờ.
- Lối tắt website bố cục vòm 4 cột; favicon tải lazy, có fallback chữ khi lỗi.
- Thanh tìm kiếm/dock cố định ở đáy, gợi ý truy vấn, lịch sử tìm gần đây và chọn Google / DuckDuckGo / Bing / Cốc Cốc.
- Theme tự động theo hệ thống hoặc ép sáng/tối; lưu lựa chọn trong localStorage.
- Safe-area cho status/navigation bar, `100dvh`, xử lý bàn phím co viewport và `prefers-reduced-motion`.
- Không có framework UI, ảnh nền lớn, feed tin tức, thư viện icon ngoài, canvas animation hay blur.

## Tích hợp Android

- Trang chủ dùng `WebMessageListener` chỉ cho origin `https://start.dymith.invalid`.
- Hành động `navigate` được Kotlin kiểm tra scheme/host rồi mới gọi WebView; không dùng `window.open` giả lập.
- Thanh địa chỉ native tự ẩn khi ở trang chủ (vì trang chủ có dock riêng), tự hiện trên website; không bị bật lại do cuộn trang chủ.
- Các trang web bên ngoài vẫn dùng thanh địa chỉ native, tải xuống, lịch sử, dấu trang và menu trình duyệt hiện có.
- RSS engine cũ được gỡ khỏi đường khởi động và khỏi source pass này để tránh tác vụ mạng/parse không cần thiết trên trang chủ.

## Tối ưu hiệu năng

- HTML/CSS/JS thuần, không framework và không bundle runtime.
- Chỉ một timer đồng hồ, canh đúng ranh giới phút và tạm dừng khi WebView ẩn.
- Favicon dùng `loading=lazy`, `decoding=async`; không tải ảnh hero.
- Giảm thời lượng animation mở lối tắt; tắt animation khi hệ thống bật reduced motion.
- Rust/JNI/URL resolver/ad-block giữ nguyên vì báo cáo hiện tại chưa chứng minh chúng là nút thắt của jank giao diện.

## Build

Dùng workflow GitHub Actions hiện tại hoặc `ci/build.sh` trên runner có JDK 17/21, Android SDK/NDK và Rust target `aarch64-linux-android`. Repo không chứa Gradle wrapper.

## Kiểm thử cần thực hiện trên HONOR MT6765

1. Mở app nguội và mở lại từ nền.
2. Nhập từ khóa, nhập URL, đổi công cụ tìm kiếm.
3. Chạm các lối tắt và kiểm tra điều hướng trong cùng WebView.
4. Mở panel gợi ý, chọn lịch sử, xóa lịch sử.
5. Mở bàn phím, xoay màn hình, bật theme tối/sáng.
6. Cuộn trang chủ: thanh địa chỉ native không được xuất hiện.
7. Mở website: thanh địa chỉ native phải hiện và vẫn hoạt động.
8. Đo p50/p90/p99 và dropped frames bằng cùng kịch bản Perfetto/FrameMetrics trước đó.

Chưa tuyên bố FPS hay thời gian khởi động cải thiện cho tới khi có benchmark trên thiết bị thật.
