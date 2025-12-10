#!/bin/bash
set -e

# Default settings
DB_USER="postgres"
APP_USER="app"
APP_PASS="pass"
DBZ_USER="debezium"
DBZ_PASS="dbz"
DB_NAME="orders"

# --- Functions ---
run_psql_command() {
  docker exec -i postgres psql -U "$DB_USER" -d "$1" <<< "$2"
}

# --- Main Logic ---
echo "🚀 Starting PostgreSQL Bootstrap for Chaos Symphony..."

echo "1. Creating roles and database..."
run_psql_command "postgres" "
  DO \$\$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '$APP_USER') THEN CREATE ROLE $APP_USER WITH LOGIN PASSWORD '$APP_PASS'; END IF; END \$\$;
  DO \$\$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '$DBZ_USER') THEN CREATE ROLE $DBZ_USER WITH LOGIN REPLICATION PASSWORD '$DBZ_PASS'; END IF; END \$\$;
  DROP DATABASE IF EXISTS $DB_NAME;
  CREATE DATABASE $DB_NAME OWNER $APP_USER;
"

echo "2. Configuring WAL, privileges, and outbox table..."
run_psql_command "$DB_NAME" "
  -- Privileges
  GRANT CONNECT ON DATABASE $DB_NAME TO $APP_USER, $DBZ_USER;
  GRANT USAGE ON SCHEMA public TO $APP_USER, $DBZ_USER;

  -- Outbox Table
  CREATE TABLE public.order_outbox (
    id             UUID PRIMARY KEY,
    aggregate_id   UUID NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    type           VARCHAR(255) NOT NULL,
    payload        JSONB NOT NULL,
    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT now()
  );
  ALTER TABLE public.order_outbox OWNER TO $APP_USER;
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.order_outbox TO $APP_USER;
  GRANT SELECT ON TABLE public.order_outbox TO $DBZ_USER;
"

echo "3. Configuring logical replication (requires container restart)..."
run_psql_command "postgres" "
  ALTER SYSTEM SET wal_level = 'logical';
  ALTER SYSTEM SET max_wal_senders = '10';
  ALTER SYSTEM SET max_replication_slots = '10';
"
echo "   Restarting PostgreSQL container to apply WAL settings..."
docker restart postgres
# Wait a few seconds for Postgres to be ready again
sleep 5

echo "4. Creating Debezium publication..."
run_psql_command "$DB_NAME" "
  DROP PUBLICATION IF EXISTS dbz_publication;
  CREATE PUBLICATION dbz_publication FOR TABLE public.order_outbox;
"

echo "✅ Bootstrap complete! PostgreSQL is ready for Debezium."