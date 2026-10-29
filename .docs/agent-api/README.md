# Budlog Agent REST API（只读 v1）

> 接口前缀 `/api/v1/agent/v1`。本文根据 [实施计划](../budlog_agent_api_implementation_plan_v1.md) 及开发中的 Java DTO/过滤器编写；以最终 Controller、Service 和实际部署验收为准。目前文档不表示 API 已发布、MCP 已接入或定时任务可用。机器可读契约见 [openapi.yaml](openapi.yaml)。

## 范围与安全边界

仅提供 GET：`/profile`、`/feedings`、`/feedings/daily`、`/feedings/trends`、`/care/daily`、`/daily-report`、`/weights/trends`。返回当前宝宝的已录入、未软删除数据；不提供写入，不应当作医疗诊断或完整照护记录。原家庭 API 的 `X-App-Password` **不能**认证 Agent；Agent Bearer Token 也不授予原家庭 API 访问权。不要将 Token 放入 URL、任务提示词、示例代码仓库或日志。

### 配置与密钥

Agent 默认关闭：`BUDLOG_AGENT_ENABLED=false`。生产使用与项目根 `.env` 分离的、仅包含 Agent 开关与摘要的 `.agent.env`（权限 0600）；开发环境可用 `BUDLOG_AGENT_ENABLED=true` 和 `BUDLOG_AGENT_CLIENTS_JSON` 注入。启用时运行时需提供 `BUDLOG_AGENT_ENABLED=true`、`BUDLOG_AGENT_CLIENTS_JSON`，后者格式为 **JSON 对象** `{"clientId":"SHA256(hex)"}`（每客户端独立随机 Token，值为其 SHA-256 的 64 位十六进制摘要，**不是**明文 Token）。客户端 ID 允许字母、数字、`_`、`-`，长度 1–64。`BUDLOG_AGENT_MAX_RANGE_DAYS` 默认 90（有效配置范围 1–90）；所有日期范围（包括趋势的前后两个窗口）均受此上限约束。启用但客户端配置缺失/无效时应启动失败；不要通过禁用家庭密码替代 Agent 认证。

以下在安全的终端会话中生成 32 字节随机 Token 并计算摘要；**不要把真实输出粘贴到项目文件、命令行参数、工单或文档里**。示例命令仅输出 SHA-256 摘要，Token 由 `read -s` 在终端不回显输入（先由受保护的密钥管理器生成并保存随机 Token）：

```sh
read -s 'TOKEN?Paste a securely generated 32-byte-or-longer random token: '; printf '\n'
printf '%s' "$TOKEN" | shasum -a 256 | awk '{print $1}'
unset TOKEN
```

上述为 macOS/zsh 示例；若需在本机生成随机 Token，可在受保护环境中用 `openssl rand -base64 48`，将其**直接**录入密钥管理器，切勿将明文或真实摘要提交 Git。摘要也应按配置敏感信息对待。配置示意（**占位符，不可直接启动**）：`BUDLOG_AGENT_CLIENTS_JSON='{"example_client":"<64-hex-digest-from-secure-terminal>"}'`。调用方安全保管原始 Token，并通过 `Authorization: Bearer <token>` 发送；服务端按摘要匹配客户端 ID，不要求额外 `clientId` 请求头。至少 32 字节密码学安全随机数，轮换时更新摘要和客户端保存的原始 Token。

认证失败（缺失/错误）为 401；关闭模块为 404；非 GET 为 405（过滤器优先拒绝）；认证路径响应设置 `Cache-Control: no-store`。无论开发环境家庭密码开关如何，Agent 都必须独立验证。仅通过 HTTPS 对外提供服务；不要暴露后端 8080 或数据库。可按客户端限流并记录不含 Token/完整育儿明细的最小审计信息。

### 查询与响应

