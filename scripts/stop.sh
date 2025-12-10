#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."
(cd deployment && docker compose down)
