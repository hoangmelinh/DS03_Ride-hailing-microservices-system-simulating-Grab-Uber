# TÀI LIỆU THIẾT KẾ CƠ SỞ DỮ LIỆU

## DS03 – Ride-Hailing Microservices System (GoRide)

**Học phần:** Distributed Systems  
**Phiên bản:** 1.0  
**Trạng thái:** APPROVED – Baseline để triển khai  
**Ngày:** 27/09/2026  
**Phạm vi:** Baseline logical database cho 6 bounded context; làm nguồn tham chiếu để triển khai Flyway/JPA

---

## 1. Mục đích tài liệu

Tài liệu này mô tả thiết kế cơ sở dữ liệu đề xuất cho hệ thống GoRide theo kiến trúc Microservices + Clean Architecture + Hexagonal Architecture + DDD-lite + Event-Driven Architecture.

Mục tiêu chính:

- Chốt ranh giới sở hữu dữ liệu giữa các microservice.
- Xác định logical schema, bảng, trường, khóa, constraint và index.
- Hỗ trợ các yêu cầu Distributed Systems: concurrency control, Saga, idempotency, retry, deduplication và eventual consistency.
- Làm cơ sở để nhóm review trước khi sinh Flyway migration và JPA adapter.
- Tránh thiết kế shared database hoặc cross-service foreign key.

> Tài liệu này là **Database Design Baseline v1.0 đã được duyệt**. Agent và developer phải tuân thủ thiết kế này khi triển khai Flyway/JPA. Mọi thay đổi làm lệch database ownership, bảng nghiệp vụ cốt lõi, invariant, Saga persistence hoặc reliability strategy phải được đề xuất và ghi nhận bằng ADR trước khi triển khai.

---

## 2. Nguyên tắc thiết kế

1. Mỗi business service sở hữu dữ liệu của riêng mình.
2. Service không được đọc/ghi trực tiếp database của service khác.
3. Không tạo foreign key xuyên bounded context.
4. Cross-service reference dùng UUID logic.
5. PostgreSQL là source of truth cho business state.
6. Redis của Matching Service chỉ là read projection, không phải source of truth.
7. Driver reservation phải được bảo vệ bằng atomic database operation và unique constraint.
8. Distributed transaction xử lý bằng Saga/compensation, không dùng distributed ACID transaction.
9. Critical event publication dùng Transactional Outbox.
10. Event consumer quan trọng dùng Inbox/deduplication.
11. Operation có thể retry phải có idempotency strategy.
12. Dùng Flyway cho mọi thay đổi schema sau khi thiết kế được duyệt.

---

## 3. Mô hình triển khai database

Trong local/dev có thể dùng một PostgreSQL instance, nhưng tạo sáu database logic độc lập:

```text
PostgreSQL
├── goride_user
├── goride_driver
├── goride_trip
├── goride_matching
├── goride_payment
└── goride_notification
```

Mỗi service dùng credential riêng và chỉ có quyền trên database của mình.

| Service | Database |
|---|---|
| User Service | `goride_user` |
| Driver Service | `goride_driver` |
| Trip Service | `goride_trip` |
| Matching Service | `goride_matching` |
| Payment Service | `goride_payment` |
| Notification Service | `goride_notification` |

---

## 4. Quy ước dữ liệu chung

| Hạng mục | Quy ước |
|---|---|
| Primary key | `UUID` |
| Timestamp | `TIMESTAMPTZ`, lưu UTC |
| Tiền | `NUMERIC(12,2)` |
| Latitude | `NUMERIC(9,6)` |
| Longitude | `NUMERIC(9,6)` |
| Status | `VARCHAR` + `CHECK` constraint |
| Event payload | `JSONB` |
| Naming DB | `snake_case` |
| Naming Java | `camelCase` |
| Concurrency version | `BIGINT version` |
| Cross-service reference | UUID logic, không FK |

---

# 5. USER SERVICE DATABASE

**Database:** `goride_user`

## 5.1. Bảng `users`

| Cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
|---|---|---|---|
| `id` | UUID | PK, NOT NULL | Định danh user |
| `phone` | VARCHAR(20) | UNIQUE, NOT NULL | Số điện thoại đăng nhập |
| `email` | VARCHAR(255) | UNIQUE, NULL | Email |
| `password_hash` | VARCHAR(255) | NOT NULL | BCrypt/Argon hash, không lưu plaintext |
| `full_name` | VARCHAR(150) | NOT NULL | Họ tên |
| `status` | VARCHAR(20) | NOT NULL, CHECK | `ACTIVE`, `LOCKED`, `DISABLED` |
| `created_at` | TIMESTAMPTZ | NOT NULL | Thời điểm tạo |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Thời điểm cập nhật |

## 5.2. Bảng `roles`

| Cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
|---|---|---|---|
| `id` | SMALLINT | PK | ID role |
| `code` | VARCHAR(30) | UNIQUE, NOT NULL | `PASSENGER`, `DRIVER`, `ADMIN` |

## 5.3. Bảng `user_roles`

| Cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
|---|---|---|---|
| `user_id` | UUID | FK -> `users.id` | User |
| `role_id` | SMALLINT | FK -> `roles.id` | Role |
| PK | - | (`user_id`,`role_id`) | Composite primary key |

Thiết kế này cho phép một account có thể đồng thời có nhiều role, ví dụ `PASSENGER + DRIVER`.

