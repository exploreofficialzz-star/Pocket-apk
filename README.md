# PO Signal Bot — Android App

A mini Android browser that opens Pocket Option, lets you login,
then **automatically extracts your session and connects the signal bot**.
No copy-pasting, no DevTools, no address bar tricks.

---

## How to get the APK (5 minutes, no coding needed)

### Step 1 — Create a free GitHub account
Go to github.com → Sign up (if you don't have one)

### Step 2 — Create a new repository
1. Click the **+** button (top right) → "New repository"
2. Name it: `po-signal-bot-app`
3. Set to **Public**
4. Click **Create repository**

### Step 3 — Upload the files
1. Click **"uploading an existing file"** link on the repo page
2. Drag and drop ALL these files maintaining the folder structure:
   - `.github/workflows/build.yml`
   - `app/src/main/java/com/posignalbot/MainActivity.kt`
   - `app/src/main/AndroidManifest.xml`
   - `app/src/main/res/values/styles.xml`
   - `app/src/main/res/xml/network_security_config.xml`
   - `app/src/main/res/mipmap-*/ic_launcher.png`
   - `app/build.gradle`
   - `build.gradle`
   - `settings.gradle`
   - `gradle/wrapper/gradle-wrapper.properties`
   - `gradlew`
3. Click **Commit changes**

### Step 4 — Wait for the APK to build (~3 minutes)
1. Click the **Actions** tab on your repo
2. You'll see "Build APK" workflow running
3. Wait for the green checkmark ✅

### Step 5 — Download the APK
1. Click on the completed workflow run
2. Scroll down to **Artifacts**
3. Click **PO-Signal-Bot-APK** to download
4. Extract the ZIP → you'll find `app-release.apk`

### Step 6 — Install on your Android phone
1. Transfer the APK to your phone (WhatsApp, email, Google Drive)
2. On your phone: **Settings → Security → Install unknown apps** → allow your file manager
3. Tap the APK file → Install

---

## How the app works

1. Opens **po.trade** in a built-in browser
2. You login normally with your email and password
3. After login, the app **automatically reads your session token**
4. Sends it to the signal bot on Render
5. Status bar shows **"✅ Bot connected!"**
6. Close the app — signals will now fire in Telegram

**You only need to do this once.** The session is saved on the Render server.

---

## Troubleshooting

**"Bot connected!" shows but signals aren't coming?**
→ Wait 2-3 minutes for the bot to collect enough candle history

**App shows "Session not found yet"?**
→ Make sure you're fully logged in and on the trading dashboard, not the login page

**"Network error"?**
→ Check your internet connection and make sure the Render bot is running

---

## Update the bot server URL

If you change your Render URL, edit this line in `MainActivity.kt`:
```
.url("https://pocket-bot-ssh6.onrender.com/ssid")
```
Replace with your actual Render URL.
