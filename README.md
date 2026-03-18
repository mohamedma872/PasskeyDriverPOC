# PasskeyDriverPOC

A proof-of-concept demonstrating **FIDO2/WebAuthn passkey authentication** for a driver login app.
Drivers register their fingerprint once, then log in with a single touch — no password needed.

---

## What This Demonstrates

- Android biometric (fingerprint) login using the **Credential Manager API**
- Full **WebAuthn registration and authentication** flow with real cryptographic verification
- **Serverless backend** on Vercel with Upstash Redis + Neon PostgreSQL
- **Persistent credentials** that survive app restarts via SharedPreferences
- Proper **Digital Asset Links** binding the Android app to the backend domain

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────┐
│  ANDROID APP (Kotlin + Jetpack Compose)             │
│                                                     │
│  DriverListScreen  →  tap driver  →  fingerprint   │
│  RegisterScreen    →  login  →  enable passkey     │
│  DashboardScreen   →  authenticated home           │
│                                                     │
│  PasskeyManager                                     │
│    └── Android Credential Manager API              │
│         └── Google Password Manager (private key)  │
│                                                     │
│  WebAuthnServer (local mock, POC only)             │
│    └── CredentialStore (SharedPreferences)         │
└────────────────────┬────────────────────────────────┘
                     │ HTTPS (production path)
                     ▼
┌─────────────────────────────────────────────────────┐
│  VERCEL SERVERLESS BACKEND (Node.js)                │
│                                                     │
│  POST /api/passkey/register-begin                  │
│  POST /api/passkey/register-finish                 │
│  POST /api/passkey/auth-begin                      │
│  POST /api/passkey/auth-finish                     │
│  POST /api/setup  (one-time schema init)           │
│                                                     │
│  @simplewebauthn/server  ← real crypto verification│
└──────────┬──────────────────────────┬──────────────┘
           │                          │
           ▼                          ▼
┌──────────────────┐      ┌───────────────────────────┐
│  Upstash Redis   │      │  Neon PostgreSQL           │
│                  │      │                            │
│  challenges      │      │  drivers table             │
│  (60s TTL)       │      │  credentials table         │
│  one-time use    │      │  public keys (BYTEA)       │
│  replay-safe     │      │  sign_count (replay guard) │
└──────────────────┘      └───────────────────────────┘

┌─────────────────────────────────────────────────────┐
│  Firebase Hosting                                   │
│                                                     │
│  /.well-known/assetlinks.json                      │
│  Proves Android app owns the passkey domain        │
└─────────────────────────────────────────────────────┘
```

---

## User Flows

### Registration (first time)

```
Open app
  └── RegisterScreen: enter username + password (mock login)
        └── "Enable fingerprint login on this device?"
              └── [Yes] → touch fingerprint sensor
                    └── Passkey created in Google Password Manager
                          └── Public key stored on backend (PostgreSQL)
                                └── Driver appears in list next time
```

### Daily Login

```
Open app
  └── DriverListScreen: tap your name
        └── touch fingerprint sensor
              └── Signature verified by backend
                    └── DashboardScreen ✓
```

### Fallback

```
Any screen → "Use password instead" → RegisterScreen (password login)
```

---

## Project Structure

```
PasskeyDriverPOC/
│
├── app/                                    Android application
│   └── src/main/java/com/example/passkeydriver/
│       │
│       ├── MainActivity.kt                 App entry point, DI wiring
│       │
│       ├── auth/
│       │   ├── PasskeyManager.kt           Credential Manager API wrapper
│       │   │                               registerPasskey() / authenticateWithPasskey()
│       │   ├── WebAuthnServer.kt           Local mock server (POC)
│       │   │                               Generates + verifies WebAuthn JSON
│       │   │                               Uses CredentialStore for persistence
│       │   └── ProductionWebAuthnServer.kt Reference: how to call real backend
│       │
│       ├── data/
│       │   ├── Driver.kt                   Data model (driverId, name, username)
│       │   ├── DriverRepository.kt         In-memory list + persistence bridge
│       │   └── CredentialStore.kt          SharedPreferences persistence layer
│       │                                   Stores: credentialId→userId mappings
│       │                                   Stores: registered driver list
│       │
│       ├── viewmodel/
│       │   ├── RegisterViewModel.kt        Handles login + passkey registration
│       │   └── DriverListViewModel.kt      Handles passkey authentication per driver
│       │
│       ├── ui/screens/
│       │   ├── DriverListScreen.kt         Home: list of passkey-registered drivers
│       │   ├── RegisterScreen.kt           Login form + passkey enrolment prompt
│       │   └── DashboardScreen.kt          Post-auth screen with driver info
│       │
│       └── navigation/
│           └── AppNavigation.kt            NavHost: driver_list → register → dashboard
│
├── backend/                                Vercel serverless API
│   ├── api/
│   │   ├── _redis.js                       Upstash Redis client
│   │   │                                   storeChallenge() / getAndDeleteChallenge()
│   │   ├── _postgres.js                    Neon PostgreSQL client
│   │   │                                   upsertDriver / storeCredential / getCredential
│   │   ├── setup.js                        POST /api/setup — creates DB tables
│   │   └── passkey/
│   │       ├── register-begin.js           POST /api/passkey/register-begin
│   │       ├── register-finish.js          POST /api/passkey/register-finish
│   │       ├── auth-begin.js               POST /api/passkey/auth-begin
│   │       └── auth-finish.js              POST /api/passkey/auth-finish
│   ├── public/
│   │   └── .well-known/assetlinks.json     Android domain binding (Vercel copy)
│   └── package.json
│
├── assetlinks-hosting/                     Firebase Hosting (serves assetlinks.json)
│   └── public/
│       └── .well-known/assetlinks.json
│
├── SETUP.md                                Step-by-step deployment guide
└── Passkey_Architecture_Guide.pdf          21-page deep-dive reference
```

---

## Backend API Reference

All endpoints return JSON. CORS is open (`*`) for the POC.

### `POST /api/passkey/register-begin`

Starts passkey registration. Returns WebAuthn options JSON to pass to Android Credential Manager.

**Request:**
```json
{
  "driverId": "driver-001",
  "username": "ahmed",
  "displayName": "Ahmed Hassan"
}
```

**Response:**
```json
{
  "challengeId": "uuid-v4",
  "options": { /* WebAuthn PublicKeyCredentialCreationOptions */ }
}
```

---

### `POST /api/passkey/register-finish`

Completes registration. Verifies the attestation and stores the public key.

**Request:**
```json
{
  "challengeId": "uuid-v4",
  "driverId": "driver-001",
  "response": { /* CreatePublicKeyCredentialResponse JSON from Android */ }
}
```

**Response:**
```json
{ "verified": true, "credentialId": "base64url-credential-id" }
```

---

### `POST /api/passkey/auth-begin`

Starts passkey authentication.

**Request:**
```json
{
  "credentialIds": ["base64url-credential-id"]  // optional, narrows to specific driver
}
```

**Response:**
```json
{
  "challengeId": "uuid-v4",
  "options": { /* WebAuthn PublicKeyCredentialRequestOptions */ }
}
```

---

### `POST /api/passkey/auth-finish`

Completes authentication. Verifies signature and returns the authenticated driver.

**Request:**
```json
{
  "challengeId": "uuid-v4",
  "response": { /* PublicKeyCredential JSON from Android */ }
}
```

**Response:**
```json
{ "verified": true, "driverId": "driver-001" }
```

---

### `POST /api/setup?secret=YOUR_SECRET`

One-time endpoint. Creates the `drivers` and `credentials` tables in PostgreSQL.

---

## Database Schema

```sql
-- Permanent driver records
CREATE TABLE drivers (
  driver_id   TEXT PRIMARY KEY,
  name        TEXT NOT NULL,
  username    TEXT NOT NULL,
  created_at  TIMESTAMP DEFAULT NOW(),
  updated_at  TIMESTAMP DEFAULT NOW()
);

