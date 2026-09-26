# Kịch Bản Tự Động Hóa & Vận Hành (`scripts`)

Thư mục quản lý các kịch bản phục vụ việc thiết lập môi trường, chuẩn bị dữ liệu và mô phỏng lỗi khi demo:

- **Khởi tạo môi trường (Bootstrap)**: Kiểm tra phiên bản JDK 21, Maven 3.9+, và tạo các container cơ sở dữ liệu.
- **Dữ liệu mẫu (Data Seeding)**: Nạp trước danh sách hành khách, tài xế với tọa độ cố định để kịch bản demo luôn tái lập được (deterministic).
- **Mô phỏng lỗi (Fault Injection)**: Các script tắt/bật container (Notification Service, Payment Service) hoặc giả lập timeout nhằm minh họa cơ chế tự phục hồi, retry và Saga compensation trong buổi vấn đáp.
