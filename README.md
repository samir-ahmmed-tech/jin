# JIN - AI Coding Super-Agent App

![JIN Logo](assets/icons/placeholder.png)

## Overview
JIN is a powerful AI coding assistant designed to help developers write, debug, and explain code efficiently. It integrates multiple AI models, including Gemini, Claude, DeepSeek, and OpenAI, providing a super-agent experience right at your fingertips.

## Features
- **Multi-Model Support:** Connect to cutting-edge AI models (Gemini 2.5, Claude 3.7, DeepSeek V3/R1, GPT-4o).
- **Code Assistance:** Write, debug, and refactor code seamlessly.
- **Beautiful UI:** A stunning dark mode interface crafted with Flutter.
- **Secure Storage:** Keep your API keys safely stored on your device.
- **Markdown & Highlighting:** View responses in formatted markdown with syntax highlighting.

## Setup Instructions

1. Clone the repository:
   ```sh
   git clone https://github.com/your-username/jin.git
   cd jin
   ```

2. Install dependencies:
   ```sh
   flutter pub get
   ```

3. Run the app:
   ```sh
   flutter run
   ```

4. **Add API Keys:**
   - When you launch the app, go to Settings.
   - Enter your API keys for the providers you want to use (e.g., Gemini, OpenAI).
   - Keys are stored securely on your device using `flutter_secure_storage`.

## Build Instructions

### Android APK
To build the release APK for Android:
```sh
flutter build apk --release
```
The APK will be located at `build/app/outputs/flutter-apk/app-release.apk`.

### GitHub Actions (Automated Build)
This repository includes a GitHub Actions workflow that automatically builds the Android APK on every push to the `main` branch.

## Screenshots
*(Screenshots coming soon...)*
