#!/bin/zsh
set -e

SCRIPT_DIR=${0:A:h}
PROJECT_ROOT=${SCRIPT_DIR:h}
H5_BUILD_DIR="$PROJECT_ROOT/budlog-app/dist/build/h5"
H5_DEPLOY_DIR="$SCRIPT_DIR/h5"

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
if [[ ! -s "$H5_BUILD_DIR/index.html" ]]; then
  print -u2 "Frontend artifact was not generated"
  exit 1
fi
if [[ ! -s "$H5_BUILD_DIR/version.json" ]]; then
  print -u2 "Frontend version metadata was not generated"
  exit 1
fi

cp "$PROJECT_ROOT/budlog-server/target/budlog-server.jar" "$SCRIPT_DIR/budlog-server.jar"
mkdir -p "$H5_DEPLOY_DIR"

# Keep previously published hashed assets so already-open clients can finish loading them.
rsync -a \
  --exclude '/index.html' \
  --exclude '/version.json' \
  "$H5_BUILD_DIR/" "$H5_DEPLOY_DIR/"

H5_INDEX_TEMP="$H5_DEPLOY_DIR/.index.html.$$"
H5_VERSION_TEMP="$H5_DEPLOY_DIR/.version.json.$$"
cleanup_staged_frontend_entries() {
  rm -f "$H5_INDEX_TEMP" "$H5_VERSION_TEMP"
}
trap cleanup_staged_frontend_entries EXIT

cp "$H5_BUILD_DIR/index.html" "$H5_INDEX_TEMP"
cp "$H5_BUILD_DIR/version.json" "$H5_VERSION_TEMP"
chmod 0644 "$H5_INDEX_TEMP" "$H5_VERSION_TEMP"
mv -f "$H5_INDEX_TEMP" "$H5_DEPLOY_DIR/index.html"
mv -f "$H5_VERSION_TEMP" "$H5_DEPLOY_DIR/version.json"
trap - EXIT

if [[ ! -s "$SCRIPT_DIR/budlog-server.jar" ]]; then
  print -u2 "Backend deployment artifact was not copied"
  exit 1
fi
if [[ ! -s "$H5_DEPLOY_DIR/index.html" ]]; then
  print -u2 "Frontend deployment artifact was not copied"
  exit 1
fi
if [[ ! -s "$H5_DEPLOY_DIR/version.json" ]]; then
  print -u2 "Frontend version metadata was not copied"
  exit 1
fi

print "Deployment artifacts generated:"
print "  $SCRIPT_DIR/budlog-server.jar"
print "  $SCRIPT_DIR/h5/"
