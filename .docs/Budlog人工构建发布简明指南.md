# Budlog 人工构建发布简明指南

本文用于日常人工发布。故障排查、回滚和完整参数见 [详细指南](./Budlog构建发布与Android安装指南.md)。命令默认从 `<项目目录>` 执行，尖括号内容必须替换为实际值。

## 1. 判断发布类型

| 修改内容 | 操作 |
| --- | --- |
| H5 页面、样式、前端逻辑 | 只发布 H5，不重装 APK |
| 后端、migration、Nginx、部署文件 | 完整发布后端与 H5 |
| Android 壳、H5 域名、Manifest、权限、图标、原生能力 | 重新打包并安装 APK |
| H5 和壳同时依赖新能力 | 先发布兼容 APK，再发布 H5 |

执行前确认依赖已安装、正式 H5 域名使用 HTTPS。服务器根 `.env` 必须保留且不得上传、覆盖或打包。

## 2. 只发布 H5

### 2.1 本机构建上传

每次换一个新编号，不要照抄旧编号：

```sh
cd <项目目录>
RELEASE_ID=h5-20261001-01

cd budlog-app
npm run type-check
BUDLOG_BUILD_ID="$RELEASE_ID" npm run build:h5
test -s dist/build/h5/index.html
test -s dist/build/h5/version.json

cd ..
tar -C budlog-app/dist/build -czf "budlog-${RELEASE_ID}.tar.gz" h5
scp "budlog-${RELEASE_ID}.tar.gz" <部署账号>@<服务器>:/tmp/
```

### 2.2 服务器替换

```sh
cd /tmp
RELEASE_ID=h5-20261001-01
RELEASE_STAGE="$(mktemp -d /tmp/budlog-h5.XXXXXX)"
tar -xzf "budlog-${RELEASE_ID}.tar.gz" -C "$RELEASE_STAGE"

cd <服务器项目目录>
test -s "$RELEASE_STAGE/h5/index.html"
test -s "$RELEASE_STAGE/h5/version.json"
rsync -a --exclude '/index.html' --exclude '/version.json' \
  "$RELEASE_STAGE/h5/" deploy/h5/
install -m 0644 "$RELEASE_STAGE/h5/index.html" deploy/h5/.index.html.next
install -m 0644 "$RELEASE_STAGE/h5/version.json" deploy/h5/.version.json.next
mv -f deploy/h5/.index.html.next deploy/h5/index.html
mv -f deploy/h5/.version.json.next deploy/h5/version.json
```

不用重启 Web 容器，不要先删除 `deploy/h5/`。保留当前版和上一版压缩包。

### 2.3 验收

```sh
curl -fsSI https://<正式H5域名>/
curl -fsS https://<正式H5域名>/version.json
```

确认浏览器可登录和操作、`version.json` 是本次编号、Android App 重开后显示新 H5。

## 3. 发布后端或完整服务

### 3.1 本机构建上传

```sh
cd <项目目录>
RELEASE_ID=20261001-01
BUDLOG_BUILD_ID="h5-${RELEASE_ID}" ./deploy/build.sh

tar -czf "budlog-prod-${RELEASE_ID}.tar.gz" \
  deploy/budlog-server.jar deploy/h5 \
  deploy/docker-compose.yml deploy/compose.sh deploy/start.sh \
  deploy/server.Dockerfile deploy/server.Dockerfile.dockerignore \
  deploy/web.Dockerfile deploy/web.Dockerfile.dockerignore \
  deploy/nginx.conf deploy/backup.sh deploy/restore.sh
scp "budlog-prod-${RELEASE_ID}.tar.gz" <部署账号>@<服务器>:/tmp/
```

### 3.2 服务器发布

包含 migration 时，先在服务器执行 `cd <服务器项目目录> && ./deploy/backup.sh prod`，确认备份成功后继续：

```sh
cd <服务器项目目录>
tar -xzf /tmp/budlog-prod-20261001-01.tar.gz -C .
chmod +x deploy/compose.sh deploy/start.sh deploy/backup.sh deploy/restore.sh
./deploy/compose.sh prod config --quiet
./deploy/start.sh
./deploy/compose.sh prod ps
./deploy/compose.sh prod logs --tail=200 server web
```

确认 `server`、`web` 均为 `healthy`，正式 HTTPS 地址可登录和读写，后端连接的是 `budlog` 而不是 `budlog_dev`。

## 4. 用 HBuilderX 打包 APK

先检查 `budlog-android-shell/.env.local`：

```dotenv
VITE_BUDLOG_H5_URL=https://<正式H5域名>/
```

该地址必须是可访问的 HTTPS 根地址。然后：

1. 进入 `budlog-android-shell`，执行 `npm version <新versionName> --no-git-tag-version`，让 `package.json` 和 `package-lock.json` 同步更新。
2. 同步 `src/manifest.json` 的 `versionName`，并递增整数 `versionCode`。重新打同一个展示版本时可以保持 `versionName`，但 `versionCode` 仍必须递增。
3. 如果修改了 `budlog-native` 源码或 AAR，递增 `src/uni_modules/budlog-native/package.json` 的模块版本，并删除 `dist/cache/apk` 与 `dist/cache/uts_custom_android`。否则 HBuilderX 快速打包可能复用旧原生基础 APK。
4. 执行 `npm run type-check` 和 `npm run build:app`。
5. 用 HBuilderX 导入 `<项目目录>/budlog-android-shell`。
6. 打开 `src/manifest.json`，核对 AppID、包名 `dev.oumuv.budlog`、版本、图标和权限。
7. 登录固定 DCloud 账号，选择“发行 → 原生 App-云打包”。
8. 选择 Android、正式包和自有证书，填写证书信息后开始打包。
9. 下载 APK，记录版本号、时间和所用证书。原生模块有改动时，安装前必须检查 APK 的 DEX 确实包含本次新增的方法或常量。

证书与密码必须放在项目外。后续升级必须使用相同包名和签名。

## 5. 安装与验收 APK

启动模拟器或连接已开启 USB 调试的手机：

```sh
adb devices
adb install -r "<APK绝对路径>"
adb shell monkey -p dev.oumuv.budlog -c android.intent.category.LAUNCHER 1
```

`-r` 会保留 App 数据。签名不一致时应找回原证书，不要卸载绕过。安装后确认：

1. 全程留在 App 内，不打开外部浏览器。
2. 登录、首页、设置和一次写操作正常。
3. Android 返回键可以回退 H5 页面。
4. 断网有错误状态，恢复网络后可以重新加载。
5. 浏览器中的原 H5 仍可使用。
6. 原生音乐缓存改动需额外验证：首次播放完成缓存，强制结束 App 后重开，再次播放应直接使用本地文件且不重新下载音频。

## 6. 必须记住

- 普通 H5 更新不需要重新打包或安装 APK。
- 壳代码、固定 H5 origin、Manifest、权限、图标、包名、签名或原生桥接变化必须重新打包 APK。
- H5 使用同源 `/api/v1`，正常部署不需要在前端配置后端 API 地址。
- 生产 `.env`、数据库密码、家庭访问密码和签名信息不得提交或放入发布包。
