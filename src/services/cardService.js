// Card lookup, inventory and reveal-gate business logic for G4.F11
// (Tra cứu thẻ + Quản lý thẻ đã mua + Card reveal gate).
//
// State machine reference (canonical = G4.FB05.U3): this module only handles the
// two agent-facing states + the reveal transition described in G4.F11.U3:
//   delivered_unrevealed -> revealed_sold   (irreversible, BR3.2)
// Other lifecycle states (used / expired / blocked_*) are read-only here.

// --- Configurable values (every number/format is a config value per PRD §frontmatter) ---
export const CONFIG = {
  // Fully-masked Zing card PIN shown for unsold cards (BR2.2 — hide ALL, including tail).
  pinMask: "***-****-****",
  // Serial format validation (CC1 US1 — concrete rule is TBD with the card-schema team).
  serialPattern: /^[A-Za-z0-9]{6,20}$/,
  // Sales PIN is a 6-digit code set at first login (G2-F06).
  salesPinPattern: /^\d{6}$/,
}

// Technical card states (subset agent can encounter — canonical 8-state set is G4.FB05.U3).
export const CARD_STATE = {
  DELIVERED_UNREVEALED: "delivered_unrevealed",
  REVEALED_SOLD: "revealed_sold",
  USED: "used",
  EXPIRED: "expired",
  BLOCKED_FRAUD: "blocked_fraud",
  BLOCKED_RECOVERABLE: "blocked_recoverable",
}

// Friendly business labels shown to the dealer (BR1.3) — mapped from technical state.
const STATE_LABEL = {
  delivered_unrevealed: "Chưa bán",
  revealed_sold: "Đã bán",
  used: "Đã sử dụng",
  expired: "Hết hạn",
  blocked_fraud: "Bị khóa",
  blocked_recoverable: "Bị khóa",
}

export function friendlyStateLabel(state) {
  return STATE_LABEL[state] ?? "Không xác định"
}

// Sell status (Trạng thái bán) derived from technical state.
export function sellStatusLabel(state) {
  return state === CARD_STATE.REVEALED_SOLD || state === CARD_STATE.USED ? "Đã bán" : "Chưa bán"
}

const BLOCKED_STATES = [CARD_STATE.BLOCKED_FRAUD, CARD_STATE.BLOCKED_RECOVERABLE]

// --- In-memory stores (demo). One card store + one audit-log store. ---
const cards = []
const activityLog = [] // card-handling actions only (sell / view) — NOT lookups (BR2.9)
let nextCardId = 1

// Per-agent sales PIN (set at first login G2-F06; reset at G5-F12 — out of scope here).
const salesPins = new Map()

export function reset() {
  cards.length = 0
  activityLog.length = 0
  salesPins.clear()
  nextCardId = 1
}

// Seed a card into the store (cards are generated on payment success — G3.F08, out of scope).
export function createCard({
  agentId,
  serial,
  pin,
  denomination,
  orderId,
  purchaseDate = null,
  soldDate = null,
  usedDate = null,
  expiryDate = null,
  state = CARD_STATE.DELIVERED_UNREVEALED,
}) {
  const card = {
    id: nextCardId++,
    agentId,
    serial,
    pin, // secret — never returned for unsold cards
    denomination,
    orderId,
    purchaseDate,
    soldDate,
    usedDate,
    expiryDate,
    state,
  }
  cards.push(card)
  return card
}

export function setSalesPin(agentId, pin) {
  salesPins.set(agentId, pin)
}

function audit(entry) {
  // Audit records never contain the Zing card PIN value (Compliance §1.6).
  const record = { ...entry, timestamp: new Date().toISOString() }
  activityLog.push(record)
  return record
}

// --- US1: status lookup by serial (read-only, never reveals PIN) ---
export class CardError extends Error {
  constructor(code, message) {
    super(message)
    this.code = code
  }
}

