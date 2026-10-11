#!/usr/bin/env bash
set -e

# =============================================================================
# JIN 1-Click Push to GitHub & APK Trigger Script
# Developed by Samir Ahmmed (https://github.com/samir-ahmmed-tech/jin)
# =============================================================================

REPO_OWNER="samir-ahmmed-tech"
REPO_NAME="jin"
REPO_URL="https://github.com/${REPO_OWNER}/${REPO_NAME}.git"

echo "================================================================="
echo "🚀 JIN - Push Changes to GitHub & Trigger APK Build"
echo "👨‍💻 Developer: Samir Ahmmed"
echo "📦 Repository: ${REPO_OWNER}/${REPO_NAME}"
echo "================================================================="

# Set git user info if not configured
git config user.name "Samir Ahmmed"
git config user.email "samir-ahmmed-tech@users.noreply.github.com"

# Check if token was provided as argument or environment variable
GH_TOKEN="${1:-${GITHUB_TOKEN:-}}"

if [ -z "$GH_TOKEN" ]; then
    echo ""
    echo "🔑 Please enter your GitHub Personal Access Token (PAT):"
    echo "   (Note: Password authentication is disabled by GitHub, you need a PAT with 'repo' scope)"
    read -r -s -p "GitHub Token: " GH_TOKEN
    echo ""
fi

if [ -z "$GH_TOKEN" ]; then
    echo "❌ Error: GitHub Token cannot be empty. Aborted."
    exit 1
fi

echo "📦 Staging changes..."
git add -A

if git diff --staged --quiet; then
    echo "ℹ️  No new changes to commit."
else
    COMMIT_MSG="${2:-feat: JIN v2.1.0 - Enterprise On-Device Encryption, Privacy Policy, Mobile Workspace Folder Creation, 4MB High-Speed I/O & Unlimited PC Power Suite}"
    git commit -m "$COMMIT_MSG"
fi

echo "🔄 Pushing to https://github.com/${REPO_OWNER}/${REPO_NAME} on branch main..."
git push "https://${GH_TOKEN}@github.com/${REPO_OWNER}/${REPO_NAME}.git" main

echo ""
echo "================================================================="
echo "🎉 SUCCESS! Changes successfully pushed to GitHub!"
echo "================================================================="
echo ""
echo "🤖 GitHub Actions is now building your Release APK:"
echo "   👉 https://github.com/${REPO_OWNER}/${REPO_NAME}/actions"
echo ""
echo "📥 Once the workflow finishes (approx. 2-3 mins):"
echo "   1. Open the latest workflow run: 'Build JIN Release APK'"
echo "   2. Scroll down to 'Artifacts'"
echo "   3. Download and install 'JIN-Termux-Release-APK'!"
echo "================================================================="
