# ⚡ AuraSync — Enterprise P2P Media Relay & Unlimited Google Photos Hub Engine

[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-00E676?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3%2B-6366F1?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20(Material%203)-00E676?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Telegram Community](https://img.shields.io/badge/Telegram-Join%20AuraSync-26A5E4?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/AuraSyncapp)
[![PRs Welcome](https://img.shields.io/badge/PRs-Welcome-00E676?style=for-the-badge)](https://github.com/sivap/AuraSync/pulls)

**AuraSync** is a high-performance, fault-tolerant, dual-role peer-to-peer (P2P) Android application engineered to automatically relay photos and 4K videos from one or more **Primary Phones** directly to a **Secondary "Unlimited Google Photos" Hub Phone** over local Wi-Fi or mobile data anywhere in the world.

Built with **Kotlin, Jetpack Compose (Material 3), Room Database, and WorkManager**, AuraSync operates with zero cloud intermediaries, strict 64KB chunked socket streaming, end-to-end encryption (E2EE), and two-tier cryptographic SHA-256 deduplication.

---

## 🎯 Why AuraSync Was Made (Purpose & Motivation)

Many users own a secondary device capable of **Unlimited Google Photos Backup** (such as an original Google Pixel or a secondary backup device) alongside their modern flagship daily driver. However, moving new photos and large 4K videos from a primary phone to that secondary hub device usually involves:
1. Manually copying files over USB cables or slow third-party cloud drives.
2. Dealing with duplicate uploads, corrupted half-transferred videos, and overwritten filenames.
3. Background sync apps getting killed by Android's aggressive Doze mode or draining battery when away from home Wi-Fi.

**AuraSync was built to solve this permanently:**
- **Single Unified APK, Two Roles:** Install the exact same APK on both devices. Set your daily driver as the **Primary Sender Node** and your backup phone as the **Secondary Google Hub**.
- **Zero-Touch Automation:** Newly captured camera photos and videos are detected immediately via `MediaStore` content observers (or synced on a custom daily schedule) and streamed directly into the Secondary Hub's `DCIM/Camera` directory so Google Photos backs them up automatically.
- **Sync From Anywhere:** Works seamlessly over home Wi-Fi using Zero-Config mDNS discovery and automatically switches over to **Tailscale / ZeroTier Mesh VPN or WAN** when you leave home on 4G/5G mobile data.

---

## ✨ Key Features

### 1. Core Architecture & Breathtaking Material 3 UI
- **Single APK Dual-Role Engine:** Runtime switch between **Primary Sender Node** and **Secondary Google Hub (Receiver)** persisted in AES-256-GCM encrypted storage.
- **Unstoppable 24/7 Foreground Service:** Acquires a CPU `PARTIAL_WAKE_LOCK` and a high-performance Wi-Fi lock (`WIFI_MODE_FULL_LOW_LATENCY`) to bypass Android Doze mode and keep relay sockets alive 24/7.
- **Actionable System Notifications:** Real-time transfer speed, file progress bar, and interactive **"Sync Now"** and **"Pause / Resume"** buttons directly in the notification shade.
- **Obsidian Material 3 Design:** Deep obsidian dark mode with a reactive **Master Dark/Light Theme Toggle**, glowing emerald and indigo gradient cards, live circular progress rings, a global Hamburger Navigation Drawer, and a focused 3-tab bottom bar (**Dashboard**, **Folders**, **Network**).

### 2. Cryptographic Vault & Zero-Re-Entry Pairing
- **Biometric & 6-Digit PIN Lock:** Protects pairing tokens and security settings with Android's `BiometricPrompt` API (Fingerprint / Face Unlock) and a salted SHA-256 6-digit PIN.
- **Persistent QR Code Device Pairing:** The Secondary Hub generates a cryptographic `PairingToken` QR code containing LAN IP, Tailscale/WAN endpoint, port, PIN hash, and a 256-bit E2EE shared secret. Scan once on the Primary phone using the built-in CameraX QR scanner for permanent pairing.
- **End-to-End Encrypted Streams (E2EE):** All socket payloads are encrypted in transit using `AES/CTR/NoPadding` paired with `HmacSHA256` pre-flight authentication tags.

### 3. Smart Connectivity & "Sync From Anywhere"
- **Zero-Config Local mDNS Discovery:** Automatically broadcasts and discovers active Secondary Hubs on home Wi-Fi via Android `NsdManager` (`_syncvault._tcp.`).
- **Tailscale / ZeroTier & WAN Fallback:** Automatically detects `100.x.x.x` Mesh VPN interfaces or custom WAN/DDNS hosts to relay media over 4G/5G mobile data anywhere in the world.
- **Seamless Network Handover:**Monitors live network transitions via `ConnectivityManager.NetworkCallback` to pause and re-route active streams without crashing or corrupting files.
- **Trusted Wi-Fi SSID Whitelisting:** Restrict heavy scheduled background syncs strictly to your home Wi-Fi SSIDs to prevent accidental data usage on metered hotspots.

### 4. Two-Tier SHA-256 Deduplication & Granular Folder Control
- **Tier 1 (Local Fast-Path Index):** Queries the local Room Database (`filePath` + `fileSize` + `lastModified`) to skip unchanged, already-synced local files without redundant disk reads or re-hashing.
- **Tier 2 (Remote Pre-Flight Handshake):** Computes a 256-bit SHA-256 checksum and performs a pre-flight TCP check against the Secondary Hub's Room ledger *before* sending a single binary chunk—blocking duplicates across all connected phones.
- **Granular Source Folder Filtering:** Check or uncheck individual `MediaStore` buckets (`Camera`, `Screenshots`, `WhatsApp Images`, `WhatsApp Video`, `Download`, and custom device albums).

### 5. Extreme Performance & Priority Queueing
- **Strict 64KB Chunked Socket & Disk I/O:** 100% of Room queries, SHA-256 hashing, and encrypted socket transfers run exclusively on `Dispatchers.IO` using fixed 64KB (`65,536`-byte) buffers—eliminating UI jank, GC thrashing, and Out-Of-Memory (OOM) crashes during multi-gigabyte 4K video relays.
- **`PriorityBlockingQueue` & Urgent Queue-Jumping:** Assign device priority weights (`1` to `5`) or tap **"Upload Immediately"** on any folder or single file to dispatch Express Priority-5 packets that jump ahead of batch queues.
- **Exponential Backoff WorkManager:** Automated retries starting at $5\text{s}$ ($5\text{s} \rightarrow 10\text{s} \rightarrow 20\text{s}$, up to 3 attempts) if the Hub is temporarily unreachable.

### 6. Safety Switches & Microscopic Edge-Case Guards
- **Atomic Temporary File Writes:** Incoming payloads stream into hidden staging files (`.tmp_[sha256]_[filename]`) and must pass a post-transfer SHA-256 checksum verification before being atomically committed to `MediaStore` (`DCIM/Camera`).
- **Filename Collision Guard:** Appends an 8-character cryptographic hash suffix (`IMG_0001_A9F40C12.jpg`) so files with identical names from multiple phones never overwrite each other.
- **Thermal, Battery & Low-Storage Guards:**
  - Automatically pauses transfers if battery temperature exceeds $45^\circ\text{C}$ ($104^\circ\text{F}$).
  - Pauses background sync if battery drops below $15\%$ while unplugged.
  - Rejects incoming transfers and fires an emergency notification if Secondary Hub free storage drops below $1\text{ GB}$.
- **Config JSON Export & Import:** Backup and restore paired tokens, schedules, and folder preferences in one tap.

---

## 🛠️ Tech Stack & Architecture

| Layer | Technology Used |
| :--- | :--- |
| **Language & Concurrency** | 100% Kotlin, Coroutines (`Dispatchers.IO`), `StateFlow`, `Mutex`, `PriorityBlockingQueue` |
| **UI Framework** | Jetpack Compose, Material 3, Dynamic Obsidian/Light Theme, Custom Canvas/Progress Rings |
| **Local Persistence** | AndroidX Room Database (KSP2) for Tier-1 & Tier-2 SHA-256 Deduplication Ledgers & Audit Logs |
| **Security & Vault** | `EncryptedSharedPreferences` (`MasterKey` AES-256-GCM), `BiometricPrompt`, `AES/CTR/NoPadding` + `HmacSHA256` |
| **Networking** | Raw TCP `ServerSocket` / `Socket` (64KB Chunked), `NsdManager` (`_syncvault._tcp.`), Tailscale/WAN Router |
| **Background Execution** | 24/7 `ForegroundService` (`dataSync\|connectedDevice`), `WorkManager`, `MediaStore` `ContentObserver` |
| **QR & Camera** | Google ZXing (`MultiFormatWriter` / `MultiFormatReader`) + AndroidX CameraX (`ImageAnalysis`) |
| **Serialization** | `kotlinx.serialization` JSON |

---

## 🚀 How to Use AuraSync (End-to-End Setup Guide)

### Step 1: Set Up the Secondary Hub (Receiver Phone)
1. Install and open **AuraSync** on your Secondary "Unlimited Google Photos" phone and grant the requested permissions.
2. On the **Dashboard** tab, select **Secondary Google Hub** inside the *Dual-Role Engine Selector* card.
3. Tap **Exempt Doze** on the Dashboard safety card to allow AuraSync to run 24/7 without battery restrictions.
4. Go to the **Network** tab, create a **6-Digit Cryptographic PIN**, and unlock the Vault. A high-contrast **QR Code** will be generated automatically with your Hub's LAN IP (and Tailscale IP if active).
5. Ensure the **Google Photos** app on this Hub phone has Backup enabled for `DCIM/Camera`.

### Step 2: Pair the Primary Phone (Sender Phone)
1. Install and open **AuraSync** on your Primary daily-driver phone and keep the role set to **Primary Sender Node**.
2. Tap **Exempt Doze** on the Dashboard card.
3. Open the **Network** tab, unlock the Vault with a 6-digit PIN or Biometric prompt, and tap **Scan Hub QR Code (Primary Node)**.
4. Point your camera at the QR Code displayed on the Secondary Hub phone. Both devices are now permanently paired with end-to-end encryption.

### Step 3: Customize Folders, Schedule & Relay Media
1. Open the **Folders** tab on your Primary phone and check the folders you want synced (`Camera`, `Screenshots`, `WhatsApp Images`, `WhatsApp Video`, `Download`).
2. Tap **Upload Now** on any folder—or expand a folder and tap **Upload Immediately** on a single photo—to test Express Priority-5 Queue-Jumping.
3. Return to the **Dashboard** tab to set your **Daily Automated Sync Time**, adjust the **Daytime Bandwidth Throttle** (uncapped automatically between 02:00 AM and 06:00 AM), or tap **Sync Now** to run a full deduplicated batch transfer.

### Step 4: Optional — Sync Over 4G/5G Anywhere in the World
1. Install [Tailscale](https://tailscale.com/) (or ZeroTier) on both your Primary and Secondary phones and log into the same mesh network.
2. On the Secondary Hub's **Network** tab, verify that its `100.x.x.x` Tailscale IP is detected (or type your custom WAN/DDNS address) and tap the QR refresh button, then scan the updated QR code once on your Primary phone.
3. Whenever you leave home Wi-Fi, AuraSync will automatically hand over active connections to the Tailscale/WAN route.

---

## 💻 Building from Source (Android Studio)

AuraSync is configured for **Android Studio Rabbit (2026.2+)** using **AGP 9.x** and **Gradle 9.4+**.

1. **Clone the repository:**
   ```bash
   git clone https://github.com/sivap/AuraSync.git
   cd AuraSync
   ```
2. **Open in Android Studio:**
   - Open Android Studio and select **Open** -> choose the `AuraSync` folder.
   - Ensure your Gradle JDK is set to **JDK 17** (**Settings -> Build, Execution, Deployment -> Build Tools -> Gradle**).
3. **Compile Debug APK:**
   ```bash
   ./gradlew assembleDebug
   ```
   The installable APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.
4. **Install directly via ADB:**
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

## 🤝 Open to Contributions!

**AuraSync is open-source and actively welcoming contributors!** Whether you want to fix bugs, optimize socket throughput, add new Material 3 animations, improve NAS/SMB support, or refine documentation, your help is appreciated.

### How to Contribute
1. **Fork** the repository on GitHub.
2. **Create a feature branch:**
   ```bash
   git checkout -b feature/your-feature-name
   ```
3. **Commit your changes** with clear, descriptive commit messages:
   ```bash
   git commit -m "feat: add custom notification sound toggle"
   ```
4. **Push to your branch** and open a **Pull Request**.

Found a bug or have a feature request? Please open an issue on the [GitHub Issues](https://github.com/sivap/AuraSync/issues) tab!

---

## 💬 Community & Support

Join our official Telegram community to get setup help, download test builds, share feedback, and discuss new features with the developer and other users:

👉 **Official Telegram Group:** [https://t.me/AuraSyncapp](https://t.me/AuraSyncapp) (`t.me/AuraSyncapp`)

---

## 👨‍💻 Author & Credits

- **Lead Developer & Architect:** **Sivaprasad** ([@sivaprasad2003](https://github.com/sivaprasad2003))
- **Community & Updates:** [t.me/AuraSyncapp](https://t.me/AuraSyncapp)

If **AuraSync** saves you cloud storage subscriptions and keeps your memories backed up effortlessly, please consider giving this repository a ⭐ **Star** on GitHub and sharing it with the community!
