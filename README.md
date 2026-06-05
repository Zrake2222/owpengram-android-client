# 🤖 OwpenGram Android

**Android client for OwpenGram server - Telegram-compatible messaging.**

![android-demo](https://github.com/user-attachments/assets/6728fd39-724f-4bfc-8a05-7c98062fe619)

---

## ✨ What is OwpenGram Android?

OwpenGram Android is an Android messaging client compatible with the [OwpenGram Server](https://github.com/owpengram/owpengram-server). Based on Telegram for Android, it provides a familiar interface for connecting to your own self-hosted messaging server.

## 🔗 Compatibility

This client is designed to work with the [OwpenGram Server](https://github.com/owpengram/owpengram-server). Simply configure the client to connect to your OwpenGram server instance and start messaging!

## ⚙️ Server Configuration

To change the server address, replace all instances of the server IP address in `TMessagesProj/jni/tgnet/ConnectionsManager.cpp` with your server's IP address or domain name.

## 📚 Build Instructions & Documentation

### Interactive build (Windows)

Double-click or run from any terminal:

```bat
build-android.cmd
```

The script will guide you through server IP, API credentials, submodules, SDK setup, and Gradle build. Settings are saved to `.owpengram-build.local.json` (gitignored).

Requirements: **JDK 17**, **Android SDK** (API 35, build-tools 35.0.0, NDK 21.4.7075529), **Git**.

For manual steps and other platforms, see the original [Telegram for Android repository](https://github.com/DrKLO/Telegram).

## 💬 Community

- 📢 **Telegram Channel:** [@owpengram](https://t.me/owpengram)
- 💬 **Telegram Chat:** [Join the discussion](https://t.me/+sVB6Ymv70jEwNTAy)

## 📄 License

[GPL-2.0](LICENSE)

---

## ⭐ Give us a Star!

If OwpenGram Android helps you, consider giving us a star on GitHub!
