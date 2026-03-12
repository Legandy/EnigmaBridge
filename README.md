# Enigma Bridge

**Enigma Bridge** is an Android application and plugin for the [TV-Browser](https://www.tvbrowser.org/) app. it allows you to quickly schedule recordings and manage timers on your Enigma2-based receiver (e.g., VU+, Dreambox) directly from your smartphone.

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

The project is currently undergoing modernization.
- [ ] Migration to **Jetpack Compose**.
- [ ] Transitioning to **Jetpack DataStore**.
- [ ] F-Droid release preparation.

## 🤝 Contributing

Contributions are welcome! If you have a bug report or a feature request, please open an issue or submit a pull request.

## 💳 Support

If you find this app useful, consider supporting the development:
- [Ko-Fi](https://ko-fi.com/legandy)
- [GitHub Sponsors](https://github.com/sponsors/Legandy)

## ⚖️ License

This project is licensed under the **GPL-3.0 License** - see the [LICENSE](LICENSE) file for details.