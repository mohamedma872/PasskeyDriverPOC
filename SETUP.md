# Passkey Driver POC — Setup Guide

## The Problem
Android Passkeys require a **domain** (Relying Party ID) verified via Digital Asset Links.
You don't need a real website — just a domain that hosts a single JSON file.

## Solution: Firebase Hosting (Free)

Firebase Hosting gives you a free `*.web.app` subdomain. We only host one file: `assetlinks.json`.

### Step 1: Get your debug keystore SHA-256

Run this command:

```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android 2>/dev/null | grep SHA256
```

Copy the SHA-256 fingerprint (looks like: `AA:BB:CC:DD:...`).

### Step 2: Create Firebase project

1. Go to https://console.firebase.google.com
2. Create a new project (e.g., "passkeydriver-poc")
3. Note the project ID — your domain will be `<project-id>.web.app`

### Step 3: Update assetlinks.json

Open `assetlinks-hosting/public/.well-known/assetlinks.json` and replace
`TODO:REPLACE_WITH_YOUR_DEBUG_KEYSTORE_SHA256` with your SHA-256 from Step 1.

### Step 4: Deploy to Firebase Hosting

```bash
cd assetlinks-hosting
npm install -g firebase-tools   # if not installed
firebase login
firebase init hosting           # select your project, keep defaults
firebase deploy --only hosting
```

### Step 5: Update the app's RP ID

If your Firebase project ID is different from `passkeydriver-poc`, update these two places:

1. `app/src/main/java/.../auth/WebAuthnServer.kt` → change `RP_ID`
2. `app/src/main/res/values/strings.xml` → change the domain in `asset_statements`

Both must match your `<project-id>.web.app` domain.

### Step 6: Verify

Visit `https://<project-id>.web.app/.well-known/assetlinks.json` in a browser.
You should see the JSON with your SHA-256 fingerprint.

### Step 7: Build and run

Open the project in Android Studio, sync Gradle, and run on an Android 14+ device.

## Test Accounts

| Username | Password | Display Name   |
|----------|----------|----------------|
| ahmed    | pass123  | Ahmed Hassan   |
| fatima   | pass123  | Fatima Ali     |
| omar     | pass123  | Omar Khalid    |
| sara     | pass123  | Sara Mohamed   |

## Alternative: GitHub Pages (also free)

If you prefer GitHub Pages over Firebase:

1. Create a GitHub repo
2. Add `.well-known/assetlinks.json` to the repo root
3. Enable GitHub Pages in repo settings
4. Your domain will be `<username>.github.io/<repo-name>`
5. Update RP_ID and asset_statements accordingly

## For Production

In production, you would:
- Host `assetlinks.json` on your company's actual domain
- Use a release signing key (not debug keystore)
- Have a real backend server handling WebAuthn challenge/response
