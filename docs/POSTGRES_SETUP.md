# PostgreSQL Setup for Debezium CDC

This guide provides the necessary SQL commands to configure a PostgreSQL database for use with Debezium and the Transactional Outbox pattern.

## Step 1: Create Users and Database

First, connect to your PostgreSQL instance as a superuser (`postgres`).

```sql
-- Create the application user
CREATE ROLE app WITH LOGIN PASSWORD 'pass';

-- Create the Debezium user with REPLICATION privilege
CREATE ROLE debezium WITH LOGIN REPLICATION PASSWORD 'dbz';

-- Create the database and set the owner
CREATE DATABASE orders OWNER app;

```

## Step 2: Configure Logical Replication

Debezium requires `wal_level` to be set to `logical`.

> **Important:** This change requires a PostgreSQL restart.



```
-- Set the required WAL level for CDC
ALTER SYSTEM SET wal_level = 'logical';
ALTER SYSTEM SET max_wal_senders = '10';
ALTER SYSTEM SET max_replication_slots = '10';

```

After running these commands, restart your PostgreSQL container or service.

```
docker restart <your-postgres-container-name>
```


## Step 3: Connect to the `orders` Database and Set Privileges

Connect to the newly created database: `\c orders`


```
-- Grant necessary privileges to roles
GRANT CONNECT ON DATABASE orders TO app;
GRANT CONNECT ON DATABASE orders TO debezium;

GRANT USAGE ON SCHEMA public TO app;
GRANT USAGE ON SCHEMA public TO debezium;

-- Grant future privileges on new objects
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO debezium;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app;

```

## Step 4: Create the Outbox Table

This is the table that Debezium will monitor for changes.


```
CREATE TABLE IF NOT EXISTS public.order_outbox (
  id             UUID PRIMARY KEY,
  aggregate_id   UUID NOT NULL,
  aggregate_type VARCHAR(255) NOT NULL,
  type           VARCHAR(255) NOT NULL,
  payload        JSONB NOT NULL,
  occurred_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Grant permissions on the outbox table
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.order_outbox TO app;
GRANT SELECT ON TABLE public.order_outbox TO debezium;

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_order_outbox_aggregate ON order_outbox (aggregate_id);
CREATE INDEX IF NOT EXISTS idx_order_outbox_type_occurred ON order_outbox (type, occurred_at);

```

## Step 5: Create the Debezium Publication

Debezium needs a publication to subscribe to changes for the specified table.



```
CREATE PUBLICATION dbz_publication FOR TABLE public.order_outbox;

```

Your database is now fully configured for Chaos Symphony.