## 5.4. Bảng `refresh_tokens`

| Cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
|---|---|---|---|
| `id` | UUID | PK | Token record |
| `user_id` | UUID | FK -> `users.id` | Chủ sở hữu |
| `token_hash` | VARCHAR(255) | UNIQUE, NOT NULL | Hash của refresh token |
| `expires_at` | TIMESTAMPTZ | NOT NULL | Hết hạn |
| `revoked_at` | TIMESTAMPTZ | NULL | Thời điểm revoke |
| `created_at` | TIMESTAMPTZ | NOT NULL | Tạo token |

---

# 6. DRIVER SERVICE DATABASE

**Database:** `goride_driver`

Driver Service là source of truth cho trạng thái và reservation của tài xế.

## 6.1. Bảng `drivers`

| Cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
|---|---|---|---|
| `id` | UUID | PK | Driver ID |
| `user_id` | UUID | UNIQUE, NOT NULL | Logical ref -> User Service |
| `license_number` | VARCHAR(50) | UNIQUE, NOT NULL | GPLX |
| `status` | VARCHAR(20) | NOT NULL, CHECK | `OFFLINE`, `AVAILABLE`, `RESERVED`, `ON_TRIP` |
| `current_trip_id` | UUID | NULL | Logical ref -> Trip Service |
| `reserved_until` | TIMESTAMPTZ | NULL | Hạn reservation |
| `version` | BIGINT | DEFAULT 0 | Optimistic/concurrency version |
| `created_at` | TIMESTAMPTZ | NOT NULL | Tạo |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Cập nhật |

## 6.2. Bảng `vehicles`

| Cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
|---|---|---|---|
| `id` | UUID | PK | Vehicle ID |
| `driver_id` | UUID | FK -> `drivers.id` | Chủ phương tiện |
| `plate_number` | VARCHAR(20) | UNIQUE, NOT NULL | Biển số |
| `brand` | VARCHAR(80) | NULL | Hãng |
| `model` | VARCHAR(80) | NULL | Mẫu xe |
| `color` | VARCHAR(50) | NULL | Màu |
| `capacity` | SMALLINT | NULL | Số chỗ |
| `status` | VARCHAR(20) | CHECK | `ACTIVE`, `INACTIVE` |
| `created_at` | TIMESTAMPTZ | NOT NULL | Tạo |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Cập nhật |

## 6.3. Bảng `driver_locations`

Chỉ lưu vị trí mới nhất trong PostgreSQL; Matching Service duy trì Redis GEO projection.

| Cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
|---|---|---|---|
| `driver_id` | UUID | PK, FK -> `drivers.id` | Driver |
| `latitude` | NUMERIC(9,6) | NOT NULL | Vĩ độ |
| `longitude` | NUMERIC(9,6) | NOT NULL | Kinh độ |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Lần cập nhật |

## 6.4. Bảng `driver_reservations`

| Cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
|---|---|---|---|
| `id` | UUID | PK | Reservation ID |
| `driver_id` | UUID | FK -> `drivers.id` | Driver |
| `trip_id` | UUID | NOT NULL | Logical ref -> Trip |
| `status` | VARCHAR(20) | CHECK | `RESERVED`, `CONFIRMED`, `RELEASED`, `EXPIRED` |
| `idempotency_key` | VARCHAR(100) | UNIQUE, NOT NULL | Retry-safe command key |
| `correlation_id` | UUID | NOT NULL | Theo dõi distributed flow |
| `reserved_at` | TIMESTAMPTZ | NOT NULL | Bắt đầu reserve |
| `expires_at` | TIMESTAMPTZ | NOT NULL | Hết hạn |
| `confirmed_at` | TIMESTAMPTZ | NULL | Confirm |
| `released_at` | TIMESTAMPTZ | NULL | Release |
| `created_at` | TIMESTAMPTZ | NOT NULL | Tạo |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Cập nhật |

### Constraint quan trọng

```sql
CREATE UNIQUE INDEX uq_driver_active_reservation
ON driver_reservations(driver_id)
WHERE status IN ('RESERVED', 'CONFIRMED');
```

```sql
CREATE UNIQUE INDEX uq_trip_active_driver
ON driver_reservations(trip_id)
WHERE status IN ('RESERVED', 'CONFIRMED');
```

### Atomic reservation

```sql
UPDATE drivers
SET
    status = 'RESERVED',
    current_trip_id = :tripId,
    reserved_until = :expiresAt,
    version = version + 1,
    updated_at = NOW()
WHERE id = :driverId
  AND status = 'AVAILABLE';
```

- `affectedRows = 1`: reservation thành công.
- `affectedRows = 0`: driver đã bị request khác giữ hoặc không AVAILABLE.

`UPDATE drivers` và `INSERT driver_reservations` phải nằm trong cùng một local transaction.

---

# 7. TRIP SERVICE DATABASE

**Database:** `goride_trip`

## 7.1. Bảng `trips`

| Cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
|---|---|---|---|
| `id` | UUID | PK | Trip ID |
| `passenger_id` | UUID | NOT NULL | Logical ref -> User |
| `driver_id` | UUID | NULL | Logical ref -> Driver |
| `pickup_latitude` | NUMERIC(9,6) | NOT NULL | Điểm đón |
| `pickup_longitude` | NUMERIC(9,6) | NOT NULL | Điểm đón |
| `pickup_address` | VARCHAR(255) | NULL | Địa chỉ hiển thị |
| `destination_latitude` | NUMERIC(9,6) | NOT NULL | Điểm đến |
| `destination_longitude` | NUMERIC(9,6) | NOT NULL | Điểm đến |
| `destination_address` | VARCHAR(255) | NULL | Địa chỉ hiển thị |
| `status` | VARCHAR(30) | NOT NULL, CHECK | Trạng thái trip |
| `estimated_fare` | NUMERIC(12,2) | NULL | Giá ước tính |
| `final_fare` | NUMERIC(12,2) | NULL | Giá cuối |
| `currency` | CHAR(3) | DEFAULT `VND` | Tiền tệ |
| `requested_at` | TIMESTAMPTZ | NOT NULL | Request |
| `assigned_at` | TIMESTAMPTZ | NULL | Gán driver |
| `accepted_at` | TIMESTAMPTZ | NULL | Driver nhận |
| `started_at` | TIMESTAMPTZ | NULL | Bắt đầu |
| `completed_at` | TIMESTAMPTZ | NULL | Kết thúc |
| `cancelled_at` | TIMESTAMPTZ | NULL | Hủy |
| `cancellation_reason` | VARCHAR(255) | NULL | Lý do hủy |
| `version` | BIGINT | DEFAULT 0 | Concurrency version |
| `created_at` | TIMESTAMPTZ | NOT NULL | Tạo |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Cập nhật |

### Trip lifecycle đề xuất

```text
REQUESTED
  -> MATCHING
  -> DRIVER_ASSIGNED
  -> ACCEPTED
  -> IN_PROGRESS
  -> COMPLETED
  -> PAYMENT_PENDING
       -> PAID
       -> PAYMENT_FAILED

Ngoại lệ:
CANCELLED
```

## 7.2. Bảng `trip_status_history`

| Cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
|---|---|---|---|
| `id` | UUID | PK | History ID |
| `trip_id` | UUID | FK -> `trips.id` | Trip |
| `from_status` | VARCHAR(30) | NULL | Trạng thái cũ |
| `to_status` | VARCHAR(30) | NOT NULL | Trạng thái mới |
| `reason` | VARCHAR(255) | NULL | Lý do |
| `actor_type` | VARCHAR(30) | NOT NULL | `USER`, `DRIVER`, `SYSTEM`, `MATCHING`, `PAYMENT` |
| `actor_id` | UUID | NULL | Logical actor ID |
| `correlation_id` | UUID | NOT NULL | Distributed flow |
| `changed_at` | TIMESTAMPTZ | NOT NULL | Thời điểm |

### Index đề xuất

```sql
CREATE INDEX idx_trips_passenger_time
ON trips(passenger_id, requested_at DESC);

CREATE INDEX idx_trips_driver_status
ON trips(driver_id, status);

CREATE INDEX idx_trips_status
ON trips(status);
```

---

# 8. MATCHING SERVICE DATABASE

**Database:** `goride_matching`

Matching Service sở hữu Saga state và matching attempts; không sở hữu Driver/Trip business state.

## 8.1. Bảng `matching_sagas`

| Cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
|---|---|---|---|
| `id` | UUID | PK | Saga ID |
| `trip_id` | UUID | UNIQUE, NOT NULL | Logical ref -> Trip |
| `selected_driver_id` | UUID | NULL | Logical ref -> Driver |
| `status` | VARCHAR(30) | NOT NULL, CHECK | Saga state |
| `current_step` | VARCHAR(50) | NULL | Bước hiện tại |
| `correlation_id` | UUID | UNIQUE, NOT NULL | Flow ID |
| `attempt_count` | INTEGER | DEFAULT 0 | Số lần thử |
| `last_error_code` | VARCHAR(100) | NULL | Lỗi cuối |
| `last_error_message` | VARCHAR(500) | NULL | Chi tiết lỗi |
| `version` | BIGINT | DEFAULT 0 | Concurrent update control |
| `started_at` | TIMESTAMPTZ | NOT NULL | Bắt đầu |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Cập nhật |
| `completed_at` | TIMESTAMPTZ | NULL | Kết thúc |

Saga states:

```text
STARTED
CANDIDATE_SELECTED
DRIVER_RESERVED
TRIP_ASSIGNED
COMPLETED
COMPENSATING
COMPENSATED
FAILED
```

## 8.2. Bảng `matching_attempts`

| Cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
|---|---|---|---|
| `id` | UUID | PK | Attempt ID |
| `saga_id` | UUID | FK -> `matching_sagas.id` | Saga |
| `driver_id` | UUID | NOT NULL | Candidate |
| `candidate_rank` | INTEGER | NOT NULL | Thứ tự |
| `distance_meters` | INTEGER | NULL | Khoảng cách |
| `attempt_no` | INTEGER | NOT NULL | Lần thử |
| `reserve_result` | VARCHAR(30) | CHECK | `SUCCESS`, `DRIVER_BUSY`, `TIMEOUT`, `ERROR` |
| `failure_code` | VARCHAR(100) | NULL | Mã lỗi |
| `failure_message` | VARCHAR(500) | NULL | Lỗi |
| `created_at` | TIMESTAMPTZ | NOT NULL | Thời điểm |

---

# 9. REDIS READ MODEL CHO MATCHING

