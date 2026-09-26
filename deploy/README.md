# Cấu Hình Triển Khai (`deploy`)

Thư mục này chứa cấu hình triển khai Docker Compose và kịch bản khởi chạy hệ thống cục bộ:

- `docker-compose.yml`: Triển khai các container hạ tầng (PostgreSQL, RabbitMQ, Redis) và các microservice.
- Service discovery dựa trên cơ chế DNS nội bộ của Docker Compose thông qua tên container (ví dụ `http://driver-service:8080`).
- Mọi biến môi trường và bí mật được nạp tự động từ file `.env` (tạo từ `.env.example`).
