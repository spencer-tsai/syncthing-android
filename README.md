# Syncthing Android

An Android app that embeds [Syncthing](https://syncthing.net) as a foreground service and exposes folder/device management through a native Jetpack Compose UI.

![Syncthing Logo](https://docs.syncthing.net/_static/logo-horizontal.svg)

---

## Features

- **Embedded Syncthing binary** — runs the official Syncthing binary as a foreground service directly on the device; no separate daemon or root required
- **Folder management** — add and delete sync folders; browse the filesystem with the system folder picker (supports paths accessible by other apps such as Obsidian)
- **Device pairing** — add remote devices by scanning a QR code or entering the Device ID manually
- **Pending request cards** — devices or folders offered by remote peers appear as one-tap approval cards on the home screen
- **Real-time status** — sync progress bar, bytes synced, online/offline badge per device; refreshes every 5 seconds
- **Adaptive icon** — Syncthing's official logo, all mipmap densities

---

## Architecture

```
app/
├── service/
│   └── SyncthingService.kt      # Foreground service; exec libsyncthing.so
├── api/
│   ├── SyncthingApi.kt          # Retrofit interface (REST API)
│   └── ApiClient.kt             # OkHttp client with X-API-Key header
├── repository/
│   └── SyncthingRepository.kt   # Wraps API calls in runCatching
├── ui/
│   ├── MainViewModel.kt         # UiState + StateFlow, 5 s polling
│   ├── HomeScreen.kt            # Main screen (LazyColumn)
│   ├── AddFolderDialog.kt       # AlertDialog with folder picker
│   └── AddDeviceDialog.kt       # AlertDialog with QR scanner + ID validation
└── MainActivity.kt              # Permission handling, service lifecycle
```

### Key design decisions

| Problem | Solution |
|---|---|
| Android SELinux blocks `exec` from `filesDir` | Binary packaged as `jniLibs/libsyncthing.so`; executed from `nativeLibraryDir` |
| AGP 9 default: `extractNativeLibs=false` | `jniLibs { useLegacyPackaging = true }` in `build.gradle.kts` |
| Android 9+ blocks HTTP cleartext | `network_security_config.xml` permits HTTP to `127.0.0.1` only |
| Go 1.26 bans external `go:linkname` to `net.zoneCache` | Patched `wlynxg/anet` bundled in the [syncthing fork](https://github.com/spencer-tsai/syncthing) |

---

## Requirements

| | |
|---|---|
| **Android** | 9.0 (API 28) or later |
| **Architecture** | arm64-v8a, armeabi-v7a, x86_64 |
| **Permissions** | `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS`, `CAMERA` (QR scanner), `MANAGE_EXTERNAL_STORAGE` (optional, for browsing arbitrary folders) |

---

## Building from source

### Prerequisites

- Android Studio Meerkat or later
- Android NDK 30.x (for recompiling the Syncthing binary — not needed if you use the pre-built `.so` files already included)

### Clone and open

```bash
git clone https://github.com/spencer-tsai/syncthing-android.git
cd syncthing-android
```

Open the project in Android Studio and run on a connected device or emulator.

The pre-built Syncthing binaries are already included under `app/src/main/jniLibs/`. No additional compilation step is needed for a normal build.

### Recompiling the Syncthing binary (optional)

If you want to rebuild the binary from source, use the patched fork:

```bash
git clone https://github.com/spencer-tsai/syncthing.git
cd syncthing

# Prerequisites: Go 1.21+, Android NDK
export NDK_PATH="/path/to/ndk"
export CC="$NDK_PATH/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android28-clang"
export CXX="$NDK_PATH/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android28-clang++"

GOOS=android GOARCH=arm64 CGO_ENABLED=1 \
  go build -tags "noupgrade noassets" \
  -o android-out/arm64-v8a/syncthing \
  ./cmd/syncthing
```

Repeat for `armeabi-v7a` (`GOARCH=arm`, `GOARM=7`) and `x86_64` (`GOARCH=amd64`), then copy the binaries to `app/src/main/jniLibs/<abi>/libsyncthing.so`.

---

## How it works

1. **API key** — a random 32-char hex key is generated on first launch and stored in `SharedPreferences`.
2. **Service start** — `SyncthingService` executes `libsyncthing.so` with `--gui-address 127.0.0.1:8384 --gui-apikey <key> --no-browser --no-restart`.
3. **REST client** — once the service reports ready, the app polls `http://127.0.0.1:8384` every 5 seconds using Retrofit with the `X-API-Key` header.
4. **Config changes** — adding/deleting folders and devices calls `POST /rest/config/folders`, `POST /rest/config/devices`, `DELETE /rest/config/folders/{id}`, etc. Changes are applied live without restarting Syncthing.
5. **Pending requests** — `GET /rest/cluster/pending/devices` and `/rest/cluster/pending/folders` are polled to surface approval cards for incoming connection requests.

---

## Pairing a device

The easiest way to pair two Syncthing devices is with the QR code:

1. On the **remote device** (desktop or another phone), open the Syncthing Web UI and go to **Actions → Show ID**. A QR code of the Device ID is displayed.
2. On this app, tap the **`+`** button next to "裝置".
3. Tap **掃描 QR Code** — the camera opens.
4. Point the camera at the QR code. The Device ID fills in automatically once scanned.
5. Optionally enter a friendly name, then tap **新增**.

If the remote device does not have a QR code readily available, you can also type or paste the 63-character Device ID manually into the text field.

> **Tip:** Syncthing pairing is mutual. After adding the remote device here, the remote side must also add this device's ID (shown in the **服務狀態** card on the home screen).

---

## Syncing with Obsidian

To sync an Obsidian vault:

1. Grant **"Allow management of all files"** in the app (tap the permission button inside the Add Folder dialog).
2. Add a folder and tap **瀏覽資料夾…** to pick your vault path (e.g. `/storage/emulated/0/Documents/Obsidian/MyVault`).
3. Pair the desktop Syncthing device — the phone will show a **"新裝置請求連線"** card; tap **接受**.
4. On the desktop, share the same folder with the phone's Device ID.
5. The phone will show a **"對方共享資料夾"** card; tap **接受** to begin syncing.

---

## Tech stack

| Layer | Library |
|---|---|
| UI | Jetpack Compose, Material 3 |
| State | ViewModel + StateFlow |
| HTTP | Retrofit 2.11 + OkHttp 4.12 |
| JSON | Gson |
| Concurrency | Kotlin Coroutines 1.9 |
| QR scanner | ZXing Android Embedded 4.3.0 |
| Build | AGP 9.2, Kotlin 2.2, Gradle 8 |

---

## License

The Android app code in this repository is released under the [MIT License](LICENSE).

The embedded Syncthing binary is subject to the [Syncthing MPL-2.0 License](https://github.com/syncthing/syncthing/blob/main/LICENSE).