Redis chỉ là projection.

```text
driver:geo
driver:meta:{driverId}
```

Ví dụ:

```text
GEOADD driver:geo 105.8342 21.0285 D01
```

`driver:meta:D01`:

```text
status=AVAILABLE
version=14
updatedAt=...
```

Matching có thể dùng `GEOSEARCH` để lấy candidate gần nhất, nhưng reservation cuối cùng vẫn phải gọi Driver Service.

---

# 10. PAYMENT SERVICE DATABASE

**Database:** `goride_payment`

## 10.1. Bảng `payments`

| Cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
|---|---|---|---|
| `id` | UUID | PK | Payment ID |
| `trip_id` | UUID | UNIQUE, NOT NULL | Logical ref -> Trip |
| `passenger_id` | UUID | NOT NULL | Logical ref -> User |
| `amount` | NUMERIC(12,2) | NOT NULL | Số tiền |
| `currency` | CHAR(3) | DEFAULT `VND` | Tiền tệ |
| `status` | VARCHAR(20) | CHECK | `PENDING`, `PROCESSING`, `SUCCEEDED`, `FAILED` |
| `provider` | VARCHAR(30) | DEFAULT `SIMULATED` | Provider |
| `provider_reference` | VARCHAR(100) | NULL | Ref giả lập |
| `failure_code` | VARCHAR(100) | NULL | Mã lỗi |
| `failure_message` | VARCHAR(500) | NULL | Lỗi |
| `created_at` | TIMESTAMPTZ | NOT NULL | Tạo |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Cập nhật |
| `completed_at` | TIMESTAMPTZ | NULL | Hoàn tất |

## 10.2. Bảng `payment_attempts`

| Cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
|---|---|---|---|
| `id` | UUID | PK | Attempt |
| `payment_id` | UUID | FK -> `payments.id` | Payment |
| `attempt_no` | INTEGER | NOT NULL | Lần thử |
| `status` | VARCHAR(20) | CHECK | `STARTED`, `SUCCESS`, `FAILED` |
| `failure_code` | VARCHAR(100) | NULL | Lỗi |
| `failure_message` | VARCHAR(500) | NULL | Chi tiết |
| `requested_at` | TIMESTAMPTZ | NOT NULL | Request |
| `completed_at` | TIMESTAMPTZ | NULL | Hoàn tất |

---

# 11. NOTIFICATION SERVICE DATABASE

**Database:** `goride_notification`

## 11.1. Bảng `notifications`

| Cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
|---|---|---|---|
| `id` | UUID | PK | Notification ID |
| `source_event_id` | UUID | NOT NULL | Event nguồn |
| `recipient_user_id` | UUID | NOT NULL | Người nhận |
| `recipient_role` | VARCHAR(30) | NULL | Role |
| `trip_id` | UUID | NULL | Logical Trip |
| `type` | VARCHAR(50) | NOT NULL | Loại notification |
| `channel` | VARCHAR(20) | DEFAULT `SIMULATED` | Kênh |
| `status` | VARCHAR(20) | CHECK | `PENDING`, `SENT`, `FAILED` |
| `payload` | JSONB | NOT NULL | Nội dung |
| `attempt_count` | INTEGER | DEFAULT 0 | Retry count |
| `last_error` | VARCHAR(500) | NULL | Lỗi cuối |
| `created_at` | TIMESTAMPTZ | NOT NULL | Tạo |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Cập nhật |
| `sent_at` | TIMESTAMPTZ | NULL | Gửi thành công |

Constraint:

```sql
UNIQUE (source_event_id, recipient_user_id, type)
```

---

# 12. CÁC BẢNG KỸ THUẬT CHO RELIABILITY

Các bảng dưới đây dùng cùng một mẫu thiết kế nhưng tồn tại riêng trong database của từng service cần sử dụng.

## 12.1. `outbox_event`

| Cột | Kiểu dữ liệu | Mô tả |
|---|---|---|
| `id` | UUID PK | Event ID |
| `aggregate_type` | VARCHAR(50) | Loại aggregate |
| `aggregate_id` | UUID | Aggregate ID |
| `event_type` | VARCHAR(100) | Event name |
| `event_version` | INTEGER | Contract version |
| `payload` | JSONB | Payload |
| `correlation_id` | UUID | Flow |
| `causation_id` | UUID NULL | Event/request gây ra |
| `occurred_at` | TIMESTAMPTZ | Business time |
| `status` | VARCHAR(20) | `PENDING`, `PUBLISHED`, `FAILED` |
| `retry_count` | INTEGER | Retry |
| `next_retry_at` | TIMESTAMPTZ NULL | Retry tiếp |
| `published_at` | TIMESTAMPTZ NULL | Publish success |
| `created_at` | TIMESTAMPTZ | Persist |

Index:

```sql
CREATE INDEX idx_outbox_pending
ON outbox_event(status, next_retry_at);
```

> **Nguyên tắc cốt lõi về Transactional Outbox (outbox_event):**  
> Transactional Outbox là mẫu thiết kế bắt buộc được tích hợp trực tiếp và đồng bộ trong toàn bộ các service phát hành sự kiện nghiệp vụ (`user-service`, `driver-service`, `trip-service`, `matching-service`, `payment-service`). Nhằm triệt tiêu hoàn toàn rủi ro Dual-Write Problem và bảo đảm tính nhất quán cuối cùng (Eventual Consistency), mọi business event đều phải được ghi nguyên tử cùng local transaction với business entity vào bảng `outbox_event`. Sau đó, Outbox Poller/Publisher sẽ đọc định kỳ và phát hành tin cậy sang RabbitMQ với cơ chế bounded retry và bảo đảm ngữ nghĩa at-least-once delivery.

