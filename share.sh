#!/usr/bin/env bash
set -e

PORT=8080
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/web"

echo "=========================================="
echo " AurumIQ — Shareable URL Tunnel Launcher "
echo "=========================================="

# Check if local server is already running
if ! lsof -i :$PORT > /dev/null 2>&1; then
  echo "[+] Starting local HTTP server on port $PORT (serving $DIR)..."
  python3 -m http.server $PORT --directory "$DIR" &
  SERVER_PID=$!
  sleep 1
else
  echo "[i] Server already listening on port $PORT."
fi

echo "[+] Starting Cloudflare Tunnel..."
cloudflared tunnel --url http://127.0.0.1:$PORT
