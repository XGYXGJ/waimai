# 多端智能外卖点餐平台

基于 Spring Boot 3 + Vue 3 的四端外卖平台（用户端 H5 / 商户端 Web / 管理端 Web / 骑手端 H5），
集成推荐算法、竞价排名与 AI 大模型应用。完整设计见 `../waimai-proposal/外卖平台-完整设计文档.md`。

## 目录结构

```
waimai/
├─ sql/waimai.sql            # 建库建表 + 初始数据
├─ waimai-server/            # Spring Boot 后端 (8080)
├─ waimai-ai/                # Python FastAPI AI 服务 (8000)
├─ waimai-web/               # 前端 monorepo (npm workspaces)
│  ├─ packages/shared/       # 四端共享：request/ws/工具
│  └─ apps/
│     ├─ user-h5/            # 用户端   http://localhost:5173
│     ├─ rider-h5/           # 骑手端   http://localhost:5174
│     ├─ merchant-web/       # 商户端   http://localhost:5175
│     └─ admin-web/          # 管理端   http://localhost:5176
├─ docker-compose.yml        # 一键部署 (mysql/redis/rabbitmq/server/ai/nginx)
└─ nginx.conf
```

## 快速启动（开发模式）

### 1. 中间件（Redis / RabbitMQ / AI 由后端自动启停）
只要本机有可用的 MySQL（开发环境连 `localhost:3306`，见 `application-dev.yml`）并已导入建表脚本，
Redis、RabbitMQ、AI 服务**都不用手动启动**：后端启动时会自动 `docker compose up -d --no-deps` 拉起这三个容器，
后端关闭时自动 `docker compose stop` 停掉（前提是本机 Docker Desktop 已在运行）。

如需用容器里的 MySQL，或想手动准备中间件：
```bash
docker compose up -d mysql redis rabbitmq
# 等待 mysql 就绪后导入建表
docker exec -i waimai-mysql mysql -uroot -pwaimai123 waimai < sql/waimai.sql
```

### 2. 后端（一键启动：后端 + docker 依赖 + 四端前端）
用 IDE 运行 `WaimaiApplication`（需 JDK 17+，会自动建表所需初始数据），或：
```bash
cd waimai-server && mvn spring-boot:run   # 本机需 Maven
```

**在 IDEA 里 Run 一次 `WaimaiApplication`，整套系统就都起来了**：后端会依次
① 自动拉起 Redis / RabbitMQ / AI 三个 docker 容器，并等到 AI 的 `/health` 就绪；
② 自动拉起四端前端 Vite dev server（5173~5176）。
点 Stop 关闭后端时，四端前端与这三个容器会一起被停掉 —— 无需手动执行下面的第 3、4 步。

- 前端自动启停开关：`application.yml` 的 `waimai.frontend.auto-start`
- docker 依赖自动启停开关：`application.yml` 的 `waimai.deps.*`（`auto-start` / `services` /
  `compose-file` / `ai-health-url` / `start-timeout-seconds` / `ready-timeout-seconds` /
  `check-database` / `database-service` / `database-ready-timeout-seconds` /
  `stop-timeout-seconds` / `stop-on-shutdown`）；
  生产部署（后端跑在容器里）应把 `waimai.deps.auto-start` 与 `waimai.frontend.auto-start` 都置为 `false`。
- docker 依赖是在 **Spring 容器 refresh 之前**（`ApplicationPreparedEvent` 前置阶段）拉起的，所以后端自己的
  RabbitMQ 监听器启动时中间件已经就绪，启动日志里不会再出现 `AmqpConnectException: Connection refused`。
- 拉起之后还会等**服务真正就绪**再进入容器 refresh：Redis 探 `127.0.0.1:6379`，RabbitMQ 直接做一次
  **AMQP 握手**（Docker 的端口映射在容器刚起来时就已监听宿主端口，只探 TCP 会误判「已就绪」，
  实测 RabbitMQ 从容器启动到能握手约 5 秒），AI 轮询 `/health`。等待上限 `ready-timeout-seconds`（默认 60）。
- **本机 MySQL 不归 docker 管**（它是 Windows 服务、后端的数据源，compose 里的 mysql 在 3307 只给容器内的 server/ai 用）：
  启动前置阶段会拿 `spring.datasource.url` 里的 host:port 探一次，连不上就打印一句人话 +
  可执行提示，而不是等 Hikari 抛一大串 `CommunicationsException`；若配了 `waimai.deps.database-service: MySQL80`，
  还会尝试 `net start MySQL80`（**IDEA 需以管理员身份运行才有效**，否则只失败并给提示）。
  它不会被关闭钩子停掉（数据安全，其它工具可能也在用）。
  遇到「连不上数据库」，先确认这个服务在跑：管理员 PowerShell 执行 `net start MySQL80`。
