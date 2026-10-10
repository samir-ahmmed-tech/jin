# 🤖 JIN v2.0.2 — AI Coding Super-Agent

> **Next-Generation Autonomous AI Coding Companion for Android & Termux**  
> **Developed by Samir Ahmmed** • [GitHub Profile](https://github.com/samir-ahmmed-tech) • [Repository](https://github.com/samir-ahmmed-tech/jin)

<p align="center">
  <img src="repo_icon.png" alt="JIN AI Mascot" width="160" height="160" style="border-radius: 28px;" />
</p>

---

## 🌟 Overview

**JIN** is an autonomous AI coding super-agent engineered natively for Android and Termux. Inspired by the sleek, minimalist design of ChatGPT and powered by the world's most capable AI engines, JIN turns your mobile device into a full-fledged software engineering workstation.

With **100% privacy, local execution guards, and direct API key support**, JIN eliminates subscription barriers and allows developers to run cutting-edge models locally or through their own API keys.

---

## ✨ 12+ Key Features

### 1. 🤖 Full 5-Agent Architecture
- **Antigravity 2.0**: Gemini 2.5 Flash & Pro models through Google Antigravity hub.
- **Claude Code CLI**: Full Anthropic Claude 3.7 & 3.5 Sonnet hybrid reasoning agent.
- **DeepSeek Harness**: Direct streaming with DeepSeek V3, DeepSeek Coder, and Hermes.
- **OpenAI Codex**: GPT-4o, GPT-4o Mini, and Codex coder agents.
- **OpenRouter Universal**: Access to Llama 3.3, Codestral, Qwen 2.5, and thousands of models.

### 2. 🎨 Brand-New 3D Glossy Robot Mascot
- Iconic 3D robot astronaut app icon integrated across launcher, splash screen, onboarding, and in-app branding.
- True AMOLED Pitch Black theme (`#0B0C0E`) with seamless adaptive icon blending.

### 3. ⚡ Direct Token Streaming & Multi-Turn Memory
- Instant real-time token streaming for all direct API models with full conversational context.
- Persistent local chat history saved safely on-device in JSON format.

### 4. 🔑 Direct API Keys Vault (Zero Subscription Barrier)
- Built-in secure credentials vault in **Settings → API Keys & Providers**.
- Use your own API keys for **OpenAI, DeepSeek, Anthropic, Gemini, and OpenRouter** without paid subscriptions.
- Keys are encrypted and stored 100% locally on your Android device.

### 5. 🌐 Custom OpenAI-Compatible Proxies & 3rd-Party Endpoints
- Connect any OpenAI-compatible API endpoint: **Groq**, **Ollama**, **Together AI**, **vLLM**, or local LLM servers.
- Configure custom base URLs, custom model identifiers, and test connectivity in 1 click.

### 6. 🛡️ 100% Privacy-First Architecture
- **Zero external telemetry**: Chat histories, prompts, tokens, and workspace files never leave your device.
- Local sandbox guards and explicit approval workflows before executing bash scripts or editing source code.

### 7. 📦 Native Android & Termux Terminal Integration
- Deep Termux PTY shell runner, persistent SSH bridge, and native filesystem access.
- Embedded Sora editor with syntax highlighting and file tree navigation.

### 8. 🔄 In-App Auto-Updater
- One-tap OTA updater checking releases directly from [samir-ahmmed-tech/jin](https://github.com/samir-ahmmed-tech/jin).
- Automatic release note diffing, version verification, and background APK downloading.

### 9. 📊 Live Token & Quota Monitoring
- Real-time token usage telemetry, rate limit tracking, and model quota HUD.

### 10. 🛠️ Autonomous Tool Ecosystem
- Built-in MCP (Model Context Protocol) servers, Google Plugins catalog, and custom skill scripts.

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
2. Download `jin-termux-v2.0.2-release.apk`.
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
