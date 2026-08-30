#!/bin/zsh
set -e

SCRIPT_DIR=${0:A:h}
PROJECT_ROOT=${SCRIPT_DIR:h}
ENVIRONMENT=${1:-dev}

case "$ENVIRONMENT" in
  dev)
    COMPOSE_FILE="$SCRIPT_DIR/docker-compose.dev.yml"
    ;;
  prod)
    COMPOSE_FILE="$SCRIPT_DIR/docker-compose.yml"
    ;;
  *)
    print -u2 "Unknown environment: $ENVIRONMENT (expected dev or prod)"
    exit 1
    ;;
esac

SDKMAN_INSTALL_DIR=${SDKMAN_DIR:-"$HOME/.sdkman"}
SDKMAN_INIT="$SDKMAN_INSTALL_DIR/bin/sdkman-init.sh"
JAVA_VERSION=${BUDLOG_JAVA_VERSION:-8.0.361-orcl}
MAVEN_INSTALL_DIR=${MAVEN_HOME:-"$HOME/apache-maven-3.6.0"}
MAVEN_EXECUTABLE=${MAVEN_BIN:-"$MAVEN_INSTALL_DIR/bin/mvn"}
MAVEN_REPOSITORY=${MAVEN_REPO_LOCAL:-"$MAVEN_INSTALL_DIR/repository"}
ENV_FILE=${BUDLOG_ENV_FILE:-"$PROJECT_ROOT/.env"}

if [[ ! -s "$SDKMAN_INIT" ]]; then
  print -u2 "SDKMAN init script not found: $SDKMAN_INIT"
  exit 1
fi
if [[ ! -x "$MAVEN_EXECUTABLE" ]]; then
  print -u2 "Maven executable not found: $MAVEN_EXECUTABLE"
  exit 1
fi
if [[ ! -d "$MAVEN_REPOSITORY" ]]; then
  print -u2 "Maven local repository not found: $MAVEN_REPOSITORY"
  exit 1
fi
if [[ ! -d "$PROJECT_ROOT/budlog-app/node_modules" ]]; then
  print -u2 "Frontend dependencies are missing. Run npm ci in budlog-app first."
  exit 1
fi
if [[ ! -f "$ENV_FILE" ]]; then
  print -u2 "Environment file not found: $ENV_FILE"
  exit 1
fi

docker compose --project-directory "$SCRIPT_DIR" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" config --quiet

source "$SDKMAN_INIT"
sdk use java "$JAVA_VERSION"
set -u

(
  cd "$PROJECT_ROOT/budlog-server"
  "$MAVEN_EXECUTABLE" -o -nsu -llr \
    -Dmaven.repo.local="$MAVEN_REPOSITORY" \
    -Dmaven.test.skip=true package
)

(
  cd "$PROJECT_ROOT/budlog-app"
  npm run type-check
  npm run build:h5
)

if [[ ! -s "$PROJECT_ROOT/budlog-server/target/budlog-server.jar" ]]; then
  print -u2 "Backend artifact was not generated"
  exit 1
fi
if [[ ! -s "$PROJECT_ROOT/budlog-app/dist/build/h5/index.html" ]]; then
  print -u2 "Frontend artifact was not generated"
  exit 1
fi

docker compose --project-directory "$SCRIPT_DIR" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" build server web
