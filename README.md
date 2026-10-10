# 🤖 JIN v2.0.3 — AI Coding Super-Agent

> **Next-Generation Autonomous AI Coding Companion for Android & Termux**  
> **Developed by Samir Ahmmed** • [GitHub Profile](https://github.com/samir-ahmmed-tech) • [Repository](https://github.com/samir-ahmmed-tech/jin)

<p align="center">
  <img src="repo_icon.png" alt="JIN AI Mascot" width="160" height="160" style="border-radius: 28px;" />
</p>

---

## 🌟 Overview

**JIN** is an autonomous AI coding super-agent engineered natively for Android and Termux. Inspired by the sleek, minimalist design of modern AI IDEs and powered by the world's most capable AI engines, JIN turns your mobile device into a full-fledged software engineering workstation.

With **100% privacy, local execution guards, swarm multi-agent concurrency, and direct API key support**, JIN eliminates subscription barriers and allows developers to run cutting-edge models locally or through their own API keys.

---

## ✨ Key Features in v2.0.3

### 1. 🚀 Swarm Multi-Agent Concurrency (1 to 6 Agents)
- Run multiple autonomous agents concurrently! Select **Antigravity + DeepSeek + Claude + Codex + OpenRouter** all at once without stopping Antigravity.
- Configurable concurrency limit (1 to 6 concurrent running agents) with dynamic preset modes: Solo, Dual, Trio, and Full Swarm.

### 2. ⚡ Ready-Only Model Picker & Instant Unlock
- Chat box only displays providers and models that are ready and configured.
- Unconfigured models (OpenAI, DeepSeek, Claude, OpenRouter) display clean "+ Add API Key" cards with an instant unlock dialog right in the picker sheet.

### 3. 🏎️ Supercharged 200–300 MB/s Network Engine
- 2MB buffered streams for blazingly fast downloads and uploads.
- OkHttpClient connection pooling (16 concurrent idle connections) and dispatcher optimized for maximum throughput.

### 4. 📱 Global UI Scale Customization (25% to 200%)
- Full granular zoom control across the entire app via Compose `LocalDensity`.
- Quick-select presets: 50%, 75%, 100%, 125%, 150%, 200% for phone, foldable, and tablet screens.

### 5. 🔄 Automatic Model Quota Failover
- Real-time automatic failover on HTTP 429 quota exhaustion.
- Instantly switches to the next configured fallback model seamlessly with an inline notice so coding tasks never freeze.

### 6. ⚙️ Hardware Performance Tuning & Safeguards
- Deep hardware diagnostics HUD: CPU cores, RAM, and runtime heap limits.
- Configurable CPU thread limiters, memory cache sliders, and performance profiles (Turbo, Balanced, Power Saver, Custom).

### 7. 🧩 Dynamic Community Skills & MCP Installer
- 1-click GitHub (`user/repo` or git URL), npm, or raw URL skill and MCP server installer.
- 8 curated community presets ready to install with a single tap.

### 8. 🖥️ Desktop / PC IDE Super-Features
- **Quick Open File Palette** (`Ctrl+P` modal): Instant fuzzy search across workspace files.
- **Scratchpad & Notes**: Built-in notepad for code snippets and quick ideas right inside the IDE.
- Full **JIN IDE** branding and status telemetry.

### 9. 🔇 Quiet Startup & Clean Logs
- Suppressed noisy startup toasts and background notification clutter for a smooth, distraction-free environment.

### 10. 🎨 Unified 3D Robot Astronaut Mascot & JIN Branding
- Unified glossy 3D robot astronaut icon matching across all launcher mipmaps, adaptive round icons, splash screen, and notifications.
- Consistent **JIN** name everywhere across the OS.

---

## 🧠 Supported Model Families

| Model Family | Highlights | Key Models |
|---|---|---|
| **Antigravity 2.0** | Agentic workflows, multi-step planning | `antigravity-2.0-flash`, `antigravity-2.0-pro` |
| **OpenAI** | Industry standard coding & reasoning | `openai-codex`, `gpt-4o`, `gpt-4o-mini` |
| **DeepSeek** | Ultra-fast math, logic & coding | `deepseek-hermes`, `deepseek-v3`, `deepseek-coder` |
| **Claude** | Deep architectural refactoring & hybrid reasoning | `claude-3-7-sonnet`, `claude-3-5-sonnet` |
| **Google Gemini** | Massive context window (2M+ tokens) | `gemini-2.5-flash`, `gemini-2.5-pro` |
| **OpenRouter & Custom** | Groq, Ollama, OpenRouter, Codestral, Qwen | `meta-llama/llama-3.3-70b-instruct:free`, `codestral-2501`, `qwen-2.5-coder` |

---

## 🔑 How to Use Your Own API Keys

1. Open **JIN** on your device.
2. Tap the **Settings** icon (top right).
3. Select **API Keys & Providers**.
4. Enter your keys for any provider:
   - **DeepSeek Key**: `sk-...`
   - **OpenAI Key**: `sk-...`
   - **OpenRouter Key**: `sk-or-...`
   - **Anthropic Claude Key**: `sk-ant-...`
   - **Google Gemini Key**: `AIza...`
   - **Custom Base URL**: e.g., `https://api.groq.com/openai/v1` or `http://127.0.0.1:11434/v1`
5. Tap **Test Connection** to verify your key instantly.
6. Tap **Save All API Keys**.
7. In the chat screen, select your preferred agent or model and start chatting!

---

## 🚀 Building the APK via GitHub Actions

This repository is configured with automated CI/CD via GitHub Actions (`.github/workflows/build-apk.yml`). Every push to the `main` branch automatically builds signed release APKs using **Java 21** and **Go 1.22**.

### Download Pre-built APKs:
1. Navigate to the [GitHub Actions tab](https://github.com/samir-ahmmed-tech/jin/actions) or [Releases](https://github.com/samir-ahmmed-tech/jin/releases).
2. Download `jin-termux-v2.0.3-release.apk`.
3. Install and launch on your Android device!

---

## 💻 1-Click Push to GitHub

To push changes to GitHub from Termux or your terminal:

```bash
cd ~/projects/jin
./push_to_github.sh <YOUR_GITHUB_PAT_TOKEN>
```

---

## 👨‍💻 Developer & Credits

- **Lead Developer**: **Samir Ahmmed**
- **GitHub**: [@samir-ahmmed-tech](https://github.com/samir-ahmmed-tech)
- **Repository**: [https://github.com/samir-ahmmed-tech/jin](https://github.com/samir-ahmmed-tech/jin)
- **License**: MIT License
