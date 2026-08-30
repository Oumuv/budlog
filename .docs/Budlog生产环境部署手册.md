# Budlog 生产环境部署手册

## 1. 适用范围

本文档用于把 Budlog H5 和 Spring Boot 服务部署到 Linux 服务器的 Docker Compose 生产环境，覆盖以下内容：

- 将 dev 和 prod 统一连接到同一个 PostgreSQL 逻辑数据库。
- 在开发机生成后端 Jar、前端 H5 和 Docker 镜像。
- 打包并复制服务器构建所需的最小文件集合。
- 在服务器构建目标架构镜像、启动生产服务并完成验收。
- 执行上线前备份，以及代码回滚和数据库恢复前的安全检查。

本文档使用以下占位符，执行时必须替换为实际值：

| 占位符 | 含义 | 示例 |
| --- | --- | --- |
| `<项目目录>` | 开发机上的仓库根目录 | `budlog` 仓库根目录 |
| `<部署目录>` | 服务器上的 Budlog 目录 | `/opt/budlog` |
| `<发布标识>` | 不重复的镜像标签 | `20260830-01` |
| `<共享数据库名>` | dev 和 prod 共用的 PostgreSQL 数据库 | 以实际权威数据库为准 |
| `<服务器地址>` | SSH 可访问的服务器主机名或 IP | `budlog.example.com` |

除非某一步明确说明，开发机命令都从 `<项目目录>` 执行，服务器命令都从 `<部署目录>` 执行。

## 2. 部署模型

生产流量链路如下：

```text
浏览器
  -> HTTPS 反向代理（服务器 443）
  -> 127.0.0.1:5173
  -> budlog-prod 的 web 容器（Nginx）
  -> budlog-prod 的 server 容器（Spring Boot 8080）
  -> 外部 PostgreSQL 的共享逻辑数据库
```

关键约束：

- PostgreSQL 是外部服务，Compose 不创建 PostgreSQL 容器或数据卷。
- dev 和 prod 使用不同的 Compose 项目名、Spring Profile 和 Web 端口，但连接同一个逻辑数据库。
- dev 和 prod 共享全部业务数据。任何开发环境写入、删除和 migration 都会影响生产数据。
- 不允许 dev 和 prod 长期运行不兼容的应用版本。数据库结构变化上线时，应停止所有连接该数据库的旧版本写入端。
- 生产环境固定启用家庭访问密码，所有公网访问必须经过 HTTPS。
- 默认 `BUDLOG_PROD_WEB_BIND=127.0.0.1`，5173 端口只供服务器上的 HTTPS 反向代理访问。
- 后端 8080 和 PostgreSQL 5432 不应直接暴露到公网。

## 3. 上线前必须确认的数据归属

当前仓库配置仍将 dev 和 prod 指向不同数据库：dev 默认使用 `budlog_dev`，prod 默认使用 `budlog`。改为共享数据库前，必须先确定哪个现有数据库是权威数据源。

如果两个数据库都已有需要保留的数据，不要直接把两个环境切到其中任意一个数据库。应先分别备份，再单独设计数据核对与合并方案；本手册不执行数据库合并。

切换前至少完成以下检查：

1. 确认 `<共享数据库名>`。
2. 确认该数据库允许配置账号连接，并具备 Flyway 建表或迁移所需权限。
3. 使用切换前的配置分别备份当前 dev 和 prod 数据库。
4. 暂停所有可能写入两个旧数据库的应用实例和人工操作。
5. 确认 V1、V2 Flyway migration 不会被修改；后续结构调整只能新增 migration。

共享数据库意味着环境名不再提供数据隔离。开发调试需要写数据时，应明确接受其对生产数据的直接影响。

## 4. 仓库一次性配置调整

必须先完成本节调整，再生成新的生产构建产物。调整后应只保留一个数据库名变量 `POSTGRESQL_DATABASE`。

### 4.1 修改生产 Compose

在 `deploy/docker-compose.yml` 中，将 `server` 的数据库环境变量从：