## 12.2. `inbox_event`

| Cột | Kiểu dữ liệu | Mô tả |
|---|---|---|
| `event_id` | UUID PK | Dedup key |
| `event_type` | VARCHAR(100) | Event |
| `consumer` | VARCHAR(100) | Consumer name |
| `correlation_id` | UUID | Flow |
| `processed_at` | TIMESTAMPTZ | Xử lý |

## 12.3. `idempotency_record`

| Cột | Kiểu dữ liệu | Mô tả |
|---|---|---|
| `idempotency_key` | VARCHAR(100) PK | Retry key |
| `operation` | VARCHAR(100) | Operation |
| `request_hash` | VARCHAR(128) | Chống reuse key với request khác |
| `status` | VARCHAR(20) | `PROCESSING`, `COMPLETED`, `FAILED` |
| `response_status` | INTEGER NULL | HTTP status cache |
| `response_body` | JSONB NULL | Response cache |
| `created_at` | TIMESTAMPTZ | Tạo |
| `expires_at` | TIMESTAMPTZ | Cleanup |

## 12.4. `dead_letter_messages`

Bảng lưu trữ thông điệp lỗi sau khi vượt quá giới hạn thử lại (Bounded Retries), phục vụ giám sát, cách ly lỗi và cơ chế phát lại (Re-drive / Replay):

| Cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
|---|---|---|---|
| `id` | UUID | PK, NOT NULL | Định danh bản ghi lỗi |
| `queue_name` | VARCHAR(100) | NOT NULL | Tên queue xảy ra lỗi |
| `exchange_name` | VARCHAR(100) | NOT NULL | Exchange gốc phát hành |
| `routing_key` | VARCHAR(100) | NOT NULL | Routing key của message |
| `payload` | JSONB | NOT NULL | Nội dung event đầy đủ |
| `correlation_id` | UUID | NULL | Flow correlation ID |
| `exception_class` | VARCHAR(255) | NULL | Tên exception gây lỗi |
| `exception_message` | TEXT | NULL | Chi tiết thông báo lỗi |
| `retry_count` | INTEGER | NOT NULL DEFAULT 3 | Số lần đã retry |
| `status` | VARCHAR(20) | NOT NULL, CHECK | `DEAD`, `REPROCESSED`, `RESOLVED`, `DISCARDED` |
| `failed_at` | TIMESTAMPTZ | NOT NULL | Thời điểm message chết |
| `reprocessed_at` | TIMESTAMPTZ | NULL | Thời điểm phát lại thành công |
| `created_at` | TIMESTAMPTZ | NOT NULL | Thời điểm ghi DB |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Thời điểm cập nhật |

```sql
CREATE INDEX idx_dlq_status ON dead_letter_messages(status);
CREATE INDEX idx_dlq_queue ON dead_letter_messages(queue_name);
```

### Hạ tầng RabbitMQ Dead-Letter Queue (Topology Design)
Kiến trúc hàng đợi thiết lập cơ chế Dead-Letter Exchange (DLX) tập trung. Mọi queue chính khi khai báo đều liên kết với DLX qua cấu hình arguments để tự động hứng thông điệp chết khi gặp lỗi hoặc quá số lần retry:
1. **Dead Letter Exchange (DLX):** `goride.dead-letter.exchange` (Exchange Type: `topic`, Durable: `true`).
2. **Hàng đợi Dead-Letter riêng biệt cho từng Service:**
   - `driver.dead-letter.queue` (Binding: `goride.dead-letter.exchange` với pattern `dlq.driver.#`)
   - `trip.dead-letter.queue` (Binding: `goride.dead-letter.exchange` với pattern `dlq.trip.#`)
   - `matching.dead-letter.queue` (Binding: `goride.dead-letter.exchange` với pattern `dlq.matching.#`)
   - `payment.dead-letter.queue` (Binding: `goride.dead-letter.exchange` với pattern `dlq.payment.#`)
   - `notification.dead-letter.queue` (Binding: `goride.dead-letter.exchange` với pattern `dlq.notification.#`)
3. **Cấu hình Arguments trên Main Queue:**
   - `x-dead-letter-exchange`: `goride.dead-letter.exchange`
   - `x-dead-letter-routing-key`: `dlq.<service_name>.<event_type>` (ví dụ: `dlq.payment.trip_completed`)

## 12.5. Quy trình xử lý lỗi, Retry và DLQ (Fault-Handling Workflow)
Quy trình bảo đảm không làm gián đoạn luồng xử lý chính (không nghẽn queue) và đáp ứng yêu cầu Fault Tolerance của đề tài:
```text
[Main Queue]
     │
     ▼
[Consumer Service] ──(Xử lý thành công)──> [ACK] ──> [Lưu inbox_event / Hoàn tất]
     │
     ├─ (Gặp Exception / Timeout / Sập Service)
     │
     ▼
[Spring Bounded Retry with Exponential Backoff]
     ├─ Lần 1: Thử lại sau 1000ms
     ├─ Lần 2: Thử lại sau 2000ms
     ├─ Lần 3: Thử lại sau 4000ms
     │
     ▼
(Vượt quá 3 lần retry vẫn thất bại)
     │
     ▼
[Basic.Nack / Basic.Reject (requeue = false)]
     │
     ▼
[goride.dead-letter.exchange] (DLX)
     │
     ▼
[service.dead-letter.queue] (DLQ)
     │
     ▼
[DLQ Consumer / Error Logger Daemon]
     │
     ▼
[INSERT INTO dead_letter_messages (status = 'DEAD')]
```

