# Budlog 构建发布与 Android 安装指南

## 1. 适用范围

本文档用于交接 Budlog 的 H5、后端和 Android 薄壳构建发布流程。命令默认从 `<项目目录>` 执行，所有域名、账号、密码、证书路径和服务器地址均使用占位符。

当前正式发布线为 `1.0.0`：

- H5 继续是唯一业务页面实现，浏览器可直接访问。
- Android App 是独立的 uni-app 薄壳，通过原生 `<web-view>` 加载固定 HTTPS H5。
- 普通 H5 页面、样式和业务逻辑更新不需要重新安装 APK。
- Android 壳层、安全域名、原生权限、Manifest 或桥接能力变化必须重新发布 APK。
- H5 使用同源 `/api/v1`，没有需要写入 App 的后端 API 地址变量。

## 2. 先判断需要发布什么

| 变更类型 | H5 构建与发布 | 后端构建与发布 | Android APK | 说明 |
| --- | --- | --- | --- | --- |
| Vue 页面、样式、前端业务逻辑 | 是 | 否 | 否 | App 重开或确认刷新后加载新 H5 |
| H5 调用已有 API | 是 | 否 | 否 | 必须保持 API 契约兼容 |
| Controller、Service、Repository 或后端配置 | 视情况 | 是 | 否 | 前端契约变化时同步发布 H5 |
| Flyway migration | 视情况 | 是 | 否 | 发布前必须备份目标数据库 |
| Nginx 缓存、反向代理或部署脚本 | 视情况 | 视情况 | 否 | 需要更新服务器部署文件并重建相关容器 |
| Android 壳页面、WebView、返回键、权限、包名 | 否 | 否 | 是 | 已安装用户必须升级 APK |
| `VITE_BUDLOG_H5_URL` 或允许的 H5 origin | 否 | 否 | 是 | 地址编译进壳层，不能通过 H5 热更新 |
| H5 与 Android 壳同时依赖的新能力 | 是 | 视情况 | 是 | 先发兼容 APK，再发启用能力的 H5 |

不要为了普通 H5 更新制作 WGT。本项目当前的热更新方式是发布线上 H5。

## 3. 目录与产物

```text
<项目目录>/
├── budlog-app/                    # H5 源码
├── budlog-android-shell/          # Android 薄壳源码
├── budlog-server/                 # Spring Boot API
└── deploy/
    ├── budlog-server.jar          # build.sh 生成，不提交
    ├── h5/                        # build.sh 生成，不提交
    ├── build.sh                   # 完整构建入口
    ├── start.sh                   # 生产容器启动入口
    └── compose.sh                 # dev/prod Compose 统一入口
```

主要构建产物：

| 产物 | 来源 | 用途 |
| --- | --- | --- |
| `budlog-app/dist/build/h5/` | `npm run build:h5` | H5 原始构建结果 |
| `deploy/h5/` | `./deploy/build.sh` | 服务器发布目录，包含 `version.json` |
| `deploy/budlog-server.jar` | `./deploy/build.sh` | 后端部署 Jar |
| `budlog-android-shell/dist/build/app/` | `npm run build:app` | App 原生资源，不等同于可安装 APK |
| APK | HBuilderX 云打包或已配置的原生打包链路 | Android 安装包 |

## 4. 版本规则

### 4.1 Android 壳版本

Android 版本由以下文件共同维护：

- `budlog-android-shell/package.json`：`version`
- `budlog-android-shell/package-lock.json`：根包 `version`
- `budlog-android-shell/src/manifest.json`：`versionName`、`versionCode`

`vite.config.ts` 会校验 `package.json` 与 `manifest.json` 的可见版本一致。建议规则：

- `versionName` 使用语义版本，例如 `1.0.1`。
- `versionCode` 只能递增，当前 `1.0.0` 对应 `10000`。
- 可继续使用 `主版本 * 10000 + 次版本 * 100 + 修订号`，但一旦 APK 已分发，不得复用或降低 `versionCode`。

更新 npm 版本时可执行：

```sh
cd <项目目录>/budlog-android-shell
npm version <新versionName> --no-git-tag-version
```

随后手工同步 `manifest.json` 的 `versionName` 和递增后的 `versionCode`。不要让 `npm version` 自动创建 Git tag。

### 4.2 H5 版本

H5 的线上版本以 `version.json` 中的 `buildId` 为准，不以 APK 版本为准。正式构建必须给每次发布使用唯一值，推荐：

