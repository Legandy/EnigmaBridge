# Enigma Bridge

**Enigma Bridge** is a plugin for
the [TV-Browser](https://www.tvbrowser-app.de/index.php?setlang=en) app to quickly schedule a
recording on your Enigma2 device via context menu.

## ✨ Features

- **TV-Browser Plugin**: Schedule recordings directly from the TV-Browser context menu.
- **Advanced Scheduling**: Fine-tune timers with custom start/end padding, repeat rules (Daily,
  Weekly), and after-event actions (Standby, Deep Standby).
- **Timer Management**: View, edit and delete timers on your receiver from the app.
- **Syncing**:
    - Channel list synchronization from your favorite Bouquet.
    - Periodic background timer synchronization.
    - Broadcast Intent support for external sync triggers.
- **Notifications**: Get notified when a recording starts, when a timer is scheduled, or when a sync
  completes.
- **Modern UI**: Supports System, Light, and Dark modes with customizable accent colors.

## 🛠 Requirements

- **Android device** running 7.0 (Nougat) or higher.
- **TV-Browser Android App** (to use as a plugin).
- **Enigma2 Receiver** with OpenWebIf enabled and accessible via your network.

## 🚀 Getting Started

1. **Install**: Download the latest APK from
   the [Releases](https://github.com/Legandy/EnigmaBridge/releases) page.
2. **Configure Receiver**:
    - Open Enigma Bridge -> **Receiver Settings**.
    - Enter your receiver's IP address, Port, and Credentials (default is usually `root`).
    - Use **Test Connection** to verify.
3. **Sync Channels**:
    - Select your preferred Bouquet and tap **Sync Channel List**.
4. **Use in TV-Browser**:
    - Open TV-Browser, find a program, long-press it, and select **Schedule Recording** or *
      *Advanced Scheduling**.

## 🏗 Build

Requires JDK 17

1. Clone the repository:
   git clone https://github.com/legandy/EnigmaBridge.git

2. Navigate to the directory:
   cd EnigmaBridge

3. Build the release APK:
   ./gradlew :app:assembleRelease

The generated APK will be available in `app/build/outputs/apk/release/`.

## 🏗 Development

This app was created by myself with AI-assisted programming.<br>
I'm a newbie in Kotlin and Android Development.<br>
I used Gemini in Android Studio mostly as a tutor and reviewer, but AI-generated code was used
too.<br>
I encourage the ethical use of AI, but AI-generated code must be reviewed by a human with an
understanding of programming languages.<br>
But without AI-assisted programming I wouldn't have felt encouraged enough to learn Kotlin and
Android Development on a practical learning basis.<br>
Includes code of the [SimpleMarkerPlugin](https://github.com/ds10git/tvbrowsersimplemarkerplugin)
for the TV Browser to understand how the plugin system works and to mark a timer inside the TV
Browser.<br>
Enigma communication logic was referenced
from [EnigmaDroid](https://github.com/deprec8/EnigmaDroid)<br>
I developed Enigma Bridge for my own use case.<br>
Open source is great, so maybe this app is useful for you too.<br>

## 💖 Special Thanks

Without the great [TV Browser](https://www.tvbrowser-app.de/index.php?id=download) this app wouldn't
exist and wouldn't be really useful.<br>
Of course also special thanks to [F-Droid](https://f-droid.org/en/)
and [IzzyOnDroid](https://izzyondroid.org/) for creating platforms for open source apps.

## 🤝 Contributing

Contributions are welcome! If you have a bug report or a feature request, please open an issue or
submit a pull request.

## 💳 Support

If you find this app useful, consider supporting the development:

- [Ko-Fi](https://ko-fi.com/legandy)
- [GitHub Sponsors](https://github.com/sponsors/Legandy)

## ⚖️ License

This project is licensed under the **GPL-3.0 License** - see the [LICENSE](LICENSE) file for
details.
