# PRD-005 — List orders endpoint

## Goal
Expose `GET /orders` returning all orders as JSON array.

## Requirements
- Method GET, path /orders
- Response 200 with JSON array of orders (id, item, qty, status)
- Empty array when no orders

## Acceptance
- GET /orders returns 200 and a JSON array