export function lookupStatus(agentId, serial) {
  // BR1.2 — lookup is by serial ONLY; PIN is never a lookup key and is never returned.
  if (typeof serial !== "string" || !CONFIG.serialPattern.test(serial.trim())) {
    throw new CardError("invalid_serial", "Vui lòng nhập đúng định dạng Seri")
  }
  const normalized = serial.trim()
  const card = cards.find((c) => c.serial === normalized && c.agentId === agentId)
  // BR1.1 — out-of-scope and non-existent are indistinguishable (uniform "not found").
  if (!card) {
    throw new CardError("not_found", "Không tìm thấy thẻ trong phạm vi của bạn")
  }
  return {
    serial: card.serial,
    statusLabel: friendlyStateLabel(card.state), // friendly label (AC4: "Bị khóa" w/o reason)
    denomination: card.denomination,
    expiryDate: card.expiryDate,
    // BR1.2 — NO pin field here.
  }
}

// --- US2: inventory list with the closed filter set (BR2.1, BR2.8) ---
export function listCards(agentId, filters = {}) {
  let result = cards.filter((c) => c.agentId === agentId) // scope to owning dealer (BR2.1)

  const { serial, sellStatus, cardState, denomination, soldDate, usedDate, expiryDate, purchaseDate } = filters
  if (serial) {
    const q = String(serial).trim().toLowerCase()
    result = result.filter((c) => c.serial.toLowerCase().includes(q))
  }
  if (sellStatus) result = result.filter((c) => sellStatusLabel(c.state) === sellStatus)
  if (cardState) result = result.filter((c) => c.state === cardState)
  if (denomination != null) result = result.filter((c) => c.denomination === Number(denomination))
  if (soldDate) result = result.filter((c) => c.soldDate === soldDate)
  if (usedDate) result = result.filter((c) => c.usedDate === usedDate)
  if (expiryDate) result = result.filter((c) => c.expiryDate === expiryDate)
  if (purchaseDate) result = result.filter((c) => c.purchaseDate === purchaseDate)

  // Row shape — BR2.1: list NEVER exposes the PIN column.
  return result.map((c) => ({
    id: c.id,
    serial: c.serial,
    denomination: c.denomination,
    sellStatus: sellStatusLabel(c.state),
    cardStatus: friendlyStateLabel(c.state),
    usedDate: c.usedDate,
    expiryDate: c.expiryDate,
    purchaseDate: c.purchaseDate,
    soldDate: c.soldDate,
    orderId: c.orderId,
  }))
}

function findOwnedCard(agentId, cardId) {
  const card = cards.find((c) => c.id === Number(cardId) && c.agentId === agentId)
  if (!card) throw new CardError("not_found", "Không tìm thấy thẻ trong phạm vi của bạn")
  return card
}

// --- US2: card detail (row click). PIN shown by context (BR2.7, BR3.1) ---
// `reveal` is true when the dealer explicitly views a SOLD card via [Xem lại] (logs each time).
export function getCardDetail(agentId, cardId, { reveal = false } = {}) {
  const card = findOwnedCard(agentId, cardId)
  const isSold = card.state === CARD_STATE.REVEALED_SOLD || card.state === CARD_STATE.USED
  let pin = CONFIG.pinMask
  if (isSold && reveal) {
    pin = card.pin // BR3.5 — full PIN shown EACH view of a sold card
    audit({ action: "view_sold", agentId, actor: agentId, serial: card.serial })
  }
  return {
    id: card.id,
    statusLabel: friendlyStateLabel(card.state),
    serial: card.serial,
    denomination: card.denomination,
    orderId: card.orderId,
    expiryDate: card.expiryDate,
    deliveredDate: card.purchaseDate, // "Ngày xuất bán cho đại lý"
    soldDate: card.soldDate, // empty until sold
    usedDate: card.usedDate, // empty until player tops up
    pin, // masked unless this is an explicit reveal of a sold card
    sellable: card.state === CARD_STATE.DELIVERED_UNREVEALED, // [Bán thẻ] enabled? (CC4)
    // BR2.7 — "Ngày tạo" intentionally omitted.
  }
}

