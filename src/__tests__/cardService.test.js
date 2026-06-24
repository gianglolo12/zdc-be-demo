import { test, beforeEach } from "node:test"
import assert from "node:assert/strict"
import * as svc from "../services/cardService.js"

const AGENT = "agent-1"
const OTHER = "agent-2"
const PIN = "654321"

function seed() {
  svc.reset()
  svc.setSalesPin(AGENT, PIN)
  svc.createCard({
    agentId: AGENT, serial: "ZC50K0001", pin: "1111-2222-3333", denomination: 50000,
    orderId: 1, purchaseDate: "2026-06-01", expiryDate: "2027-06-01",
    state: svc.CARD_STATE.DELIVERED_UNREVEALED,
  })
  svc.createCard({
    agentId: AGENT, serial: "ZC100K0002", pin: "4444-5555-6666", denomination: 100000,
    orderId: 1, purchaseDate: "2026-06-01", soldDate: "2026-06-10", expiryDate: "2027-06-01",
    state: svc.CARD_STATE.REVEALED_SOLD,
  })
  svc.createCard({
    agentId: AGENT, serial: "ZCBLK0003", pin: "7777-8888-9999", denomination: 50000,
    orderId: 2, purchaseDate: "2026-06-05", expiryDate: "2027-06-05",
    state: svc.CARD_STATE.BLOCKED_FRAUD,
  })
  svc.createCard({
    agentId: OTHER, serial: "ZCOTH0099", pin: "0000-0000-0000", denomination: 50000,
    orderId: 9, purchaseDate: "2026-06-01", state: svc.CARD_STATE.DELIVERED_UNREVEALED,
  })
}

beforeEach(seed)

// --- US1 ---
test("US1 AC1: lookup by serial returns friendly status, no PIN", () => {
  const r = svc.lookupStatus(AGENT, "ZC50K0001")
  assert.equal(r.statusLabel, "Chưa bán")
  assert.equal(r.denomination, 50000)
  assert.equal(r.expiryDate, "2027-06-01")
  assert.ok(!("pin" in r), "lookup result must not contain a PIN")
})

test("US1 AC4: blocked card shows 'Bị khóa' label", () => {
  const r = svc.lookupStatus(AGENT, "ZCBLK0003")
  assert.equal(r.statusLabel, "Bị khóa")
})

test("US1 AC3/BR1.1: other-agent and non-existent serials are indistinguishable", () => {
  const capture = (fn) => {
    try {
      fn()
    } catch (e) {
      return e
    }
    throw new Error("expected an error")
  }
  const otherErr = capture(() => svc.lookupStatus(AGENT, "ZCOTH0099"))
  const missingErr = capture(() => svc.lookupStatus(AGENT, "ZZNONE9999"))
  assert.equal(otherErr.message, missingErr.message)
  assert.equal(otherErr.code, "not_found")
})

test("US1 CC1: empty / malformed serial is rejected before lookup", () => {
  assert.throws(() => svc.lookupStatus(AGENT, ""), /định dạng Seri/)
  assert.throws(() => svc.lookupStatus(AGENT, "bad serial!"), /định dạng Seri/)
})

// --- US2 ---
test("US2 AC1/BR2.1: inventory scoped to agent, rows carry no PIN", () => {
  const rows = svc.listCards(AGENT)
  assert.equal(rows.length, 3)
  assert.ok(rows.every((r) => !("pin" in r)), "list rows must not contain a PIN")
  assert.ok(rows.every((r) => r.agentId === undefined))
  const cols = Object.keys(rows[0])
  for (const c of ["serial", "denomination", "sellStatus", "cardStatus", "usedDate", "expiryDate", "purchaseDate", "soldDate", "orderId"]) {
    assert.ok(cols.includes(c), `missing column ${c}`)
  }
})

test("US2 BR2.8: serial search filter narrows the list", () => {
  assert.equal(svc.listCards(AGENT, { serial: "100K" }).length, 1)
  assert.equal(svc.listCards(AGENT, { sellStatus: "Chưa bán" }).length, 2)
})

test("US2 AC2/BR2.2: unsold card detail fully masks the PIN", () => {
  const [unsold] = svc.listCards(AGENT, { serial: "ZC50K0001" })
  const d = svc.getCardDetail(AGENT, unsold.id)
  assert.equal(d.pin, svc.CONFIG.pinMask)
  assert.equal(d.pin, "***-****-****")
  assert.equal(d.sellable, true)
})

