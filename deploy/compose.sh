#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
ENVIRONMENT=${1:-}

if [ -z "$ENVIRONMENT" ]; then
  echo "Usage: $0 <dev|prod> <docker compose arguments...>" >&2
  exit 1
fi
shift

if [ "$#" -eq 0 ]; then
  echo "Usage: $0 <dev|prod> <docker compose arguments...>" >&2
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
    echo "Unknown environment: $ENVIRONMENT (expected dev or prod)" >&2
    exit 1
    ;;
esac

ENV_FILE=${BUDLOG_ENV_FILE:-"$PROJECT_ROOT/.env"}
if [ ! -f "$ENV_FILE" ]; then
  echo "Environment file not found: $ENV_FILE" >&2
  exit 1
fi

exec docker compose --project-directory "$SCRIPT_DIR" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
