# Budlog 本地构建与运行指南

## 1. 适用范围

本文档用于在 macOS 本地构建和运行 Budlog，包括开发环境、生产配置预演、日志查看、数据库备份及恢复。

项目使用一套 PostgreSQL 连接凭据，并通过不同数据库名隔离环境：

| 环境 | Compose 项目名 | Spring Profile | 数据库 | 默认访问地址 |
| --- | --- | --- | --- | --- |
| 开发 | `budlog-dev` | `dev` | `budlog_dev` | `http://127.0.0.1:5174` |
| 生产 | `budlog-prod` | `prod` | `budlog` | `http://127.0.0.1:5173` |

PostgreSQL 是外部服务，Compose 不创建数据库容器或数据卷。`budlog_dev` 和 `budlog` 必须提前存在，并允许容器使用 `.env` 中的账号连接。

## 2. 项目结构

```text
<项目目录>/
├── budlog-app/       # uni-app H5 前端
├── budlog-server/    # Spring Boot 后端
├── deploy/           # Docker、Compose 与运维脚本
├── .env              # 本地环境变量，不提交到 Git
└── .docs/            # 本地项目文档
```

后续命令均从 `<项目目录>` 执行。

## 3. 前置条件

- Docker 环境可用，并支持 `docker compose` v2。
- 已安装 `jq`，备份和恢复脚本使用它解析 Compose 配置。
- SDKMAN 已安装 JDK `8.0.361-orcl`。
- Maven 位于 `$HOME/apache-maven-3.6.0`。
- Maven 本地仓库位于 `$HOME/apache-maven-3.6.0/repository`，且已包含后端构建所需依赖。构建脚本强制离线运行，不会重复下载 Jar。
- Node.js 和 npm 可用，`budlog-app/node_modules` 已安装。构建脚本不会自动安装前端依赖。
- PostgreSQL 可从 Docker 容器访问。

可先检查工具：

```sh
docker compose version
jq --version
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk current java
"$HOME/apache-maven-3.6.0/bin/mvn" -version
```

如果 PostgreSQL 运行在当前 Mac 上，数据库地址通常填写 `host.docker.internal`，不能填写容器内指向自身的 `127.0.0.1`。如果 PostgreSQL 位于其他机器，填写容器可路由到的主机名或 IP。

## 4. 环境变量

根目录 `.env` 同时供开发和生产 Compose 使用。首次配置且文件不存在时，可执行：

```sh
cp deploy/.env.example .env
```

如果 `.env` 已存在，不要用示例文件覆盖。字段说明如下：

配置完成后收紧本地权限：

```sh
chmod 600 .env
```

| 变量 | 必填 | 用途与默认值 |
| --- | --- | --- |
| `POSTGRESQL_HOST` | 是 | PostgreSQL 地址，必须能从容器访问 |
| `POSTGRESQL_PORT` | 否 | PostgreSQL 端口，默认 `5432` |
| `POSTGRESQL_USER` | 是 | 开发和生产共用的数据库账号 |
| `POSTGRESQL_PASSWORD` | 是 | 开发和生产共用的数据库密码 |
| `POSTGRESQL_DATABASE` | 否 | 开发数据库，固定建议为 `budlog_dev` |
| `POSTGRESQL_PROD_DATABASE` | 否 | 生产数据库，固定建议为 `budlog` |
| `APP_ACCESS_PASSWORD` | 生产必填 | 生产环境家庭访问密码；为空时生产配置直接拒绝启动或构建 |
| `BUDLOG_TIMEZONE` | 否 | 业务时区，默认 `Asia/Shanghai` |
| `BUDLOG_FEEDING_INTERVAL_MIN` | 否 | 默认喂养间隔分钟数，默认 `180` |
| `BUDLOG_DEV_WEB_PORT` | 否 | 开发 Web 端口，默认 `5174` |
| `BUDLOG_PROD_WEB_BIND` | 否 | 生产 Web 监听地址，默认 `127.0.0.1` |
| `BUDLOG_PROD_WEB_PORT` | 否 | 生产 Web 端口，默认 `5173` |
| `BUDLOG_IMAGE_TAG` | 否 | 本地镜像标签，默认 `local` |
| `BUDLOG_CORS_ALLOWED_ORIGINS` | 否 | 生产环境额外跨域来源；同源经 Nginx 访问时通常不需要设置 |

最小配置示例只使用占位值：

```dotenv
POSTGRESQL_HOST=host.docker.internal
POSTGRESQL_PORT=5432
POSTGRESQL_USER=<数据库账号>
POSTGRESQL_PASSWORD=<数据库密码>
POSTGRESQL_DATABASE=budlog_dev
POSTGRESQL_PROD_DATABASE=budlog
APP_ACCESS_PASSWORD=<生产家庭访问密码>
```

不要把 `.env`、数据库备份或真实密码提交到 Git。

## 5. 生成部署产物

```sh
./deploy/build.sh
```

构建脚本依次执行以下步骤：

1. 通过 SDKMAN 切换到 JDK `8.0.361-orcl`。
2. 使用指定 Maven 3.6.0 和本地仓库离线打包后端，并跳过测试。
3. 执行前端 TypeScript 类型检查和 H5 构建。
4. 将 Jar 和 H5 同步到 `deploy/budlog-server.jar` 与 `deploy/h5/`。

