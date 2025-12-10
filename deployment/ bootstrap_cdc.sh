#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-docker}"   # docker | local

### === PARAMÉTEREK (állítsd, ha kell) ===
# Postgres
PG_HOST_LOCAL="localhost"
PG_PORT_LOCAL="5432"
PG_SUPERUSER_LOCAL="postgres"

# Docker konténer neve (ha docker mód)
PG_DOCKER_CONTAINER="postgres"
PG_SUPERUSER_DOCKER="postgres"

DB_NAME="orders"
APP_USER="app"
APP_PASS="pass"

DBZ_USER="debezium"
DBZ_PASS="dbz"

OUTBOX_TABLE="public.order_outbox"
PUBLICATION_NAME="dbz_publication"
REPL_SLOT_NAME="orders_slot"

# Debezium/Kafka Connect
CONNECT_URL="http://localhost:8083"
CONNECTOR_NAME="orders-connector"

# A Connect konténerből a host Postgres elérhetősége:
PG_HOST_FROM_CONNECT="host.docker.internal"
PG_PORT_FROM_CONNECT="5432"

### === SEGÉD FÜGGVÉNYEK ===
psql_docker() {
  docker exec -i "$PG_DOCKER_CONTAINER" psql -v ON_ERROR_STOP=1 -U "$PG_SUPERUSER_DOCKER" "$@"
}

psql_local() {
  psql -v ON_ERROR_STOP=1 -h "$PG_HOST_LOCAL" -p "$PG_PORT_LOCAL" -U "$PG_SUPERUSER_LOCAL" "$@"
}

curl_jq() {
  if command -v jq >/dev/null 2>&1; then
    curl -s "$@" | jq .
  else
    curl -s "$@"
  fi
}

### === 1) ROLE/DB létrehozás, jogosultságok, outbox tábla ===
SQL_BOOTSTRAP=$(cat <<'EOSQL'
DO $$
BEGIN
   IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'app_user') THEN
      EXECUTE format('CREATE ROLE %I WITH LOGIN PASSWORD %L', :'app_user', :'app_pass');
   END IF;
END$$;

DO $$
BEGIN
   IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'dbz_user') THEN
      EXECUTE format('CREATE ROLE %I WITH LOGIN REPLICATION PASSWORD %L', :'dbz_user', :'dbz_pass');
   ELSE
      EXECUTE format('ALTER ROLE %I WITH REPLICATION', :'dbz_user');
   END IF;
END$$;

DO $$
BEGIN
   IF NOT EXISTS (SELECT FROM pg_database WHERE datname = :'db_name') THEN
      EXECUTE format('CREATE DATABASE %I OWNER %I', :'db_name', :'app_user');
   END IF;
END$$;
EOSQL
)

SQL_DB_SETUP=$(cat <<'EOSQL'
-- kapcsolódás a céldatabázishoz
\c :db_name

GRANT CONNECT ON DATABASE :db_name TO :dbz_user;
GRANT CONNECT ON DATABASE :db_name TO :app_user;

GRANT USAGE ON SCHEMA public TO :dbz_user;
GRANT USAGE ON SCHEMA public TO :app_user;
GRANT CREATE ON SCHEMA public TO :app_user;

-- DEFAULT PRIVS a jövőbeli táblákra
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO :dbz_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :app_user;

-- OUTBOX tábla
CREATE TABLE IF NOT EXISTS public.order_outbox (
  id            UUID PRIMARY KEY,
  aggregate_id  UUID        NOT NULL,
  occurred_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  payload       TEXT        NOT NULL,
  published     BOOLEAN     NOT NULL DEFAULT false,
  type          VARCHAR(255) NOT NULL
);

-- Outbox SMT-hez ajánlott oszlopok:
ALTER TABLE public.order_outbox
  ADD COLUMN IF NOT EXISTS aggregate_type VARCHAR(255) NOT NULL DEFAULT 'Order';

-- ha saját epoch millis timestamp kellene később:
-- ALTER TABLE public.order_outbox ADD COLUMN IF NOT EXISTS occurred_at_ms BIGINT;

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.order_outbox TO :app_user;
GRANT SELECT ON TABLE public.order_outbox TO :dbz_user;
EOSQL
)

if [[ "$MODE" == "docker" ]]; then
  echo "==> Bootstrap (docker exec $PG_DOCKER_CONTAINER)..."
  psql_docker -d postgres -v app_user="$APP_USER" -v app_pass="$APP_PASS" -v dbz_user="$DBZ_USER" -v dbz_pass="$DBZ_PASS" -v db_name="$DB_NAME" -c "$SQL_BOOTSTRAP"
  psql_docker -d postgres -v app_user="$APP_USER" -v dbz_user="$DBZ_USER" -v db_name="$DB_NAME" -c "$SQL_DB_SETUP"
else
  echo "==> Bootstrap (local psql $PG_HOST_LOCAL:$PG_PORT_LOCAL)..."
  psql_local -d postgres -v app_user="$APP_USER" -v app_pass="$APP_PASS" -v dbz_user="$DBZ_USER" -v dbz_pass="$DBZ_PASS" -v db_name="$DB_NAME" -c "$SQL_BOOTSTRAP"
  psql_local -d postgres -v app_user="$APP_USER" -v dbz_user="$DBZ_USER" -v db_name="$DB_NAME" -c "$SQL_DB_SETUP"