**Định nghĩa vòng đời bản ghi lỗi (`status`):**
- `DEAD`: Message xử lý thất bại sau toàn bộ số lần retry; đã được cách ly an toàn vào bảng lưu trữ để bảo vệ main queue không bị nghẽn.
- `REPROCESSED`: Sự cố downstream đã được khắc phục (ví dụ: Payment Service đã khởi động lại); message được phát lại vào exchange gốc và xử lý thành công.
- `RESOLVED`: Lỗi logic nghiệp vụ không thể phát lại tự động; kỹ sư can thiệp dữ liệu thủ công và đóng bản ghi.
- `DISCARDED`: Message rác, dữ liệu test sai định dạng hoặc event hết hạn giá trị sử dụng; bị đánh dấu hủy bỏ.

## 12.6. Quy trình Re-drive Message (Recovery & Replay Mechanism)
Hệ thống cung cấp cơ chế khôi phục nhằm tái xử lý các message bị lưu trữ trong DLQ sau khi nguyên nhân lỗi đã được khắc phục:
1. **Điều kiện kích hoạt:** Service phụ thuộc được phục hồi hoặc bản vá lỗi được deploy.
2. **Quy trình Re-drive:**
   - Quản trị viên gọi API nội bộ: `POST /internal/dlq/redrive/{id}` hoặc kích hoạt tiến trình batch theo `queue_name`.
   - Hệ thống đọc bản ghi `dead_letter_messages` tương ứng có trạng thái `DEAD`.
   - Gửi lại nội dung payload vào `exchange_name` với `routing_key` ban đầu.
   - Cập nhật bản ghi lỗi: `status = 'REPROCESSED'`, `reprocessed_at = NOW()`.
   - Consumer đích nhận lại message, kiểm tra bảng `inbox_event` (idempotency/deduplication) và tiếp tục luồng nghiệp vụ bình thường.

## 12.7. Ma trận kỹ thuật độ tin cậy hệ thống (Tích hợp toàn diện)

Toàn bộ các kỹ thuật độ tin cậy được tích hợp trực tiếp vào kiến trúc và triển khai thực tế của các microservice, không tách rời thành các tính năng tùy chọn:

| Service | Outbox Pattern | Inbox (Deduplication) | Idempotency Control | DLQ & Error-Message Storage |
|---|---|---|---|---|
| User Service | Có (`outbox_event`) | Request Idempotency | Unique Constraints (`phone`, `email`) | RabbitMQ DLQ |
| Driver Service | Có (`outbox_event`) | Có (`inbox_event`) | `driver_reservations.idempotency_key` + conditional CAS | RabbitMQ DLQ + `dead_letter_messages` |
| Trip Service | Có (`outbox_event`) | Có (`inbox_event`) | `idempotency_record` + State-precondition | RabbitMQ DLQ + `dead_letter_messages` |
| Matching Service | Có (`outbox_event`) | Có (`inbox_event`) | Persistent Saga (`correlation_id`) | RabbitMQ DLQ + `dead_letter_messages` |
| Payment Service | Có (`outbox_event`) | Có (`inbox_event`) | `payments.trip_id` UNIQUE + `idempotency_record` | RabbitMQ DLQ + `dead_letter_messages` |
| Notification Service | Sẵn sàng (`outbox_event`) | Có (`inbox_event`) | Composite UNIQUE (`source_event_id, recipient, type`) | RabbitMQ DLQ + `dead_letter_messages` |

## 12.8. Kịch bản minh chứng nghiệm thu khả năng chịu lỗi (Fault Tolerance & Reliability Verification)
Kiến trúc độ tin cậy trên được thiết kế nhằm bảo đảm hệ thống vượt qua mọi kịch bản lỗi phân tán thực tế và minh chứng tính chịu lỗi (Fault Tolerance & Resilience):
1. **Chuẩn bị:** Chuyến đi hoàn tất, Trip Service phát event `trip.completed`.
2. **Kích hoạt lỗi:** Đột ngột tắt (`docker stop payment-service`) container của Payment Service.
3. **Minh chứng Bounded Retry:** Mở console log của Trip/Consumer chứng minh hệ thống kích hoạt retry đủ 3 lần kèm timestamp lùi dần (backoff 1s, 2s, 4s).
4. **Minh chứng Queue Handling & DLQ:** Mở RabbitMQ Management Dashboard. Chỉ rõ: Main queue của Payment không bị tắc, message lỗi tự động được chuyển hướng sang `payment.dead-letter.queue`.
5. **Minh chứng Error-Message Storage:** Truy vấn PostgreSQL: `SELECT * FROM payment_service.dead_letter_messages WHERE status = 'DEAD';` hiển thị trực quan payload JSON của chuyến đi, thời gian chết `failed_at`, mã lỗi `ConnectionRefusedException`, số lần thử `retry_count = 3`.
6. **Minh chứng Khôi phục (Recovery):** Khởi động lại container Payment Service. Kích hoạt endpoint re-drive; message được xử lý thành công, trạng thái chuyến đi chuyển sang `PAID` và bản ghi lỗi đổi thành `REPROCESSED`.

