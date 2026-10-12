#!/data/data/com.termux/files/usr/bin/bash
# ==============================================================================
# JIN Multi-Agent CLI Suite Installer
# Developed by Samir Ahmmed (https://github.com/samir-ahmmed-tech/jin)
#
# Sets up native Termux executable CLI commands for:
# - opencode (OpenCode AI Terminal Assistant & Zen Gateway)
# - deepseek (DeepSeek Autonomous Coding CLI)
# - codex    (OpenAI Codex Autonomous Coding CLI)
# - openrouter (OpenRouter Universal Model CLI)
# ==============================================================================

set -e

PREFIX_BIN="/data/data/com.termux/files/usr/bin"
if [ ! -d "$PREFIX_BIN" ]; then
    PREFIX_BIN="$HOME/../usr/bin"
fi

if [ ! -d "$PREFIX_BIN" ]; then
    PREFIX_BIN="$HOME/.local/bin"
    mkdir -p "$PREFIX_BIN"
fi

echo "Installing JIN Multi-Agent CLI Suite into $PREFIX_BIN..."

# 1. OpenCode AI CLI
cat << 'EOF' > "$PREFIX_BIN/opencode"
#!/data/data/com.termux/files/usr/bin/bash
# OpenCode AI Terminal Assistant
# Developed by Samir Ahmmed (https://github.com/samir-ahmmed-tech/jin)

API_KEY="${OPENCODE_API_KEY:-free-opencode-zen}"
BASE_URL="${OPENCODE_BASE_URL:-https://opencode.ai/zen/v1}"
MODEL="${OPENCODE_MODEL:-opencode-zen-coder}"

if [ -z "$1" ]; then
    echo "Usage: opencode <prompt>"
    echo "Options: env vars OPENCODE_API_KEY, OPENCODE_BASE_URL, OPENCODE_MODEL"
    exit 1
fi

PROMPT="$*"
echo "🤖 [OpenCode AI] Querying $MODEL..."
curl -s -N -X POST "$BASE_URL/chat/completions" \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d "{\"model\": \"$MODEL\", \"stream\": true, \"messages\": [{\"role\": \"user\", \"content\": $(jq -R -s . <<< "$PROMPT")}]}" | \
  grep '^data:' | sed 's/^data: //g' | jq -r -j '.choices[0].delta.content // empty'
echo ""
EOF
chmod +x "$PREFIX_BIN/opencode"

# 2. DeepSeek CLI
cat << 'EOF' > "$PREFIX_BIN/deepseek"
#!/data/data/com.termux/files/usr/bin/bash
# DeepSeek Coding CLI
# Developed by Samir Ahmmed (https://github.com/samir-ahmmed-tech/jin)

API_KEY="${DEEPSEEK_API_KEY}"
BASE_URL="https://api.deepseek.com/v1"
MODEL="${DEEPSEEK_MODEL:-deepseek-chat}"

if [ -z "$API_KEY" ]; then
    echo "Error: DEEPSEEK_API_KEY environment variable not set."
    exit 1
fi

if [ -z "$1" ]; then
    echo "Usage: deepseek <prompt>"
    exit 1
fi

PROMPT="$*"
echo "⚡ [DeepSeek] Processing with $MODEL..."
curl -s -N -X POST "$BASE_URL/chat/completions" \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d "{\"model\": \"$MODEL\", \"stream\": true, \"messages\": [{\"role\": \"user\", \"content\": $(jq -R -s . <<< "$PROMPT")}]}" | \
  grep '^data:' | sed 's/^data: //g' | jq -r -j '.choices[0].delta.content // empty'
echo ""
EOF
chmod +x "$PREFIX_BIN/deepseek"

# 3. Codex CLI
cat << 'EOF' > "$PREFIX_BIN/codex"
#!/data/data/com.termux/files/usr/bin/bash
# OpenAI Codex CLI
# Developed by Samir Ahmmed (https://github.com/samir-ahmmed-tech/jin)

API_KEY="${OPENAI_API_KEY}"
BASE_URL="https://api.openai.com/v1"
MODEL="${CODEX_MODEL:-gpt-4o}"

if [ -z "$API_KEY" ]; then
    echo "Error: OPENAI_API_KEY environment variable not set."
    exit 1
fi

if [ -z "$1" ]; then
    echo "Usage: codex <prompt>"
    exit 1
fi

PROMPT="$*"
echo "🧠 [Codex] Running $MODEL..."
curl -s -N -X POST "$BASE_URL/chat/completions" \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d "{\"model\": \"$MODEL\", \"stream\": true, \"messages\": [{\"role\": \"user\", \"content\": $(jq -R -s . <<< "$PROMPT")}]}" | \
  grep '^data:' | sed 's/^data: //g' | jq -r -j '.choices[0].delta.content // empty'
echo ""
EOF
chmod +x "$PREFIX_BIN/codex"

# 4. OpenRouter CLI
cat << 'EOF' > "$PREFIX_BIN/openrouter"
#!/data/data/com.termux/files/usr/bin/bash
# OpenRouter Universal CLI
# Developed by Samir Ahmmed (https://github.com/samir-ahmmed-tech/jin)

API_KEY="${OPENROUTER_API_KEY}"
BASE_URL="https://openrouter.ai/api/v1"
MODEL="${OPENROUTER_MODEL:-deepseek/deepseek-r1}"

if [ -z "$API_KEY" ]; then
    echo "Error: OPENROUTER_API_KEY environment variable not set."
    exit 1
fi

if [ -z "$1" ]; then
    echo "Usage: openrouter <prompt>"
    exit 1
fi

PROMPT="$*"
echo "🌐 [OpenRouter] Querying $MODEL..."
curl -s -N -X POST "$BASE_URL/chat/completions" \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d "{\"model\": \"$MODEL\", \"stream\": true, \"messages\": [{\"role\": \"user\", \"content\": $(jq -R -s . <<< "$PROMPT")}]}" | \
  grep '^data:' | sed 's/^data: //g' | jq -r -j '.choices[0].delta.content // empty'
echo ""
EOF
chmod +x "$PREFIX_BIN/openrouter"

echo "✅ JIN Multi-Agent CLI suite successfully installed!"
echo "Commands available: opencode, deepseek, codex, openrouter"