-- Permanent passkey credentials (one driver can have multiple)
CREATE TABLE credentials (
  credential_id  TEXT PRIMARY KEY,           -- base64url, from WebAuthn
  driver_id      TEXT REFERENCES drivers,
  public_key     BYTEA NOT NULL,             -- COSE-encoded public key
  sign_count     INTEGER DEFAULT 0,          -- replay attack protection
  created_at     TIMESTAMP DEFAULT NOW(),
  last_used_at   TIMESTAMP DEFAULT NOW()
);
```

Redis keys (auto-expire after 60 seconds):
```
challenge:{uuid}  →  { challenge: "base64url", driverId: "..." }
```

---

## How WebAuthn Works (the short version)

```
REGISTRATION
  Server  →  challenge (random bytes)
  Device  →  creates key pair (private stays in secure enclave)
  Device  →  signs challenge with private key → sends public key + signature
  Server  →  verifies signature, stores public key

AUTHENTICATION
  Server  →  new challenge
  Device  →  signs challenge with private key (requires fingerprint)
  Server  →  verifies signature using stored public key → identity confirmed
```

The private key **never leaves the device**. Even if the server is compromised, attackers cannot impersonate drivers.

---

## Tech Stack

| Layer | Technology | Why |
|-------|-----------|-----|
| Android UI | Jetpack Compose + Material 3 | Modern declarative UI |
| Passkey API | AndroidX Credentials 1.3.0 | Official Android 14+ passkey API |
| Persistence | SharedPreferences (CredentialStore) | Survives app restart, no DB needed on device |
| Backend | Vercel serverless functions | Free tier, no server management |
| Crypto verification | @simplewebauthn/server v11 | Battle-tested WebAuthn library |
| Challenge storage | Upstash Redis (HTTPS) | TTL built-in, serverless-compatible |
| Credential storage | Neon PostgreSQL (HTTP) | Serverless-compatible, free 0.5GB |
| Domain binding | Firebase Hosting | Serves assetlinks.json on the RP ID domain |

---

## Requirements

- **Android 14+** (API 34) — required for Credential Manager passkey support
- **Google Play Services** — required for Google Password Manager (passkey storage)
- **Fingerprint enrolled** on the device

---

## Quick Start

See [SETUP.md](SETUP.md) for full deployment instructions, including:
1. Firebase Hosting setup for assetlinks.json
2. Vercel deployment
3. Upstash Redis + Neon PostgreSQL integration
4. Environment variable configuration
5. Schema initialization

---

## Key Files to Read First

| File | Description |
|------|-------------|
| [PasskeyManager.kt](app/src/main/java/com/example/passkeydriver/auth/PasskeyManager.kt) | Main passkey logic — registration + authentication |
| [WebAuthnServer.kt](app/src/main/java/com/example/passkeydriver/auth/WebAuthnServer.kt) | Local mock server (POC) — generates WebAuthn JSON |
| [CredentialStore.kt](app/src/main/java/com/example/passkeydriver/data/CredentialStore.kt) | SharedPreferences persistence for credentials |
| [backend/api/passkey/](backend/api/passkey/) | 4 serverless endpoints handling the full WebAuthn flow |
| [backend/api/_postgres.js](backend/api/_postgres.js) | PostgreSQL schema + queries |
| [backend/api/_redis.js](backend/api/_redis.js) | Redis challenge storage with TTL |
| [ProductionWebAuthnServer.kt](app/src/main/java/com/example/passkeydriver/auth/ProductionWebAuthnServer.kt) | Reference: how to call the real backend from Android |
