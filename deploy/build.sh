#!/bin/zsh
set -e

SCRIPT_DIR=${0:A:h}
PROJECT_ROOT=${SCRIPT_DIR:h}

if (( $# != 0 )); then
  print -u2 "Usage: $0"
  exit 1
fi

SDKMAN_INSTALL_DIR=${SDKMAN_DIR:-"$HOME/.sdkman"}
SDKMAN_INIT="$SDKMAN_INSTALL_DIR/bin/sdkman-init.sh"
JAVA_VERSION=${BUDLOG_JAVA_VERSION:-8.0.361-orcl}
MAVEN_INSTALL_DIR=${MAVEN_HOME:-"$HOME/apache-maven-3.6.0"}
MAVEN_EXECUTABLE=${MAVEN_BIN:-"$MAVEN_INSTALL_DIR/bin/mvn"}
MAVEN_REPOSITORY=${MAVEN_REPO_LOCAL:-"$MAVEN_INSTALL_DIR/repository"}

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
if ! command -v rsync >/dev/null 2>&1; then
  print -u2 "rsync is required to synchronize frontend artifacts"
  exit 1
fi

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

cp "$PROJECT_ROOT/budlog-server/target/budlog-server.jar" "$SCRIPT_DIR/budlog-server.jar"
mkdir -p "$SCRIPT_DIR/h5"
rsync -a --delete "$PROJECT_ROOT/budlog-app/dist/build/h5/" "$SCRIPT_DIR/h5/"

if [[ ! -s "$SCRIPT_DIR/budlog-server.jar" ]]; then
  print -u2 "Backend deployment artifact was not copied"
  exit 1
fi
if [[ ! -s "$SCRIPT_DIR/h5/index.html" ]]; then
  print -u2 "Frontend deployment artifact was not copied"
  exit 1
fi

print "Deployment artifacts generated:"
print "  $SCRIPT_DIR/budlog-server.jar"
print "  $SCRIPT_DIR/h5/"
