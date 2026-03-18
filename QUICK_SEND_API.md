# Quick Send API Contract

This document defines the API expected by `Quick Send` compose UI.

For a consolidated list of all recent API updates (including campaigns/flows/templates), see:
- `UI_API_CHANGELOG.md`

## Base

- Base URL: `/api/v1`
- Headers:
  - `Authorization: Bearer <token>`
  - `X-Client-Id: <client-id>`

---

## 1) Send text/template message

### `POST /messages/send`

Content-Type: `application/json`

### Request body

```json
{
  "to": "919971495703",
  "messageType": "TEXT",
  "text": "Hello from CRM"
}
```

```json
{
  "to": "919971495703",
  "messageType": "TEMPLATE",
  "templateId": "d8b1f42d-5f11-4f48-8cdf-fdf8f47d3e4d",
  "variables": {
    "1": "Suresh",
    "2": "ORD-1001"
  }
}
```

### Rules

- `messageType` is required and must be `TEXT` or `TEMPLATE`
- `messageType=TEXT` => `text` required
- `messageType=TEMPLATE` => `templateId` required; `variables` optional
- `messageType` is not inferred from `templateId`
- `to` must be E.164-compatible phone format without special chars

### Success

- `200 OK` or `202 Accepted`

```json
{
  "id": "message-uuid",
  "providerMessageId": "wamid....",
  "status": "SENT"
}
```

---

## 2) Send media message (image/video/document)

### `POST /messages/send-media`

Content-Type: `multipart/form-data`

### Form fields

- `to` (string, required)
- `messageType` (`IMAGE` | `VIDEO` | `DOCUMENT`, required)
- `file` (binary, required)
- `caption` (string, optional)

### Example cURL

```bash
curl -X POST "https://<host>/api/v1/messages/send-media" \
  -H "Authorization: Bearer <token>" \
  -H "X-Client-Id: <client-id>" \
  -F "to=919971495703" \
  -F "messageType=IMAGE" \
  -F "file=@/tmp/photo.jpg" \
  -F "caption=Invoice screenshot"
```

### Validation

- `IMAGE` => file mime starts with `image/`
- `VIDEO` => file mime starts with `video/`
- `DOCUMENT` => allow office/pdf/text docs
- max size is enforced via `spring.servlet.multipart.max-file-size`
- backend flow follows WhatsApp Cloud API:
  1) upload to `/{phone-number-id}/media`
  2) send media message with uploaded `media_id`
  3) persist sent message metadata in CRM DB

### Success

- `200 OK` or `202 Accepted`

```json
{
  "id": "message-uuid",
  "providerMessageId": "wamid....",
  "status": "SENT"
}
```

---

## 3) History API (used by chat)

### `GET /messages/history?phone=<number>`

Returns sorted message items including:

- `id`
- `direction`
- `status`
- `messageType`
- `payloadJson`
- `responseJson`
- `createdAt`

---

## 4) Media retrieval API (used for preview/open/download)

Use the contract in `MESSAGE_MEDIA_API.md`:

- `GET /messages/{messageId}/media?disposition=inline`
- `GET /messages/{messageId}/media?disposition=attachment`

---

## 5) Error model

Recommended error response:

```json
{
  "error": "VALIDATION_ERROR",
  "message": "Template is not approved.",
  "details": {
    "field": "templateId"
  }
}
```

Common errors:

- `400` invalid payload / missing fields
- `401` unauthorized
- `403` forbidden / tenant mismatch
- `404` contact/template not found
- `409` template not approved / policy conflict
- `413` file too large
- `422` unsupported media type (mime type mismatch for selected `messageType`)
- `429` rate limited
- `500` provider/internal failure