fi

### === 2) WAL logical + paraméterek ===
SQL_WAL=$(cat <<'EOSQL'
ALTER SYSTEM SET wal_level = 'logical';
ALTER SYSTEM SET max_wal_senders = 10;
ALTER SYSTEM SET max_replication_slots = 10;
EOSQL
)

if [[ "$MODE" == "docker" ]]; then
  echo "==> Beállítom a WAL logical-t (docker)..."
  psql_docker -d postgres -c "$SQL_WAL"
  echo "==> Restartolom a Postgres konténert..."
  docker restart "$PG_DOCKER_CONTAINER" >/dev/null
  sleep 2
  echo "=> wal_level:"
  psql_docker -d "$DB_NAME" -c "SHOW wal_level;"
else
  echo "==> Beállítom a WAL logical-t (local)..."
  psql_local -d postgres -c "$SQL_WAL"
  echo "==> FONTOS: helyi Postgres restart szükséges!"
  echo "   - Postgres.app: Stop → Start"
  echo "   - Homebrew: brew services restart postgresql@XX"
  read -p "Nyomj Entert, ha újraindítottad a helyi Postgrest..." _
  echo "=> wal_level:"
  psql_local -d "$DB_NAME" -c "SHOW wal_level;"
fi

### === 3) Publication létrehozás ===
SQL_PUB=$(cat <<EOSQL
DROP PUBLICATION IF EXISTS $PUBLICATION_NAME;
CREATE PUBLICATION $PUBLICATION_NAME FOR TABLE $OUTBOX_TABLE;
\\dRp+
EOSQL
)

if [[ "$MODE" == "docker" ]]; then
  echo "==> Publication frissítése (docker)..."
  psql_docker -d "$DB_NAME" -c "$SQL_PUB"
else
  echo "==> Publication frissítése (local)..."
  psql_local -d "$DB_NAME" -c "$SQL_PUB"
fi

### === 4) Debezium connector konfigurálása (Outbox SMT-vel, TS nélkül) ===
CONNECT_CFG=$(cat <<JSON
{
  "connector.class": "io.debezium.connector.postgresql.PostgresConnector",

  "database.hostname": "$PG_HOST_FROM_CONNECT",
  "database.port": "$PG_PORT_FROM_CONNECT",
  "database.user": "$DBZ_USER",
  "database.password": "$DBZ_PASS",
  "database.dbname": "$DB_NAME",

  "topic.prefix": "ordersdb",
  "slot.name": "$REPL_SLOT_NAME",
  "plugin.name": "pgoutput",

  "publication.name": "$PUBLICATION_NAME",
  "publication.autocreate.mode": "disabled",

  "schema.include.list": "public",
  "table.include.list": "$OUTBOX_TABLE",
  "tombstones.on.delete": "false",

  "transforms": "outbox",
  "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
  "transforms.outbox.route.topic.replacement": "orders.\$r",

  "transforms.outbox.table.field.event.id": "id",
  "transforms.outbox.table.field.event.key": "aggregate_id",
  "transforms.outbox.table.field.event.type": "type",
  "transforms.outbox.table.field.event.aggregate.type": "aggregate_type",
  "transforms.outbox.table.field.payload": "payload",

  "transforms.outbox.table.fields.additional.placement": "type:header:eventType,aggregate_id:header:aggregateId,aggregate_type:header:aggregateType",
  "transforms.outbox.table.expand.headers": "true",

  "key.converter": "org.apache.kafka.connect.json.JsonConverter",
  "value.converter": "org.apache.kafka.connect.json.JsonConverter",
  "key.converter.schemas.enable": "false",
  "value.converter.schemas.enable": "false"
}
JSON
)

echo "==> Connector (PUT $CONNECTOR_NAME)..."
curl -s -X PUT "$CONNECT_URL/connectors/$CONNECTOR_NAME/config" \
  -H 'Content-Type: application/json' \
  -d "$CONNECT_CFG" >/dev/null

echo "==> Connector restart + státusz..."
curl -s -X POST "$CONNECT_URL/connectors/$CONNECTOR_NAME/restart" >/dev/null
sleep 2
curl_jq "$CONNECT_URL/connectors/$CONNECTOR_NAME/status"

### === 5) Hasznos diagnosztika ===
echo "==> Replikációs slot állapot ($REPL_SLOT_NAME):"
if [[ "$MODE" == "docker" ]]; then
  psql_docker -d "$DB_NAME" -c "SELECT slot_name, active, confirmed_flush_lsn FROM pg_replication_slots WHERE slot_name='$REPL_SLOT_NAME';"
else
  psql_local -d "$DB_NAME" -c "SELECT slot_name, active, confirmed_flush_lsn FROM pg_replication_slots WHERE slot_name='$REPL_SLOT_NAME';"
fi

echo "==> Kész. Küldj egy rendelést az order-api felé, majd nézd Kafdropban a 'orders.OrderCreated' topikot."
