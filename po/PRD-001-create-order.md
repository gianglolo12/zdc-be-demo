# PRD-001 — Create Order (Mua-nhiều-bán-nhiều)

## Mục tiêu
Đại lý tạo 1 đơn gồm NHIỀU dòng mệnh giá (mỗi dòng có qty riêng), thanh toán 1 lần.

## User stories
- US1: Đại lý gửi `POST /orders` với `agentId` + danh sách `items[{denomination, qty}]` → hệ thống tính tổng tiền, tạo đơn `status=created`, trả về đơn.
- US2: Đại lý xem lại đơn qua `GET /orders/:id`.

## Business rules
- BR1: `items` không được rỗng; mỗi item phải có `denomination > 0` và `qty > 0`.
- BR2: Tổng tiền = Σ(denomination × qty).

## Out of scope (P1)
- Chiết khấu, hạn mức, voucher (để placeholder).
