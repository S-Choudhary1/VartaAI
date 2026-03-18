# UI Integration API Changelog

This document summarizes all recent API changes for frontend integration, including updated existing endpoints and newly added endpoints.

References:
- WhatsApp overview: https://developers.facebook.com/documentation/business-messaging/whatsapp/overview
- Meta template API: https://developers.facebook.com/docs/graph-api/reference/page/message_templates

## Base

- Base URL: `/api/v1`
- Required headers:
  - `Authorization: Bearer <token>`
  - `X-Client-Id: <client-id>`

---

## 1) Messages API Changes

### Updated: `POST /messages/send`

Breaking/important updates:
- `messageType` is now required and must be `TEXT` or `TEMPLATE`.
- `TEXT` requires `text`.
- `TEMPLATE` requires `templateId`.
- Template send enforces approved template status.

Response now includes internal message id:

```json
{
  "id": "message-uuid",
  "providerMessageId": "wamid....",
  "status": "SENT"
}
```

### New: `POST /messages/send-media`

- Content-Type: `multipart/form-data`
- Fields:
  - `to`
  - `messageType` = `IMAGE | VIDEO | DOCUMENT`
  - `file`
  - `caption` (optional)

Behavior:
- Uploads media to Meta, then sends message using returned `media_id`.

Response:

```json
{
  "id": "message-uuid",
  "providerMessageId": "wamid....",
  "status": "SENT",
  "mediaId": "meta-media-id",
  "mimeType": "image/jpeg",
  "filename": "photo.jpg"
}
```

### New: `GET /messages/{messageId}/media`

- Query: `disposition=inline|attachment` (default `inline`)
- Returns binary file with proper `Content-Type` and `Content-Disposition`.
- Includes tenant safety and provider URL expiry handling.

For complete message/media contract:
- `QUICK_SEND_API.md`
- `MESSAGE_MEDIA_API.md`

---

## 2) Template API Changes

### Existing endpoints (enhanced response fields)

- `POST /templates`
- `POST /templates/v2`
- `PUT /templates/{id}`
- `PUT /templates/v2/{id}`
- `GET /templates`
- `GET /templates/{id}`

`TemplateResponse` now includes additional fields:
- `allowCategoryChange`
- `componentsJson`
- `exampleValuesJson`
- `rawTemplateJson`
- `lastSyncedAt`

### New/updated Meta endpoints

- `GET /templates/meta`
  - Fetches from Meta and **upserts into internal templates table**.
- `GET /templates/meta/approved`
  - Returns approved templates from **internal DB** (same response shape).
- `POST /templates/meta` (SUPER_ADMIN only)
  - Creates template in Meta + stores/upserts internally.

For full details and examples:
- `TEMPLATE_APIS.md`

---

## 3) Campaign API Changes

### Updated: `POST /campaigns/upload-csv`

Request fields:
- `file` (required)
- `name` (required)
- `uploadedBy` (required)
- `scheduledAt` (optional)
- `templateId` (optional)
- `flowVersionId` (optional)

Rules:
- At least one of `templateId` or `flowVersionId` is required.
- If `flowVersionId` is provided, flow-based campaign launch is used.
- If only `templateId` is provided, existing template blast behavior is used.

Response now includes:

```json
{
  "campaignId": "uuid",
  "name": "Campaign Name",
  "flowVersionId": "uuid-or-null",
  "status": "PENDING|COMPLETED|FAILED"
}
```

### New: `GET /campaigns/{id}/flow-runs`

Returns per-contact run state:

```json
[
  {
    "runId": "uuid",
    "contactId": "9199xxxxxxx",
    "phone": "9199xxxxxxx",
    "currentStep": "step_2",
    "lastResponse": "YES",
    "status": "ACTIVE|COMPLETED|FAILED",
    "completed": false,
    "failed": false,
    "startedAt": "2026-02-23T10:00:00Z",
    "updatedAt": "2026-02-23T10:01:00Z"
  }
]
```

---

## 4) New Flow APIs

Base: `/api/v1/flows`

- `POST /flows` -> create flow definition
- `POST /flows/with-version` -> create flow + first version
- `GET /flows` -> list flows
- `POST /flows/{flowId}/versions` -> create new version
- `POST /flows/versions/{versionId}/publish` -> publish a version

Flow runtime behavior:
- One active flow per contact.
- Operators: `EQUALS`, `CONTAINS`, `DEFAULT`.
- Step-1 sent at campaign launch for all contacts.
- On matching reply, next step is sent immediately.
- Unmatched reply is ignored.
- No timeout behavior in current version.

---

## 5) UI Integration Checklist

- Update send message payloads to always send `messageType`.
- Handle optional new fields in media send response (`mediaId`, `mimeType`, `filename`).
- Use `/templates/meta` to refresh/sync template library.
- Use `/templates/meta/approved` for approved list from internal DB.
- For flow campaigns, submit `flowVersionId` in campaign upload.
- Use `/campaigns/{id}/flow-runs` for campaign flow progress UI.
