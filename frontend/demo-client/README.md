# Demo Client (`frontend/demo-client`)

Giao diện Web Client trực quan hóa phân tán (Distributed Flow Simulator & Demo Client) xây dựng bằng **React + Vite** nhằm mục đích kích hoạt các luồng nghiệp vụ phân tán, quan sát gói tin di chuyển theo thời gian thực và soi chi tiết Payload/Correlation ID khi bảo vệ đồ án.

---

## 1. Các bảng điều khiển chính:
1. **Passenger Panel**:
   - Chọn tuyến đường mẫu (ĐH Bách Khoa ➔ Hồ Gươm, Sân bay Nội Bài ➔ Keangnam).
   - Dự toán cước phí Haversine + Surge Pricing (`POST /api/v1/trips/estimate`).
   - Đặt chuyến đi (`POST /api/v1/trips`) và theo dõi trạng thái vòng đời.
2. **Driver Panel**:
   - Bật/tắt trạng thái nhận cuốc (Online/Offline).
   - Nhận thông báo cuốc xe gán từ Matching Service.
   - Các hành động tài xế: Nhận cuốc (Accept), Từ chối (kích hoạt Saga Rollback), Bắt đầu đi (Start), Hoàn thành chuyến (Complete ➔ kích hoạt Payment & Notification).
3. **Interactive Flow Canvas (Bàn cờ luồng phân tán)**:
   - Hiển thị trực quan 7 Microservices + 2 Client + RabbitMQ Bus.
   - **Gói tin di chuyển (Packet Flight)**: Hiển thị gói tin gồm 3 phần (Protocol, Action, Signature/CorrelationId) bay qua lại giữa các Node giống kiến trúc visual explainer.
   - Bấm trực tiếp vào Packet để mở **Payload Inspector** soi JSON, Header và `correlationId`.
4. **Distributed Event Stream & Tracing Log**:
   - Bảng log thời gian thực nối chuỗi các sự kiện theo `correlationId`.

---

## 2. Các kịch bản demo tích hợp sẵn:
- **Kịch bản 1 - Happy Flow**: Chu trình đặt xe ➔ RabbitMQ ➔ Saga Matching ➔ Gán xe ➔ Hoàn thành ➔ Thanh toán.
- **Kịch bản 2 - Saga Compensation (R02)**: Gán cuốc thất bại ➔ Matching tự động phát lệnh `RELEASE_DRIVER` hoàn trả trạng thái `AVAILABLE`.
- **Kịch bản 3 - Concurrency Control (C01)**: Hai hành khách cùng tranh một tài xế ➔ Database conditional CAS khóa tài xế duy nhất.

---

## 3. Cách khởi chạy:

```bash
cd frontend/demo-client
npm install
npm run dev
```

Truy cập: `http://localhost:3000`

> **Lưu ý:** Client có chế độ kép:
> - Nếu API Gateway (`http://localhost:8080`) đang chạy, client sẽ kết nối và gửi request thật.
> - Nếu backend chưa bật, client tự động chuyển sang chế độ **Simulation Mode** để bạn vẫn có thể thuyết trình luồng mượt mà 100%.
