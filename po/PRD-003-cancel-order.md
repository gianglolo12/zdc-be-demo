# PRD-003 — Hủy đơn (Cancel Order)

## Mục tiêu
Cho phép đại lý hủy 1 đơn đang `created` (trước khi thanh toán/giao thẻ).

## User stories
- US1: `POST /orders/:id/cancel` với body `{reason}` → nếu đơn `status=created` → set `status=cancelled`, lưu `reason` + `cancelledAt`, trả về đơn.
- US2: Không hủy được đơn đã `cancelled` (409) hoặc không tồn tại (404).

## Business rules
- BR1: Chỉ hủy được đơn `created`.
- BR2: `reason` bắt buộc, tối thiểu 5 ký tự.

## Acceptance
- AC1: hủy đơn created → 200 + status=cancelled.
- AC2: hủy đơn đã cancelled → 409.
- AC3: thiếu reason → 400.
