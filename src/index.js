import express from "express"
import { cardsRouter } from "./routes/cards.js"
import * as cardService from "./services/cardService.js"

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

app.get("/orders/:id", (req, res) => {
  const order = orders.find((o) => o.id === Number(req.params.id))
  if (!order) return res.status(404).json({ error: "not found" })
  res.json(order)
})

// G4.F11 — card lookup / inventory / reveal gate routes.
app.use("/cards", cardsRouter)

// Seed a little demo data so the card endpoints are usable when running standalone.
function seedDemoCards() {
  cardService.setSalesPin("agent-1", "123456")
  cardService.createCard({
    agentId: "agent-1", serial: "ZC50K0001", pin: "1111-2222-3333", denomination: 50000,
    orderId: 1, purchaseDate: "2026-06-01", expiryDate: "2027-06-01",
    state: cardService.CARD_STATE.DELIVERED_UNREVEALED,
  })
  cardService.createCard({
    agentId: "agent-1", serial: "ZC100K0002", pin: "4444-5555-6666", denomination: 100000,
    orderId: 1, purchaseDate: "2026-06-01", soldDate: "2026-06-10", expiryDate: "2027-06-01",
    state: cardService.CARD_STATE.REVEALED_SOLD,
  })
  cardService.createCard({
    agentId: "agent-1", serial: "ZC50K0003", pin: "7777-8888-9999", denomination: 50000,
    orderId: 2, purchaseDate: "2026-06-05", expiryDate: "2027-06-05",
    state: cardService.CARD_STATE.BLOCKED_FRAUD,
  })
}

const port = process.env.PORT ?? 8080
if (process.env.NODE_ENV !== "test") {
  seedDemoCards()
  app.listen(port, () => console.log(`zdc-be-demo listening on :${port}`))
}

export { app, seedDemoCards }
