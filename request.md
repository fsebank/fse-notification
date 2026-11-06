# FSE Notification API – Endpoint Reference

Base URL (local profile): `http://localhost:8182`

All request and response bodies are JSON. Timestamps are ISO-8601 UTC (e.g. `2026-11-05T10:15:30Z`). Money amounts are decimal numbers with 2 places, always in `NGN`.

## Contents

- [Authentication overview](#authentication-overview)
- [Error format](#error-format)
- [Pagination](#pagination)
- **Admin – Auth**
  - [1. Login](#1-login)
  - [2. Change own password](#2-change-own-password)
- **Admin – Users**
  - [3. Create admin](#3-create-admin)
  - [4. List admins](#4-list-admins)
  - [5. Enable / disable admin](#5-enable--disable-admin)
  - [6. Reset another admin's password](#6-reset-another-admins-password)
- **Admin – Access keys**
  - [7. Create access key](#7-create-access-key)
  - [8. List access keys](#8-list-access-keys)
  - [9. Get access key](#9-get-access-key)
  - [10. Update access key](#10-update-access-key)
  - [11. Update access key status](#11-update-access-key-status)
  - [12. Rotate access key secret](#12-rotate-access-key-secret)
  - [13. Delete access key](#13-delete-access-key)
- **Admin – Access key balance**
  - [14. Credit balance](#14-credit-balance)
  - [15. Get balance](#15-get-balance)
  - [16. List balance transactions](#16-list-balance-transactions)
- **Admin – Message prices**
  - [17. List message prices](#17-list-message-prices)
  - [18. Get message price](#18-get-message-price)
  - [19. Create message price](#19-create-message-price)
  - [20. Update message price](#20-update-message-price)
  - [21. Delete message price](#21-delete-message-price)
- **Partner – Account**
  - [22. Verify access key (partner "me")](#22-verify-access-key-partner-me)
  - [23. Get own balance](#23-get-own-balance)
  - [24. List own balance transactions](#24-list-own-balance-transactions)
- **Partner – Messages**
  - [How messages are charged](#how-messages-are-charged)
  - [25. Send SMS](#25-send-sms)
  - [26. Send email](#26-send-email)
  - [27. Get message](#27-get-message)
  - [28. List messages](#28-list-messages)

---

## Authentication overview

| Area | Paths | How to authenticate |
|---|---|---|
| Admin login | `POST /api/v1/admin/auth/login` | None (open) |
| Admin APIs | `/api/v1/admin/**` | `Authorization: Bearer <accessToken>` from login |
| Partner APIs | `/api/**` (outside `/api/v1/admin`), e.g. `/api/v1/partner/**`, `/api/v1/messages/**` | `X-Client-Id` and `X-Client-Secret` headers. A Bearer token is **not** accepted |

- Admin tokens expire after `expiresIn` seconds (default 1800 = 30 min). Log in again to get a new one.
- If login returns `"passwordChangeRequired": true`, the token only works on **Change own password**. Every other admin endpoint returns `403` until the password is changed.
- After any password change, tokens issued before it stop working.
- Login and change-password are rate limited. 5 failures per username or 20 per IP within 15 minutes cause a lockout of 15 minutes (`429`).
- Access keys never expire. They work until suspended, revoked, deleted or their secret is rotated. The IP whitelist is optional.

---

## Error format

Every error response has this shape:

```json
{
  "timestamp": "2026-11-05T10:15:30Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/v1/admin/access-keys",
  "details": ["clientName: must not be blank"]
}
```

`details` lists field errors on validation failures. In all other cases it is an empty array.

Common errors on every admin endpoint (except login):

| Status | `message` | Cause |
|---|---|---|
| 401 | `Authentication required` | Missing, invalid or expired token; admin disabled; token issued before a password change |
| 403 | `Access denied` | Caller is not an admin |
| 403 | `Password change required; call POST /api/v1/admin/auth/change-password` | Admin must change password first |

Common errors on every partner endpoint:

| Status | `message` | Cause |
|---|---|---|
| 401 | `Missing X-Client-Id or X-Client-Secret header` | Header absent or blank (also when an admin Bearer token is sent instead) |
| 401 | `Invalid client credentials` | Unknown client id or wrong secret |
| 401 | `Access key is suspended` / `Access key is revoked` | Key not `ACTIVE` |
| 403 | `Request IP address is not whitelisted` | Key has a whitelist and the caller IP is not in it |

---

## Pagination

List endpoints that page take these query parameters:

| Parameter | Default | Rules |
|---|---|---|
| `page` | `0` | Zero-based page number |
| `size` | `20` | 1–100 |

They return:

```json
{
  "content": [ ],
  "page": 0,
  "size": 20,
  "totalElements": 57,
  "totalPages": 3
}
```

Items are newest first.

---

# Admin – Auth

## 1. Login

Exchanges an admin username and password for a Bearer token.

**URL:** `POST /api/v1/admin/auth/login`

**Headers**

| Header | Value |
|---|---|
| `Content-Type` | `application/json` |

Any `Authorization` header sent to this endpoint is ignored.

**Request**

```json
{
  "username": "admin",
  "password": "S3cure-Passw0rd!"
}
```

| Field | Type | Rules |
|---|---|---|
| `username` | string | required, max 100 |
| `password` | string | required, max 128 |

**Response – `200 OK`**

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJmc2Ut...",
  "tokenType": "Bearer",
  "expiresIn": 1800,
  "username": "admin",
  "passwordChangeRequired": false
}
```

**Errors**

| Status | `message` | Cause |
|---|---|---|
| 400 | `Validation failed` | Missing username or password |
| 401 | `Invalid username or password` | Wrong credentials, unknown user or disabled account (deliberately not distinguished) |
| 401 | `Authentication required` | Request sent as `GET` instead of `POST` |
| 429 | `Too many failed attempts; try again later` | Lockout. The `Retry-After` response header gives the seconds to wait |

**cURL**

```bash
curl -X POST http://localhost:8182/api/v1/admin/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"S3cure-Passw0rd!"}'
```

---

## 2. Change own password

Changes the signed-in admin's password and clears any pending forced change. Returns a **new token**, because the old token stops working.

**URL:** `POST /api/v1/admin/auth/change-password`

**Headers**

| Header | Value |
|---|---|
| `Authorization` | `Bearer <accessToken>` |
| `Content-Type` | `application/json` |

Works with a token that has `passwordChangeRequired: true`.

**Request**

```json
{
  "currentPassword": "S3cure-Passw0rd!",
  "newPassword": "An0ther-Str0ng-Pass"
}
```

| Field | Type | Rules |
|---|---|---|
| `currentPassword` | string | required, max 128 |
| `newPassword` | string | required, 12–128 characters, must differ from the current password |

**Response – `200 OK`**

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJmc2Ut...",
  "tokenType": "Bearer",
  "expiresIn": 1800,
  "username": "admin",
  "passwordChangeRequired": false
}
```

**Errors**

| Status | `message` | Cause |
|---|---|---|
| 400 | `Validation failed` | Missing field or new password shorter than 12 characters |
| 400 | `Current password is incorrect` | Wrong current password (counts toward the rate limit) |
| 400 | `New password must be different from the current password` | Same password |
| 429 | `Too many failed attempts; try again later` | Lockout. See the `Retry-After` header |

**cURL**

```bash
curl -X POST http://localhost:8182/api/v1/admin/auth/change-password \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"currentPassword":"S3cure-Passw0rd!","newPassword":"An0ther-Str0ng-Pass"}'
```

---

# Admin – Users

**Admin response object** (used by the endpoints below):

| Field | Type | Description |
|---|---|---|
| `id` | number | Admin id |
| `username` | string | Login name |
| `fullName` | string \| null | Display name |
| `enabled` | boolean | `false` = cannot log in, existing tokens rejected |
| `mustChangePassword` | boolean | `true` = must change password at next login |
| `passwordChangedAt` | timestamp \| null | Last password change |
| `createdBy` | string \| null | Admin who created this account (`system` for the seeded default admin) |
| `createdAt` | timestamp | |
| `updatedAt` | timestamp | |

## 3. Create admin

Creates an admin. The new admin must change the password at first login.

**URL:** `POST /api/v1/admin/users`

**Headers**

| Header | Value |
|---|---|
| `Authorization` | `Bearer <accessToken>` |
| `Content-Type` | `application/json` |

**Request**

```json
{
  "username": "henry.tommy",
  "password": "Temp-Passw0rd-123",
  "fullName": "Henry Tommy"
}
```

| Field | Type | Rules |
|---|---|---|
| `username` | string | required, 3–100 characters, letters, digits and `. _ @ -` only |
| `password` | string | required, 12–128 characters |
| `fullName` | string | optional, max 150 |

**Response – `201 Created`**

```json
{
  "id": 2,
  "username": "henry.tommy",
  "fullName": "Henry Tommy",
  "enabled": true,
  "mustChangePassword": true,
  "passwordChangedAt": "2026-11-05T10:20:00Z",
  "createdBy": "admin",
  "createdAt": "2026-11-05T10:20:00Z",
  "updatedAt": "2026-11-05T10:20:00Z"
}
```

**Errors**

| Status | `message` | Cause |
|---|---|---|
| 400 | `Validation failed` | Invalid fields |
| 400 | `Username already exists` | Duplicate (case-insensitive) |

**cURL**

```bash
curl -X POST http://localhost:8182/api/v1/admin/users \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"username":"henry.tommy","password":"Temp-Passw0rd-123","fullName":"Henry Tommy"}'
```

---

## 4. List admins

**URL:** `GET /api/v1/admin/users`

**Headers**

| Header | Value |
|---|---|
| `Authorization` | `Bearer <accessToken>` |

**Request:** no body.

**Response – `200 OK`**

```json
[
  {
    "id": 1,
    "username": "admin",
    "fullName": "Default Administrator",
    "enabled": true,
    "mustChangePassword": false,
    "passwordChangedAt": "2026-11-05T09:00:00Z",
    "createdBy": "system",
    "createdAt": "2026-11-05T08:55:00Z",
    "updatedAt": "2026-11-05T09:00:00Z"
  }
]
```

**cURL**

```bash
curl http://localhost:8182/api/v1/admin/users \
  -H "Authorization: Bearer <accessToken>"
```

---

## 5. Enable / disable admin

A disabled admin cannot log in, and their existing tokens are rejected immediately. You cannot disable your own account.

**URL:** `PATCH /api/v1/admin/users/{id}/status`

**Headers**

| Header | Value |
|---|---|
| `Authorization` | `Bearer <accessToken>` |
| `Content-Type` | `application/json` |

**Path parameters:** `id` is the admin id.

**Request**

```json
{
  "enabled": false
}
```

**Response – `200 OK`:** Admin response object with the updated `enabled` value.

**Errors**

| Status | `message` | Cause |
|---|---|---|
| 400 | `You cannot disable your own account` | `id` is the caller |
| 404 | `Admin user not found` | Unknown `id` |

**cURL**

```bash
curl -X PATCH http://localhost:8182/api/v1/admin/users/1/status \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"enabled": false}'
```

---

## 6. Reset another admin's password

Sets a temporary password for another admin. Their existing tokens stop working, and they must change the password at next login. To change your own password, use **Change own password** instead.

**URL:** `PUT /api/v1/admin/users/{id}/password`

**Headers**

| Header | Value |
|---|---|
| `Authorization` | `Bearer <accessToken>` |
| `Content-Type` | `application/json` |

**Path parameters:** `id` is the admin id.

**Request**

```json
{
  "newPassword": "Temp-Reset-Passw0rd"
}
```

| Field | Type | Rules |
|---|---|---|
| `newPassword` | string | required, 12–128 characters |

**Response – `200 OK`:** Admin response object with `"mustChangePassword": true`.

**Errors**

| Status | `message` | Cause |
|---|---|---|
| 400 | `Validation failed` | Password too short or missing |
| 400 | `Use the change-password endpoint to change your own password` | `id` is the caller |
| 404 | `Admin user not found` | Unknown `id` |

**cURL**

```bash
curl -X PUT http://localhost:8182/api/v1/admin/users/2/password \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"newPassword":"Temp-Reset-Passw0rd"}'
```

---

# Admin – Access keys

**Access key response object** (used by the endpoints below):

| Field | Type | Description |
|---|---|---|
| `id` | number | Access key id |
| `clientName` | string | Partner name |
| `clientId` | string | Public id sent by the partner in `X-Client-Id` (format `fse_` + 24 hex characters) |
| `whitelistedIps` | string[] | Allowed IPs/CIDRs. Empty = any IP allowed |
| `status` | string | `ACTIVE`, `SUSPENDED` or `REVOKED` |
| `balance` | number | Prepaid balance in NGN |
| `lastUsedAt` | timestamp \| null | Last successful partner request (updated at most once per minute) |
| `createdBy` | string | Admin who created the key |
| `createdAt` | timestamp | |
| `updatedAt` | timestamp | |

**Credentials response object** (returned only by create and rotate-secret). The `clientSecret` is shown **once only**:

| Field | Type | Description |
|---|---|---|
| `id`, `clientName`, `clientId`, `whitelistedIps`, `status` | | As above |
| `clientSecret` | string | Raw secret for `X-Client-Secret` (format `fsk_` + 43 characters). Only a hash is stored |
| `warning` | string | Reminder to store the secret |

## 7. Create access key

New keys start with a balance of `0.00`. Credit the balance (**Credit balance**) before the partner can send messages.

**URL:** `POST /api/v1/admin/access-keys`

**Headers**

| Header | Value |
|---|---|
| `Authorization` | `Bearer <accessToken>` |
| `Content-Type` | `application/json` |

**Request**

```json
{
  "clientName": "Acme Payments Ltd",
  "whitelistedIps": ["196.46.20.10", "10.0.0.0/24"]
}
```

| Field | Type | Rules |
|---|---|---|
| `clientName` | string | required, max 150 |
| `whitelistedIps` | string[] | optional, max 50 entries. IPv4/IPv6 literals, optional CIDR suffix. Omit or `[]` for no IP restriction |

**Response – `201 Created`**

```json
{
  "id": 5,
  "clientName": "Acme Payments Ltd",
  "clientId": "fse_3f9a1c0b7d2e4a6f8b1c2d3e",
  "clientSecret": "fsk_Qm9sZC1yYW5kb20tc2VjcmV0LXZhbHVlLWhlcmUxMjM",
  "whitelistedIps": ["196.46.20.10", "10.0.0.0/24"],
  "status": "ACTIVE",
  "warning": "Store the clientSecret securely now; it will not be shown again."
}
```

**Errors**

| Status | `message` | Cause |
|---|---|---|
| 400 | `Validation failed` | Missing `clientName`, too many IPs |
| 400 | `Invalid IP address or CIDR: <value>` | Bad whitelist entry (host names are not accepted) |

**cURL**

```bash
curl -X POST http://localhost:8182/api/v1/admin/access-keys \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"clientName":"Acme Payments Ltd","whitelistedIps":["196.46.20.10","10.0.0.0/24"]}'
```

---

## 8. List access keys

**URL:** `GET /api/v1/admin/access-keys`

**Headers**

| Header | Value |
|---|---|
| `Authorization` | `Bearer <accessToken>` |

**Request:** no body.

**Response – `200 OK`**

```json
[
  {
    "id": 5,
    "clientName": "Acme Payments Ltd",
    "clientId": "fse_3f9a1c0b7d2e4a6f8b1c2d3e",
    "whitelistedIps": ["196.46.20.10", "10.0.0.0/24"],
    "status": "ACTIVE",
    "balance": 4980.00,
    "lastUsedAt": "2026-11-05T11:02:00Z",
    "createdBy": "admin",
    "createdAt": "2026-11-05T10:30:00Z",
    "updatedAt": "2026-11-05T10:30:00Z"
  }
]
```

**cURL**

```bash
curl http://localhost:8182/api/v1/admin/access-keys \
  -H "Authorization: Bearer <accessToken>"
```

---

## 9. Get access key

**URL:** `GET /api/v1/admin/access-keys/{id}`

**Headers**

| Header | Value |
|---|---|
| `Authorization` | `Bearer <accessToken>` |

**Path parameters:** `id` is the access key id.

**Request:** no body.

**Response – `200 OK`:** a single access key response object (same shape as one list item).

**Errors**

| Status | `message` | Cause |
|---|---|---|
| 404 | `Access key not found` | Unknown `id` |

**cURL**

```bash
curl http://localhost:8182/api/v1/admin/access-keys/5 \
  -H "Authorization: Bearer <accessToken>"
```

---

## 10. Update access key

Partial update: fields that are `null` or omitted are left unchanged. The balance cannot be changed here; use **Credit balance**.

**URL:** `PUT /api/v1/admin/access-keys/{id}`

**Headers**

| Header | Value |
|---|---|
| `Authorization` | `Bearer <accessToken>` |
| `Content-Type` | `application/json` |

**Path parameters:** `id` is the access key id.

**Request**

```json
{
  "clientName": "Acme Payments Limited",
  "whitelistedIps": ["196.46.20.11"]
}
```

| Field | Type | Rules / meaning |
|---|---|---|
| `clientName` | string | optional, max 150. Blank is ignored |
| `whitelistedIps` | string[] | optional, max 50 entries. Replaces the whole list. `[]` removes the IP restriction. Omit to keep the current list |

**Response – `200 OK`:** updated access key response object.

**Errors**

| Status | `message` | Cause |
|---|---|---|
| 400 | `Validation failed` | Field too long, too many IPs |
| 400 | `Invalid IP address or CIDR: <value>` | Bad whitelist entry |
| 400 | `Access key is revoked and can no longer be modified` | Key is `REVOKED` |
| 404 | `Access key not found` | Unknown `id` |

**cURL**

```bash
curl -X PUT http://localhost:8182/api/v1/admin/access-keys/5 \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"whitelistedIps":[]}'
```

---

## 11. Update access key status

- `SUSPENDED` blocks the key temporarily, and `ACTIVE` re-enables it.
- `REVOKED` is permanent: a revoked key can't be modified, credited or re-activated.

**URL:** `PATCH /api/v1/admin/access-keys/{id}/status`

**Headers**

| Header | Value |
|---|---|
| `Authorization` | `Bearer <accessToken>` |
| `Content-Type` | `application/json` |

**Path parameters:** `id` is the access key id.

**Request**

```json
{
  "status": "SUSPENDED"
}
```

| Field | Type | Rules |
|---|---|---|
| `status` | string | required. `ACTIVE`, `SUSPENDED` or `REVOKED` |

**Response – `200 OK`:** updated access key response object.

**Errors**

| Status | `message` | Cause |
|---|---|---|
| 400 | `Validation failed` | Missing `status` |
| 400 | `Access key is revoked and can no longer be modified` | Key is already `REVOKED` |
| 404 | `Access key not found` | Unknown `id` |

**cURL**

```bash
curl -X PATCH http://localhost:8182/api/v1/admin/access-keys/5/status \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"status":"SUSPENDED"}'
```

---

## 12. Rotate access key secret

Issues a new `clientSecret`. The old secret stops working immediately. The `clientId` and balance stay the same.

**URL:** `POST /api/v1/admin/access-keys/{id}/rotate-secret`

**Headers**

| Header | Value |
|---|---|
| `Authorization` | `Bearer <accessToken>` |

**Path parameters:** `id` is the access key id.

**Request:** no body.

**Response – `200 OK`:** credentials response object (same shape as **Create access key**), containing the new `clientSecret`.

**Errors**

| Status | `message` | Cause |
|---|---|---|
| 400 | `Access key is revoked and can no longer be modified` | Key is `REVOKED` |
| 404 | `Access key not found` | Unknown `id` |

**cURL**

```bash
curl -X POST http://localhost:8182/api/v1/admin/access-keys/5/rotate-secret \
  -H "Authorization: Bearer <accessToken>"
```

---

## 13. Delete access key

Permanently deletes an **unused** key and its IP whitelist. A key that has sent messages or has any balance history is kept for audit: set its status to `REVOKED` instead.

**URL:** `DELETE /api/v1/admin/access-keys/{id}`

**Headers**

| Header | Value |
|---|---|
| `Authorization` | `Bearer <accessToken>` |

**Path parameters:** `id` is the access key id.

**Request:** no body.

**Response – `204 No Content`:** empty body.

**Errors**

| Status | `message` | Cause |
|---|---|---|
| 400 | `Access key has messages or balance history and cannot be deleted; set its status to REVOKED instead` | Key has been used or credited |
| 404 | `Access key not found` | Unknown `id` |

**cURL**

```bash
curl -X DELETE http://localhost:8182/api/v1/admin/access-keys/5 \
  -H "Authorization: Bearer <accessToken>"
```

---

# Admin – Access key balance

Every balance change is recorded as a transaction that is never edited or deleted. The balance can never go below `0.00`.

**Balance transaction object** (used by the endpoints below and by the partner transaction list):

| Field | Type | Description |
|---|---|---|
| `id` | number | Transaction id |
| `type` | string | `CREDIT` (admin top-up), `DEBIT` (message charge) or `REFUND` (charge returned after a failed send) |
| `amount` | number | Always positive; `type` gives the direction |
| `balanceAfter` | number | Balance immediately after this transaction |
| `currency` | string | `NGN` |
| `description` | string | Top-up reason, or a description of the message |
| `messageId` | number \| null | Message charged or refunded; `null` for credits |
| `createdBy` | string | Admin username for credits; partner `clientId` for debits and refunds |
| `createdAt` | timestamp | |

## 14. Credit balance

Tops up a key's prepaid balance.

**URL:** `POST /api/v1/admin/access-keys/{id}/balance/credit`

**Headers**

| Header | Value |
|---|---|
| `Authorization` | `Bearer <accessToken>` |
| `Content-Type` | `application/json` |

**Path parameters:** `id` is the access key id.

**Request**

```json
{
  "amount": 5000.00,
  "reason": "Top-up, payment ref TRX-20261105-001"
}
```

| Field | Type | Rules |
|---|---|---|
| `amount` | number | required, at least `0.01`, at most 2 decimal places |
| `reason` | string | required, max 255 |

**Response – `201 Created`**

```json
{
  "id": 41,
  "type": "CREDIT",
  "amount": 5000.00,
  "balanceAfter": 5000.00,
  "currency": "NGN",
  "description": "Top-up, payment ref TRX-20261105-001",
  "messageId": null,
  "createdBy": "admin",
  "createdAt": "2026-11-05T10:35:00Z"
}
```

**Errors**

| Status | `message` | Cause |
|---|---|---|
| 400 | `Validation failed` | Missing or non-positive amount, more than 2 decimals, missing reason |
| 400 | `Access key is revoked and can no longer be credited` | Key is `REVOKED` |
| 404 | `Access key not found` | Unknown `id` |

**cURL**

```bash
curl -X POST http://localhost:8182/api/v1/admin/access-keys/5/balance/credit \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"amount":5000.00,"reason":"Top-up, payment ref TRX-20261105-001"}'
```

---

## 15. Get balance

**URL:** `GET /api/v1/admin/access-keys/{id}/balance`

**Headers**

| Header | Value |
|---|---|
| `Authorization` | `Bearer <accessToken>` |

**Path parameters:** `id` is the access key id.

**Request:** no body.

**Response – `200 OK`**

```json
{
  "accessKeyId": 5,
  "clientId": "fse_3f9a1c0b7d2e4a6f8b1c2d3e",
  "balance": 4980.00,
  "currency": "NGN"
}
```

**Errors**

| Status | `message` | Cause |
|---|---|---|
| 404 | `Access key not found` | Unknown `id` |

**cURL**

```bash
curl http://localhost:8182/api/v1/admin/access-keys/5/balance \
  -H "Authorization: Bearer <accessToken>"
```

---

## 16. List balance transactions

**URL:** `GET /api/v1/admin/access-keys/{id}/balance/transactions?page=0&size=20`

**Headers**

| Header | Value |
|---|---|
| `Authorization` | `Bearer <accessToken>` |

**Path parameters:** `id` is the access key id. **Query parameters:** see [Pagination](#pagination).

**Request:** no body.

**Response – `200 OK`**

```json
{
  "content": [
    {
      "id": 43,
      "type": "REFUND",
      "amount": 4.00,
      "balanceAfter": 4984.00,
      "currency": "NGN",
      "description": "Refund: SMS to 1 recipient(s), 1 unit(s), message #102",
      "messageId": 102,
      "createdBy": "fse_3f9a1c0b7d2e4a6f8b1c2d3e",
      "createdAt": "2026-11-05T11:05:01Z"
    },
    {
      "id": 42,
      "type": "DEBIT",
      "amount": 4.00,
      "balanceAfter": 4980.00,
      "currency": "NGN",
      "description": "SMS to 1 recipient(s), 1 unit(s), message #102",
      "messageId": 102,
      "createdBy": "fse_3f9a1c0b7d2e4a6f8b1c2d3e",
      "createdAt": "2026-11-05T11:05:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 3,
  "totalPages": 1
}
```

**Errors**

| Status | `message` | Cause |
|---|---|---|
| 404 | `Access key not found` | Unknown `id` |

**cURL**

```bash
curl "http://localhost:8182/api/v1/admin/access-keys/5/balance/transactions?page=0&size=20" \
  -H "Authorization: Bearer <accessToken>"
```

---

# Admin – Message prices

Prices are stored in the database, one per channel (`SMS`, `EMAIL`). The first migration seeds SMS at `4.00` and EMAIL at `1.00`.

- A price change applies to messages sent after it. Messages already sent keep the cost they were charged.
- If a channel has **no price**, partners get `503` when sending on that channel, and nothing is charged. Create a price to re-enable it.
- A price of `0.00` makes the channel free.

**Message price object** (used by the endpoints below):

| Field | Type | Description |
|---|---|---|
| `id` | number | Price id |
| `channel` | string | `SMS` or `EMAIL` |
| `unitPrice` | number | SMS: per page per recipient. EMAIL: per email per recipient |
| `currency` | string | `NGN` |
| `description` | string \| null | Free-text note |
| `createdBy` | string | Admin who created the price (`system` for seeded prices) |
| `updatedBy` | string | Admin who last changed it |
| `createdAt` | timestamp | |
| `updatedAt` | timestamp | |

## 17. List message prices

**URL:** `GET /api/v1/admin/message-prices`

**Headers**

| Header | Value |
|---|---|
| `Authorization` | `Bearer <accessToken>` |

**Request:** no body.

**Response – `200 OK`**

```json
[
  {
    "id": 2,
    "channel": "EMAIL",
    "unitPrice": 1.00,
    "currency": "NGN",
    "description": "Per email per recipient",
    "createdBy": "system",
    "updatedBy": "system",
    "createdAt": "2026-11-06T09:00:00Z",
    "updatedAt": "2026-11-06T09:00:00Z"
  },
  {
    "id": 1,
    "channel": "SMS",
    "unitPrice": 4.00,
    "currency": "NGN",
    "description": "Per SMS page per recipient",
    "createdBy": "system",
    "updatedBy": "system",
    "createdAt": "2026-11-06T09:00:00Z",
    "updatedAt": "2026-11-06T09:00:00Z"
  }
]
```

**cURL**

```bash
curl http://localhost:8182/api/v1/admin/message-prices \
  -H "Authorization: Bearer <accessToken>"
```

---

## 18. Get message price

**URL:** `GET /api/v1/admin/message-prices/{id}`

**Headers**

| Header | Value |
|---|---|
| `Authorization` | `Bearer <accessToken>` |

**Path parameters:** `id` is the price id.

**Request:** no body.

**Response – `200 OK`:** a single message price object.

**Errors**

| Status | `message` | Cause |
|---|---|---|
| 404 | `Message price not found` | Unknown `id` |

**cURL**

```bash
curl http://localhost:8182/api/v1/admin/message-prices/1 \
  -H "Authorization: Bearer <accessToken>"
```

---

## 19. Create message price

Adds a price for a channel that does not have one (for example after deleting it).

**URL:** `POST /api/v1/admin/message-prices`

**Headers**

| Header | Value |
|---|---|
| `Authorization` | `Bearer <accessToken>` |
| `Content-Type` | `application/json` |

**Request**

```json
{
  "channel": "SMS",
  "unitPrice": 4.50,
  "description": "Per SMS page per recipient"
}
```

| Field | Type | Rules |
|---|---|---|
| `channel` | string | required. `SMS` or `EMAIL` |
| `unitPrice` | number | required, `0.00` or more, at most 2 decimal places |
| `description` | string | optional, max 255 |

**Response – `201 Created`:** the new message price object.

**Errors**

| Status | `message` | Cause |
|---|---|---|
| 400 | `Validation failed` | Missing or unknown channel, negative price, more than 2 decimals |
| 409 | `A price for SMS already exists; update it instead` | Channel already has a price |

**cURL**

```bash
curl -X POST http://localhost:8182/api/v1/admin/message-prices \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"channel":"SMS","unitPrice":4.50,"description":"Per SMS page per recipient"}'
```

---

## 20. Update message price

Changes a channel's price. The channel itself cannot be changed.

**URL:** `PUT /api/v1/admin/message-prices/{id}`

**Headers**

| Header | Value |
|---|---|
| `Authorization` | `Bearer <accessToken>` |
| `Content-Type` | `application/json` |

**Path parameters:** `id` is the price id.

**Request**

```json
{
  "unitPrice": 5.00,
  "description": "Per SMS page per recipient (from 1 Dec)"
}
```

| Field | Type | Rules |
|---|---|---|
| `unitPrice` | number | required, `0.00` or more, at most 2 decimal places |
| `description` | string | optional, max 255. Omit or send blank to clear it |

**Response – `200 OK`:** the updated message price object.

**Errors**

| Status | `message` | Cause |
|---|---|---|
| 400 | `Validation failed` | Missing or negative price, more than 2 decimals |
| 404 | `Message price not found` | Unknown `id` |

**cURL**

```bash
curl -X PUT http://localhost:8182/api/v1/admin/message-prices/1 \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"unitPrice":5.00,"description":"Per SMS page per recipient"}'
```

---

## 21. Delete message price

Removes a channel's price. **Partners can no longer send on that channel** (they get `503`) until a new price is created.

**URL:** `DELETE /api/v1/admin/message-prices/{id}`

**Headers**

| Header | Value |
|---|---|
| `Authorization` | `Bearer <accessToken>` |

**Path parameters:** `id` is the price id.

**Request:** no body.

**Response – `204 No Content`:** empty body.

**Errors**

| Status | `message` | Cause |
|---|---|---|
| 404 | `Message price not found` | Unknown `id` |

**cURL**

```bash
curl -X DELETE http://localhost:8182/api/v1/admin/message-prices/1 \
  -H "Authorization: Bearer <accessToken>"
```

---

# Partner – Account

All partner endpoints use these headers (and return the [common partner errors](#error-format)):

| Header | Value |
|---|---|
| `X-Client-Id` | `fse_3f9a1c0b7d2e4a6f8b1c2d3e` |
| `X-Client-Secret` | `fsk_Qm9sZC1yYW5kb20tc2VjcmV0LXZhbHVlLWhlcmUxMjM` |

## 22. Verify access key (partner "me")

Lets a partner confirm their credentials work.

**URL:** `GET /api/v1/partner/me`

**Request:** no body.

**Response – `200 OK`**

```json
{
  "accessKeyId": 5,
  "clientId": "fse_3f9a1c0b7d2e4a6f8b1c2d3e",
  "clientName": "Acme Payments Ltd"
}
```

**cURL**

```bash
curl http://localhost:8182/api/v1/partner/me \
  -H "X-Client-Id: fse_3f9a1c0b7d2e4a6f8b1c2d3e" \
  -H "X-Client-Secret: fsk_Qm9sZC1yYW5kb20tc2VjcmV0LXZhbHVlLWhlcmUxMjM"
```

---

## 23. Get own balance

**URL:** `GET /api/v1/partner/balance`

**Request:** no body.

**Response – `200 OK`**

```json
{
  "accessKeyId": 5,
  "clientId": "fse_3f9a1c0b7d2e4a6f8b1c2d3e",
  "balance": 4980.00,
  "currency": "NGN"
}
```

**cURL**

```bash
curl http://localhost:8182/api/v1/partner/balance \
  -H "X-Client-Id: fse_3f9a1c0b7d2e4a6f8b1c2d3e" \
  -H "X-Client-Secret: fsk_Qm9sZC1yYW5kb20tc2VjcmV0LXZhbHVlLWhlcmUxMjM"
```

---

## 24. List own balance transactions

**URL:** `GET /api/v1/partner/balance/transactions?page=0&size=20`

**Query parameters:** see [Pagination](#pagination).

**Request:** no body.

**Response – `200 OK`:** a page of balance transaction objects (same shape as **List balance transactions**).

**cURL**

```bash
curl "http://localhost:8182/api/v1/partner/balance/transactions?page=0&size=20" \
  -H "X-Client-Id: fse_3f9a1c0b7d2e4a6f8b1c2d3e" \
  -H "X-Client-Secret: fsk_Qm9sZC1yYW5kb20tc2VjcmV0LXZhbHVlLWhlcmUxMjM"
```

---

# Partner – Messages

Same headers as **Partner – Account**.

## How messages are charged

**Cost = unit price × units × recipients**, using the current price for the channel (see **Admin – Message prices**).

- **SMS units** are pages:
  - Plain GSM text: 160 characters for one page; longer messages are split into pages of 153.
  - Text with any other character (e.g. emoji, Arabic, curly quotes): 70 characters for one page, then pages of 67.
  - The GSM characters `^ { } \ [ ] ~ | €` count as 2.
- **Email units** are always 1 per recipient.
- Duplicate recipients in a request are removed before charging. Local Nigerian numbers (`08012345678`) are converted to `+2348012345678`.

**What happens on send**

1. The cost is checked against the balance. If the balance is too low, the request fails with `402` and nothing is charged or sent.
2. The balance is charged (a `DEBIT` transaction) and the message is sent.
3. If sending fails, the message is marked `FAILED` and the charge is returned (a `REFUND` transaction). The response is still `201`. Check `message.status`.

**Retries:** send a unique `reference` with each message. Reusing a reference returns `409` and charges nothing, so a retried request can never be charged twice.

**Message object:**

| Field | Type | Description |
|---|---|---|
| `messageId` | number | Message id |
| `channel` | string | `SMS` or `EMAIL` |
| `status` | string | `PENDING`, `SENT` or `FAILED` |
| `recipients` | string[] | Normalized recipients |
| `recipientCount` | number | |
| `units` | number | SMS pages per recipient; `1` for email |
| `cost` | number | Amount charged |
| `currency` | string | `NGN` |
| `reference` | string \| null | Partner reference |
| `failureReason` | string \| null | Set when `FAILED` |
| `createdAt` | timestamp | |

**Send response object:**

| Field | Type | Description |
|---|---|---|
| `message` | object | Message object |
| `balance` | number | Balance after the charge (and any refund) |
| `currency` | string | `NGN` |

Errors common to both send endpoints:

| Status | `message` | Cause |
|---|---|---|
| 400 | `Validation failed` | See each endpoint's field rules |
| 402 | `Insufficient balance: required 8.00 NGN, available 4.00 NGN` | Balance lower than the cost |
| 409 | `A message with reference 'INV-1001' already exists` | `reference` already used by this key |
| 503 | `SMS sending is not available: no price is configured` | Admin has not set a price for the channel |

## 25. Send SMS

**URL:** `POST /api/v1/messages/sms`

**Headers**

| Header | Value |
|---|---|
| `X-Client-Id` | your client id |
| `X-Client-Secret` | your client secret |
| `Content-Type` | `application/json` |

**Request**

```json
{
  "recipients": ["08012345678", "+2348098765432"],
  "message": "Your OTP is 482913. It expires in 5 minutes.",
  "senderId": "FSEBank",
  "reference": "OTP-20261105-0001"
}
```

| Field | Type | Rules |
|---|---|---|
| `recipients` | string[] | required, 1–100. International (`+2348012345678`) or local Nigerian (`08012345678`) format; spaces, `()` and `-` are allowed |
| `message` | string | required, max 1530 characters |
| `senderId` | string | optional, max 11 letters, digits or spaces |
| `reference` | string | optional, max 100. Unique per access key |

**Response – `201 Created`**

```json
{
  "message": {
    "messageId": 101,
    "channel": "SMS",
    "status": "SENT",
    "recipients": ["+2348012345678", "+2348098765432"],
    "recipientCount": 2,
    "units": 1,
    "cost": 8.00,
    "currency": "NGN",
    "reference": "OTP-20261105-0001",
    "failureReason": null,
    "createdAt": "2026-11-05T11:00:00Z"
  },
  "balance": 4992.00,
  "currency": "NGN"
}
```

**Errors:** the common send errors above, plus:

| Status | `message` | Cause |
|---|---|---|
| 400 | `Invalid phone number: <value>` | Number is not 7–15 digits after removing formatting |

**cURL**

```bash
curl -X POST http://localhost:8182/api/v1/messages/sms \
  -H "X-Client-Id: fse_3f9a1c0b7d2e4a6f8b1c2d3e" \
  -H "X-Client-Secret: fsk_Qm9sZC1yYW5kb20tc2VjcmV0LXZhbHVlLWhlcmUxMjM" \
  -H "Content-Type: application/json" \
  -d '{"recipients":["08012345678"],"message":"Your OTP is 482913.","reference":"OTP-20261105-0001"}'
```

---

## 26. Send email

**URL:** `POST /api/v1/messages/email`

**Headers**

| Header | Value |
|---|---|
| `X-Client-Id` | your client id |
| `X-Client-Secret` | your client secret |
| `Content-Type` | `application/json` |

**Request**

```json
{
  "recipients": ["customer@example.com"],
  "subject": "Your statement is ready",
  "body": "Dear customer, your October statement is now available.",
  "reference": "STMT-2026-10-0042"
}
```

| Field | Type | Rules |
|---|---|---|
| `recipients` | string[] | required, 1–100 valid email addresses, max 254 characters each. Compared case-insensitively for duplicates |
| `subject` | string | required, max 255 |
| `body` | string | required, max 100,000 characters |
| `reference` | string | optional, max 100. Unique per access key |

**Response – `201 Created`**

```json
{
  "message": {
    "messageId": 103,
    "channel": "EMAIL",
    "status": "SENT",
    "recipients": ["customer@example.com"],
    "recipientCount": 1,
    "units": 1,
    "cost": 1.00,
    "currency": "NGN",
    "reference": "STMT-2026-10-0042",
    "failureReason": null,
    "createdAt": "2026-11-05T11:10:00Z"
  },
  "balance": 4991.00,
  "currency": "NGN"
}
```

**Errors:** the common send errors above.

**cURL**

```bash
curl -X POST http://localhost:8182/api/v1/messages/email \
  -H "X-Client-Id: fse_3f9a1c0b7d2e4a6f8b1c2d3e" \
  -H "X-Client-Secret: fsk_Qm9sZC1yYW5kb20tc2VjcmV0LXZhbHVlLWhlcmUxMjM" \
  -H "Content-Type: application/json" \
  -d '{"recipients":["customer@example.com"],"subject":"Your statement is ready","body":"Dear customer, your October statement is now available."}'
```

---

## 27. Get message

Returns one of the caller's own messages.

**URL:** `GET /api/v1/messages/{id}`

**Path parameters:** `id` is the message id.

**Request:** no body.

**Response – `200 OK`:** a message object.

**Errors**

| Status | `message` | Cause |
|---|---|---|
| 404 | `Message not found` | Unknown `id`, or the message belongs to another access key |

**cURL**

```bash
curl http://localhost:8182/api/v1/messages/101 \
  -H "X-Client-Id: fse_3f9a1c0b7d2e4a6f8b1c2d3e" \
  -H "X-Client-Secret: fsk_Qm9sZC1yYW5kb20tc2VjcmV0LXZhbHVlLWhlcmUxMjM"
```

---

## 28. List messages

Lists the caller's own messages, newest first.

**URL:** `GET /api/v1/messages?page=0&size=20`

**Query parameters:** see [Pagination](#pagination).

**Request:** no body.

**Response – `200 OK`:** a page of message objects.

```json
{
  "content": [
    {
      "messageId": 103,
      "channel": "EMAIL",
      "status": "SENT",
      "recipients": ["customer@example.com"],
      "recipientCount": 1,
      "units": 1,
      "cost": 1.00,
      "currency": "NGN",
      "reference": "STMT-2026-10-0042",
      "failureReason": null,
      "createdAt": "2026-11-05T11:10:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

**cURL**

```bash
curl "http://localhost:8182/api/v1/messages?page=0&size=20" \
  -H "X-Client-Id: fse_3f9a1c0b7d2e4a6f8b1c2d3e" \
  -H "X-Client-Secret: fsk_Qm9sZC1yYW5kb20tc2VjcmV0LXZhbHVlLWhlcmUxMjM"
```
