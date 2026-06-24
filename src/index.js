import express from "express"
const app = express()
app.use(express.json())

// In-memory order store (demo).
const orders = []

// Create an order: { agentId, items: [{ denomination, qty }] }
app.post("/orders", (req, res) => {
  const { agentId, items } = req.body ?? {}
  if (!agentId || !Array.isArray(items) || items.length === 0) {
    return res.status(400).json({ error: "agentId and non-empty items required" })
  }
  const total = items.reduce((s, it) => s + (it.denomination ?? 0) * (it.qty ?? 0), 0)
  const order = { id: orders.length + 1, agentId, items, total, status: "created" }
  orders.push(order)
  res.status(201).json(order)
})

// List orders for an agent (newest first): GET /orders?agentId=X
app.get("/orders", (req, res) => {
  const { agentId } = req.query
  if (!agentId) {
    return res.status(400).json({ error: "agentId query param required" })
  }
  const result = orders
    .filter((o) => String(o.agentId) === String(agentId))
    .sort((a, b) => b.id - a.id)
  res.json(result)
})

app.get("/orders/:id", (req, res) => {
  const order = orders.find((o) => o.id === Number(req.params.id))
  if (!order) return res.status(404).json({ error: "not found" })
  res.json(order)
})

export { app }

// Only start the server when run directly, so tests can import `app`.
if (process.argv[1] && import.meta.url === `file://${process.argv[1]}`) {
  const port = process.env.PORT ?? 8080
  app.listen(port, () => console.log(`zdc-be-demo listening on :${port}`))
}