```text
h5-YYYYMMDD-NN
```

例如：`h5-20260930-01`。允许字符为字母、数字、点、下划线、加号和连字符，总长度不超过 64。

H5 在生产环境进入前台时最多每 5 分钟检查一次 `/version.json`。发现新 `buildId` 后提示用户刷新，不强制丢弃正在编辑的内容。

### 4.3 Bridge 版本

`budlog-android-shell/src/config/environment.ts` 中的 `BRIDGE_VERSION` 当前为 `0`，表示没有开放原生桥接。只有新增并稳定发布桥接协议时才递增；普通 H5 或 WebView 修复不修改它。

## 5. 配置与变量

### 5.1 Android 壳 H5 地址

在本机创建以下忽略文件：

```text
budlog-android-shell/.env.local
```

内容只配置线上 H5 根地址：

```dotenv
VITE_BUDLOG_H5_URL=https://<正式H5域名或域名加端口>/
```

约束：

- 正式构建必须使用 HTTPS。
- 必须是绝对 URL。
- 不得包含用户名、密码、查询参数或 `#` 片段。
- 壳层只允许该 URL 的同源页面，修改域名或端口后必须重新发布 APK。
- 不要把家庭访问密码、数据库信息或签名信息写入此文件。

`.env.local` 已被 Git 忽略，不得强制提交。

### 5.2 H5 构建变量

| 变量 | 必填 | 说明 |
| --- | --- | --- |
| `BUDLOG_BUILD_ID` | 正式发布建议必填 | 写入 H5 和 `version.json` 的唯一发布标识 |
| `BUDLOG_JAVA_VERSION` | 否 | `build.sh` 使用的 SDKMAN Java，默认 `8.0.361-orcl` |
| `MAVEN_HOME` | 否 | Maven 安装目录，默认 `$HOME/apache-maven-3.6.0` |
| `MAVEN_BIN` | 否 | Maven 可执行文件，优先于 `MAVEN_HOME` |
| `MAVEN_REPO_LOCAL` | 否 | Maven 离线仓库，默认 `$HOME/apache-maven-3.6.0/repository` |

未设置 `BUDLOG_BUILD_ID` 时会生成时间型标识，适合本地验证，不建议用于人工可追溯的正式发布。

### 5.3 Compose 与服务端变量

默认读取 `<项目目录>/.env`。可以用 `BUDLOG_ENV_FILE` 指向其他受控文件，但不要同时维护根 `.env` 和 `deploy/.env` 两套冲突配置。

| 变量 | 必填 | 说明 |
| --- | --- | --- |
| `POSTGRESQL_HOST` | 是 | 容器可访问的 PostgreSQL 地址 |
| `POSTGRESQL_PORT` | 否 | 默认 `5432` |
| `POSTGRESQL_USER` | 是 | 数据库账号 |
| `POSTGRESQL_PASSWORD` | 是 | 数据库密码 |
| `POSTGRESQL_DATABASE` | 开发环境 | 固定为 `budlog_dev` |
| `POSTGRESQL_PROD_DATABASE` | 生产环境 | 固定为 `budlog` |
| `APP_ACCESS_PASSWORD` | 生产必填 | 家庭访问密码 |
| `BUDLOG_TIMEZONE` | 否 | 默认 `Asia/Shanghai` |
| `BUDLOG_FEEDING_INTERVAL_MIN` | 否 | 默认 `180` |
| `BUDLOG_DEV_WEB_PORT` | 否 | 开发 H5 端口，默认 `5174` |
| `BUDLOG_PROD_WEB_PORT` | 否 | 生产 H5 端口，默认 `5173` |
| `BUDLOG_IMAGE_TAG` | 否 | Docker 镜像标签，默认 `local` |
| `BUDLOG_CORS_ALLOWED_ORIGINS` | 否 | 额外跨域来源；同源部署通常不需要 |
| `BUDLOG_ENV_FILE` | 否 | 覆盖 Compose 使用的环境文件路径 |
| `BUDLOG_BACKUP_DIR` | 备份时可选 | 数据库备份输出目录 |

`.env` 权限应为 `600`，不得提交、打包、打印或复制到文档。

### 5.4 Android Manifest 与签名

正式 APK 发布前核对：

- DCloud AppID：`budlog-android-shell/src/manifest.json` 的 `appid`
- 包名：`dev.oumuv.budlog`
- `versionName` 与 `versionCode`
- 权限仅包含当前业务实际需要的项目
- 正式签名证书、别名和密码