```yaml
POSTGRESQL_PROD_DATABASE: ${POSTGRESQL_PROD_DATABASE:-budlog}
```

修改为：

```yaml
POSTGRESQL_DATABASE: ${POSTGRESQL_DATABASE:?POSTGRESQL_DATABASE is required}
```

将 `db-tools` 的目标数据库从：

```yaml
PGDATABASE: ${POSTGRESQL_PROD_DATABASE:-budlog}
```

修改为：

```yaml
PGDATABASE: ${POSTGRESQL_DATABASE:?POSTGRESQL_DATABASE is required}
```

这两处必须同时修改，否则应用与备份工具可能连接不同数据库。

### 4.2 收紧开发 Compose

在 `deploy/docker-compose.dev.yml` 中，将 `server` 和 `db-tools` 的数据库配置统一改为必填变量，避免未加载 `.env` 时静默回退到 `budlog_dev`：

```yaml
POSTGRESQL_DATABASE: ${POSTGRESQL_DATABASE:?POSTGRESQL_DATABASE is required}
```

```yaml
PGDATABASE: ${POSTGRESQL_DATABASE:?POSTGRESQL_DATABASE is required}
```

### 4.3 修改 Spring Profile

将 `budlog-server/src/main/resources/application-dev.yml` 和 `application-prod.yml` 的 JDBC URL 都统一为：

```yaml
spring:
  datasource:
    url: "${DB_URL:jdbc:postgresql://${POSTGRESQL_HOST:127.0.0.1}:${POSTGRESQL_PORT:5432}/${POSTGRESQL_DATABASE}}"
```

保留 `DB_URL` 兼容入口，但 Compose 部署统一传入 `POSTGRESQL_HOST`、`POSTGRESQL_PORT` 和 `POSTGRESQL_DATABASE`。

### 4.4 修改环境变量示例

在 `deploy/.env.example` 中：

- 将数据库名说明改为 dev 和 prod 共用。
- 只保留 `POSTGRESQL_DATABASE=<共享数据库名>`。
- 删除 `POSTGRESQL_PROD_DATABASE`。

根目录真实 `.env` 也只保留一个数据库名变量：

```dotenv
POSTGRESQL_DATABASE=<共享数据库名>
```

不得把真实 `.env` 内容复制进文档、Git、构建日志或工单。

### 4.5 校正文档描述

`.docs/Budlog本地构建与运行指南.md` 和根目录 `AGENTS.md` 目前仍描述 dev/prod 通过数据库名隔离。配置调整完成后应同步校正这些描述，避免后续运维人员按旧规则执行备份或恢复。

### 4.6 临时兼容方式

如果代码配置尚未调整，但必须先验证两个环境连接同一数据库，可暂时在 `.env` 中将以下两个变量设置为完全相同的值：

```dotenv
POSTGRESQL_DATABASE=<共享数据库名>
POSTGRESQL_PROD_DATABASE=<共享数据库名>
```

这只保证运行结果相同，仍保留重复配置和误配风险，不应作为最终状态。

## 5. 生产服务器前置条件

服务器至少需要：

- Linux 操作系统。
- Docker Engine 和 Docker Compose v2。
- `tar`、`sha256sum`、`curl`。
- `jq`，供 `backup.sh` 和 `restore.sh` 解析 Compose 配置。
- 能访问外部 PostgreSQL。
- 能拉取 Dockerfile 中固定摘要的 Eclipse Temurin、Nginx 镜像；执行备份时还需拉取固定摘要的 PostgreSQL 18 Alpine 镜像。
- 已配置 HTTPS 反向代理，并将请求转发到 `127.0.0.1:5173`。
- 部署账号对 `<部署目录>` 有写权限，并能执行 Docker 命令。

检查服务器架构和基础工具：

```sh
uname -m
docker version
docker compose version
jq --version
curl --version
```

如果 PostgreSQL 与 Docker 位于同一台 Linux 服务器，容器中的 `127.0.0.1` 指向容器自身，不能用于访问宿主机 PostgreSQL。应填写容器可路由的宿主机地址、私网 IP 或 DNS 名称。`host.docker.internal` 在 Linux 上不保证默认可用，不应照搬 macOS 配置。

