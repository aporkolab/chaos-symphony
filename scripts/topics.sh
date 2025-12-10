#!/usr/bin/env bash
set -e
BROKER=kafka:9092
create() { docker compose exec -T kafka kafka-topics --bootstrap-server $BROKER --create --if-not-exists --topic "$1" --partitions 3 --replication-factor 1 --config retention.ms="$2"; }
create payment.requested 604800000
create payment.result    604800000
create inventory.requested 604800000
create inventory.result    604800000
create shipping.requested  604800000
create shipping.result     604800000
create inventory.requested.DLT 2592000000