- 注意：IDEA 用 **Force Kill**（而不是 Stop）结束进程时 JVM 不会执行关闭钩子，容器不会被自动停掉，
  需要手动 `docker compose stop redis rabbitmq ai`。

### 订单超时取消的双保险
「15 分钟未支付自动取消」由两条路径共同保证，时长共用 `RabbitMQConfig.ORDER_PAY_TIMEOUT_MINUTES = 15`：
- 正常路径：下单时投递延迟消息（延迟队列 TTL 15 分钟 → 死信队列 `waimai.order.timeout-q`），
  由 `OrderTimeoutListener` 消费后调用 `OrderService.cancelOnTimeout()` 取消；
- 兜底路径：`ScheduleJobs.cancelTimeoutOrders()` 每分钟扫描一次 `status = 'PENDING_PAYMENT'` 且创建超过 15 分钟的订单并取消。
  因此 RabbitMQ 不可用也不会让订单一直挂着（下单时投递失败只记一条 ERROR 日志，不影响下单事务）。

首次启动自动创建演示数据（详见下方账号表）。

### 3. AI 服务（默认由后端自动启动，通常无需手动执行）
后端启动时会自动 `docker compose up -d --no-deps ai`，并轮询 `http://localhost:8000/health` 直到就绪
（默认最多等 120 秒，超时只在日志里告警，不影响主业务）。

只有在**本地用 Python 直接调试 AI**（改 `waimai-ai` 代码想热重载）时才需要手动跑：先把
`application.yml` 的 `waimai.deps.auto-start` 置为 `false`，再执行
```bash
cd waimai-ai
pip install -r requirements.txt
uvicorn app.main:app --port 8000
```

### 4. 前端（可选，通常无需手动启动）
默认由后端自动拉起；如需手动启动或独立调试前端：
```bash
cd waimai-web
npm install
npm run dev:user      # 用户端 5173
npm run dev:rider     # 骑手端 5174
npm run dev:merchant  # 商户端 5175
npm run dev:admin     # 管理端 5176
```

## 演示账号（首次启动自动生成）

| 角色 | 账号 | 密码 | 端 |
|---|---|---|---|
| 管理员 | admin | admin123 | 管理端 |
| 用户 | 13800000001 | 123456 | 用户端 |
| 商户 | 13800000002 | 123456 | 商户端 |
| 骑手 | 13800000003 | 123456 | 骑手端 |

## 数据库密码配置

后端数据库密码**不写死在配置里**，通过环境变量 `MYSQL_PASSWORD` 传入：

```bash
# IDEA 运行配置：VM options 或 Environment 里加
MYSQL_PASSWORD=你的MySQL密码
# 或命令行
MYSQL_PASSWORD=你的密码 mvn spring-boot:run
```

开发环境（`application-dev.yml`）连接本机 `localhost:3306/waimai`，用户 `root`；
生产环境（`application-prod.yml`）连接 docker 的 mysql 容器，密码由 `docker-compose.yml` 的 `MYSQL_PASSWORD` 注入。

## LLM API Key 与地图 Key 配置（重要）

**所有 Key 均在管理端「系统设置 → AI 与地图配置」页面填写**，存入数据库 `sys_config` 表，
无需改代码或重启：
- LLM：Provider（deepseek/dashscope）、API Key、Base URL、模型名；可选 Ollama 地址（降级用）
- 高德地图：JS API Key（前端地图）+ Web 服务 Key（后端路径规划）

申请地址：https://open.amap.com （个人开发者免费额度足够毕设演示）。
未配置 LLM Key 时 AI 功能自动走 Ollama；两者都不可用时走内置降级话术，主业务不受影响。

## 生产部署（一键）
```bash
docker compose up -d --build
# 访问 http://localhost  (用户端)  /rider /merchant /admin
```

## 骑手端弹性分级

骑手端通过 `apps/rider-h5/.env` 的 `VITE_APP_MAP_LEVEL` 控制级别：
- `0` = L1 纯接单状态流转（无地图）
- `1` = L2 加地图与路线
- `2` = L3 加实时定位/模拟骑行（默认）

演示环境定位不可靠时，骑手端接单后自动启用「模拟骑行」沿规划路线移动。
