# Thư Viện Kỹ Thuật Dùng Chung (`libs`)

Tuân thủ nguyên tắc kiến trúc Clean Architecture theo **[architecture.md](../docs/architecture/architecture.md)** và **[.agents/AGENTS.md](../.agents/AGENTS.md)**:

## Nguyên tắc phân định:
1. **CHỈ CHỨA CÁC VẤN ĐỀ KỸ THUẬT (Technical Concerns)**:
   - Các tiện ích đo kiểm và logging phân tán (OpenTelemetry, Micrometer, Correlation ID filter).
   - Các tiện ích phân giải/xác thực JWT dùng chung.
   - Các tiện ích tuần tự hóa envelope sự kiện RabbitMQ.
2. **TUYỆT ĐỐI KHÔNG TẠO THƯ VIỆN CHỨA DOMAIN NGHIỆP VỤ**:
   - Không chia sẻ các domain model nghiệp vụ (`Trip`, `Driver`, `Payment`).
   - Không chia sẻ DTO nghiệp vụ giữa các service.
   - Không chia sẻ JPA repository hoặc logic truy vấn cơ sở dữ liệu.

Mỗi microservice tự quản lý domain model và logic nghiệp vụ bên trong bounded context của mình.
