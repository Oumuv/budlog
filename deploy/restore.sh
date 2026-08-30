#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
BACKUP_FILE=${1:-}
ENVIRONMENT=${2:-}
ENV_FILE=${3:-${BUDLOG_ENV_FILE:-"$PROJECT_ROOT/.env"}}

if [ -z "$BACKUP_FILE" ] || [ ! -f "$BACKUP_FILE" ]; then
  echo "Usage: $0 <backup.dump> <dev|prod> [env-file]" >&2
  exit 1
fi

case "$ENVIRONMENT" in
  dev)
    COMPOSE_FILE="$SCRIPT_DIR/docker-compose.dev.yml"
    ;;
  prod)
    COMPOSE_FILE="$SCRIPT_DIR/docker-compose.yml"
    ;;
  *)
    echo "Usage: $0 <backup.dump> <dev|prod> [env-file]" >&2
    exit 1
    ;;
esac

if [ ! -f "$ENV_FILE" ]; then
  echo "Environment file not found: $ENV_FILE" >&2
  exit 1
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi

compose() {
  docker compose --project-directory "$SCRIPT_DIR" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" \
    --profile tools "$@"
}

TARGET_DATABASE=$(compose config --format json | jq -er '.services["db-tools"].environment.PGDATABASE | select(type == "string" and length > 0)')
case "$TARGET_DATABASE" in
  ''|*[!A-Za-z0-9_]*)
    echo "Invalid database name resolved from Compose: $TARGET_DATABASE" >&2
    exit 1
    ;;
esac

compose run --rm -T --no-deps db-tools pg_restore --list < "$BACKUP_FILE" > /dev/null
CONNECTED_DATABASE=$(compose run --rm -T --no-deps db-tools \
  psql -X -v ON_ERROR_STOP=1 -Atqc 'select current_database()' < /dev/null)
if [ "$CONNECTED_DATABASE" != "$TARGET_DATABASE" ]; then
  echo "Database preflight mismatch: expected $TARGET_DATABASE, connected to $CONNECTED_DATABASE" >&2
  exit 1
fi

EXPECTED_CONFIRMATION="RESTORE $TARGET_DATABASE"
if [ "${BUDLOG_RESTORE_CONFIRM:-}" != "$EXPECTED_CONFIRMATION" ]; then
  printf 'This replaces database "%s". Type "%s" to continue: ' "$TARGET_DATABASE" "$EXPECTED_CONFIRMATION"
  read -r confirmation
  if [ "$confirmation" != "$EXPECTED_CONFIRMATION" ]; then
    echo "Restore canceled"
    exit 1
  fi
fi

SERVER_WAS_RUNNING=false
restart_server() {
  if [ "$SERVER_WAS_RUNNING" = "true" ]; then
    compose start server > /dev/null
  fi
}
trap restart_server EXIT INT TERM

if [ "$(compose ps --status running --services server)" = "server" ]; then
  SERVER_WAS_RUNNING=true
  compose stop server
fi

compose run --rm -T --no-deps db-tools \
  pg_restore --clean --if-exists --exit-on-error --single-transaction --no-owner --no-privileges \
  < "$BACKUP_FILE"

restart_server
trap - EXIT INT TERM
echo "Restore completed: $BACKUP_FILE -> $TARGET_DATABASE"
