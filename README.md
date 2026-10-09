# HDHR MultiView

A high-performance Android, Tablet, and Google TV multiview player for HDHomeRun networked TV tuners (including HDHomeRun FLEX 4K).

Watch up to 4 simultaneous live broadcast streams in real time with dynamic audio switching, interactive layouts, hardware decoding, and extensible audio transcoding for NextGen TV broadcasts.

---

## Features

* **Multi-Stream Hardware Playback**: Plays up to 4 live broadcast streams simultaneously in hardware.
* **NextGen TV & Advanced Audio Engine**: Built-in client-side transparent proxy with unpatched upstream FFmpeg media engine, with user option to supply external custom FFmpeg binaries for extended audio decoding capabilities with 0% video overhead (pure HEVC hardware pass-through).
* **Flexible Multiview Layouts**:
  * **4-Stream Grid (`2x2`)**: Equal 16:9 quadrants for sports and live news monitoring.
  * **1 + 3 Focus**: Large primary stream with three peripheral PIP slots.
  * **Dual Stream**: Side-by-side or stacked two-channel view.
  * **3D Cover Flow Carousel**: Horizontal swipe pager with 3D perspective depth, angle tilt, center snapping, and live audio follow.
  * **Fullscreen**: Instant one-tap / remote expansion of any slot.
* **Google TV & Android TV Native**:
  * Full D-pad remote navigation with high-contrast focus rings.
  * Remote-friendly Slot Actions menu (`Change Channel`, `Audio Focus`, `Fullscreen`, `Clear Slot`).
  * Dedicated hardware key support: `CHANNEL_UP/DOWN`, `GUIDE`, `MENU`, `MEDIA_PLAY/PAUSE`, `VOLUME_MUTE`, and `BACK` (graceful fullscreen exit).
  * 16:9 Leanback launcher banner and safe overscan margins.
* **Universal Architecture**: Pre-compiled and packaged for `arm64-v8a` (phones/tablets/Shield TV), `armeabi-v7a` (Chromecast with Google TV, Onn 4K), and `x86_64` (emulators/Chromebooks).

---

## Requirements

* **Android Host**: Android 8.0 (API 26) or higher.
* **HDHomeRun Tuner**: HDHomeRun CONNECT, EXTEND, PRIME, or FLEX 4K connected to the local network.
* **Display**: Phones (Portrait & Landscape), Tablets, and Android/Google TV (Landscape).

---

## Building from Source

```bash
# Clone the repository
git clone https://github.com/jay0lee/hdhr-multiview.git
cd hdhr-multiview

# Build debug APK
./gradlew assembleDebug

# Build release App Bundle (AAB) for Google Play
./gradlew bundleRelease
```

---

## License

This project is licensed under the Apache 2.0 License. Native decoding modules utilize upstream LGPL 2.1 FFmpeg.
