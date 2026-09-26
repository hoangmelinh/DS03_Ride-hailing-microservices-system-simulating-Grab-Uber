# Hợp Đồng REST API (OpenAPI)

Thư mục này chứa các đặc tả OpenAPI (Swagger 3.0+) phiên bản hóa cho các REST API của từng microservice:

- `user-service`: Đăng ký, đăng nhập, xác thực JWT và thông tin người dùng.
- `driver-service`: Trạng thái tài xế, cập nhật vị trí, giữ chỗ tài xế nguyên tử (atomic reservation).
- `trip-service`: Vòng đời chuyến đi, gán tài xế, chuyển đổi trạng thái chuyến.
- `matching-service`: Kích hoạt tìm kiếm tài xế và truy vấn trạng thái Saga.
- `payment-service`: Mô phỏng xử lý thanh toán.
- `notification-service`: Thông báo cho hành khách và tài xế.

Mọi thay đổi đối với REST API phải cập nhật đặc tả tương ứng tại đây để đảm bảo tính tương thích ngược.
