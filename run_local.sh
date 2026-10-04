#!/usr/bin/env bash
set -e

PORT=8080
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/web"

echo "=========================================="
echo "    AurumIQ — Localhost Web Server        "
echo "=========================================="

if ! lsof -i :$PORT > /dev/null 2>&1; then
  echo "[+] Starting local HTTP server on port $PORT..."
  python3 -m http.server $PORT --directory "$DIR" &
  sleep 1
else
  echo "[i] Server is already active on port $PORT."
fi

echo "[+] Opening http://localhost:$PORT in your default browser..."
open "http://localhost:$PORT"
