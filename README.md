# 🤖 JIN — AI Coding Super-Agent

> **Next-Generation Autonomous AI Coding Companion for Android & Termux**  
> **Developed by Samir Ahmmed** • [GitHub Profile](https://github.com/samir-ahmmed-tech) • [Repository](https://github.com/samir-ahmmed-tech/jin)

---

## 🌟 Overview

**JIN** is an autonomous AI coding super-agent engineered natively for Android and Termux. Inspired by the sleek, minimalist design of ChatGPT and powered by the world's most capable AI engines, JIN turns your mobile device into a full-fledged software engineering workstation.

With **100% privacy, local execution guards, and direct API key support**, JIN eliminates subscription barriers and allows developers to run cutting-edge models locally or through their own API keys.

---

## ✨ 12+ Key Features

### 1. 🎨 Sleek ChatGPT-Style UI
- Fully redesigned modern chat interface featuring a clean capsule input bar, smooth bubble styling, and refined dark (`#212121`) & light (`#FFFFFF`) themes.
- Quick model selector pill with instant family badges and quota visualization.

### 2. 🚀 Google Antigravity 2.0 Integration
- Native support for **Antigravity 2.0 Flash** and **Antigravity 2.0 Pro** (Gemini 2.5 architecture).
- Multi-turn autonomous tool calling, file editing, and project-aware trajectory reasoning.

### 3. 💻 OpenAI Codex & GPT-4o Engines
- Supercharged code generation, refactoring, and debugging powered by **OpenAI Codex**, **GPT-4o**, and **GPT-4o Mini**.

### 4. ⚡ DeepSeek Hermes & DeepSeek V3 / Coder
- High-performance, cost-effective reasoning powered by **DeepSeek Hermes**, **DeepSeek V3**, and **DeepSeek Coder**.

### 5. 🧠 Claude Code CLI & Anthropic Claude 3.7
- Deep support for Anthropic's **Claude 3.7 Sonnet** (hybrid thinking) and **Claude 3.5 Sonnet** with Claude Code agent architecture.

### 6. 🔑 Direct API Keys Vault (Zero Subscription Barrier)
- Built-in secure credentials vault in **Settings → API Keys & Providers**.
- Use your own API keys for **OpenAI, DeepSeek, Anthropic, Gemini, and OpenRouter** without needing paid platform subscriptions.
- Keys are encrypted and stored 100% locally on your Android device.

### 7. 🌐 Custom OpenAI-Compatible Proxies & 3rd-Party Endpoints
- Connect any OpenAI-compatible API endpoint: **Groq**, **Ollama**, **Together AI**, **vLLM**, or self-hosted local LLM servers.
- Configure custom base URLs, custom model identifiers, and test connectivity in 1 click.

### 8. 🛡️ 100% Privacy-First Architecture
- **Zero external telemetry**: Chat histories, prompts, tokens, and workspace files never leave your device.
- Local sandbox guards and explicit approval workflows before executing bash scripts or editing source code.

### 9. 📦 Native Android & Termux Terminal Integration
- Deep Termux PTY shell runner, persistent SSH bridge, and native filesystem access.
- Embedded Sora editor with syntax highlighting and file tree navigation.

### 10. 🔄 In-App Auto-Updater
- One-tap OTA updater checking releases directly from [samir-ahmmed-tech/jin](https://github.com/samir-ahmmed-tech/jin).
- Automatic release note diffing, version verification, and background APK downloading.

### 11. 📊 Live Token & Quota Monitoring
- Real-time token usage telemetry, rate limit tracking, and model quota HUD.

### 12. 🛠️ Autonomous Tool Ecosystem
- Built-in MCP (Model Context Protocol) servers, Google Plugins catalog, and custom skill scripts.

---

## 🧠 Supported Model Families

| Model Family | Highlights | Key Models |
|---|---|---|
| **Antigravity 2.0** | Agentic workflows, multi-step planning | `antigravity-2.0-flash`, `antigravity-2.0-pro` |
| **OpenAI** | Industry standard coding & reasoning | `openai-codex`, `gpt-4o`, `gpt-4o-mini` |
| **DeepSeek** | Ultra-fast math, logic & coding | `deepseek-hermes`, `deepseek-v3`, `deepseek-coder` |
| **Claude** | Deep architectural refactoring & hybrid reasoning | `claude-3-7-sonnet`, `claude-3-5-sonnet` |
| **Google Gemini** | Massive context window (1M+ tokens) | `gemini-2.5-flash`, `gemini-2.5-pro` |
| **Custom / 3rd Party** | Groq, Ollama, OpenRouter, Codestral, Qwen | `custom-api`, `codestral-latest`, `qwen-2.5-coder` |

---

## 🔑 How to Use Your Own API Keys

1. Open **JIN** on your device.
2. Tap the **Settings** icon (top right).
3. Select **API Keys & Providers**.
4. Enter your keys for any provider:
   - **OpenAI Key**: `sk-...`
   - **DeepSeek Key**: `sk-...`
   - **Anthropic Claude Key**: `sk-ant-...`
   - **Google Gemini Key**: `AIza...`
   - **OpenRouter Key**: `sk-or-...`
   - **Custom Base URL**: e.g., `https://api.groq.com/openai/v1` or `http://127.0.0.1:11434/v1`
5. Tap **Test Connection** to verify your key instantly.
6. Tap **Save All API Keys**.
7. In the chat screen, tap the model pill and select your preferred model!

---

## 🚀 Building the APK via GitHub Actions

This repository is configured with automated CI/CD via GitHub Actions (`.github/workflows/build-apk.yml`). Every push to the `main` branch automatically builds signed release APKs using **Java 21** and **Go 1.22**.

### Download Pre-built APKs:
1. Navigate to the [GitHub Actions tab](https://github.com/samir-ahmmed-tech/jin/actions).
2. Click on the latest workflow run: **Build JIN Release APK**.
3. Under **Artifacts**, download `JIN-Termux-Release-APK`.
4. Install `jin-termux-v2.0.0-release.apk` on your device.

---

## 💻 Manual Push to GitHub

To push changes to GitHub from Termux or your terminal:

```bash
cd ~/projects/jin
./push_to_github.sh
```

Or manually:

```bash
git add .
git commit -m "feat: complete JIN v2.0.0 ChatGPT UI, Antigravity 2.0, Codex, DeepSeek, and API keys"
git push origin main
```

---

## 👨‍💻 Developer & Credits

- **Lead Developer**: **Samir Ahmmed**
- **GitHub**: [@samir-ahmmed-tech](https://github.com/samir-ahmmed-tech)
- **Repository**: [https://github.com/samir-ahmmed-tech/jin](https://github.com/samir-ahmmed-tech/jin)
- **License**: MIT License
