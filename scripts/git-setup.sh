#!/bin/bash
# Git setup helper for first push

echo "🚀 CSK4 Git Setup"
echo "=================="

read -p "GitHub Username: " USERNAME
read -p "GitHub Email: " EMAIL
read -p "Repo Name (default: csk4-project): " REPO
REPO=${REPO:-csk4-project}

cd "$(dirname "$0")/.."

git init
git config user.name "$USERNAME"
git config user.email "$EMAIL"

git add .
git status

echo ""
read -p "Commit now? (y/n): " CONFIRM
if [ "$CONFIRM" = "y" ] || [ "$CONFIRM" = "Y" ]; then
    git commit -m "Initial commit - CSK4 PRO complete"
    git branch -M main
    git remote add origin "https://github.com/$USERNAME/$REPO.git"
    echo ""
    echo "✅ Ready! Now run:"
    echo "   git push -u origin main"
    echo "🔑 Use Personal Access Token as password"
else
    echo "Skipped commit. Run git commands manually."
fi