## 6. 生产 `.env` 配置

不要把开发机 `.env` 原样复制到服务器。应在服务器 `<部署目录>/.env` 中单独配置生产值，并限制文件权限。

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
BUDLOG_IMAGE_TAG=<发布标识>

# 仅跨域部署时配置；同域经 Nginx 访问通常不需要。
# BUDLOG_CORS_ALLOWED_ORIGINS=https://budlog.example.com
```

配置要求：

- 不再设置 `POSTGRESQL_PROD_DATABASE`。
- `POSTGRESQL_DATABASE` 必须与 dev 使用的值完全一致。
- `APP_ACCESS_PASSWORD` 必须非空，建议至少 16 位随机字符，并与数据库密码不同。
- `.env` 不进入发布压缩包，不提交 Git，不发送到聊天记录。
- 值中包含 `#`、`$`、空格或引号时，必须遵循 Docker Compose env-file 转义规则，并在启动前执行只读解析校验。

设置权限：

```sh
chmod 600 .env
```

只验证配置是否合法，不输出解析后的敏感值：

```sh
./deploy/compose.sh prod config --quiet
```

不要把不带 `--quiet` 的完整 `docker compose config` 输出粘贴到日志或工单，因为解析结果可能包含数据库密码和家庭访问密码。

## 7. 开发机生成生产构建产物

### 7.1 构建前检查

确认以下条件：

- 已完成第 4 节配置调整。
- 根 `.env` 包含非空生产必填项，但不会被提交或打包。
- `BUDLOG_IMAGE_TAG` 使用新的 `<发布标识>`，不要长期复用 `local` 或覆盖上一版本标签。
- `budlog-app/node_modules` 已存在，不重复执行 `npm install`。
- SDKMAN 中存在 JDK `8.0.361-orcl`。
- Maven 位于 `$HOME/apache-maven-3.6.0`，离线仓库完整。
- Docker 环境可用。

只读检查：

```sh
git status --short
docker compose version
test -d budlog-app/node_modules
test -x "$HOME/apache-maven-3.6.0/bin/mvn"
test -d "$HOME/apache-maven-3.6.0/repository"
./deploy/compose.sh prod config --quiet
```

### 7.2 执行生产构建

```sh
./deploy/build.sh prod
```

脚本会依次执行：

1. 校验生产 Compose 和 `.env` 必填项。
2. 通过 SDKMAN 切换到 JDK `8.0.361-orcl`。
3. 使用 Maven 3.6.0 和指定本地仓库离线打包后端，跳过测试。
4. 执行前端 `npm run type-check`。
5. 执行前端 `npm run build:h5`。
6. 构建 `budlog-server:<发布标识>` 和 `budlog-web:<发布标识>` 本机镜像。

如果离线 Maven 仓库缺少依赖，或 `node_modules` 不存在，应停止并补齐开发环境；不要临时切换到未经确认的在线依赖安装流程。

### 7.3 构建结果

| 类型 | 产物 | 是否复制到服务器 |
| --- | --- | --- |
| 后端 | `budlog-server/target/budlog-server.jar` | 默认流程需要 |
| 前端 | `budlog-app/dist/build/h5/` | 默认流程需要整个目录 |
| 后端镜像 | `budlog-server:<发布标识>` | 默认流程不复制；同架构镜像交付时复制 |
| Web 镜像 | `budlog-web:<发布标识>` | 默认流程不复制；同架构镜像交付时复制 |
| Maven 中间文件 | `budlog-server/target/` 内其他文件 | 不需要 |
| 前端依赖 | `budlog-app/node_modules/` | 不需要 |
| Maven 仓库 | 本地离线 repository | 不需要 |
| 数据库 migration | 已打包进后端 Jar | 不单独复制 |

验证关键产物：

```sh
test -s budlog-server/target/budlog-server.jar
test -s budlog-app/dist/build/h5/index.html
docker image inspect "budlog-server:<发布标识>" >/dev/null
docker image inspect "budlog-web:<发布标识>" >/dev/null
```