test("US2 AC6/BR2.7: unsold detail has no 'Ngày tạo', soldDate/usedDate empty", () => {
  const [unsold] = svc.listCards(AGENT, { serial: "ZC50K0001" })
  const d = svc.getCardDetail(AGENT, unsold.id)
  assert.equal(d.soldDate, null)
  assert.equal(d.usedDate, null)
  assert.ok(!("createdDate" in d) && !("ngayTao" in d))
  assert.equal(d.deliveredDate, "2026-06-01")
})

// --- US3 ---
test("US3 AC1/BR3.2/BR3.3: sell single reveals full PIN, flips state, audits", () => {
  const [unsold] = svc.listCards(AGENT, { serial: "ZC50K0001" })
  const r = svc.sellSingle(AGENT, unsold.id, PIN, { now: "2026-06-24" })
  assert.equal(r.pin, "1111-2222-3333")
  assert.equal(r.statusLabel, "Đã bán")
  const log = svc.getActivityHistory(AGENT)
  const sell = log.find((e) => e.action === "sell_single")
  assert.ok(sell)
  assert.equal(sell.serial, "ZC50K0001")
  assert.ok(!("pin" in sell), "audit record must not contain the card PIN")
})

test("US3 BR3.2: sold card cannot be reverted to unsold", () => {
  const [unsold] = svc.listCards(AGENT, { serial: "ZC50K0001" })
  svc.sellSingle(AGENT, unsold.id, PIN)
  const d = svc.getCardDetail(AGENT, unsold.id)
  assert.equal(d.sellable, false)
  assert.equal(d.statusLabel, "Đã bán")
})

test("US3 CC3 US2: wrong sales PIN does not sell the card", () => {
  const [unsold] = svc.listCards(AGENT, { serial: "ZC50K0001" })
  assert.throws(() => svc.sellSingle(AGENT, unsold.id, "000000"), /PIN bán hàng không đúng/)
  assert.equal(svc.getCardDetail(AGENT, unsold.id).sellable, true)
})

test("US3 CC4/CC2: expired/blocked cards are not sellable", () => {
  const [blocked] = svc.listCards(AGENT, { serial: "ZCBLK0003" })
  assert.throws(() => svc.sellSingle(AGENT, blocked.id, PIN), /bị khóa/)
})

test("US3 AC5/BR3.5: re-viewing a sold card reveals PIN each time and logs each view", () => {
  const [sold] = svc.listCards(AGENT, { serial: "ZC100K0002" })
  const a = svc.getCardDetail(AGENT, sold.id, { reveal: true })
  const b = svc.getCardDetail(AGENT, sold.id, { reveal: true })
  assert.equal(a.pin, "4444-5555-6666")
  assert.equal(b.pin, "4444-5555-6666")
  const views = svc.getActivityHistory(AGENT).filter((e) => e.action === "view_sold")
  assert.equal(views.length, 2)
  assert.ok(views.every((v) => !("pin" in v)))
})

test("US3 CC3 US3: selling an already-sold card becomes a view, no duplicate sell log", () => {
  const [sold] = svc.listCards(AGENT, { serial: "ZC100K0002" })
  const r = svc.sellSingle(AGENT, sold.id, PIN)
  assert.equal(r.alreadySold, true)
  assert.equal(r.pin, "4444-5555-6666")
  const log = svc.getActivityHistory(AGENT)
  assert.equal(log.filter((e) => e.action === "sell_single").length, 0)
  assert.equal(log.filter((e) => e.action === "view_sold").length, 1)
})

test("US3 AC7/BR2.6: bulk sell flips state, delivers via Excel email, hides PIN", () => {
  const r = svc.sellBulk(AGENT, ["ZC50K0001"], PIN, { now: "2026-06-24" })
  assert.deepEqual(r.soldSerials, ["ZC50K0001"])
  assert.equal(r.pinShown, false)
  assert.equal(r.delivery, "excel_email")
  const bulk = svc.getActivityHistory(AGENT).filter((e) => e.action === "sell_bulk")
  assert.equal(bulk.length, 1)
  assert.ok(!("pin" in bulk[0]))
})

test("US2 BR2.9: activity history logs sell/view but never lookups", () => {
  svc.lookupStatus(AGENT, "ZC50K0001") // must NOT be logged
  const [unsold] = svc.listCards(AGENT, { serial: "ZC50K0001" })
  svc.sellSingle(AGENT, unsold.id, PIN)
  const log = svc.getActivityHistory(AGENT)
  assert.ok(log.every((e) => e.action !== "lookup" && e.action !== "status_searched"))
  assert.ok(log.some((e) => e.action === "sell_single"))
})

test("US2 BR2.1: agent cannot access another agent's card detail", () => {
  const otherRows = svc.listCards(OTHER)
  const otherId = otherRows[0].id
  assert.throws(() => svc.getCardDetail(AGENT, otherId), /phạm vi của bạn/)
})
