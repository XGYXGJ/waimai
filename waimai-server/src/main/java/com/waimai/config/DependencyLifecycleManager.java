package com.waimai.config;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 依赖服务生命周期：拉起/停止 docker 依赖（默认 Redis / RabbitMQ / AI 服务），
 * 即「在 IDEA 里启动 WaimaiApplication 就等于启动全部功能，关闭时一起关闭」。
 *
 * <p>本类是纯逻辑、不是 Spring Bean：由 {@link DependencyPreflight} 在
 * <b>Spring 容器 refresh 之前</b>（ApplicationPreparedEvent）创建并调用 {@link #start()}，
 * 这样 Tomcat 与 RabbitMQ 监听器开始工作时依赖已经就绪 —— 既不会有启动期
 * 「Connection refused」报错，也不存在「刚启动几秒内下单发不出 MQ 消息」的窗口。
 * 停止方向由 {@link DependencyPreflight} 注册的 JVM 关闭钩子调用 {@link #stop()}。
 *
 * <p>与 {@link FrontendLifecycleManager}（四端前端 dev server，@Order(1000) 的 ApplicationRunner）
 * 配套：依赖先于容器 refresh 起来，四端前端在后端就绪后由那个管理器拉起。
 *
 * <p>配置项（application.yml 的 waimai.deps.*，读取见 {@link Config#from(Environment)}）：
 * <ul>
 *   <li>auto-start            是否启用，默认 true（生产环境建议 false）</li>
 *   <li>services              需要接管的 compose 服务名，默认 redis,rabbitmq,ai</li>
 *   <li>compose-file          默认留空：自动从工作目录向上查找 docker-compose.yml</li>
 *   <li>ai-health-url         AI 服务健康检查地址，默认跟随 waimai.ai.base-url</li>
 *   <li>start-timeout-seconds 启动命令与健康检查的超时，默认 120（首次构建镜像会更久）</li>
 *   <li>ready-timeout-seconds 容器 TCP 就绪探测（redis 6379 / rabbitmq 5672）的等待上限，默认 60</li>
 *   <li>stop-timeout-seconds  关闭时 docker compose stop 的超时，默认 60</li>
 *   <li>stop-on-shutdown      关闭后端时是否一起停掉这些容器，默认 true</li>
 *   <li>check-database        是否探测本机 MySQL，默认 true</li>
 *   <li>database-service      MySQL 的 Windows 服务名（配了就 best-effort net start，需管理员权限），默认空</li>
 *   <li>database-ready-timeout-seconds 发出 net start 后等 3306 就绪的上限，默认 30</li>
 * </ul>
 *
 * <p>说明：MySQL 是<b>本机 Windows 服务</b>（3306，后端的数据源），不在 compose 里 ——
 * compose 里的 mysql 映射在 3307，只给容器内的 server/ai 用。<b>它不会随 docker 依赖一起启停</b>：
 * {@link #ensureDatabase()} 只在启动前置阶段探一次端口，连不上时打印可执行提示，
 * 如果配了 {@code database-service} 还会尝试 {@code net start MySQL80}（IDEA 以管理员运行时生效）。
 * 之所以要探这一下：这个服务没随开机启动时，Spring 会在 Hikari 初始化处抛一大串
 * CommunicationsException + MyBatisSystemException，然后就 fail-fast 退出，很难一眼看出根因。
 * Docker 未启动、compose 文件找不到等情况下只告警不影响后端启动。
 */
@Slf4j
public class DependencyLifecycleManager {

    /** 健康检查用到的服务名（只有 AI 服务对外暴露 /health） */
    private static final String AI_SERVICE = "ai";

    /** 就绪探测用的 TCP 端口（docker-compose 里 redis 6379 的默认映射） */
    private static final int PORT_REDIS = 6379;

    /** docker 可执行文件的候选位置（Windows 上 IDEA 的 PATH 未必包含 Docker Desktop） */
    private static final String[] DOCKER_CANDIDATES = {
            "docker",
            "C:\\Program Files\\Docker\\Docker\\resources\\bin\\docker.exe"
    };

    private final Config config;

    /** 只有本进程确认过依赖可用（启动成功或本来就在运行），退出时才去停容器 */
    private volatile boolean managed = false;

    public DependencyLifecycleManager(Config config) {
        this.config = config;
    }

    /**
     * 拉起依赖。返回 true 表示依赖已由本进程接管（退出时应当停止它们）。
     * 任何异常都只告警：依赖问题不应影响后端本身启动。
     */
    public boolean start() {
        // 本机 MySQL 是后端自己的数据源，先探一次（它是 Windows 服务，不归 docker 管）
        ensureDatabase();

        if (!config.autoStart()) {
            log.info("[依赖] waimai.deps.auto-start=false，跳过自动管理 docker 依赖");
            return false;
        }

        List<String> serviceList = parseServices();
        if (serviceList.isEmpty()) {
            log.warn("[依赖] waimai.deps.services 为空，跳过自动管理 docker 依赖");
            return false;
        }

        try {
            File compose = locateComposeFile();
            if (compose == null) {
                log.warn("[依赖] 未找到 docker-compose.yml，跳过自动启动 {}. 可手动执行：docker compose up -d --no-deps {}",
                        serviceList, String.join(" ", serviceList));
                return false;
            }

            List<String> docker = resolveDocker();
            if (docker == null) {
                log.warn("[依赖] 未检测到可用的 Docker（Docker Desktop 未启动？），跳过自动启动 {}。"
                                + "手动启动：docker compose -f {} up -d --no-deps {}",
                        serviceList, compose.getAbsolutePath(), String.join(" ", serviceList));
                return false;
            }

            List<String> composeCmd = resolveComposeCommand(docker);
            if (composeCmd == null) {
                log.warn("[依赖] 未找到 docker compose 命令（需要 Docker Compose v2 或 docker-compose），跳过自动启动依赖");
                return false;
            }

            List<String> alreadyRunning = runningServices(composeCmd, compose);
            if (alreadyRunning.containsAll(serviceList)) {
                log.info("[依赖] {} 已在运行，后端关闭时会一并停止（waimai.deps.stop-on-shutdown 控制）",
                        String.join(", ", serviceList));
                managed = true;
                return true;
            }

            log.info("[依赖] 启动前置：正在拉起 docker 依赖 {}（首次构建镜像可能较久）...",
                    String.join(", ", serviceList));
            CmdResult result = exec(upCommand(composeCmd, compose, serviceList, false), config.startTimeoutSeconds());

            if (result.exit() != 0 && looksLikeMissingImage(result.output())) {
                log.info("[依赖] 镜像不存在，改为构建后启动（--build，可能几分钟）...");
                result = exec(upCommand(composeCmd, compose, serviceList, true), config.startTimeoutSeconds());
            }

            if (result.exit() != 0) {
                if (looksLikeDockerDown(result.output())) {
                    log.warn("[依赖] Docker 守护进程不可用，依赖未启动。请先启动 Docker Desktop 再重启后端");
                } else {
                    log.error("[依赖] 启动 docker 依赖失败（exit={}）：{}", result.exit(), firstLines(result.output()));
                }
                return false;
            }

            managed = true;
            log.info("[依赖] docker 依赖已启动：{}", String.join(", ", serviceList));

            // 容器 start 完成 ≠ 服务可接受连接，等端口通了再让容器 refresh（否则 RabbitMQ 监听器会刷连接报错）
            waitForServiceReady(serviceList);

            if (serviceList.contains(AI_SERVICE)) {
                waitForAiHealth();
            }
            return true;
        } catch (Exception e) {
            // 依赖问题不应影响后端本身启动
            log.warn("[依赖] 自动管理 docker 依赖时异常：{}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 停止依赖容器（由 {@link DependencyPreflight} 注册的 JVM 关闭钩子调用）。
     * 「优雅停止」（IDEA 的 Stop 按钮、Ctrl+C、System.exit）会执行关闭钩子；
     * 若是「强制结束进程」（Force Kill / taskkill /F）则不执行，需要手动 docker compose stop。
     */
    public void stop() {
        if (!managed) {
            return;
        }
        if (!config.stopOnShutdown()) {
            log.info("[依赖] waimai.deps.stop-on-shutdown=false，保留 docker 依赖继续运行");
            return;
        }
        List<String> serviceList = parseServices();
        try {
            File compose = locateComposeFile();
            List<String> docker = resolveDocker();
            List<String> composeCmd = docker == null ? null : resolveComposeCommand(docker);
            if (compose == null || composeCmd == null) {
                log.warn("[依赖] 环境不可用，跳过停止 docker 依赖，可手动执行：docker compose stop {}",
                        String.join(" ", serviceList));
                return;
            }

            log.info("[依赖] 后端关闭，正在停止 docker 依赖：{}...", String.join(", ", serviceList));
            List<String> cmd = new ArrayList<>(composeCmd);
            cmd.add("-f");
            cmd.add(compose.getAbsolutePath());
            cmd.add("stop");
            cmd.addAll(serviceList);

            CmdResult result = exec(cmd, config.stopTimeoutSeconds());
            if (result.exit() == 0) {
                log.info("[依赖] docker 依赖已停止：{}", String.join(" ", serviceList));
            } else {
                log.warn("[依赖] 停止 docker 依赖失败（exit={}）：{}", result.exit(), firstLines(result.output()));
            }
        } catch (Exception e) {
            log.warn("[依赖] 停止 docker 依赖时异常：{}", e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ 启动辅助

    /** 轮询 AI 服务健康检查，直到就绪或超时（超时只告警，不影响后端） */
    private void waitForAiHealth() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        long start = System.currentTimeMillis();
        long deadline = start + config.startTimeoutSeconds() * 1000L;
        long lastLog = 0L;

        while (System.currentTimeMillis() < deadline) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(config.aiHealthUrl()))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    log.info("[依赖] AI 服务已就绪（耗时 {}s）：{} → {}",
                            (System.currentTimeMillis() - start) / 1000, config.aiHealthUrl(), response.body());
                    return;
                }
            } catch (Exception ignored) {
                // 服务还没起来，继续等
            }

            long elapsed = System.currentTimeMillis() - start;
            if (elapsed - lastLog >= 10000) {
                lastLog = elapsed;
                log.info("[依赖] 等待 AI 服务就绪...（{}s/{}s）", elapsed / 1000, config.startTimeoutSeconds());
            }
            try {
                Thread.sleep(2000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        log.warn("[依赖] AI 服务 {}s 内未就绪，后端继续启动，AI 相关功能会降级提示。"
                        + "排查：docker logs waimai-ai --tail 100",
                config.startTimeoutSeconds());
    }

    /**
     * 本机 MySQL 探测（可选 best-effort 启动）。
     *
     * <p>MySQL 是后端的数据源、也是本机 Windows 服务，不属于 docker 依赖；这里只做两件事：
     * 探一次 TCP 端口给出明确结论；配了 {@code waimai.deps.database-service} 时尝试
     * {@code net start &lt;服务名&gt;}（需要管理员权限，非管理员只会失败并给出提示）。
     * 无论结果如何都不抛异常 —— 连不上时后端会在 Hikari 初始化处照常失败，但日志里先有一句人话。
     */
    private void ensureDatabase() {
        if (!config.checkDatabase()) {
            return;
        }
        String host = config.databaseHost();
        int port = config.databasePort();

        if (isPortReachable(host, port)) {
            log.info("[依赖] 本机数据库已就绪：{}:{}", host, port);
            return;
        }

        log.error("[依赖] 本机数据库连不上（{}:{}）：MySQL 服务可能还没启动"
                        + "（自动启动的服务没有随开机起来时就是这样）。", host, port);

        String service = config.databaseService();
        if (service == null || service.isBlank()) {
            log.error("[依赖] 请用管理员权限执行 net start MySQL80（或在 services.msc 里启动 MySQL80），再重跑后端");
            return;
        }
        if (!isWindows()) {
            log.error("[依赖] 当前系统不是 Windows，无法自动启动服务 {}，请先手动启动数据库再重跑后端", service);
            return;
        }

        log.info("[依赖] 尝试启动 Windows 服务 {}（需要管理员权限，IDEA 非管理员运行时这一步会失败）...", service);
        CmdResult result = exec(List.of("net", "start", service), 60);
        if (result.exit() != 0) {
            log.error("[依赖] 启动 Windows 服务 {} 失败（exit={}）。请用管理员权限执行：net start {}"
                            + "（或在 services.msc 里启动），然后重跑后端",
                    service, result.exit(), service);
            logDatabaseFailureHint(port);
            return;
        }

        long deadline = System.currentTimeMillis() + config.databaseReadyTimeoutSeconds() * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (isPortReachable(host, port)) {
                log.info("[依赖] MySQL 服务 {} 已启动并就绪：{}:{}", service, host, port);
                return;
            }
            if (!sleepQuietly(500L)) {
                return;
            }
        }
        log.error("[依赖] Windows 服务 {} 已发出启动命令，但 {}:{} 在 {}s 内仍不可用（查 MySQL 错误日志："
                        + "C:\\ProgramData\\MySQL\\MySQL Server 8.0\\Data\\<主机名>.err）",
                service, host, port, config.databaseReadyTimeoutSeconds());
        logDatabaseFailureHint(port);
    }

    /**
     * 数据库服务起不来时最该看的两个地方：MySQL 自己的错误日志，以及「端口被系统整段预留」。
     *
     * <p>后者在装了 Docker Desktop/WSL 的机器上很常见：Hyper-V 的 NAT 会圈走一大段 TCP 端口
     * （`netsh int ipv4 show excludedportrange protocol=tcp` 能看到，例如 3239-3338 把 3306 包进去），
     * 此时任何进程（包括 SYSTEM 权限的 docker-proxy 和 MySQL 服务）绑这个端口都会收到
     * WSAEACCES「以一种访问权限不允许的方式做了一个访问套接字的尝试」，
     * 表现就是「服务启动后立刻停止」。修复要用管理员权限释放预留：`net stop winnat` → `net start winnat`，
     * 且顺序要先起 MySQL、再起 Docker。
     */
    private void logDatabaseFailureHint(int port) {
        log.error("[依赖] 排查提示：如果 MySQL 是「启动后立刻停止」，常见原因是端口 {} 被占用或被系统预留。"
                        + "① 看 MySQL 错误日志 C:\\ProgramData\\MySQL\\MySQL Server 8.0\\Data\\<主机名>.err；"
                        + "② 执行 netsh int ipv4 show excludedportrange protocol=tcp，"
                        + "若 {} 落在某个范围里（Docker Desktop/WSL 的 Hyper-V NAT 会整段圈走端口，"
                        + "此时连 SYSTEM 权限的进程也绑不上，报 WSAEACCES），"
                        + "用管理员权限执行 net stop winnat 再 net start winnat 释放，然后先起 MySQL、最后开 Docker",
                port, port);
    }

    /** 等待 docker 依赖的服务真正开始监听端口（容器 start 完成 ≠ 端口可连接） */
    private void waitForServiceReady(List<String> serviceList) {
        for (String service : serviceList) {
            if ("rabbitmq".equalsIgnoreCase(service)) {
                waitForRabbit();
                continue;
            }
            int port = readyPort(service);
            if (port > 0) {
                waitForPort(service, port);
            }
        }
    }

    /** 服务名 → 就绪探测端口；rabbitmq 走 AMQP 握手、AI 走 HTTP 健康检查，都不在这里探 */
    private int readyPort(String service) {
        if ("redis".equalsIgnoreCase(service)) {
            return PORT_REDIS;
        }
        return -1;
    }

    /**
     * RabbitMQ 就绪探测：直接做一次 AMQP 连接握手。
     *
     * <p>不能用 TCP 端口探测代替 —— Docker 的端口映射（docker-proxy）在容器一启动就已经监听宿主端口，
     * {@code connect()} 会立刻成功，可 broker 还在启动 Erlang，AMQP 握手会抛 EOFException。
     * 这正是「[依赖] docker 依赖已启动 之后，AMQP 监听器仍然刷
     * Failed to check/redeclare auto-delete queue(s)」的原因。
     */
    private void waitForRabbit() {
        Config.Rabbit rabbit = config.rabbit();
        long start = System.currentTimeMillis();
        long deadline = start + config.readyTimeoutSeconds() * 1000L;
        long lastLog = 0L;

        while (System.currentTimeMillis() < deadline) {
            if (canHandshake(rabbit)) {
                log.info("[依赖] rabbitmq 已就绪（耗时 {}s）：{}:{}",
                        (System.currentTimeMillis() - start) / 1000, rabbit.host(), rabbit.port());
                return;
            }
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed - lastLog >= 10000) {
                lastLog = elapsed;
                log.info("[依赖] 等待 rabbitmq 就绪（AMQP 握手）...（{}s/{}s）", elapsed / 1000, config.readyTimeoutSeconds());
            }
            if (!sleepQuietly(1000L)) {
                return;
            }
        }

        log.warn("[依赖] rabbitmq {}:{} 在 {}s 内未完成 AMQP 握手，后端继续启动（Spring AMQP 自己会重试）",
                rabbit.host(), rabbit.port(), config.readyTimeoutSeconds());
    }

    /** 尝试建立一次 AMQP 连接（成功即说明 broker 真正能收请求），随后立即关闭 */
    private boolean canHandshake(Config.Rabbit rabbit) {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(rabbit.host());
        factory.setPort(rabbit.port());
        factory.setUsername(rabbit.username());
        factory.setPassword(rabbit.password());
        factory.setConnectionTimeout(1500);
        factory.setAutomaticRecoveryEnabled(false);
        try (Connection ignored = factory.newConnection()) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void waitForPort(String service, int port) {
        long start = System.currentTimeMillis();
        long deadline = start + config.readyTimeoutSeconds() * 1000L;
        long lastLog = 0L;

        while (System.currentTimeMillis() < deadline) {
            if (isPortReachable("127.0.0.1", port)) {
                log.info("[依赖] {} 已就绪（耗时 {}s）：127.0.0.1:{}",
                        service, (System.currentTimeMillis() - start) / 1000, port);
                return;
            }
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed - lastLog >= 10000) {
                lastLog = elapsed;
                log.info("[依赖] 等待 {} 就绪...（{}s/{}s）", service, elapsed / 1000, config.readyTimeoutSeconds());
            }
            if (!sleepQuietly(500L)) {
                return;
            }
        }

        log.warn("[依赖] {} 的端口 127.0.0.1:{} 在 {}s 内未就绪，后端继续启动"
                        + "（compose 里改过端口映射时可忽略这条告警）",
                service, port, config.readyTimeoutSeconds());
    }

    /** 是否 Windows：`net start` 只在 Windows 上存在（其它系统跳过服务启动，只给提示） */
    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /** TCP 连接探测：能连上说明服务已经开始监听 */
    private boolean isPortReachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** 睡一会儿；被中断返回 false */
    private boolean sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private List<String> upCommand(List<String> composeCmd, File compose, List<String> serviceList, boolean build) {
        List<String> cmd = new ArrayList<>(composeCmd);
        cmd.add("-f");
        cmd.add(compose.getAbsolutePath());
        cmd.add("up");
        cmd.add("-d");
        if (build) {
            cmd.add("--build");
        }
        // --no-deps 避免连带拉起 compose 里的 mysql（本机 3306 的 MySQL 才是后端的数据源）
        cmd.add("--no-deps");
        cmd.addAll(serviceList);
        return cmd;
    }

    /** 查询当前已在运行的 compose 服务名 */
    private List<String> runningServices(List<String> composeCmd, File compose) {
        List<String> cmd = new ArrayList<>(composeCmd);
        cmd.add("-f");
        cmd.add(compose.getAbsolutePath());
        cmd.add("ps");
        cmd.add("--services");
        cmd.add("--filter");
        cmd.add("status=running");
        CmdResult result = exec(cmd, 30);
        if (result.exit() != 0) {
            return List.of();
        }
        return Arrays.stream(result.output().split("\\R"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    // ------------------------------------------------------------------ 环境探测

    /** 返回可用的 docker 命令行前缀；Docker 未安装或守护进程未运行时返回 null */
    private List<String> resolveDocker() {
        for (String candidate : DOCKER_CANDIDATES) {
            CmdResult result = exec(List.of(candidate, "version", "--format", "{{.Server.Version}}"), 20);
            if (result.exit() == 0) {
                return List.of(candidate);
            }
        }
        return null;
    }

    /** 返回 docker compose 命令前缀（v2 的 docker compose 或独立的 docker-compose），都不可用返回 null */
    private List<String> resolveComposeCommand(List<String> docker) {
        List<String> v2 = new ArrayList<>(docker);
        v2.add("compose");
        List<String> v2Version = new ArrayList<>(v2);
        v2Version.add("version");
        if (exec(v2Version, 20).exit() == 0) {
            return v2;
        }
        if (exec(List.of("docker-compose", "version"), 20).exit() == 0) {
            return List.of("docker-compose");
        }
        return null;
    }

    /** 定位 docker-compose.yml：优先配置项，否则从工作目录向上逐级查找 */
    private File locateComposeFile() {
        String composeFile = config.composeFile();
        if (composeFile != null && !composeFile.isBlank()) {
            File file = new File(composeFile.trim());
            if (!file.isAbsolute()) {
                file = new File(System.getProperty("user.dir"), composeFile.trim());
            }
            return file.isFile() ? file : null;
        }
        File dir = new File(System.getProperty("user.dir"));
        for (int i = 0; i < 6 && dir != null; i++) {
            File candidate = new File(dir, "docker-compose.yml");
            if (candidate.isFile()) {
                return candidate;
            }
            dir = dir.getParentFile();
        }
        return null;
    }

    private List<String> parseServices() {
        return Arrays.stream(config.services().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    private boolean looksLikeMissingImage(String output) {
        String text = output == null ? "" : output.toLowerCase();
        return text.contains("no such image")
                || text.contains("pull access denied")
                || text.contains("manifest unknown")
                || text.contains("failed to solve")
                || text.contains("not found locally");
    }

    private boolean looksLikeDockerDown(String output) {
        String text = output == null ? "" : output.toLowerCase();
        return text.contains("cannot connect to the docker daemon")
                || text.contains("error during connect")
                || text.contains("is the docker daemon running");
    }

    // ------------------------------------------------------------------ 命令执行

    /** 执行外部命令并收集输出（stdout+stderr 合并），超时则强制结束 */
    private CmdResult exec(List<String> command, long timeoutSeconds) {
        log.debug("[依赖] 执行命令：{}", String.join(" ", command));
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new CmdResult(-1, output + System.lineSeparator() + "[超时 " + timeoutSeconds + "s，已强制结束]");
            }
            return new CmdResult(process.exitValue(), output);
        } catch (IOException e) {
            return new CmdResult(-1, "命令执行失败：" + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CmdResult(-1, "命令被中断");
        }
    }

    /** 只取前几行输出写日志，避免 docker 把整段构建日志灌进控制台 */
    private String firstLines(String output) {
        if (output == null || output.isBlank()) {
            return "(无输出)";
        }
        String[] lines = output.strip().split("\\R");
        int limit = Math.min(lines.length, 5);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            sb.append(lines[i].trim());
            if (i < limit - 1) {
                sb.append(" | ");
            }
        }
        if (lines.length > limit) {
            sb.append(" | ...（共 ").append(lines.length).append(" 行）");
        }
        return sb.toString();
    }

    private record CmdResult(int exit, String output) {
    }

    /**
     * 依赖管理的配置快照。
     * 之所以不用 @Value 注入：本类要在 Spring 容器 refresh 之前工作，那时 Bean 还没创建，
     * 只能从已经准备好的 {@link Environment}（application.yml 已加载）里直接读。
     */
    public record Config(boolean autoStart, String services, String composeFile, String aiHealthUrl,
                         long startTimeoutSeconds, long stopTimeoutSeconds, boolean stopOnShutdown,
                         long readyTimeoutSeconds, boolean checkDatabase, String databaseHost, int databasePort,
                         String databaseService, long databaseReadyTimeoutSeconds, Rabbit rabbit) {

        /** AMQP 握手用的连接信息（取自 spring.rabbitmq.*） */
        public record Rabbit(String host, int port, String username, String password) {
        }

        /** 从 spring.datasource.url 里抠出主机与端口（jdbc:mysql://host:port/db?...） */
        private static final Pattern JDBC_URL = Pattern.compile("jdbc:mysql://([^/:?]+)(?::(\\d+))?");

        public static Config from(Environment env) {
            String aiBaseUrl = env.getProperty("waimai.ai.base-url", "http://localhost:8000");

            String jdbcUrl = env.getProperty("spring.datasource.url", "");
            Matcher matcher = JDBC_URL.matcher(jdbcUrl == null ? "" : jdbcUrl);
            boolean matched = matcher.find();
            String databaseHost = matched ? matcher.group(1) : "localhost";
            int databasePort = matched && matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 3306;

            return new Config(
                    env.getProperty("waimai.deps.auto-start", Boolean.class, Boolean.TRUE),
                    env.getProperty("waimai.deps.services", "redis,rabbitmq,ai"),
                    env.getProperty("waimai.deps.compose-file", ""),
                    env.getProperty("waimai.deps.ai-health-url", aiBaseUrl + "/health"),
                    env.getProperty("waimai.deps.start-timeout-seconds", Long.class, 120L),
                    env.getProperty("waimai.deps.stop-timeout-seconds", Long.class, 60L),
                    env.getProperty("waimai.deps.stop-on-shutdown", Boolean.class, Boolean.TRUE),
                    env.getProperty("waimai.deps.ready-timeout-seconds", Long.class, 60L),
                    env.getProperty("waimai.deps.check-database", Boolean.class, Boolean.TRUE),
                    databaseHost,
                    databasePort,
                    env.getProperty("waimai.deps.database-service", ""),
                    env.getProperty("waimai.deps.database-ready-timeout-seconds", Long.class, 30L),
                    new Rabbit(
                            env.getProperty("spring.rabbitmq.host", "localhost"),
                            env.getProperty("spring.rabbitmq.port", Integer.class, 5672),
                            env.getProperty("spring.rabbitmq.username", "guest"),
                            env.getProperty("spring.rabbitmq.password", "guest")));
        }
    }
}