function ensureValidSalesPin(agentId, salesPin) {
  // BR3.4 — sales PIN required in both sell flows; never store/return its value in audit.
  if (typeof salesPin !== "string" || !CONFIG.salesPinPattern.test(salesPin)) {
    throw new CardError("invalid_pin", "Mã PIN bán hàng phải gồm 6 chữ số")
  }
  const expected = salesPins.get(agentId)
  if (expected == null || salesPin !== expected) {
    // CC3 US2 — wrong PIN: no sale, card stays unsold.
    throw new CardError("wrong_pin", "Mã PIN bán hàng không đúng")
  }
}

// --- US3: card reveal gate — sell a single card (Path B, flow bán 1 thẻ) ---
export function sellSingle(agentId, cardId, salesPin, { now = null } = {}) {
  const card = findOwnedCard(agentId, cardId)

  // CC3 US3 — race: already sold -> treat as view-again, no duplicate sell log (BR3.5).
  if (card.state === CARD_STATE.REVEALED_SOLD || card.state === CARD_STATE.USED) {
    audit({ action: "view_sold", agentId, actor: agentId, serial: card.serial })
    return { id: card.id, serial: card.serial, statusLabel: friendlyStateLabel(card.state), pin: card.pin, alreadySold: true }
  }
  // CC4 / CC2 — expired or blocked cannot be sold; PIN stays hidden.
  if (card.state === CARD_STATE.EXPIRED) {
    throw new CardError("not_sellable", "Thẻ đã hết hạn — không thể bán")
  }
  if (BLOCKED_STATES.includes(card.state)) {
    throw new CardError("blocked", "Thẻ này đang bị khóa, vui lòng liên hệ hỗ trợ")
  }

  ensureValidSalesPin(agentId, salesPin)

  // Irreversible transition (BR3.2).
  card.state = CARD_STATE.REVEALED_SOLD
  card.soldDate = now ?? new Date().toISOString().slice(0, 10)

  // BR3.3 — audit: time + seller + serial; NO PIN value.
  audit({ action: "sell_single", agentId, actor: agentId, serial: card.serial })

  // BR3.1 — single-sell reveals the full PIN + copy button.
  return { id: card.id, serial: card.serial, statusLabel: friendlyStateLabel(card.state), pin: card.pin, alreadySold: false }
}

// --- US3: bulk sell (Path B, flow bán nhiều thẻ) — Excel + password to email, no PIN on UI ---
export function sellBulk(agentId, serials, salesPin, { now = null } = {}) {
  if (!Array.isArray(serials) || serials.length === 0) {
    throw new CardError("invalid_selection", "Vui lòng chọn ít nhất một thẻ")
  }
  ensureValidSalesPin(agentId, salesPin)

  const sold = []
  const skipped = []
  const soldDate = now ?? new Date().toISOString().slice(0, 10)
  for (const serial of serials) {
    const card = cards.find((c) => c.serial === serial && c.agentId === agentId)
    if (!card || card.state !== CARD_STATE.DELIVERED_UNREVEALED) {
      skipped.push(serial)
      continue
    }
    card.state = CARD_STATE.REVEALED_SOLD // irreversible (BR3.2)
    card.soldDate = soldDate
    audit({ action: "sell_bulk", agentId, actor: agentId, serial: card.serial }) // BR3.3 per card
    sold.push(card.serial)
  }
  // BR2.6 — file delivered via password-protected Excel to dealer email; UI never shows PIN.
  return { soldSerials: sold, skippedSerials: skipped, delivery: "excel_email", pinShown: false }
}

// --- US2: activity history (BR2.9) — sell/view actions, never lookups ---
export function getActivityHistory(agentId) {
  return activityLog.filter((e) => e.agentId === agentId).map((e) => ({ ...e }))
}