---

# 14. QUAN HỆ LOGIC GIỮA CÁC BOUNDED CONTEXT

Các quan hệ sau là logical reference, không phải FK DB:

```text
User.user_id
  -> Driver.user_id

User.user_id
  -> Trip.passenger_id

Driver.driver_id
  -> Trip.driver_id

Trip.trip_id
  -> Matching.trip_id

Trip.trip_id
  -> Payment.trip_id

Trip/User IDs
  -> Notification
```

Nguyên tắc:

- Không cross-database JOIN.
- Không cross-service JPA relationship.
- Không expose repository của service khác.
- Khi cần dữ liệu realtime: REST.
- Khi cần projection/eventual consistency: event.

---

# 15. CONCURRENCY VÀ CONSISTENCY

## 15.1. Hai passenger tranh cùng một driver

Driver reservation phải đảm bảo chỉ một request thành công.

```text
P01 ----\
         -> reserve D01 -> DB atomic update
P02 ----/

Kết quả:
một request affectedRows=1
một request affectedRows=0
```

## 15.2. Saga compensation

```text
Reserve Driver SUCCESS
        |
        v
Assign Trip FAILURE
        |
        v
Matching Saga -> COMPENSATING
        |
        v
Release Driver
        |
        v
Driver AVAILABLE
        |
        v
Saga COMPENSATED
```

## 15.3. Eventual consistency

Driver location/status projection:

```text
Driver DB
 -> Outbox
 -> RabbitMQ
 -> Matching Consumer
 -> Redis
```

Redis có thể chậm hơn Driver DB trong một khoảng ngắn; reservation luôn được xác thực lại bằng Driver Service.

---

# 16. INDEX VÀ CONSTRAINT CHÍNH

Bắt buộc/khuyến nghị:

- `users.phone` UNIQUE.
- `users.email` UNIQUE.
- `drivers.user_id` UNIQUE.
- `drivers.license_number` UNIQUE.
- `vehicles.plate_number` UNIQUE.
- Một Driver chỉ có một active reservation.
- Một Trip chỉ có một active reservation.
- `matching_sagas.trip_id` UNIQUE.
- `payments.trip_id` UNIQUE.
- `inbox_event.event_id` PK.
- `idempotency_record.idempotency_key` PK.
- Notification có unique dedup key theo source event + recipient + type.

Không index mọi cột; chỉ index theo query pattern thực tế.

---

# 17. BẢO MẬT DỮ LIỆU

- Không lưu password plaintext.
- Refresh token chỉ lưu hash.
- Không ghi token/secret vào log.
- Tách credential database theo service.
- Không cấp quyền cho service truy cập DB khác.
- Payload JSONB không được chứa secret nếu không cần thiết.
- Tất cả external input phải validate tại adapter/application boundary.
- Sensitive configuration dùng environment variables.

---

# 18. MIGRATION VÀ TRIỂN KHAI

Sau khi tài liệu được nhóm duyệt:

1. Tạo Flyway migration trong từng service.
2. Không tạo một thư mục migration chung cho toàn hệ thống.
3. Mỗi service tự migrate database của mình khi startup/deploy.
4. Production/shared environments không dùng Hibernate `ddl-auto=update`.
5. Khuyến nghị:
   - `ddl-auto=validate`
   - schema thay đổi qua Flyway.
6. Migration phải backward-compatible khi hệ thống đã có dữ liệu.

Ví dụ:

```text
services/driver-service/
src/main/resources/db/migration/
├── V1__create_driver_tables.sql
├── V2__create_outbox_inbox.sql
└── V3__add_driver_indexes.sql
```

---

# 19. TỔNG HỢP SỐ LƯỢNG BUSINESS TABLE

| Database | Business tables |
|---|---:|
| User | 4 |
| Driver | 4 |
| Trip | 2 |
| Matching | 2 |
| Payment | 2 |
| Notification | 1 |
| **Tổng** | **15** |

Ngoài ra có các technical table `outbox_event`, `inbox_event`, `idempotency_record` tùy theo service.

---

# 20. CÁC QUYẾT ĐỊNH ĐÃ ĐƯỢC DUYỆT

Các quyết định dưới đây là **baseline chính thức của Database Design v1.0**:

1. **Một user có thể đồng thời có role `PASSENGER` và `DRIVER`.**  
   Triển khai bằng `roles` + `user_roles`; không dùng một cột role đơn trong `users`.

2. **Trip Service giữ trạng thái payment ở mức trip lifecycle:**  
   `PAYMENT_PENDING`, `PAID`, `PAYMENT_FAILED`.  
   Payment Service vẫn là **source of truth của payment transaction**.

3. **Không lưu GPS history trong phiên bản 1.0.**  
   Driver Service chỉ lưu latest location trong PostgreSQL; Matching Service sử dụng Redis GEO projection để tìm candidate.

4. **Driver reservation có timeout.**  
   Timeout phải cấu hình được; giá trị mặc định cho môi trường demo là **20 giây**.

