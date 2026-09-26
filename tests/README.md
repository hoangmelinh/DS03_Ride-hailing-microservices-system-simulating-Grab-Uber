# Kiểm Thử Tự Động & Thực Nghiệm Hệ Thống (`tests`)

Thư mục quản lý các bộ kiểm thử phân tán phục vụ xác minh kiến trúc và demo:

- **Kiểm thử đồng thời (Concurrency Tests)**: Xác minh cơ chế conditional update/atomic reservation khi nhiều hành khách cùng tranh một tài xế khả dụng (chỉ 1 request thành công).
- **Kiểm thử chịu lỗi & phục hồi (Fault Tolerance & Recovery)**: Xác minh bounded retry, Dead Letter Queue (DLQ) khi tắt/bật Notification Service, và cơ chế bù trừ Saga (Saga compensation/RELEASE_DRIVER) khi gán chuyến thất bại.
- **Kiểm thử trùng lặp (Idempotency & Deduplication)**: Đảm bảo khi gửi cùng một `eventId` nhiều lần, consumer chỉ thực thi tác động nghiệp vụ đúng một lần nhờ bảng `inbox_event`/`processed_event`.
- **Thực nghiệm hiệu năng (Performance Experiments)**: Kịch bản kiểm thử tải bằng k6 (10, 50, 100 virtual users) đo lường độ trễ trung bình, P95, throughput và tỷ lệ thành công.
