# Mission: Senior Java Spring Boot Backend qua Flash Sale Platform

## Why
Xây dựng một Flash Sale Order & Inventory Platform đủ thực tế để có thể thiết kế, triển khai, kiểm thử và giải thích các quyết định backend ở cấp senior, đặc biệt là bài toán mua hàng đồng thời mà không oversell.

## Success looks like
- Thiết kế và triển khai REST API có contract, validation và error model nhất quán.
- Chứng minh bằng integration/concurrency test rằng tồn kho không bị bán âm khi có nhiều request đồng thời.
- Giải thích được transaction boundary, isolation, locking, index và query plan thay vì chỉ dùng annotation theo thói quen.
- Xây luồng event-driven với Kafka có idempotency, retry và xử lý lỗi rõ ràng.
- Đóng gói, vận hành và quan sát hệ thống bằng Docker và Kubernetes.
- Đọc, chất vấn, kiểm thử và sửa được code do AI tạo ra trước khi chấp nhận.

## Constraints
- Học theo project, mỗi khái niệm phải gắn với code hoặc một quyết định thiết kế có thể kiểm chứng.
- Nội dung giảng dạy bằng tiếng Việt; tên code và thuật ngữ kỹ thuật giữ theo chuẩn tiếng Anh.
- Ưu tiên PostgreSQL thật qua Testcontainers thay cho database giả lập khi kiểm thử hành vi phụ thuộc database.
- Bắt đầu bằng modular monolith; chỉ tách thành service khi có bằng chứng về boundary và nhu cầu vận hành.

## Out of scope
- Chứng chỉ cloud hoặc cấu hình chuyên biệt cho một cloud provider.
- Tối ưu frontend React chuyên sâu ngoài phần cần thiết để tiêu thụ API và mô phỏng flash sale.
- Tách microservice sớm chỉ để trình diễn công nghệ.
