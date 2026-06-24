// Routes for G4.F11 — Tra cứu thẻ + Quản lý thẻ đã mua + Card reveal gate.
// Dealer scope is taken from the `x-agent-id` header (real auth/IAM = G10.FB17).
import { Router } from "express"
import * as cardService from "../services/cardService.js"

export const cardsRouter = Router()

function agentId(req) {
  return req.header("x-agent-id")
}

function requireAgent(req, res) {
  const id = agentId(req)
  if (!id) {
    res.status(401).json({ error: "x-agent-id header required" })
    return null
  }
  return id
}

function handleError(err, res) {
  if (err instanceof cardService.CardError) {
    const status = err.code === "not_found" ? 404 : err.code === "blocked" || err.code === "not_sellable" || err.code === "wrong_pin" ? 409 : 400
    return res.status(status).json({ error: err.message, code: err.code })
  }
  return res.status(500).json({ error: "Hệ thống đang xử lý, vui lòng thử lại sau" })
}

/**
 * @openapi
 * /cards/lookup:
 *   get:
 *     summary: US1 — Tra cứu trạng thái thẻ theo Seri (read-only, không lộ Mã thẻ)
 *     parameters:
 *       - in: header
 *         name: x-agent-id
 *         required: true
 *         schema: { type: string }
 *       - in: query
 *         name: serial
 *         required: true
 *         schema: { type: string }
 *     responses:
 *       200:
 *         description: Trạng thái thẻ + thông tin cơ bản (không có Mã thẻ)
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 serial: { type: string }
 *                 statusLabel: { type: string }
 *                 denomination: { type: number }
 *                 expiryDate: { type: string, nullable: true }
 *       400: { description: Seri trống / sai định dạng }
 *       404: { description: Không tìm thấy thẻ trong phạm vi của bạn }
 */
cardsRouter.get("/lookup", (req, res) => {
  const id = requireAgent(req, res)
  if (!id) return
  try {
    res.json(cardService.lookupStatus(id, req.query.serial))
  } catch (err) {
    handleError(err, res)
  }
})

/**
 * @openapi
 * /cards:
 *   get:
 *     summary: US2 — Kho thẻ đã mua (danh sách + bộ filter đóng; không có cột Mã thẻ)
 *     parameters:
 *       - in: header
 *         name: x-agent-id
 *         required: true
 *         schema: { type: string }
 *       - in: query
 *         name: serial
 *         schema: { type: string }
 *       - in: query
 *         name: sellStatus
 *         schema: { type: string, enum: ["Chưa bán", "Đã bán"] }
 *       - in: query
 *         name: cardState
 *         schema: { type: string }
 *       - in: query
 *         name: denomination
 *         schema: { type: number }
 *       - in: query
 *         name: soldDate
 *         schema: { type: string }
 *       - in: query
 *         name: usedDate
 *         schema: { type: string }
 *       - in: query
 *         name: expiryDate
 *         schema: { type: string }
 *       - in: query
 *         name: purchaseDate
 *         schema: { type: string }
 *     responses:
 *       200:
 *         description: Danh sách thẻ thuộc phạm vi đại lý (không lộ Mã thẻ)
 */
cardsRouter.get("/", (req, res) => {
  const id = requireAgent(req, res)
  if (!id) return
  res.json(cardService.listCards(id, req.query))
})

/**
 * @openapi
 * /cards/activity-history:
 *   get:
 *     summary: US2 — Lịch sử hoạt động kho thẻ (log bán/xem thẻ; KHÔNG log tra cứu)
 *     parameters:
 *       - in: header
 *         name: x-agent-id
 *         required: true
 *         schema: { type: string }
 *     responses:
 *       200:
 *         description: Danh sách action xử lý thẻ (bán / xem lại)
 */
cardsRouter.get("/activity-history", (req, res) => {
  const id = requireAgent(req, res)
  if (!id) return
  res.json(cardService.getActivityHistory(id))
})

/**
 * @openapi
 * /cards/{id}:
 *   get:
 *     summary: US2 — Chi tiết thẻ (row click). Mã thẻ ẩn nếu chưa bán; hiện + ghi log nếu reveal thẻ đã bán
 *     parameters:
 *       - in: header
 *         name: x-agent-id
 *         required: true
 *         schema: { type: string }
 *       - in: path
 *         name: id
 *         required: true
 *         schema: { type: integer }
 *       - in: query
 *         name: reveal
 *         description: true để [Xem lại] thẻ Đã bán (hiện Mã thẻ đầy đủ + ghi audit log mỗi lần)
 *         schema: { type: boolean }
 *     responses:
 *       200: { description: Chi tiết thẻ }
 *       404: { description: Không tìm thấy thẻ trong phạm vi của bạn }
 */
cardsRouter.get("/:id", (req, res) => {
  const id = requireAgent(req, res)
  if (!id) return
  try {
    const reveal = req.query.reveal === "true" || req.query.reveal === true
    res.json(cardService.getCardDetail(id, req.params.id, { reveal }))
  } catch (err) {
    handleError(err, res)
  }
})

/**
 * @openapi
 * /cards/{id}/sell:
 *   post:
 *     summary: US3 — Card reveal gate (bán 1 thẻ). Nhập PIN bán hàng 6 số → hiện Mã thẻ đầy đủ (irreversible)
 *     parameters:
 *       - in: header
 *         name: x-agent-id
 *         required: true
 *         schema: { type: string }
 *       - in: path
 *         name: id
 *         required: true
 *         schema: { type: integer }
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required: [salesPin]
 *             properties:
 *               salesPin: { type: string, description: Mã PIN bán hàng 6 số }
 *     responses:
 *       200:
 *         description: Thẻ chuyển revealed_sold + trả về Mã thẻ đầy đủ
 *       409: { description: Thẻ bị khóa / hết hạn / sai PIN }
 */
cardsRouter.post("/:id/sell", (req, res) => {
  const id = requireAgent(req, res)
  if (!id) return
  try {
    res.json(cardService.sellSingle(id, req.params.id, req.body?.salesPin))
  } catch (err) {
    handleError(err, res)
  }
})

/**
 * @openapi
 * /cards/sell-bulk:
 *   post:
 *     summary: US3 — Bán nhiều thẻ → gửi Excel + mật khẩu về mail (KHÔNG hiện Mã thẻ trên UI)
 *     parameters:
 *       - in: header
 *         name: x-agent-id
 *         required: true
 *         schema: { type: string }
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required: [serials, salesPin]
 *             properties:
 *               serials: { type: array, items: { type: string } }
 *               salesPin: { type: string }
 *     responses:
 *       200:
 *         description: Các thẻ chuyển revealed_sold; file Excel gửi mail; pinShown=false
 */
cardsRouter.post("/sell-bulk", (req, res) => {
  const id = requireAgent(req, res)
  if (!id) return
  try {
    res.json(cardService.sellBulk(id, req.body?.serials, req.body?.salesPin))
  } catch (err) {
    handleError(err, res)
  }
})