## 8. 默认交付方式：服务器构建目标架构镜像

当前开发机可能是 `arm64`，生产服务器常见为 `amd64`。默认流程只传输架构无关的 Jar、H5 和 Docker 构建文件，再由服务器构建与自身架构匹配的镜像。

### 8.1 需要复制的文件

```text
<部署目录>/
├── .dockerignore
├── .env                              # 仅在服务器创建，不放入压缩包
├── budlog-server/
│   └── target/
│       └── budlog-server.jar
├── budlog-app/
│   └── dist/
│       └── build/
│           └── h5/
└── deploy/
    ├── docker-compose.yml
    ├── compose.sh
    ├── server.Dockerfile
    ├── web.Dockerfile
    ├── nginx.conf
    ├── backup.sh
    ├── restore.sh
    └── .env.example
```

不需要复制源码、`.git/`、`.env`、`node_modules/`、完整 Maven 仓库、完整 `target/` 或数据库备份。

### 8.2 在开发机生成发布压缩包

下面以 `20260830-01` 为示例发布标识。每次发布应换成新的值，并与服务器 `.env` 的 `BUDLOG_IMAGE_TAG` 一致。

```sh
RELEASE_ID=20260830-01
BUNDLE_NAME="budlog-prod-${RELEASE_ID}.tar.gz"

tar -czf "$BUNDLE_NAME" \
  .dockerignore \
  budlog-server/target/budlog-server.jar \
  budlog-app/dist/build/h5 \
  deploy/docker-compose.yml \
  deploy/compose.sh \
  deploy/server.Dockerfile \
  deploy/web.Dockerfile \
  deploy/nginx.conf \
  deploy/backup.sh \
  deploy/restore.sh \
  deploy/.env.example

tar -tzf "$BUNDLE_NAME"
shasum -a 256 "$BUNDLE_NAME" > "$BUNDLE_NAME.sha256"
```

生成的可传输文件为：

```text
budlog-prod-20260830-01.tar.gz
budlog-prod-20260830-01.tar.gz.sha256
```

压缩包不包含 `.env` 和数据库备份。

### 8.3 复制到服务器

```sh
DEPLOY_USER=deploy
DEPLOY_HOST=budlog.example.com
BUNDLE_NAME=budlog-prod-20260830-01.tar.gz

scp "$BUNDLE_NAME" "$BUNDLE_NAME.sha256" \
  "${DEPLOY_USER}@${DEPLOY_HOST}:/tmp/"
```

如使用其他安全文件传输工具，应保持相同文件集合，并在服务器验证 SHA-256。

## 9. 服务器部署步骤

### 9.1 校验并解压发布包

以下示例部署目录为 `/opt/budlog`，可根据服务器实际目录调整：

```sh
DEPLOY_DIR=/opt/budlog
BUNDLE_NAME=budlog-prod-20260830-01.tar.gz

cd /tmp
sha256sum -c "$BUNDLE_NAME.sha256"

sudo install -d -m 750 "$DEPLOY_DIR"
sudo tar -xzf "$BUNDLE_NAME" -C "$DEPLOY_DIR"
sudo chown -R deploy:deploy "$DEPLOY_DIR"

cd "$DEPLOY_DIR"
chmod +x deploy/compose.sh deploy/backup.sh deploy/restore.sh
```

不要删除上一版本镜像和数据库备份。发布包确认可用后，可按服务器保留策略清理 `/tmp` 中的副本。

### 9.2 创建生产 `.env`

使用服务器上的受控编辑器创建 `<部署目录>/.env`，内容按第 6 节填写。不要从开发环境复制可能包含旧数据库名或开发端口的 `.env`。

```sh
chmod 600 .env
```

确认发布标签与本次发布一致：

```dotenv
BUDLOG_IMAGE_TAG=20260830-01
```

### 9.3 只读校验 Compose

```sh
./deploy/compose.sh prod config --quiet
```