- 所有日期为宝宝时区日历日（默认 `Asia/Shanghai`）；`from`、`to` 均含当日，服务器按 `[from 00:00, to+1 日 00:00)` 查询。最长 90 个自然日；超限应返回 400。趋势 `days` 为 7 或 30，`endDate` 不传时默认为宝宝时区的昨天。
- `/feedings`：`from`、`to` 必填；`page` 默认 0，`size` 默认 50，最大 100，按喂养开始时间降序、ID 降序稳定分页。`data` 是 `content`、`page`、`size`、`totalElements`、`totalPages`。
- `/feedings/daily`：`from`、`to`；每日序列。`/feedings/trends`：`days`、可选 `endDate`，返回本期每日序列与前一等长窗口的汇总比较。
- `/care/daily`、`/daily-report`：`date`；前者返回尿便、存奶和截至当日最近实测体重，后者附喂养、宝宝资料和数据质量说明。`/weights/trends`：`from`、`to`，只返回真实测量点。
- 成功是 `{"code":0,"message":"success","data":...,"timestamp":"<UTC offset datetime>"}`；错误沿用 `ApiResponse`（HTTP 400/401/404/405 等；错误 `data` 可能省略），不泄露 Token。示例数据仅为虚构格式演示，不要用实际宝宝数据做公开样例。

可在**已配置的隔离 HTTPS 环境**中用只读调用验证（占位符替换后执行；不要开启 `curl -v`、shell trace 或保存包含凭据的命令历史）：

```sh
# TOKEN 在当前受保护 shell 中读取，不回显；API_BASE 是已核验的 HTTPS 域名
read -s 'TOKEN?Agent token: '; printf '\n'
curl --fail-with-body --silent --show-error \
  --header "Authorization: Bearer $TOKEN" \
  'https://api.example.invalid/api/v1/agent/v1/feedings/daily?from=2026-01-01&to=2026-01-07'
# 不要将响应中的真实育儿信息粘贴到工单/公开日志
unset TOKEN
```

`example.invalid` 是不可用的占位域名。只读调用仍可能返回隐私数据；生产验证选最小时间范围、安全终端，不留响应文件。

### 数据口径与解读限制

`BREAST_BOTTLE` 与 `FORMULA_BOTTLE` 的 `amountMl` 计入 `measuredBottleIntakeMl`；亲喂 `BREAST_DIRECT` 只按有效起止时间计算分钟，**不能换算为毫升**；有亲喂时 `totalIntakeKnown=false`。`recordCount` 为原始喂养记录数，同宝宝完全相同开始时刻合并为一个 `sessionCount`；间隔按相邻喂养开始时刻计算，并归属后一次开始的日期，包含跨午夜上一餐。没有有效结束时间时亲喂时长未知。瓶喂金额缺失时 `measuredBottleIntakeKnown=false`，可测量奶量只是已知部分；`averageBottleMl`、间隔及变化率可能为 `null`，不要将未知解释为零。

尿便 `BOTH` 为尿、便事件各 +1；这是**记录事件**而非实际湿尿布总数。`milkStorage.addedMl` 仅代表新增存奶，不是摄入量或实时库存。最近体重需同时看 `measuredAt`、`ageOfMeasurementDays`，不可把旧测量称作今日体重；体重趋势只用实测时间，点间增速非医学结论。空记录不等于未喂养/未排便；`dataQuality.completenessVerified=false` 表示无法证实记录完整。任何 AI 摘要在取数失败时应明确说明失败，不得编造日报。

## 生产交付限制与预检

仅发布 JAR/H5 **不会**自动更新生产 Compose 环境变量传递、独立 Agent 密钥管理或 Nginx/HTTPS 路由；这些步骤须在生产部署中单独验证。上线前由运维核对运行版本、生产 DB 与备份、Compose 是否传入上述变量、Nginx 是否正确代理 Agent 路径并限流、HTTPS 证书和端口隔离、认证失败与禁用状态、GET-only 和时区/数据统计，再决定启用。不得假设生产 API 可用，也不得仅凭文档重启或修改生产。

本 REST API **不是** ChatGPT MCP；还需独立的云端 MCP 网关、符合 ChatGPT 环境的 OAuth/授权和端到端联调。即使交互式 MCP 可用，也不能推断 ChatGPT Scheduled Tasks 可无人值守调用自定义 MCP；需单独在云端、断开本地电脑后测试定时执行与授权续期。尚未完成前不可宣称每日推送已就绪。

## 口径与实际环境

`ageDaysElapsed` 为从出生时刻开始经过的完整 24 小时数（非出生当天计为第 1 天的日龄）；当历史报表日期早于出生时钳制为 0。日报的 `baby.ageDaysElapsed` 以目标日期次日零点为计算时点。趋势只有前一个窗口存在记录且未超出出生日期等限制时才可比较；记录不完整仍需谨慎解读。生产密钥、HTTPS 与自定义 MCP 仍需独立验收。
