import { test, before, after } from "node:test"
import assert from "node:assert/strict"
import { app } from "../index.js"

let server
let baseUrl

before(async () => {
  await new Promise((resolve) => {
    server = app.listen(0, () => {
      const { port } = server.address()
      baseUrl = `http://127.0.0.1:${port}`
      resolve()
    })
  })

  // Seed orders for two different agents.
  await fetch(`${baseUrl}/orders`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ agentId: "A1", items: [{ denomination: 100, qty: 2 }] }),
  })
  await fetch(`${baseUrl}/orders`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ agentId: "A1", items: [{ denomination: 50, qty: 1 }] }),
  })
  await fetch(`${baseUrl}/orders`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ agentId: "A2", items: [{ denomination: 20, qty: 5 }] }),
  })
})

after(() => server?.close())

// AC1 + BR2: returns only the agent's orders, newest first.
test("GET /orders?agentId=A1 returns only A1 orders, newest first", async () => {
  const res = await fetch(`${baseUrl}/orders?agentId=A1`)
  assert.equal(res.status, 200)
  const body = await res.json()
  assert.equal(body.length, 2)
  assert.ok(body.every((o) => o.agentId === "A1"))
  assert.equal(body[0].id, 2) // newest first
  assert.equal(body[1].id, 1)
})

// US2: each element has the expected shape.
test("GET /orders elements expose id, agentId, items, total, status", async () => {
  const res = await fetch(`${baseUrl}/orders?agentId=A2`)
  const [order] = await res.json()
  assert.deepEqual(Object.keys(order).sort(), ["agentId", "id", "items", "status", "total"])
})

// AC2 + BR1: missing agentId → 400.
test("GET /orders without agentId returns 400", async () => {
  const res = await fetch(`${baseUrl}/orders`)
  assert.equal(res.status, 400)
})
