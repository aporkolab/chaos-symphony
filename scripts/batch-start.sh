#!/usr/bin/env bash
set -euo pipefail
for i in $(seq 1 20); do
  amt=$(( (RANDOM % 400) + 1 ))
  curl -s "http://localhost:8081/api/orders/start?amount=${amt}" >/dev/null
done
for i in $(seq 1 5); do
  curl -s "http://localhost:8081/api/orders/BREAK-ME/start?amount=1" >/dev/null
done
echo "Batch sent."
