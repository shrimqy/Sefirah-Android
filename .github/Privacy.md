# Privacy Policy

**Effective Date:** [12/2/2026]

## Introduction

Thank you for using our application "Sefirah". Your privacy is important to us. This Privacy Policy explains how our App collects, uses, and protects your information. By using our App, you agree to the practices described in this policy.

## Information We Collect

Sefirah collects and processes the following data **locally on your device** and, when connected, over your **local network** to your Windows PC:

- **Connectivity & background sync**: Uses internet and network state permissions to maintain a secure local network connection between your Android device and your Windows PC. It runs as a foreground service to keep the connection active so that notification mirroring, clipboard sync, and other features work reliably.
- **Device discovery**: Uses Wi‑Fi state, optional location (see below), and multicast to discover your PC on the same network. When “trust all networks” is turned off, location is used only to determine the current Wi‑Fi network name (SSID) so that discovery runs only on networks you’ve marked as trusted.
- **Communication & call sync**: Detects real-time call status and uses call logs and contacts so that caller names and statuses can be shown on your PC. SMS sync (if enabled) sends conversation and contact data to your PC so you can view and send texts from the desktop.
- **Notification mirroring**: Reads device notifications and forwards them to your Windows app.
- **Clipboard sync**: Syncs clipboard content (text and, if enabled, images) between your Android device and your PC. Optional accessibility permission is used only to detect when you copy content on the phone so it can be sent to the PC.
- **Media control**: Syncs media playback controls between your Android device and your PC. Posts the current media session your pc is playing in Android if enabled.
- **File sharing & storage**: Handles file sharing via the share sheet and, if enabled, exposes your Android storage to the PC over a local SFTP connection so it can appear in Windows Explorer.
- **Device info**: Shares device id, name, and optionally wallpaper and phone numbers for display.
- **Battery & device status**: Shares battery level and charging state, ringer mode, and Do Not Disturb status with the PC.
- **Setup & pairing**: Uses the camera only for scanning the pairing QR code on your PC. May send a list of installed apps and contacts when you first pair a new device (for features like SMS and contact resolution).

**Important:** Data transfer happens only over your **local Wi‑Fi/network** between your Android device and your PC. No call logs, contacts, notification contents, SMS, or clipboard data are sent to any external server or cloud service.

## How We Use Your Data

All data processed by Sefirah is:

- Stored locally on your devices (Android and PC).
- Never uploaded to any third‑party server or cloud service.
- Transmitted only over an encrypted **TLS (TLS 1.2)** connection between your Android device and your Windows PC on your local network.

Sefirah does **not**:

- Collect analytics or crash data.
- Serve ads or use tracking technologies.
- Share data with any third‑party services.

Pairing between devices uses **ECDH (Elliptic Curve Diffie–Hellman)** and verification codes so that only devices that have been approved can connect. The encryption keys are exchanged and used only on your local network.

## Permissions Explanation

### Connectivity & background sync

| Permission | Purpose |
|------------|--------|
| `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE` | Used to establish and maintain a secure local network connection between your Android device and your Windows PC. |
| `CHANGE_WIFI_MULTICAST_STATE` | Used for local device discovery (e.g. multicast) on the Wi‑Fi network. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Required to keep a persistent connection to your PC so that notification mirroring, clipboard sync, and other features work reliably in the background. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Used so that the system does not stop the Sefirah background service, helping maintain a stable connection. |
| `RECEIVE_BOOT_COMPLETED` | Used to restore the connection or discovery state after the device restarts. |

### Device discovery (optional)

| Permission | Purpose |
|------------|--------|
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION` | When “trust all networks” is **disabled**, used only to determine the current Wi‑Fi network name (SSID) so that device discovery runs only on networks you’ve saved as trusted. Not used for tracking or mapping. |

### Communication & call sync

| Permission | Purpose |
|------------|--------|
| `READ_PHONE_STATE` | Used to detect real-time call status (e.g. ringing, in progress, ended) so it can be shown on your PC and to retrieve the current phone number of the device. |
| `READ_CALL_LOG` | Used to identify the phone number of an incoming or recent caller for display on your PC. |
| `READ_CONTACTS` | Used to match phone numbers with contact names so you can see who is calling or messaging on your PC. |
| `READ_SMS`, `SEND_SMS`, `RECEIVE_SMS`, `RECEIVE_MMS` | Used only when you enable SMS sync, to read and send SMS/MMS and show conversations on your PC. |

### Notification mirroring

| Permission | Purpose |
|------------|--------|
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Allows Sefirah to read your notifications and forward them to your Windows app. |
| `POST_NOTIFICATIONS` | Used to show the app’s own notifications (e.g. connection status and the option to disconnect). |
| `RECEIVE_SENSITIVE_NOTIFICATIONS` | On Android 15+, may be required (e.g. via ADB) to mirror sensitive notifications; used only for mirroring to your PC. |
| `QUERY_ALL_PACKAGES` | Used to properly identify and display which app sent each notification. |

### Clipboard sync

| Permission / feature | Purpose |
|----------------------|--------|
| Accessibility (optional) | Used only to detect when you copy content on your phone so it can be sent to your PC. Not used for anything else. |
| Share / in‑app | You can also send clipboard content manually via the share sheet or the app’s notification/UI. |

### Media, storage & personalization

| Permission | Purpose |
|------------|--------|
| Media controls | Used to sync playback controls and “Now Playing” so you can control your **PC’s** media from your Android device. |
| `MANAGE_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE`, `READ_EXTERNAL_STORAGE` | Optional. Used for reading wallpaper to sync to the PC and for file sharing / SFTP storage integration with Windows. |

### Setup & utilities

| Permission | Purpose |
|------------|--------|
| `CAMERA` | Used only to scan the QR code on your PC for pairing. |


## Security

- All communication between your Android device and your Windows PC uses **TLS 1.2** over the local network.
- Pairing uses **ECDH** and verification codes; keys are exchanged only between your devices on the same network and are not sent over the internet.
- No personal data is stored on any external server.


## Data Retention

No personal data is stored on external servers. Mirrored notifications, clipboard content, and similar data are used in memory or in app storage only for the duration of the session or feature use and are not retained beyond what is needed for the feature.

## Your Rights

Because Sefirah does not collect or store your data on external servers, there is no central account or cloud data to delete or export. All processing is local and between your own devices; you control pairing, permissions, and which features (e.g. SMS, clipboard, notifications) are enabled per device.

## Contact

If you have questions about this Privacy Policy or how Sefirah works, you can reach us at:

- **Email**: shrimqy@gmail.com

- **Discord**: [Sefirah Discord server](https://discord.gg/MuvMqv4MES)

---

_Last Updated: [12/2/2026]_