如果提示 `APP_ACCESS_PASSWORD is required`、`POSTGRESQL_HOST is required` 或 `POSTGRESQL_DATABASE is required`，应修正 `.env`，不能通过删除必填校验绕过。

### 9.4 验证数据库连接目标

此命令会临时运行 `db-tools` 容器并执行只读查询：

```sh
./deploy/compose.sh prod --profile tools run --rm -T --no-deps db-tools \
  psql -X -v ON_ERROR_STOP=1 -Atqc \
  'select current_database(), current_user'
```

输出的数据库名必须等于 `<共享数据库名>`。如果不一致，立即停止部署并检查 `.env`、Compose 和 Spring Profile。

### 9.5 上线前备份

对于已有数据的共享数据库，每次部署前都应创建备份：

```sh
sudo install -d -m 700 -o deploy -g deploy /opt/budlog-backups
BUDLOG_BACKUP_DIR=/opt/budlog-backups ./deploy/backup.sh prod
```

示例中的 `deploy:deploy` 是部署账号和用户组，服务器使用其他账号时应同步替换。

脚本成功后只输出最终 `.dump` 路径。应确认文件存在且权限为 `600`：

```sh
ls -l /opt/budlog-backups
```

因为 dev 和 prod 共用数据库，备份期间以及涉及 migration 的启动期间，应停止或冻结所有写入端，包括其他主机上的 dev 实例。仅停止 `budlog-prod` 容器不能阻止远程 dev 写入。

### 9.6 在服务器构建镜像

```sh
./deploy/compose.sh prod build server web
```

该步骤使用发布包内的 Jar 和 H5 文件，生成与服务器 CPU 架构匹配的：

```text
budlog-server:<发布标识>
budlog-web:<发布标识>
```

验证镜像：

```sh
docker image inspect "budlog-server:<发布标识>" >/dev/null
docker image inspect "budlog-web:<发布标识>" >/dev/null
```

### 9.7 启动生产服务

```sh
./deploy/compose.sh prod up -d --no-build server web
```

`--no-build` 确保启动阶段只使用刚刚验证过的镜像，不隐式重新构建。

查看状态：

```sh
./deploy/compose.sh prod ps
```

预期 `server` 和 `web` 均为 `healthy`。

查看启动日志：

```sh
./deploy/compose.sh prod logs --tail=200 server web
```

重点检查：

- Spring Profile 为 `prod`。
- Flyway migration 成功或已处于最新版本。
- Hibernate `ddl-auto=validate` 未报告结构不一致。
- 数据库连接没有认证、网络或数据库名错误。
- 日志中没有明文数据库密码、家庭访问密码或完整业务隐私数据。

PostgreSQL 18.6 与 Flyway 8.5.13 组合可能出现“数据库版本尚未官方验证”的警告。只有 migration、连接或 schema validation 实际失败时才视为部署失败，不能仅凭该警告宣称失败或成功。

## 10. 上线验收

### 10.1 容器与端口

```sh
./deploy/compose.sh prod ps
./deploy/compose.sh prod port web 80
```

预期 Web 端口绑定到 `127.0.0.1:5173`，且没有发布后端 8080 或数据库 5432。

### 10.2 H5 首页

```sh
curl -fsS -o /dev/null -w '%{http_code}\n' \
  http://127.0.0.1:5173/
```

预期返回 `200`。

### 10.3 访问密码

不带密码验证：

```sh
curl -sS -o /dev/null -w '%{http_code}\n' -X POST \
  http://127.0.0.1:5173/api/v1/access/verify
```

预期返回 `401`。

在 Bash 中安全读取密码并验证，避免把密码直接写进命令历史：

```sh
read -r -s -p 'APP_ACCESS_PASSWORD: ' BUDLOG_CHECK_PASSWORD
printf '\n'

curl -sS -o /dev/null -w '%{http_code}\n' -X POST \
  -H "X-App-Password: ${BUDLOG_CHECK_PASSWORD}" \
  http://127.0.0.1:5173/api/v1/access/verify

curl -sS -o /dev/null -w '%{http_code}\n' \
  -H "X-App-Password: ${BUDLOG_CHECK_PASSWORD}" \
  http://127.0.0.1:5173/api/v1/dashboard

unset BUDLOG_CHECK_PASSWORD
```

