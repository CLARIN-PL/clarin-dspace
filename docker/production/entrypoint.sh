#!/usr/bin/env bash
set -Eeuo pipefail

database_host="${DSPACE_DATABASE_HOST:-dspacedb}"
database_port="${DSPACE_DATABASE_PORT:-5432}"
wait_timeout="${DSPACE_DATABASE_WAIT_TIMEOUT:-120}"
waited=0

echo "Waiting for PostgreSQL at ${database_host}:${database_port}..."
until (echo > "/dev/tcp/${database_host}/${database_port}") >/dev/null 2>&1; do
    if (( waited >= wait_timeout )); then
        echo "PostgreSQL was not reachable after ${wait_timeout}s" >&2
        exit 1
    fi
    sleep 2
    waited=$((waited + 2))
done

echo "Applying DSpace database migrations..."
/dspace/bin/dspace database migrate

echo "Starting DSpace backend..."
exec "$@"