签名文件和密码保存在项目目录外并离线备份。使用 HBuilderX CLI 云打包时，配置文件也放在项目外，权限设为 `600`。至少需要配置包名、证书类型、证书文件、证书别名、证书密码和 keystore 密码。不要把密码直接写入终端历史或 Git。

## 6. 首次安装依赖

仅在 `node_modules` 不存在或 lockfile 发生变化时执行：

```sh
cd <项目目录>/budlog-app
npm ci

cd <项目目录>/budlog-android-shell
npm ci
```

日常构建不要重复执行 `npm install`，也不要无目的更新 lockfile。

## 7. 本地构建与检查

### 7.1 只验证 H5

```sh
cd <项目目录>/budlog-app
npm run type-check
BUDLOG_BUILD_ID=h5-<YYYYMMDD-NN> npm run build:h5
```

检查产物：

```sh
test -s dist/build/h5/index.html
test -s dist/build/h5/version.json
```

本地开发服务器：

```sh
cd <项目目录>/budlog-app
npm run dev:h5 -- --host 0.0.0.0
```

### 7.2 完整部署产物

正式发布优先使用仓库入口：

```sh
cd <项目目录>
BUDLOG_BUILD_ID=h5-<YYYYMMDD-NN> ./deploy/build.sh
```

该脚本会：

1. 使用指定 JDK 8 和 Maven 3.6.0 离线构建后端。
2. 执行 H5 类型检查与构建。
3. 生成 `version.json`。
4. 将 Jar 复制到 `deploy/budlog-server.jar`。
5. 将新哈希资源复制到 `deploy/h5/`，保留旧哈希资源。
6. 最后原子替换 `index.html` 和 `version.json`。

检查产物：

```sh
test -s deploy/budlog-server.jar
test -s deploy/h5/index.html
test -s deploy/h5/version.json
jq -e '.buildId and .builtAt' deploy/h5/version.json
```

### 7.3 Android 壳静态构建

先确认 `budlog-android-shell/.env.local` 已配置，然后执行：

```sh
cd <项目目录>/budlog-android-shell
npm run type-check
npm run build:app
```

`build:app` 生成的是 App 原生资源，不是可直接安装的 APK。APK 仍需 HBuilderX 云打包或另行配置的原生打包工程。

## 8. H5 单独热发布

适用于只修改 H5 页面、样式或前端逻辑。Android APK 和后端不变。

### 8.1 本机构建发布包

```sh
cd <项目目录>
export RELEASE_ID="h5-<YYYYMMDD-NN>"
BUDLOG_BUILD_ID="$RELEASE_ID" ./deploy/build.sh

export H5_BUNDLE="budlog-${RELEASE_ID}.tar.gz"
tar -C deploy -czf "$H5_BUNDLE" h5
shasum -a 256 "$H5_BUNDLE" > "$H5_BUNDLE.sha256"
```

`build.sh` 当前仍会同时构建后端，但 H5 单独发布时只上传 H5 包。这样可以继续复用脚本中的版本清单和原子产物规则。

上传到服务器临时目录：

```sh
scp "$H5_BUNDLE" "$H5_BUNDLE.sha256" <部署账号>@<服务器>:/tmp/
```

### 8.2 服务器原子切换 H5

以下示例假设服务器项目目录为 `<服务器项目目录>`：

```sh
cd /tmp
sha256sum -c "budlog-<RELEASE_ID>.tar.gz.sha256"

export RELEASE_STAGE="$(mktemp -d /tmp/budlog-h5.XXXXXX)"
tar -xzf "budlog-<RELEASE_ID>.tar.gz" -C "$RELEASE_STAGE"

export H5_TARGET="<服务器项目目录>/deploy/h5"
test -s "$RELEASE_STAGE/h5/index.html"
test -s "$RELEASE_STAGE/h5/version.json"
jq -e '.buildId and .builtAt' "$RELEASE_STAGE/h5/version.json"

rsync -a \
  --exclude '/index.html' \
  --exclude '/version.json' \
  "$RELEASE_STAGE/h5/" "$H5_TARGET/"

install -m 0644 "$RELEASE_STAGE/h5/index.html" "$H5_TARGET/.index.html.next"
install -m 0644 "$RELEASE_STAGE/h5/version.json" "$H5_TARGET/.version.json.next"
mv -f "$H5_TARGET/.index.html.next" "$H5_TARGET/index.html"
mv -f "$H5_TARGET/.version.json.next" "$H5_TARGET/version.json"
```

