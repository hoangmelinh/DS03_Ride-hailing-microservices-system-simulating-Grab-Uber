# Hạ Tầng Hệ Thống (`infrastructure`)

Thư mục quản lý các thành phần hạ tầng dùng cho môi trường phát triển cục bộ và kiểm thử:

- **PostgreSQL**: Mỗi bounded context sở hữu một cơ sở dữ liệu/schema riêng biệt (`goride_user`, `goride_driver`, `goride_trip`, `goride_matching`, `goride_payment`, `goride_notification`). Nghiêm cấm truy vấn chéo hoặc tạo foreign key giữa các database.
- **RabbitMQ**: Message broker cho kiến trúc hướng sự kiện, cấu hình Topic Exchange `goride.events`, hàng đợi riêng cho từng service và cơ chế Dead Letter Queue (DLQ).
- **Redis**: Read model lưu trữ vị trí địa lý (GEO spatial index) và danh sách tài xế khả dụng phục vụ thuật toán tìm kiếm của Matching Service.
- **Observability**: Cấu hình Prometheus, Grafana, OpenTelemetry và Jaeger phục vụ giám sát số liệu và truy vết phân tán.
