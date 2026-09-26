# Hợp Đồng Sự Kiện Bất Đồng Bộ (AsyncAPI)

Thư mục này quản lý các đặc tả AsyncAPI cho toàn bộ sự kiện nghiệp vụ được phát hành qua RabbitMQ.

## Cấu trúc Event Envelope chuẩn:
Mọi event trao đổi qua exchange `goride.events` bắt buộc tuân theo envelope:
```json
{
  "eventId": "uuid",
  "eventType": "trip.created",
  "eventVersion": 1,
  "correlationId": "uuid",
  "causationId": "uuid-or-null",
  "aggregateId": "trip-id",
  "producer": "trip-service",
  "occurredAt": "2026-10-01T10:30:00Z",
  "payload": {}
}
```

- `eventId`: Khóa định danh duy nhất phục vụ deduplication/idempotency.
- `correlationId`: Theo dõi luồng phân tán xuyên suốt các service và hệ thống log.
- `eventType`: Định tuyến sự kiện theo chuẩn tên viết thường có dấu chấm (`trip.created`, `driver.reserved`, v.v.).