预期密码验证接口返回 `204`，Dashboard 返回 `200`。

### 10.4 HTTPS 和实际页面

通过最终生产域名访问 H5，至少检查：

- 浏览器证书有效，HTTP 会跳转到 HTTPS。
- 首次进入要求输入家庭访问密码。
- 正确密码可进入首页，错误密码不能读取业务数据。
- 首页、记录、任务、设置和统一时间线可以打开。
- 在桌面视口和 `390x844` 移动视口无明显遮挡、溢出或不可操作控件。
- 新增一条可识别的验收记录后，dev 和 prod 应看到同一条数据；验收完成后按业务规则处理该记录。

仅有构建日志、容器 `healthy` 或 HTTP 状态码，不足以替代最终 HTTPS 页面验收。

## 11. 可选交付方式：直接传输 Docker 镜像

只有开发机和服务器 CPU 架构一致时，才可以直接导出 `build.sh prod` 生成的镜像。

分别检查：

```sh
uname -m
docker image inspect "budlog-server:<发布标识>" \
  --format '{{.Architecture}}/{{.Os}}'
```

如果开发机是 `arm64` 而服务器是 `amd64`，不要使用此方式。当前 `build.sh` 没有配置多架构 `buildx` 输出。

同架构时，在开发机导出：

```sh
IMAGE_ARCHIVE=budlog-images-20260830-01.tar

docker save -o "$IMAGE_ARCHIVE" \
  "budlog-server:<发布标识>" \
  "budlog-web:<发布标识>"

shasum -a 256 "$IMAGE_ARCHIVE" > "$IMAGE_ARCHIVE.sha256"
```

服务器校验并导入：

```sh
sha256sum -c budlog-images-20260830-01.tar.sha256
docker load -i budlog-images-20260830-01.tar
./deploy/compose.sh prod up -d --no-build server web
```

为保持目录结构、Compose 配置和运维脚本一致，镜像直传时仍建议复制并解压第 8 节的发布压缩包，再额外复制镜像归档；服务器跳过 `compose build`，直接执行 `docker load` 和 `up -d --no-build`。`.env` 仍必须在服务器单独创建，不能放入任一归档。

离线服务器若还要执行备份和恢复，需要额外准备与服务器架构一致的固定摘要 PostgreSQL 18 Alpine `db-tools` 镜像。

## 12. 日常更新流程

每次发布使用新的 `<发布标识>`，不要覆盖上一版本镜像：

1. 在开发机检查 Git 状态和本次变更。
2. 执行 `./deploy/build.sh prod`。
3. 验证 Jar、H5 和镜像。
4. 生成带校验和的发布压缩包。
5. 复制到服务器并校验 SHA-256。
6. 校验生产 `.env` 和目标数据库。
7. 冻结共享数据库的所有写入端并执行备份。
8. 在服务器构建新标签镜像。
9. 执行 `up -d --no-build` 更新容器。
10. 完成容器、HTTP、鉴权、数据库和实际页面验收。
11. 验收稳定后再按保留策略清理旧发布包和旧镜像。

## 13. 回滚与恢复

### 13.1 代码和镜像回滚

如果数据库 schema 与上一版本兼容，可将服务器 `.env` 中的 `BUDLOG_IMAGE_TAG` 改回上一发布标识，然后重新创建容器：

```sh
./deploy/compose.sh prod config --quiet
./deploy/compose.sh prod up -d --no-build server web
./deploy/compose.sh prod ps
./deploy/compose.sh prod logs --tail=200 server web
```

如果旧镜像已被删除，应先重新加载旧镜像归档，或使用旧发布包重新构建。

### 13.2 数据库恢复

数据库恢复会清理并替换共享数据库中的对象和数据，不能作为普通代码回滚步骤。执行前必须：