5. **Matching Service có PostgreSQL riêng.**  
   Database này dùng để persist `matching_sagas`, `matching_attempts` và các technical table cần thiết. Redis không được dùng thay Saga persistence.

6. **Notification Service có persistence.**  
   `notifications` được lưu để chứng minh retry, recovery, deduplication và trạng thái gửi.

7. **Payment chỉ dùng provider mô phỏng trong phạm vi đồ án.**  
   `provider = SIMULATED`; không tích hợp cổng thanh toán thật ở baseline v1.0.

8. **Không áp dụng soft delete mặc định.**  
   Dùng business status khi cần. Chỉ bổ sung soft delete nếu xuất hiện requirement mới và được duyệt.

## 20.1. Quy tắc thay đổi sau khi đã duyệt

Agent/developer **không được tự ý**:

- gộp các database thành shared database;
- tạo cross-service foreign key;
- bỏ PostgreSQL của Matching Service;
- thay PostgreSQL source of truth bằng Redis;
- bỏ atomic reservation/unique active reservation invariant;
- thay đổi Trip lifecycle đã chốt;
- thêm GPS history, payment provider thật hoặc soft delete chỉ vì “best practice”;
- thêm/bớt business table cốt lõi mà không nêu lý do.

Nếu phát hiện implementation cần khác baseline, phải:

1. nêu rõ điểm xung đột;
2. đề xuất thay đổi;
3. tạo/cập nhật ADR;
4. chỉ triển khai sau khi thay đổi được duyệt.

---

# 21. CHECKLIST BASELINE TRƯỚC KHI IMPLEMENT

- [x] Nhóm thống nhất 6 database ownership.
- [x] Không có cross-service foreign key.
- [x] User/Driver/Trip relationship dùng logical UUID.
- [x] Driver reservation constraint được duyệt.
- [x] Trip state machine được duyệt.
- [x] Matching Saga states được duyệt.
- [x] Payment state được duyệt.
- [x] Notification persistence được duyệt.
- [x] Technical table strategy được duyệt.
- [x] Naming convention được duyệt.
- [x] Index/unique constraint chính được duyệt.
- [x] Baseline đã duyệt; được phép bắt đầu thiết kế physical schema/Flyway/JPA theo tài liệu này.

---

# 22. HƯỚNG DẪN SỬ DỤNG TÀI LIỆU CHO CODING AGENT

Trước mọi task liên quan database/persistence, agent phải đọc:

1. `docs/architecture/architecture.md`
2. `docs/architecture/database-design.md`
3. API/Event contract liên quan nếu đã tồn tại
4. ADR liên quan nếu đã tồn tại

Khi generate schema hoặc code persistence, agent phải:

- tạo migration trong đúng service sở hữu dữ liệu;
- giữ domain model độc lập với JPA;
- dùng persistence entity riêng và mapper rõ ràng;
- không tạo cross-service JPA relationship;
- không tạo cross-service FK;
- không dùng `ddl-auto=update`;
- không thêm table/column ngoài tài liệu chỉ để “chuẩn hóa” nếu chưa có requirement;
- giữ các unique constraint và index phục vụ invariant/concurrency;
- triển khai đầy đủ bộ giải pháp Outbox, Inbox, Idempotency và DLQ Error Storage theo đúng ma trận độ tin cậy đã tích hợp;
- chạy test/migration verification sau thay đổi;
- báo rõ mọi chỗ phải đưa ra quyết định kiến trúc mới.

## 22.1. Thứ tự triển khai persistence đề xuất

```text
1. User Service
2. Driver Service
3. Trip Service
4. Matching Service
5. Payment Service
6. Notification Service
7. Technical reliability tables theo flow thực tế
```

Không nên generate toàn bộ persistence của sáu service trong một task duy nhất. Mỗi service nên được triển khai, migrate, test và review độc lập.

---

# 23. KẾT LUẬN

Thiết kế đề xuất tách dữ liệu theo đúng bounded context, không sử dụng shared database và hỗ trợ trực tiếp các yêu cầu cốt lõi của hệ thống phân tán: độc lập service, concurrency control, distributed transaction bằng Saga, eventual consistency, retry/deduplication, recovery và fault tolerance.

Điểm trọng tâm của thiết kế là Driver Service giữ quyền quyết định cuối cùng đối với reservation, Trip Service giữ lifecycle của chuyến đi, Matching Service persist Saga trạng thái phân tán với Redis GEO projection làm bộ tìm kiếm ứng viên, và toàn bộ các service đều được tích hợp đầy đủ bộ giải pháp độ tin cậy cốt lõi: Transactional Outbox, Inbox Idempotent Consumer, Idempotency Control và Dead-Letter Queue Storage phục vụ khả năng tự phục hồi (resilience & redrive).

Tài liệu này đã được phê duyệt làm **Database Design Baseline v1.0**. Bước tiếp theo là triển khai physical schema, Flyway migration và persistence adapter theo đúng ownership/constraint/invariant đã chốt, theo thứ tự đề xuất: User -> Driver -> Trip -> Matching -> Payment -> Notification.

Khi agent triển khai, tài liệu này phải được đọc cùng `docs/architecture/architecture.md`. Nếu hai tài liệu có điểm không đồng nhất, agent phải dừng ở điểm xung đột và báo lại thay vì tự chọn một phương án.
