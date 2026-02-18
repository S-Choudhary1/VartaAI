# Auto-Reply API Documentation

This document is for frontend/UI integration of the auto-reply feature.

## Overview

Auto-reply has two parts:

1. Client-level feature flag (master switch)
2. Auto-reply rules (matching and response behavior)

If the client feature flag is OFF, webhook messages are received and stored normally, but no automatic replies are sent.

## Auth and Headers

- All endpoints are under `/api/v1/auto-replies`
- Include JWT `Authorization: Bearer <token>`
- Include `X-Client-Id: <client-uuid>`

## Enums

### `ruleType`

- `TEXT_EXACT`
- `BUTTON_PAYLOAD`
- `INTERACTIVE_REPLY_ID`
- `DEFAULT`

### `responseType`

- `TEXT`
- `TEMPLATE`

## Feature Flag Endpoints

### 1) Get feature flag

- Method: `GET`
- Path: `/api/v1/auto-replies/feature-flag`
- Roles: authenticated user (same as other read APIs)

Response `200`:

```json
{
  "clientId": "8a0e7f6a-0c17-4b51-94f6-f0e8f2bb3295",
  "enabled": false
}
```

### 2) Update feature flag

- Method: `PATCH`
- Path: `/api/v1/auto-replies/feature-flag`
- Roles: `ADMIN` or `MANAGER`

Request:

```json
{
  "enabled": true
}
```

Response `200`:

```json
{
  "clientId": "8a0e7f6a-0c17-4b51-94f6-f0e8f2bb3295",
  "enabled": true
}
```

## Rule Endpoints

### Rule Object

```json
{
  "id": "f4ecf0af-a35f-4fd8-838c-c5e8c58be0b1",
  "name": "Yes reply text",
  "ruleType": "TEXT_EXACT",
  "matchValue": "yes",
  "responseType": "TEXT",
  "responseText": "Thanks for confirming.",
  "templateId": null,
  "active": true,
  "createdAt": "2026-02-18T10:12:22.513913Z",
  "updatedAt": "2026-02-18T10:12:22.513913Z"
}
```

### 1) Create rule

- Method: `POST`
- Path: `/api/v1/auto-replies`
- Roles: `ADMIN` or `MANAGER`

#### A. Text exact + text response

Request:

```json
{
  "name": "User replied yes",
  "ruleType": "TEXT_EXACT",
  "matchValue": "yes",
  "responseType": "TEXT",
  "responseText": "Great, our team will contact you soon.",
  "active": true
}
```

#### B. Button payload + template response

Request:

```json
{
  "name": "Interested button",
  "ruleType": "BUTTON_PAYLOAD",
  "matchValue": "INTERESTED",
  "responseType": "TEMPLATE",
  "templateId": "e6f2f12a-7f37-4f79-9b45-b2baaf4c28af",
  "active": true
}
```

#### C. Default fallback

Request:

```json
{
  "name": "Default response",
  "ruleType": "DEFAULT",
  "responseType": "TEXT",
  "responseText": "Thanks for your message. We will get back to you.",
  "active": true
}
```

Response `201`: returns Rule Object.

### 2) List rules

- Method: `GET`
- Path: `/api/v1/auto-replies`

Response `200`:

```json
[
  {
    "id": "f4ecf0af-a35f-4fd8-838c-c5e8c58be0b1",
    "name": "User replied yes",
    "ruleType": "TEXT_EXACT",
    "matchValue": "yes",
    "responseType": "TEXT",
    "responseText": "Great, our team will contact you soon.",
    "templateId": null,
    "active": true,
    "createdAt": "2026-02-18T10:12:22.513913Z",
    "updatedAt": "2026-02-18T10:12:22.513913Z"
  }
]
```

### 3) Get single rule

- Method: `GET`
- Path: `/api/v1/auto-replies/{id}`

Response `200`: Rule Object.

### 4) Update rule

- Method: `PUT`
- Path: `/api/v1/auto-replies/{id}`
- Roles: `ADMIN` or `MANAGER`

Request:

```json
{
  "name": "User replied yes (updated)",
  "ruleType": "TEXT_EXACT",
  "matchValue": "yes",
  "responseType": "TEXT",
  "responseText": "Perfect. We are processing your request.",
  "active": true
}
```

Response `200`: Rule Object.

### 5) Update rule status

- Method: `PATCH`
- Path: `/api/v1/auto-replies/{id}/status`
- Roles: `ADMIN` or `MANAGER`

Request:

```json
{
  "active": false
}
```

Response `200`: Rule Object with updated `active`.

### 6) Delete rule

- Method: `DELETE`
- Path: `/api/v1/auto-replies/{id}`
- Roles: `ADMIN` or `MANAGER`

Response: `204 No Content`

## Validation Rules

1. `matchValue` required for:
   - `TEXT_EXACT`
   - `BUTTON_PAYLOAD`
   - `INTERACTIVE_REPLY_ID`
2. `matchValue` is ignored for `DEFAULT`.
3. `responseType = TEXT` requires `responseText`.
4. `responseType = TEMPLATE` requires valid `templateId` of the same client.
5. Only one active `DEFAULT` rule is allowed per client.

## Matching and Execution Logic

1. Webhook inbound event arrives.
2. Backend checks client feature flag.
   - If disabled: no auto-reply execution.
3. For enabled clients:
   - Try matching specific rules first (created order).
   - If no specific match, use active `DEFAULT` rule.
4. On match, backend sends message immediately using:
   - text reply (`responseType = TEXT`), or
   - template reply (`responseType = TEMPLATE`)

## Supported Inbound Scenarios

- Reply to previous outbound message (`context.id` present)
- Standalone inbound message (no `context.id`)
- Message payload types:
  - text
  - button
  - interactive button reply
  - interactive list reply

## Error Format

Validation/runtime errors follow global error response:

```json
{
  "status": "error",
  "message": "Validation failed",
  "errors": {
    "enabled": "Enabled is required"
  }
}
```

or

```json
{
  "status": "error",
  "message": "Only one active DEFAULT auto-reply rule is allowed per client"
}
```

## UI Build Checklist

1. Add auto-reply feature toggle screen:
   - read flag (`GET /feature-flag`)
   - update flag (`PATCH /feature-flag`)
2. Add rule CRUD screen:
   - list/create/update/delete
   - status toggle
3. Dynamic form:
   - show `matchValue` except for `DEFAULT`
   - show `responseText` for `TEXT`
   - show template selector for `TEMPLATE`
4. Add client-side guard:
   - if feature is OFF, show info banner "Auto-reply disabled for this client".
