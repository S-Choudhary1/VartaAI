# Message Media API (Quick Send)

This API contract is for rendering and downloading WhatsApp media messages in CRM chat history.

## Base

- Base URL: `/api/v1`
- Auth: `Authorization: Bearer <token>`
- Tenant header: `X-Client-Id: <client-id>`

## 1) History API (already in use)

### `GET /messages/history?phone=<e164>`

Returns chat history with metadata and payload snapshots.

Example response item:

```json
{
  "id": "ea505698-7fb7-4fec-ab72-62f806a79f27",
  "contactId": "bda0e93d-c13f-4c16-8fe0-443bb3f06580",
  "providerMessageId": "wamid....",
  "provider": "META",
  "direction": "INCOMING",
  "status": "DELIVERED",
  "messageType": "IMAGE",
  "payloadJson": "{\"type\":\"image\",\"image\":{\"id\":\"144...\",\"mime_type\":\"image/jpeg\"}}",
  "responseJson": "{\"id\":\"144...\",\"type\":\"image\",\"filename\":null,\"mimeType\":\"image/jpeg\"}",
  "createdAt": "2026-02-23T09:29:08.370194Z"
}
```

---

## 2) Media content API (new)

### `GET /messages/{messageId}/media`

Returns binary media for message IDs where `messageType` is media-capable (`IMAGE`, `VIDEO`, `AUDIO`, `DOCUMENT`, `STICKER`).

### Query params

- `disposition` (optional): `inline` | `attachment`
  - `inline` => for preview/open in browser
  - `attachment` => for file download behavior
  - default: `inline`

### Response headers

- `Content-Type`: real mime type (example `image/jpeg`, `application/pdf`)
- `Content-Disposition`:
  - inline: `inline; filename="file.jpg"`
  - attachment: `attachment; filename="file.jpg"`
- `Cache-Control`: optional short-lived cache headers

### Success responses

- `200 OK` with binary body

### Error responses

- `400 Bad Request` when message type is unsupported
- `401 Unauthorized` invalid/expired token
- `403 Forbidden` cross-tenant access
- `404 Not Found` message/media not found
- `410 Gone` provider URL expired and media not recoverable
- `502 Bad Gateway` provider media fetch failed

Error payload:

```json
{
  "error": "MEDIA_NOT_FOUND",
  "message": "Media is unavailable for this message."
}
```

---

## 3) Backend behavior notes

- Use `providerMessageId` + stored media metadata (`payloadJson`/`responseJson`) to resolve media.
- If provider URL expires:
  - Re-fetch from provider media API using media ID when possible.
  - Optionally cache media in your own storage and serve directly.
- Enforce tenant isolation by `clientId`.
- Do not expose provider access tokens to frontend.

---

## 4) UI usage in Quick Send

- For media messages, UI calls:
  - `GET /messages/{id}/media?disposition=inline` for preview/open
  - `GET /messages/{id}/media?disposition=attachment` for download
- For `LOCATION`, UI renders map link from coordinates.
- For `CONTACTS`, UI renders contact summary from payload.

