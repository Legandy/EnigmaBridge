# Enigma Bridge

**Enigma Bridge** is a plugin for the [TV-Browser](https://www.tvbrowser-app.de/index.php?setlang=en) app to quickly schedule a recording on your Enigma2 device via context menu.

## ✨ Features

- **TV-Browser Plugin**: Schedule recordings directly from the TV-Browser context menu.
- **Advanced Scheduling**: Fine-tune timers with custom start/end padding, repeat rules (Daily, Weekly), and after-event actions (Standby, Deep Standby).
- **Timer Management**: View, edit, delete, and even "Zap" to channels on your receiver from the app.
- **Smart Syncing**:
    - Automatic channel list synchronization from your favorite Bouquets.
    - Periodic background timer synchronization.
    - Broadcast Intent support for external sync triggers.
- **Notifications**: Get notified when a recording starts, when a timer is scheduled, or when a sync completes.
- **Modern UI**: Supports System, Light, and Dark modes with customizable accent colors.

## 🛠 Requirements

- **Android device** running 7.0 (Nougat) or higher.
- **TV-Browser Android App** (to use as a plugin).
- **Enigma2 Receiver** with OpenWebIf enabled and accessible via your network.

## 🚀 Getting Started

1. **Install**: Download the latest APK from the [Releases](https://github.com/Legandy/EnigmaBridge/releases) page.
2. **Configure Receiver**:
    - Open Enigma Bridge -> **Receiver Settings**.
    - Enter your receiver's IP address, Port, and Credentials (default is usually `root`).
    - Use **Test Connection** to verify.
3. **Sync Channels**:
    - Select your preferred Bouquet and tap **Sync Channel List**.
4. **Use in TV-Browser**:
    - Open TV-Browser, find a program, long-press it, and select **Schedule Recording** or **Advanced Scheduling**.

## 🏗 Development
This app was assisted by AI because I'm a newbie in Kotlin and Android Development.
I used Gemini in Android Studio as a tutor and reviewer of my code but I always have the last look.
I developed Engima Bridge for myself.
Open Source is great, so I'm sharing my work with you.

## 🤝 Contributing

Contributions are welcome! If you have a bug report or a feature request, please open an issue or submit a pull request.

## 💳 Support

If you find this app useful, consider supporting the development:
- [Ko-Fi](https://ko-fi.com/legandy)
- [GitHub Sponsors](https://github.com/sponsors/Legandy)

## ⚖️ License

This project is licensed under the **GPL-3.0 License** - see the [LICENSE](LICENSE) file for details.
