#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.yml"
ENV_FILE=${BUDLOG_ENV_FILE:-"$PROJECT_ROOT/.env"}

if [ "$#" -ne 0 ]; then
  echo "Usage: $0" >&2
  exit 1
fi
if [ ! -f "$ENV_FILE" ]; then
  echo "Environment file not found: $ENV_FILE" >&2
  exit 1
fi
if [ ! -s "$SCRIPT_DIR/budlog-server.jar" ]; then
  echo "Backend artifact not found: $SCRIPT_DIR/budlog-server.jar" >&2
  exit 1
fi
if [ ! -s "$SCRIPT_DIR/h5/index.html" ]; then
  echo "Frontend artifact not found: $SCRIPT_DIR/h5/index.html" >&2
  exit 1
fi

compose() {
  docker compose --project-directory "$SCRIPT_DIR" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

compose config --quiet
compose build server web
compose up -d --no-build --force-recreate server web
compose ps
