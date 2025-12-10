#!/usr/bin/env bash
set -e
AMOUNT=${1:-50}
for i in $(seq 1 $AMOUNT); do
  curl -s -X POST "http://localhost:8080/api/orders/start?amount=$(( (RANDOM % 100) + 1 ))" > /dev/null &
  sleep 0.05
done
echo "Fired $AMOUNT orders."