Docker 镜像不再由 `build.sh` 生成。生产环境由 `start.sh` 在部署服务器构建镜像并启动。

可按需覆盖本机工具位置：

```sh
BUDLOG_JAVA_VERSION=<SDKMAN版本> \
MAVEN_HOME=<Maven目录> \
MAVEN_REPO_LOCAL=<Maven本地仓库> \
./deploy/build.sh
```

`MAVEN_BIN` 可单独指定 Maven 可执行文件。

## 6. 启动与日常操作

启动开发环境：

```sh
./deploy/compose.sh dev up -d --build
```

启动生产环境：

```sh
./deploy/start.sh
```

查看状态：

```sh
./deploy/compose.sh dev ps
./deploy/compose.sh prod ps
```

查看日志：

```sh
./deploy/compose.sh dev logs -f --tail=200 server web
./deploy/compose.sh prod logs -f --tail=200 server web
```

重启服务：

```sh
./deploy/compose.sh dev restart
./deploy/compose.sh prod restart
```

停止并删除当前环境的应用容器：

```sh
./deploy/compose.sh dev down
./deploy/compose.sh prod down
```

两个 Compose 项目名不同，可以同时存在。默认端口也不同，但它们连接同一 PostgreSQL 服务中的不同数据库。`down` 不会删除外部 PostgreSQL 数据。

## 7. 数据库备份

备份前脚本会解析目标数据库名、实际连接数据库并比对两者；不一致时立即退出。备份采用 PostgreSQL 自定义格式，并在落盘前使用 `pg_restore --list` 校验。

必须显式传入 `dev` 或 `prod`，脚本不会默认选择生产环境。

备份开发数据库：

```sh
./deploy/backup.sh dev
```

备份生产数据库：

```sh
./deploy/backup.sh prod
```

默认备份目录为 `deploy/backups`。推荐把长期备份放在项目外：

```sh
BUDLOG_BACKUP_DIR="$HOME/budlog-backups" ./deploy/backup.sh dev
BUDLOG_BACKUP_DIR="$HOME/budlog-backups" ./deploy/backup.sh prod
```

脚本成功后只输出最终备份路径。备份目录权限设为 `700`，备份文件权限设为 `600`。

## 8. 数据库恢复

恢复会替换目标数据库内的现有对象和数据，执行前必须先确认备份文件、目标环境及数据库名。恢复脚本不会创建数据库。

开发环境示例：

```sh
./deploy/restore.sh "$HOME/budlog-backups/dev-budlog_dev-YYYYMMDD-HHMMSS.dump" dev
```

生产环境示例：

```sh
./deploy/restore.sh "$HOME/budlog-backups/prod-budlog-YYYYMMDD-HHMMSS.dump" prod
```

脚本会先验证备份格式和实际连接数据库，然后要求输入完整确认词：

```text
开发：RESTORE budlog_dev
生产：RESTORE budlog
```

确认后，脚本会在需要时停止对应环境的后端服务，使用单事务执行清理和恢复；无论恢复成功或失败，原本运行的后端都会尝试重新启动。输入其他内容会取消恢复，不改动数据库。

## 9. 常见故障

### Docker API 权限错误

确认 Docker Desktop 或 OrbStack 已启动，并检查当前终端可以执行：

```sh
docker info
```

### 数据库连接失败

- 确认 `.env` 中主机、端口、账号和密码正确。
- 确认容器能访问该地址，macOS 宿主机数据库通常使用 `host.docker.internal`。
- 确认 `budlog_dev` 和 `budlog` 已创建，并授权给配置账号。
- 检查 PostgreSQL 的监听地址、`pg_hba.conf` 和本机防火墙。

### 生产配置提示 `APP_ACCESS_PASSWORD is required`

在根目录 `.env` 设置非空 `APP_ACCESS_PASSWORD`。开发配置固定关闭家庭密码，不使用该值。

### SDKMAN 或 JDK 版本不存在

确认 `$HOME/.sdkman/bin/sdkman-init.sh` 存在，并通过 SDKMAN 安装或启用 `8.0.361-orcl`。也可用 `BUDLOG_JAVA_VERSION` 指定本机已安装的兼容 JDK 8 标识。

### Maven 离线构建缺少依赖

构建脚本不会联网下载依赖。确认 `$HOME/apache-maven-3.6.0/repository` 完整，或用 `MAVEN_REPO_LOCAL` 指向已有仓库。不要删除已有仓库后重建。

### 前端提示 `node_modules` 缺失

在 `budlog-app` 中完成一次依赖安装后重新构建。依赖安装可能访问 npm registry，构建脚本本身不会自动执行。

### Web 端口被占用

修改 `.env` 中的 `BUDLOG_DEV_WEB_PORT` 或 `BUDLOG_PROD_WEB_PORT`，然后重新启动对应环境。

## 10. 最小验收清单

开发环境启动后执行：

```sh
./deploy/compose.sh dev ps
curl -fsS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:5174/
curl -fsS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:5174/api/v1/dashboard
```

验收结果应满足：

- `server` 和 `web` 均为 `healthy`。
- 首页和 Dashboard API 均返回 HTTP `200`。
- 开发后端连接 `budlog_dev`，生产后端连接 `budlog`。
- 生产环境未配置访问密码时会被拒绝，配置后才允许构建和启动。
- 容器使用镜像内的 Jar 和 H5 文件，不依赖宿主机目录挂载。
