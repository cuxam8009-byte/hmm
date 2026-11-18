# Bài thử Phase 0

Mục tiêu: kiểm chứng đường ống và trả lời các câu hỏi rủi ro nhất bằng máy thật.
Sau khi thử, vào **Menu (3 chấm) > Chẩn đoán và báo cáo > Sao chép** rồi dán cho Claude.

## 1. Cài đặt và khởi động
- Cài APK từ mục Releases. Mở app, ghi lại "Khung đầu tiên" trong báo cáo.

## 2. Gõ tiếng Việt (Telex/VNI) ở ô địa chỉ
- Gõ "ban hang" bằng Telex, xem có ra đúng dấu không, có bị gạch chân/đứt chữ không.
- Menu > "Bàn phím ô địa chỉ: văn bản", thử lại và so sánh.
- Thử cả ô "Gõ tiếng Việt" trên trang khởi động (đây là bàn phím của WebView).

## 3. Đăng nhập Google
- Mở thẻ "Đăng nhập Google". Ghi lại có bị chặn không (thông báo "trình duyệt hoặc ứng dụng này có thể không an toàn").
- Menu > "UA chuẩn trình duyệt (bỏ wv)", tải lại và thử lần nữa.

## 4. Kính blur thật (Android 12 trở lên)
- Menu > "Kính blur thật (thử nghiệm)". Mở trang Wikipedia, cuộn lên xuống.
- Ghi lại: nền sau thanh có mờ đúng không, có nhấp nháy/đen/trống không.
- Chẩn đoán > dòng "Kính" cho biết số khung đã ghi và lỗi nếu có. Gửi kèm ảnh chụp màn hình.

## 5. Độ mượt và tài nguyên
- Cuộn Wikipedia khoảng 30 giây (kính tắt, rồi bật), mở Chẩn đoán và so sánh dòng "Độ mượt".
- Ghi lại "Bộ nhớ tiến trình vỏ (PSS)" lúc rảnh.

## 6. Chặn quảng cáo mẫu
- Mở "Thử chặn quảng cáo" và VnExpress. Chẩn đoán > "Chặn quảng cáo mẫu (JNI)":
  số request, số bị chặn, nanosecond trung bình mỗi lần gọi Rust.

## 7. Tốc độ engine
- Mở "Đo tốc độ" (browserbench.org), chạy Speedometer, ghi điểm. Chạy cùng trang đó trong Chrome để so sánh.

## Chưa có ở Phase 0
Nhiều tab, tải xuống, tải tệp lên, video toàn màn hình, lịch sử/dấu trang, chế độ tối, tìm trong trang.
