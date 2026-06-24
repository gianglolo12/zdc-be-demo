# PRD-002 — Hoàn tiền đơn (Refund Order)

## Mục tiêu
Cho phép hoàn tiền một đơn đã tạo (status=created) khi đại lý yêu cầu hủy trước khi giao thẻ.

## User stories
- US1: `POST /orders/:id/refund` với body `{reason}` → nếu đơn `status=created` → chuyển `status=refunded`, lưu `reason` + `refundedAt`, trả về đơn.
- US2: Không cho refund đơn đã `refunded` (trả 409) hoặc không tồn tại (404).

## Business rules
- BR1: Chỉ refund được đơn đang `created`.
- BR2: `reason` bắt buộc, tối thiểu 5 ký tự.
- BR3: Sau refund, đơn không thể tạo thẻ.

## Acceptance
- AC1: refund đơn created → 200 + status=refunded.
- AC2: refund đơn đã refunded → 409.
- AC3: thiếu reason → 400.