`deploy/h5` 是 Web 容器的只读绑定挂载，单独更新 H5 不需要重建或重启容器。不要先删除旧目录；旧哈希资源需要为仍打开的页面保留。

### 8.3 H5 发布验收

```sh
curl -fsSI "https://<正式H5域名或域名加端口>/"
curl -fsS "https://<正式H5域名或域名加端口>/version.json" | jq .
```

至少验证：

- 浏览器 H5 可打开并登录。
- Android App 重开后可打开同一 H5。
- 已打开的旧页面收到新版提示，选择“稍后”不会强制刷新。
- 选择“刷新”后设置页展示新的 H5 `buildId`。
- `/api/v1` 请求仍为同源且业务读写正常。

回滚时使用上一版 H5 发布包重复“服务器原子切换 H5”步骤，不需要回滚 APK。发布包至少保留当前版和上一版。

## 9. 后端或完整服务发布

涉及后端、Nginx、Dockerfile、部署脚本或数据库 migration 时，使用完整发布。

### 9.1 本地生成与打包

```sh
cd <项目目录>
export RELEASE_ID="<YYYYMMDD-NN>"
BUDLOG_BUILD_ID="h5-${RELEASE_ID}" ./deploy/build.sh

export RELEASE_BUNDLE="budlog-prod-${RELEASE_ID}.tar.gz"
tar -czf "$RELEASE_BUNDLE" \
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

shasum -a 256 "$RELEASE_BUNDLE" > "$RELEASE_BUNDLE.sha256"
```

不要把 `.env`、数据库备份、源码依赖目录或签名文件放入发布包。

### 9.2 服务器部署

1. 校验发布包摘要并解压到临时目录。
2. 保留服务器现有根 `.env`，不要用发布包覆盖。
3. 如果包含 migration 或高风险后端变更，先执行 `./deploy/backup.sh prod`。
4. 先复制新静态资源，再原子替换 Jar、`index.html` 和 `version.json`。
5. 更新其余 `deploy/` 文件后执行：

```sh
cd <服务器项目目录>
chmod +x deploy/compose.sh deploy/start.sh deploy/backup.sh deploy/restore.sh
./deploy/compose.sh prod config --quiet
./deploy/start.sh
```

`start.sh` 会检查产物和生产变量，构建服务器架构的 `server`、`web` 镜像，并强制重建两个服务容器。

### 9.3 完整发布验收

```sh
cd <服务器项目目录>
./deploy/compose.sh prod ps
./deploy/compose.sh prod logs --tail=200 server web

curl -fsS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:<生产Web端口>/
curl -sS -o /dev/null -w '%{http_code}\n' -X POST \
  http://127.0.0.1:<生产Web端口>/api/v1/access/verify
```

预期：

- `server` 和 `web` 均为 `healthy`。
- 首页返回 `200`。
- 未携带家庭密码的验证请求返回 `401`。
- 正式 HTTPS 地址可登录并完成一次读取和写入。
- 后端实际连接 `budlog`，不能连接 `budlog_dev`。

## 10. Android 调试、打包与安装

### 10.1 前置检查

```sh
export PROJECT_ROOT="<项目目录>"
export HX_CLI="<HBuilderX CLI 路径>"

"$HX_CLI" version
adb version
adb devices
```

先部署并验证正式 HTTPS H5，再构建 APK。壳层不携带 H5 业务文件，线上 H5 不可用时 APK 也无法进入业务页面。

### 10.2 运行到 Android 模拟器或设备

```sh
"$HX_CLI" project open --path "$PROJECT_ROOT/budlog-android-shell"
"$HX_CLI" devices list --platform android
"$HX_CLI" launch app-android \
  --project "$PROJECT_ROOT/budlog-android-shell" \
  --deviceId "<设备ID>"
```

同一时间只保留一条 HBuilderX 运行/编译链路，避免重复的 `npm run dev:app` 与 HBuilderX 构建相互覆盖产物。

查看最近一次运行日志：

```sh
"$HX_CLI" logcat app-android \
  --project "$PROJECT_ROOT/budlog-android-shell" \
  --deviceId "<设备ID>" \
  --mode lastBuild
```

### 10.3 正式 APK 打包

推荐在 HBuilderX 中使用固定 DCloud 账号和固定正式签名。CLI 形式为：