1. 明确恢复文件、实际连接地址和 `<共享数据库名>`。
2. 再创建一份当前数据库回滚备份。
3. 停止 dev、prod 以及其他所有写入该数据库的进程。
4. 确认恢复后的 schema 与准备运行的应用版本兼容。
5. 取得针对本次恢复的人工确认。

当前 `restore.sh prod` 只能自动停止 `budlog-prod` 的 `server` 容器，无法停止其他主机上的 dev 服务。共享数据库模式下，必须人工保证所有写入端已经停止。

确认无误后才可按脚本要求执行：

```sh
./deploy/restore.sh /absolute/path/to/backup.dump prod
```

脚本会要求输入与实际目标数据库一致的确认词：

```text
RESTORE <共享数据库名>
```

恢复后必须重新执行第 10 节全部验收项。

## 14. 常见故障

### 14.1 仍然连接旧的 prod 数据库

检查以下位置是否都已从 `POSTGRESQL_PROD_DATABASE` 改为 `POSTGRESQL_DATABASE`：

- `deploy/docker-compose.yml` 的 `server.environment`。
- `deploy/docker-compose.yml` 的 `db-tools.environment.PGDATABASE`。
- `budlog-server/src/main/resources/application-prod.yml`。
- 服务器 `.env`。

重新构建后端 Jar 和服务器镜像，旧 Jar 中仍然包含旧的 `application-prod.yml`。

### 14.2 `APP_ACCESS_PASSWORD is required`

生产 `.env` 中必须配置非空 `APP_ACCESS_PASSWORD`。不能关闭 `APP_ACCESS_ENABLED` 绕过生产访问保护。

### 14.3 数据库连接失败

- 确认数据库地址能从容器访问，而不是容器自身的 `127.0.0.1`。
- 确认 PostgreSQL 监听地址、`pg_hba.conf`、防火墙和账号权限。
- 确认 `POSTGRESQL_DATABASE` 是已经存在的共享数据库。
- 使用第 9.4 节只读查询确认实际连接目标。

### 14.4 镜像架构不匹配

出现 `exec format error` 时，比较开发机、服务器和镜像架构。改用第 8 节的服务器构建流程，不要继续加载错误架构镜像。

### 14.5 端口不可访问

- `127.0.0.1:5173` 只能在服务器本机访问，这是默认安全行为。
- 公网访问应检查 HTTPS 反向代理、证书、防火墙和域名解析。
- 不建议把 `BUDLOG_PROD_WEB_BIND` 改为 `0.0.0.0` 后直接使用明文 HTTP。

### 14.6 Flyway 或 schema validation 失败

- 不修改已应用的 V1、V2 migration。
- 不执行 `flyway repair`、清库或恢复来绕过未知错误。
- 保存完整错误日志，核对当前数据库、migration 历史和应用版本后再处理。

## 15. 最终验收清单

- [ ] 已确定唯一的 `<共享数据库名>`，并处理两个旧数据库的数据归属。
- [ ] dev/prod 的 Compose、Spring Profile 和 `.env` 都使用 `POSTGRESQL_DATABASE`。
- [ ] `.env` 未进入 Git、发布包或日志，服务器权限为 `600`。
- [ ] `APP_ACCESS_PASSWORD` 非空且与数据库密码不同。
- [ ] 本次发布使用唯一 `BUDLOG_IMAGE_TAG`。
- [ ] Jar、H5、镜像或发布压缩包均经过存在性和校验和检查。
- [ ] 服务器构建的镜像架构与服务器一致。
- [ ] 上线前已冻结共享数据库的所有写入端并完成备份。
- [ ] `server` 和 `web` 均为 `healthy`。
- [ ] 首页返回 `200`，无密码返回 `401`，正确密码验证返回 `204`，Dashboard 返回 `200`。
- [ ] Web 只绑定预期地址，后端和数据库端口未暴露公网。
- [ ] 最终生产域名使用有效 HTTPS。
- [ ] 桌面和 `390x844` 移动视口完成实际页面验收。
- [ ] 已保留上一版本镜像、发布标识和数据库备份，并明确回滚边界。
