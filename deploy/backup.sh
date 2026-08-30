#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
ENVIRONMENT=${1:-}
ENV_FILE=${2:-${BUDLOG_ENV_FILE:-"$PROJECT_ROOT/.env"}}
BACKUP_DIR=${BUDLOG_BACKUP_DIR:-"$SCRIPT_DIR/backups"}

case "$ENVIRONMENT" in
  dev)
    COMPOSE_FILE="$SCRIPT_DIR/docker-compose.dev.yml"
    ;;
  prod)
    COMPOSE_FILE="$SCRIPT_DIR/docker-compose.yml"
    ;;
  *)
    echo "Usage: $0 <dev|prod> [env-file]" >&2
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

CONNECTED_DATABASE=$(compose run --rm -T --no-deps db-tools \
  psql -X -v ON_ERROR_STOP=1 -Atqc 'select current_database()' < /dev/null)
if [ "$CONNECTED_DATABASE" != "$TARGET_DATABASE" ]; then
  echo "Database preflight mismatch: expected $TARGET_DATABASE, connected to $CONNECTED_DATABASE" >&2
  exit 1
fi

TIMESTAMP=$(date '+%Y%m%d-%H%M%S')
BACKUP_FILE="$BACKUP_DIR/$ENVIRONMENT-$TARGET_DATABASE-$TIMESTAMP.dump"
TEMP_FILE="$BACKUP_FILE.tmp"

mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"

cleanup() {
  rm -f "$TEMP_FILE"
}
trap cleanup EXIT INT TERM

compose run --rm -T --no-deps db-tools \
  pg_dump -Fc --no-owner --no-privileges < /dev/null > "$TEMP_FILE"

if [ ! -s "$TEMP_FILE" ]; then
  echo "Database backup is empty" >&2
  exit 1
fi

compose run --rm -T --no-deps db-tools pg_restore --list < "$TEMP_FILE" > /dev/null
mv "$TEMP_FILE" "$BACKUP_FILE"
chmod 600 "$BACKUP_FILE"
trap - EXIT INT TERM
echo "$BACKUP_FILE"
