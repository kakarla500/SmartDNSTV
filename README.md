# Smart DNS TV

<p align="center">
  <img src="logo.jpg" alt="Smart DNS TV Logo" width="180" height="180" style="border-radius: 24px;" />
</p>

<p align="center">
  <strong>Fast, secure, and modern DNS manager engineered specifically for Android TV.</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%20TV-3DDC84.svg?style=flat&logo=android" alt="Android TV" />
  <img src="https://img.shields.io/badge/Min%20SDK-25%20(Android%207.1+)-informational.svg?style=flat" alt="Min SDK" />
  <img src="https://img.shields.io/badge/Target%20SDK-36-blue.svg?style=flat" alt="Target SDK" />
  <img src="https://img.shields.io/badge/Version-2.0.0-emerald.svg?style=flat" alt="Version" />
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF.svg?style=flat&logo=kotlin" alt="Kotlin" />
  <img src="https://img.shields.io/badge/License-Apache%202.0-orange.svg?style=flat" alt="License" />
</p>

---

## 📺 Overview

**Smart DNS TV** is an open-source, non-root DNS management and ad-blocking application designed from the ground up for the **Android TV (Leanback)** ecosystem. It creates a local-only VPN interface solely for DNS queries, allowing you to instantly switch between top secure upstream resolvers (**AdGuard DNS**, **Cloudflare 1.1.1.1**, **Google Public DNS**, or **Custom DNS**) across your entire television on both **Wi-Fi** and **Ethernet** connections.

---

## ⚡ What Smart DNS TV is Capable Of

### 🛡️ 1. Multi-Provider DNS Profiles (1-Click Switch)
- **AdGuard DNS** (`94.140.14.14`, `94.140.15.15`): Enforces system-wide ad, tracker, and malware blocking across all TV apps.
- **Cloudflare DNS** (`1.1.1.1`, `1.0.0.1`): World's fastest, privacy-first DNS resolver for ultra-low latency streaming.
- **Google Public DNS** (`8.8.8.8`, `8.8.4.4`): Ultra-reliable global DNS infrastructure.
- **Custom DNS**: Input any custom IPv4 or IPv6 DNS resolver (Pi-hole, NextDNS, AdGuard Home, or private gateway) with built-in format validation.

### 🔄 2. Smart Wake & Standby Auto-Recovery (No More Stuck Disconnects!)
- **Standby Auto-Recovery**: Smart TV operating systems frequently kill background services and tear down network sockets when put to sleep or standby.
- **Self-Healing State**: Smart DNS TV detects whether the VPN tunnel is genuinely alive via `ConnectivityManager` and automatically reconnects upon TV wake or reboot if Auto-Reconnect is enabled. No more needing to manually disconnect and reconnect!
- **Foreground Service Protection**: Runs as a lightweight foreground service with low-priority notification to protect the DNS tunnel from Android's Low Memory Killer (LMK).

### 🌐 3. Dual-Interface Compatibility (Wi-Fi & Ethernet)
- Standard Android TV settings typically lock down DNS configuration on wired Ethernet LAN connections.
- Smart DNS TV bridges this gap using Android's `VpnService` API, ensuring that **both Ethernet LAN cables and Wi-Fi connections** receive full protection.
- Live network indicator pill shows your TV's active connection status (Ethernet 🟢 vs. Wi-Fi 📶).

### 🎮 4. 100% Android TV & Remote Friendly (D-Pad Navigation)
- Tailored exclusively for 10-foot television experiences.
- Smooth scale animations (`1.04x`) and cyan focus rings on D-Pad navigation.
- Fully compliant with **Android TV Leanback Launcher** guidelines, featuring a custom 16:9 home screen banner (`tv_banner.png`) and dedicated launcher icons.

### 🚀 5. Zero Latency & Full Streaming Speeds
- **DNS-Only Routing**: Unlike traditional VPNs that route all heavy video and audio streams through an encrypted tunnel, Smart DNS TV **only routes DNS lookup packets (Port 53)**.
- **Zero Bandwidth Throttling**: 4K/8K HDR video playback continues directly over your local ISP connection with zero speed penalty, buffer lag, or battery/CPU drain on TV processors.

---

## 🛠️ How It Works Under the Hood

```
+-------------------------------------------------------------+
|                        Android TV                           |
|                                                             |
|   [TV App / Browser / Media Player]                         |
|           |                                                 |
|           | 1. DNS Query (e.g. video.cdn.com)               |
|           v                                                 |
|   +-----------------------+                                 |
|   |  Smart DNS TV (tun0)  | <--- Local Virtual Interface    |
|   +-----------------------+      (DNS packets only)         |
|           |                                                 |
|           | 2. Redirect to Selected Provider:               |
|           |    - AdGuard DNS   (94.140.14.14)               |
|           |    - Cloudflare    (1.1.1.1)                    |
|           |    - Google Public (8.8.8.8)                    |
|           v    - Custom DNS    (Your IP)                    |
|   +-----------------------+                                 |
|   |   Upstream DNS Server | ---> Fast / Filtered Resolution |
|   +-----------------------+                                 |
|                                                             |
|   * Normal Video/Stream Data ---> Direct to ISP (Unmodified)|
+-------------------------------------------------------------+
```

---

## 📥 Installation

The compiled, ready-to-install APK is available on the [Releases](https://github.com/kakarla500/AdGuardTV/releases) page:
- **[Download SmartDNS_TV_v2.0.0.apk](https://github.com/kakarla500/AdGuardTV/releases/latest)**

### Method 1: Install via Wireless ADB (Recommended)
1. On your Android TV, navigate to **Settings > Device Preferences > About**.
2. Click **Build** 7 times until Developer Options are unlocked.
3. Go to **Settings > Device Preferences > Developer Options** and enable **Network Debugging / USB Debugging**.
4. Note your TV's IP address from **Settings > Network & Internet**.
5. Connect and install from your computer:
   ```bash
   adb connect <YOUR_TV_IP>:5555
   adb install -r SmartDNS_TV_v2.0.0.apk
   ```

### Method 2: Install via USB Drive
1. Copy the APK to a USB flash drive.
2. Plug the USB drive into your Android TV.
3. Open a file manager app on your TV (such as *File Commander* or *X-plore*).
4. Locate the APK and click **Install**.

---

## 📖 Usage Instructions

1. Open **Smart DNS TV** from your television app launcher.
2. Use the TV remote D-Pad to select your desired DNS profile:
   - **AdGuard DNS** for ad and tracker blocking.
   - **Cloudflare** for ultra-fast response and privacy.
   - **Google Public** for global stability.
   - **Custom DNS** to enter your own server address.
3. Click **⚡ Connect DNS**.
4. When prompted by Android TV, confirm the VPN Connection Request.
5. The pulsing **"PROTECTION ACTIVE"** indicator confirms that the secure DNS tunnel is active.

---

## 🏗️ Building from Source

```bash
# Clone the repository
git clone https://github.com/kakarla500/AdGuardTV.git
cd AdGuardTV

# Run local unit tests
./gradlew test

# Assemble the debug APK
./gradlew assembleDebug
```
The compiled APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

---

## 📄 License

This project is licensed under the Apache License, Version 2.0. See the [LICENSE](LICENSE) file for full details.
