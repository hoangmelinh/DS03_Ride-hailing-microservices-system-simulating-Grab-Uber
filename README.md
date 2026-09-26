# DS03 – Hệ Thống Gọi Xe Phân Tán (GoRide)

> Đồ án môn học Các Hệ Thống Phân Tán (Distributed Systems). Mô phỏng nền tảng gọi xe công nghệ (kiến trúc Grab/Uber) với trọng tâm là microservices, kiến trúc hướng sự kiện (EDA), xử lý đồng thời, Saga orchestration, bounded retry, tính lũy kế (idempotency) và khả năng chịu lỗi (fault tolerance).

---

## 1. Tổng Quan Hệ Thống

GoRide bao gồm **6 microservice nghiệp vụ**, **1 API Gateway**, và các thành phần hạ tầng:

- **API Gateway (`services/api-gateway`)**: Định tuyến request, chuyển tiếp correlation ID, kiểm tra xác thực.
- **User Service (`services/user-service`)**: Quản lý tài khoản, xác thực JWT, phân quyền hành khách/tài xế và hồ sơ người dùng.
- **Driver Service (`services/driver-service`)**: Quản lý hồ sơ tài xế, vị trí giả lập, trạng thái và kiểm soát giữ chỗ nguyên tử (atomic reservation).
- **Trip Service (`services/trip-service`)**: Nguồn sự thật cho vòng đời chuyến đi và các chuyển đổi trạng thái chuyến.
- **Matching Service (`services/matching-service`)**: Xếp hạng ứng viên, quản lý read model vị trí tài xế trên Redis và điều phối Saga ghép cuốc.
- **Payment Service (`services/payment-service`)**: Mô phỏng xử lý thanh toán và phát hành sự kiện kết quả bất đồng bộ.
- **Notification Service (`services/notification-service`)**: Xử lý thông báo bất đồng bộ, minh họa cơ chế thử lại (retry) và Dead Letter Queue (DLQ).
- **Frontend Demo Client (`frontend/demo-client`)**: Giao diện React + Vite tối giản với 3 bảng điều khiển: Passenger, Driver, và System Monitor.

---

## 2. Công Nghệ Sử Dụng

- **Ngôn ngữ**: Java 21 LTS
- **Công cụ build**: Apache Maven (Cấu trúc Monorepo, từng service có thể build độc lập)
- **Framework**: Spring Boot 3.3.4, Spring Cloud 2023.0.3 (Spring Cloud Gateway)
- **Message Broker**: RabbitMQ 3.x (Topic Exchange `goride.events`, DLX/DLQ)
- **Cơ sở dữ liệu**: PostgreSQL (Mỗi service sở hữu database riêng biệt)
- **Bộ nhớ đệm / Địa lý**: Redis (Chỉ mục GEO phục vụ tìm kiếm tài xế)
- **Kiến trúc mã nguồn**: Clean Architecture / Hexagonal Architecture (Ports & Adapters) bên trong từng service
- **Kiểm thử**: JUnit 5, Mockito, Testcontainers, ArchUnit, k6

---

## 3. Cấu Trúc Thư Mục

```text
e:/DS03/
├── .agents/                    # Quy tắc và hướng dẫn cho AI agent (AGENTS.md)
├── docs/
│   ├── architecture/           # Tài liệu kiến trúc chuẩn (architecture.md)
│   ├── plan/                   # Kế hoạch dự án, phạm vi và test plan (DS03_Project_Plan.md)
│   └── adr/                    # Hồ sơ quyết định kiến trúc (template.md)
├── contracts/
│   ├── openapi/                # Hợp đồng REST API (OpenAPI 3.0+)
│   └── asyncapi/               # Hợp đồng sự kiện RabbitMQ (AsyncAPI)
├── libs/                       # Thư viện kỹ thuật dùng chung (không chứa domain)
├── services/                   # Các microservice và API Gateway
│   ├── api-gateway/            # Spring Cloud Gateway (Port 8080)
│   ├── user-service/           # User & Auth Service (Port 8081)
│   ├── driver-service/         # Driver & Reservation Service (Port 8082)
│   ├── trip-service/           # Trip Lifecycle Service (Port 8083)
│   ├── matching-service/       # Matching & Saga Orchestrator (Port 8084)
│   ├── payment-service/        # Payment Service (Port 8085)
│   └── notification-service/   # Notification & DLQ Service (Port 8086)
├── frontend/
│   └── demo-client/            # Demo Client React + Vite
├── infrastructure/             # Cấu hình hạ tầng Postgres, RabbitMQ, Redis
├── deploy/                     # Cấu hình Docker Compose và triển khai
├── tests/                      # Kịch bản kiểm thử đồng thời, chịu lỗi, hiệu năng
├── scripts/                    # Kịch bản khởi tạo, nạp dữ liệu và mô phỏng lỗi
├── .env.example                # Mẫu biến môi trường
├── .gitignore                  # Cấu hình bỏ qua file cho Git
├── pom.xml                     # Maven Parent POM gốc (Java 21)
└── README.md                   # Tài liệu hướng dẫn dự án
```

---

## 4. Hướng Dẫn Build Dự Án

### Yêu cầu môi trường
- **JDK 21** đã được cài đặt và cấu hình `JAVA_HOME`.
- **Apache Maven 3.9+**.

### Build toàn bộ hệ thống (từ thư mục gốc)
```bash
# Thiết lập JAVA_HOME trỏ tới JDK 21 (nếu cần trên Windows PowerShell)
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"

# Biên dịch và kiểm tra toàn bộ các module
mvn clean verify
```

### Build độc lập một microservice
Mỗi service kế thừa từ parent POM thông qua đường dẫn tương đối (`<relativePath>../../pom.xml</relativePath>`) nên có thể build hoàn toàn độc lập mà không cần build cả monorepo:
```bash
cd services/trip-service
mvn clean verify
```

---

## 5. Các Bất Biến Kiến Trúc Bắt Buộc (Architectural Invariants)
1. **Sở hữu dữ liệu**: Mỗi service sở hữu database riêng. Nghiêm cấm truy cập DB chéo hoặc tạo foreign key giữa các database.
2. **Clean Architecture**: Domain model thuần túy, không phụ thuộc vào framework (không có annotation Spring/JPA/RabbitMQ trong domain).
3. **Giữ chỗ nguyên tử**: Driver Service kiểm soát giữ chỗ tài xế duy nhất bằng điều kiện nguyên tử ở mức database.
4. **Saga Orchestration**: Matching Service điều phối việc chọn ứng viên, giữ chỗ tài xế và hành động bù trừ (compensation) khi gán chuyến thất bại.
5. **Tin cậy trong giao tiếp**: Transactional Outbox khi phát hành sự kiện quan trọng; Inbox / deduplication bền vững khi nhận sự kiện.
