import { test, after } from "node:test"
import assert from "node:assert/strict"
import { app, orders } from "../index.js"

// Boot the app on an ephemeral port for the duration of the suite.
const server = app.listen(0)
const base = () => `http://127.0.0.1:${server.address().port}`

after(() => server.close())

test("GET /orders returns 200 and an empty array when no orders exist", async () => {
  const res = await fetch(`${base()}/orders`)
  assert.equal(res.status, 200)
  const body = await res.json()
  assert.ok(Array.isArray(body))
  assert.equal(body.length, 0)
})

test("GET /orders returns 200 and a JSON array of created orders", async () => {
  const createRes = await fetch(`${base()}/orders`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ agentId: "a1", items: [{ denomination: 100, qty: 2 }] }),
  })
  assert.equal(createRes.status, 201)

  const res = await fetch(`${base()}/orders`)
  assert.equal(res.status, 200)
  const body = await res.json()
  assert.ok(Array.isArray(body))
  assert.equal(body.length, 1)
  assert.equal(body[0].id, 1)
  assert.equal(body[0].status, "created")
  assert.equal(body.length, orders.length)
})
