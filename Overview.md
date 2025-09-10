# Smart Share & Scan App

## Overview
A cross-platform Flutter app that combines **QR/Barcode scanning & generation** with **offline file sharing** (via Wi-Fi Direct or Bluetooth).  
The app acts as a **personal utility hub**, merging functionalities of a **QR manager** with an **offline sharing tool**, similar to **Xender/AirDrop**.

---

## Project Breakdown

### Core Modules
- **Authentication Module**
  - Simple onboarding (no accounts)
  - Public name & hardware ID for device recognition
- **Scanner Module**
  - Scan QR & barcodes
  - Categorize and save results
  - Generate custom QR codes
- **File Sharing Module**
  - Send & receive files offline via Wi-Fi Direct or Bluetooth
  - Research best cross-platform sharing methods
- **History Module**
  - Logs for scans and file transfers
  - Search & filter by type/date
- **Settings Module**
  - App themes (light/dark/system)
  - Security options (app lock, hidden folders)
  - Data management (clear cache/history)

---

### Features

#### QR/Barcode Features
- Camera-based scanning (QR, barcodes)
- Categorized history (URLs, Wi-Fi, contacts, products)
- QR generation for:
  - Text/Links
  - Wi-Fi credentials
  - Contacts (vCard)
  - App-specific file sharing setup
- Quick search & favorites for saved scans

#### File Sharing Features
- Offline peer-to-peer file transfer via:
  - **Wi-Fi Direct** (primary)
  - **Bluetooth** (fallback)
- Support for all file types (images, videos, audio, docs, zips)
- Bulk transfer & drag-and-drop selection
- Transfer history with timestamps

#### Integration Features
- QR code pairing for faster device connection
- Generate QR for small data exchange (links, Wi-Fi, credentials)
- Scan partner’s QR to auto-connect without manual setup

---

### Possible Extensions
- **Group Sharing**: Send files to multiple devices at once
- **Encrypted Transfers**: AES-based secure communication
- **In-App Media Viewer**: Preview images, PDFs, music, videos
- **Cloud Sync**: Backup scans & logs to **existing cloud services** (Google Drive, Dropbox, OneDrive, or self-hosted servers)
  - Users can authorize and connect their preferred cloud account
  - Supports automatic backup or manual export/import
  - Keeps scan history and transfer logs synced across devices
- **Export/Import**: Save and restore scans & transfer logs
- **Cross-Platform Support**: Android, iOS, Windows, Linux (desktop build via Flutter)

---

## Tech Stack
- **Framework**: Flutter (cross-platform)  
- **Language**: Dart  
- **Core Packages**:
  - `mobile_scanner` → QR/Barcode scanning
  - `barcode` → QR/Barcode generation
  - `nearby_connections` / `wifi_direct` → Wi-Fi Direct file sharing
  - `flutter_blue` → Bluetooth transfers
  - `hive` or `sqflite` → Local storage for history/logs
  - `path_provider` → File system access
- **Database**: Hive (lightweight, fast, encrypted storage)  
- **UI/UX**: Material 3 (Flutter), theming with dynamic colors  

---
