# QR-FILE

![Kotlin Multiplatform](https://img.shields.io/badge/Kotlin_Multiplatform-7F52FF?style=flat&logo=kotlin&logoColor=white) ![Android](https://img.shields.io/badge/Android-3DDC84?style=flat&logo=android&logoColor=white) ![iOS](https://img.shields.io/badge/iOS_19+-000000?style=flat&logo=apple&logoColor=white) ![License](https://img.shields.io/badge/License-MIT-green)

> Cross-platform offline file sharing. Tap phones, transfer files — no internet, no middleman, no bloatware.

---

## The Problem

Sharing files between an iPhone and an Android in 2026 is still broken.

AirDrop is the best product in this space — fast, reliable, dead simple. It also only works if both people own Apple hardware. That's by design, not by accident. Google's Quick Share is catching up, but its iOS bridge is a third-party workaround that reverse-engineers AirDrop and breaks whenever Apple pushes an update.

The apps that *did* work across platforms — SHAREit, Xender, Zapya — combined to rack up over a billion installs. SHAREit alone got banned in India and blacklisted by a US executive order. Security researchers found it let any app on your phone read and write its files, execute arbitrary code, and silently install malware. The developers ignored the vulnerability disclosures for months. The app was adware that happened to also move files.

The cloud is not a solution here. If two phones are sitting next to each other, uploading 4GB to Google Drive and downloading it again burns your data plan, takes five minutes, and hands a copy to a third party.

The gap is real: offline, cross-platform, fast, and actually secure. Nobody has shipped it cleanly. QR-FILE is the attempt.

---

## How It Works

**1. Tap or scan to connect.**
Hold your phone against the other person's (NFC) or show them a QR code. Either way, the handshake takes under a second. No device names to scroll through, no pairing codes to confirm, no menus to navigate.

**2. Phones form a direct Wi-Fi network.**
No router involved. No internet required. The two devices connect over Wi-Fi Aware — a peer-to-peer Wi-Fi standard that Android has supported since version 8 and that iOS 19 is required to support under the EU Digital Markets Act.

**3. Files transfer at Wi-Fi speeds.**
Before any data leaves the device, it's encrypted with AES-GCM. The encryption key was generated during the handshake and sent out-of-band through NFC or QR — never over the wireless link being sniffed. A 4GB video moves in seconds. A packet capture gets you nothing.

---

## Why This Is Possible Now

The single biggest technical blocker for cross-platform P2P file sharing was Apple. AirDrop runs on Apple Wireless Direct Link (AWDL), a proprietary protocol that Apple locked to its own hardware and refused to open. Third-party apps couldn't host P2P networks on iOS at all. Every workaround — including FlyingCarpet, which is excellent — relegated iPhones to client-only roles because you can't programmatically create a hotspot from a third-party iOS app.

The EU's Digital Markets Act changed this. It legally required Apple to implement Wi-Fi Aware 4.0 — an open, hardware-standard P2P Wi-Fi protocol — in iOS 19, with Wi-Fi Aware 5.0 to follow. Apple has already published the entitlements (`com.apple.developer.wifi-aware`), the `NetworkListener` and `WASubscriberBrowser` APIs, and the developer documentation. iOS 19 will be the first time a third-party app can make an iPhone act as a true P2P network host — equal to an Android or Windows device.

QR-FILE is built to ship the moment that window opens.

---

## Security

The lesson from SHAREit is that physical proximity doesn't mean safety. A direct Wi-Fi hotspot still transmits data over the air. Anyone within range can run a packet sniffer.

QR-FILE doesn't rely on WPA2 hotspot encryption as its security layer. Every transfer uses:

- **AES-GCM** for authenticated encryption of the file payload
- **SHA-256** key derivation from a single-use session password
- Keys exchanged **out-of-band** only — through NFC or QR, never over the Wi-Fi link

The session key exists only for the duration of the transfer. A man-in-the-middle on the Wi-Fi link captures encrypted ciphertext. Without the key that was passed via NFC tap or QR scan, it's useless.

---

## Features

| Area | What it does |
|------|-------------|
| **Handshake** | NFC tap (primary, <1s) or QR code scan (fallback, universal) |
| **Transfer** | All file types, bulk selection, progress tracking |
| **Transport** | Wi-Fi Aware (iOS 19+ / Android 8+) or Nearby Connections API |
| **Encryption** | AES-GCM, session-scoped keys, SHA-256 derivation |
| **Scanner** | QR/barcode scanning, categorised history (URLs, Wi-Fi, contacts) |
| **QR Generator** | Text, links, Wi-Fi credentials, vCards |
| **History** | Searchable scan and transfer logs, filter by type/date |
| **Security** | App lock, hidden folders, clear cache/history |
| **Themes** | Light, dark, system |

---

## Tech Stack

| Layer | Technology | Why |
|-------|-----------|-----|
| Shared core | Kotlin Multiplatform | Write crypto + network logic once, run on both platforms |
| Android UI | Jetpack Compose | Native Material 3 UI with full hardware access |
| iOS UI | SwiftUI | Native iOS UI for Wi-Fi Aware entitlements |
| Cryptography | Google Tink | AES-GCM with misuse-resistant API; audited by Google |
| Android networking | Nearby Connections API | Abstracts Wi-Fi Direct + BLE; handles permissions |
| iOS networking | `WiFiAware` framework | Apple's DMA-mandated P2P API (iOS 19+) |
| Local storage | Hive | Lightweight, encrypted, no native dependencies |

---

## Project Status

This is in active development. The architecture is laid out; implementation starts with Android.

| Phase | Scope | Status |
|-------|-------|--------|
| 1 | Android P2P via Nearby Connections + QR handshake | In progress |
| 2 | iOS Wi-Fi Aware integration (targets iOS 19 DMA release) | Planned |
| 3 | NFC tap-to-connect on both platforms | Planned |
| 4 | Desktop support (Windows/Linux) | Planned |

---

## Structure

```
shared/               ← KMP shared business logic
  crypto/             ← AES-GCM, key derivation
  handshake/          ← NFC + QR payload structure
  network/            ← expect/actual P2P transport interface
  transfer/           ← file chunking + progress
  storage/            ← scan and transfer history (Hive)

android/              ← Jetpack Compose app
  ui/scanner/
  ui/sharing/
  ui/history/
  ui/settings/

ios/                  ← SwiftUI app
  Views/
```

---

## Setup

```bash
git clone https://github.com/Homme-Noir/QR-FILE.git
cd QR-FILE

# Android
./gradlew :android:assembleDebug

# Shared module tests
./gradlew :shared:test
```

iOS: open `ios/QRFile.xcodeproj` in Xcode, select a simulator or device, and run. Requires Xcode 16+ and an Apple Developer account with the `com.apple.developer.wifi-aware` entitlement for device testing on iOS 19.

---

## License

MIT — see [LICENSE](LICENSE)
