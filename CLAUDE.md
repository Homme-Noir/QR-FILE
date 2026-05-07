# QR-FILE — Project Context for Claude

## What this is

Cross-platform offline file sharing app. Tap phones (NFC) or scan a QR code, devices form a direct Wi-Fi network, files transfer encrypted at Wi-Fi speeds. No internet, no server, no accounts.

## Why it's built this way

The EU Digital Markets Act forced Apple to implement Wi-Fi Aware (open P2P Wi-Fi standard) in iOS 19. Before this, third-party apps couldn't host P2P Wi-Fi networks on iOS — AirDrop used a proprietary Apple-only protocol. iOS 19 is the first version where an iPhone can act as a full network host in a third-party app. QR-FILE is designed to ship the moment that opens.

## Tech stack

**Kotlin Multiplatform (KMP)** — shared business logic, native UIs.

| Layer | Technology |
|-------|-----------|
| Shared core | KMP (commonMain) |
| Android UI | Jetpack Compose |
| iOS UI | SwiftUI |
| Crypto | Google Tink (AES-GCM) |
| Android networking | Google Nearby Connections API |
| iOS networking | Apple `WiFiAware` framework (iOS 19+) |
| Local storage | Hive |

Not Flutter. The research doc considered Flutter but rejected it because low-level NFC, Wi-Fi Aware entitlements, and Nearby Connections integration are cleaner from fully native layers. KMP lets us share crypto, handshake logic, and data models while keeping networking 100% native.

## Module map

```
shared/
  commonMain/
    crypto/       CryptoEngine (expect) — AES-GCM, SHA-256 key derivation
    handshake/    HandshakePayload — NFC/QR data structure (serialized JSON)
    network/      P2PTransport (expect) — platform-bridged transport interface
    transfer/     TransferManager — orchestrates crypto + transport
    storage/      TransferRecord, ScanRecord — Hive-backed history
  androidMain/
    network/      P2PTransport (actual) — Nearby Connections implementation
  iosMain/
    network/      P2PTransport (actual) — WiFiAware implementation (iOS 19+)

android/          Jetpack Compose app (MainActivity, NavHost, 4 screens)
ios/QRFile/       SwiftUI app (TabView, 4 views)
```

## Security model

Keys are NEVER sent over the Wi-Fi link. The session password is generated fresh per transfer and passed out-of-band via NFC tag data or QR code payload (HandshakePayload.sessionPassword). Both devices derive the AES-GCM key locally using SHA-256. A packet sniffer on the Wi-Fi channel gets ciphertext only.

## Handshake flow

1. Sender: `TransferManager.prepareSend()` → generates HandshakePayload (serviceId + sessionPassword)
2. Sender: displays QR code or writes NFC tag containing JSON-encoded HandshakePayload
3. Receiver: scans QR or taps NFC → parses HandshakePayload
4. Both: `P2PTransport.startAdvertising()` / `startDiscovery()` with serviceId
5. Connection established → `sendFiles()` streams AES-GCM encrypted chunks
6. Session key discarded after transfer

## Phase roadmap

| Phase | Scope |
|-------|-------|
| 1 | Android: Nearby Connections + QR code handshake, scanner, history |
| 2 | iOS: Wi-Fi Aware integration (targets iOS 19 DMA release) |
| 3 | NFC tap-to-connect on both platforms |
| 4 | Desktop (Windows/Linux) |

## Permissions that matter

Android `AndroidManifest.xml`:
- `NEARBY_WIFI_DEVICES` (SDK 32+) — required for Wi-Fi Direct
- `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` — Nearby Connections fallback discovery
- `NFC` — Phase 3 tap handshake
- Camera permissions — QR scanning

iOS (Phase 2):
- Entitlement: `com.apple.developer.wifi-aware`
- Info.plist: `WiFiAwareServices` key (array of service name strings)
- `NSLocalNetworkUsageDescription` — required for Network framework usage
- `NFCReaderUsageDescription` — Phase 3 NFC

## Key decisions already made

- KMP over Flutter (native hardware access for Wi-Fi Aware + NFC)
- AES-GCM over TLS (TLS requires a server; we're fully P2P)
- Google Tink over raw javax.crypto (Tink's API makes key misuse harder)
- Nearby Connections API over raw Wi-Fi Direct (handles permission negotiation + BLE fallback)
- Point-to-Point connection strategy (Nearby Connections) — maximises throughput for 1-to-1 transfers
- Hive over SQLite for local storage (no native compile dependency, supports encryption)
