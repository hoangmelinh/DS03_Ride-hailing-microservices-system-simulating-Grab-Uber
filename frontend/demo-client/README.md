# Demo Client (`frontend/demo-client`)

Giao diện Web Client tối giản xây dựng bằng React + Vite nhằm mục đích kích hoạt các luồng nghiệp vụ phân tán và quan sát trạng thái hệ thống khi demo/vấn đáp.

## Các bảng điều khiển chính:
1. **Passenger Panel**: Yêu cầu đặt chuyến (nhập tọa độ giả lập), xem mã chuyến đi và theo dõi trạng thái.
2. **Driver Panel**: Bật/tắt trạng thái nhận cuốc (online/offline), cập nhật vị trí giả lập, nhận/từ chối cuốc, bắt đầu và hoàn thành chuyến.
3. **System Monitor**: Quan sát sự thay đổi trạng thái theo thời gian thực, correlation ID, sự kiện RabbitMQ, lỗi và cơ chế Saga compensation.
