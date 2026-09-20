#!/bin/bash
# Creates the additional databases the stack needs beyond POSTGRES_DB.
#
# Runs once, on first initialisation of an empty data volume. Adding a database
# later means either `docker compose down -v` or creating it by hand.
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    SELECT 'CREATE DATABASE keycloak'
     WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'keycloak')\gexec

    GRANT ALL PRIVILEGES ON DATABASE keycloak TO "$POSTGRES_USER";
EOSQL

echo "init-databases.sh: keycloak database ready"
