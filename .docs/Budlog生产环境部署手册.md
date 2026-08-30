# Budlog 生产环境部署步骤

适用于 Linux 服务器 Docker Compose v2 部署。PostgreSQL 为外部服务，dev 和 prod 使用同一个逻辑数据库。以下开发机命令均在项目根目录执行。

## 1. 统一数据库配置

将 `deploy/docker-compose.yml` 中两处 `POSTGRESQL_PROD_DATABASE` 改为 `POSTGRESQL_DATABASE`：

```yaml
services:
  server:
    environment:
      POSTGRESQL_DATABASE: ${POSTGRESQL_DATABASE:?POSTGRESQL_DATABASE is required}
  db-tools:
    environment:
      PGDATABASE: ${POSTGRESQL_DATABASE:?POSTGRESQL_DATABASE is required}
```

将 `budlog-server/src/main/resources/application-prod.yml` 的数据库名变量改为：

```yaml
spring:
  datasource:
    url: "${DB_URL:jdbc:postgresql://${POSTGRESQL_HOST:127.0.0.1}:${POSTGRESQL_PORT:5432}/${POSTGRESQL_DATABASE}}"
```

同时将 `deploy/docker-compose.dev.yml` 和 `application-dev.yml` 的数据库名改用同一个必填变量 `POSTGRESQL_DATABASE`，删除 `budlog_dev` 默认值。

根 `.env` 和服务器 `.env` 删除 `POSTGRESQL_PROD_DATABASE`，只保留：

```dotenv
POSTGRESQL_HOST=<容器可访问的数据库地址>
POSTGRESQL_PORT=5432
POSTGRESQL_USER=<数据库账号>
POSTGRESQL_PASSWORD=<数据库密码>
POSTGRESQL_DATABASE=<共享数据库名>
APP_ACCESS_PASSWORD=<家庭访问强密码>
BUDLOG_TIMEZONE=Asia/Shanghai
BUDLOG_FEEDING_INTERVAL_MIN=180
BUDLOG_PROD_WEB_BIND=127.0.0.1
BUDLOG_PROD_WEB_PORT=5173
BUDLOG_IMAGE_TAG=20260830-01
```

`APP_ACCESS_PASSWORD` 必须非空。服务器 `.env` 权限设为 `600`，不要提交或打包。

## 2. 本地构建

执行：

```sh
./deploy/build.sh

test -s deploy/budlog-server.jar
test -s deploy/h5/index.html
```

构建结果：

- 后端：`deploy/budlog-server.jar`
- 前端：`deploy/h5/`

## 3. 打包并上传

构建产物已经与 Docker Compose 位于 `deploy/`。上传这些文件后，由服务器构建自身架构的镜像，避免 Mac `arm64` 与服务器 `amd64` 不兼容。

```sh
RELEASE_ID=20260830-01
BUNDLE_NAME="budlog-prod-${RELEASE_ID}.tar.gz"
DEPLOY_HOST=budlog.example.com

tar -czf "$BUNDLE_NAME" \
  deploy/budlog-server.jar \
  deploy/h5 \
  deploy/docker-compose.yml \
  deploy/compose.sh \
  deploy/start.sh \
  deploy/server.Dockerfile \
  deploy/server.Dockerfile.dockerignore \
  deploy/web.Dockerfile \
  deploy/web.Dockerfile.dockerignore \
  deploy/nginx.conf \
  deploy/backup.sh \
  deploy/restore.sh

shasum -a 256 "$BUNDLE_NAME" > "$BUNDLE_NAME.sha256"
scp "$BUNDLE_NAME" "$BUNDLE_NAME.sha256" "deploy@${DEPLOY_HOST}:/tmp/"
```

不要把 `.env`、源码、`node_modules`、Maven 仓库或数据库备份放入发布包。

## 4. 服务器部署

服务器需已安装 Docker、Docker Compose v2、`jq`、`tar` 和 `curl`，并能从容器访问 PostgreSQL。

```sh
DEPLOY_DIR=/opt/budlog
BUNDLE_NAME=budlog-prod-20260830-01.tar.gz

cd /tmp
sha256sum -c "$BUNDLE_NAME.sha256"
sudo install -d -m 750 -o deploy -g deploy "$DEPLOY_DIR"
sudo tar -xzf "$BUNDLE_NAME" -C "$DEPLOY_DIR"
sudo chown -R deploy:deploy "$DEPLOY_DIR"

cd "$DEPLOY_DIR"
chmod +x deploy/compose.sh deploy/start.sh deploy/backup.sh deploy/restore.sh
```

在 `/opt/budlog/.env` 按第 1 节填写生产配置，然后执行：

```sh
chmod 600 .env
./deploy/compose.sh prod config --quiet
```

确认实际连接的是共享数据库：

```sh
./deploy/compose.sh prod --profile tools run --rm -T --no-deps db-tools \
  psql -X -v ON_ERROR_STOP=1 -Atqc 'select current_database()'
```

输出必须等于 `<共享数据库名>`。上线前停止所有 dev/prod 写入，并备份共享数据库：

```sh
sudo install -d -m 700 -o deploy -g deploy /opt/budlog-backups
BUDLOG_BACKUP_DIR=/opt/budlog-backups ./deploy/backup.sh prod
```

构建服务器架构镜像并启动：

```sh
./deploy/start.sh
```

生产 Web 默认只监听 `127.0.0.1:5173`。公网访问必须由服务器 HTTPS 反向代理转发到该地址，不要暴露后端 8080 或 PostgreSQL 5432。

## 5. 验收

```sh
./deploy/compose.sh prod ps
./deploy/compose.sh prod logs --tail=200 server web

curl -fsS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:5173/
curl -sS -o /dev/null -w '%{http_code}\n' -X POST \
  http://127.0.0.1:5173/api/v1/access/verify
```

预期 `server`、`web` 均为 `healthy`，首页返回 `200`，无密码验证返回 `401`。最后通过生产 HTTPS 域名输入正确家庭密码，确认首页和 Dashboard 能正常读取共享数据库数据。