```sh
"$HX_CLI" pack \
  --project "$PROJECT_ROOT/budlog-android-shell" \
  --platform android \
  --config "<项目外安全目录>/budlog-android-pack.json"
```

打包配置文件按 HBuilderX `pack` 官方格式维护，并放在项目外。正式包使用自有证书时需要核对：

- `android.packagename=dev.oumuv.budlog`
- `android.androidpacktype=0`
- `android.certfile`
- `android.certalias`
- `android.certpassword`
- `android.storepassword`

云打包前还要确认 HBuilderX 已登录正确 DCloud 账号，AppID、包名、版本号与签名归属一致。

### 10.4 使用 ADB 覆盖安装

```sh
adb devices
adb install -r "<APK路径>"
adb shell monkey -p dev.oumuv.budlog -c android.intent.category.LAUNCHER 1
```

`-r` 会保留现有 App 数据。不要为了普通升级先卸载 App；卸载会清除本地保存的登录信息。若提示签名不一致，应找回上一版使用的正式签名，不能直接卸载来绕过正式升级问题。

### 10.5 Android 验收

至少检查：

1. 启动后留在 App 内，不拉起外部浏览器。
2. 显示正式 H5 登录页，家庭密码可正常验证。
3. 首页、记录新增、设置页和至少一个写操作正常。
4. Android 返回键优先回退 H5 历史，无历史时退出 App。
5. 外部域名和非 HTTPS scheme 被阻止。
6. 断网、加载失败、超时和“重新加载”状态可恢复。
7. 设置页显示正确的 Android 壳版本和 H5 `buildId`。
8. 原浏览器 H5 仍可正常使用。

## 11. 1.0.0 发布描述

版本：Android Shell `1.0.0`（`versionCode 10000`），H5 `1.0.0` 发布线。

主要变化：

- 新增独立 Android 薄壳，通过固定 HTTPS origin 在 App 内加载正式 H5。
- 保留原浏览器 H5，浏览器和 Android 共用同一套业务页面及同源 API。
- 支持 H5 独立在线发布、版本检查、用户确认刷新和快速回滚，无需频繁更新 APK。
- Android 内嵌环境停用 PWA 安装入口和 Service Worker，避免双重缓存管理。
- WebView 禁止远程页面访问完整 `plus` API，并阻止外部 origin 在 App 内导航。
- 增加断网、超时、加载失败、重试及 Android 返回键处理。
- 调整 Nginx 缓存策略，入口和版本清单不强缓存，哈希资源长期缓存。
- H5 发布先复制资源，再原子替换入口和版本清单，保留旧哈希资源。

已验证基线：Android 模拟器可在 App 内打开正式 H5 登录页，并能正常登录进入业务页面。正式签名 APK、家庭实际设备和 H5 在线更新/回滚仍需每次发布按本文档重新验收。

## 12. 常见问题

### App 一直停留在“正在打开 Budlog”

- 确认线上 H5 在设备浏览器中可访问且证书受信任。
- 确认 `VITE_BUDLOG_H5_URL` 使用 HTTPS 且没有查询参数或片段。
- 确认重新编译并覆盖安装了最新 APK。
- 不要通过 `setStyle()` 将 App-vue `<web-view>` 移到屏幕外；当前实现依赖其默认全屏行为。

### App 能打开但仍是旧 H5

- 查看正式 `/version.json` 的 `buildId`。
- 确认发布时同时替换了 `index.html` 和 `version.json`。
- 将 App 切回前台或重开；版本检查最多每 5 分钟一次。
- 不要删除仍被旧页面引用的哈希资源。

### H5 需要修改后端 API 地址吗

不需要。H5 使用相对路径 `/api/v1`，由同源 Nginx 反向代理到后端。只有部署架构改为跨域时才需要重新设计 CORS 和 API 地址策略。

### 哪些修改必须重新装 APK

Android 壳代码、固定 H5 origin、Manifest、权限、DCloud AppID、包名、签名或原生桥接发生变化时必须重新打 APK。普通 Vue 页面和业务逻辑只发布 H5。

## 13. 官方工具参考

- HBuilderX CLI Android 运行：`https://hx.dcloud.net.cn/cli/launch-app?id=launch-app-android`
- HBuilderX CLI App 打包：`https://hx.dcloud.net.cn/cli/pack`
- uni-app `<web-view>`：`https://uniapp.dcloud.net.cn/component/web-view.html`
