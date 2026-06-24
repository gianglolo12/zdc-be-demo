# PRD-004 — Liệt kê đơn của đại lý (List Orders)

## Mục tiêu
Đại lý xem danh sách đơn của mình.

## User stories
- US1: `GET /orders?agentId=X` → trả mảng đơn của agentId đó (mới nhất trước). Nếu thiếu `agentId` → 400.
- US2: Mỗi phần tử gồm id, agentId, items, total, status.

## Business rules
- BR1: `agentId` query bắt buộc.
- BR2: Chỉ trả đơn đúng agentId (không lộ đơn đại lý khác).

## Acceptance
- AC1: GET có agentId → 200 + mảng đơn đúng agent.
- AC2: thiếu agentId → 400.
