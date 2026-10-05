package com.waimai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 依赖服务生命周期管理器：后端启动完成后自动拉起 docker 依赖（默认 Redis / RabbitMQ / AI 服务），
 * 后端关闭（含 IDEA 的 Stop 按钮触发 JVM 关闭）时统一停止 —— 即「在 IDEA 里启动 WaimaiApplication
 * 就等于启动全部功能，关闭时一起关闭」。
 *
 * 与 {@link FrontendLifecycleManager}（四端前端 dev server）配套：本类 @Order(900) 先执行，
 * 等 AI 服务健康检查通过后，再由前端管理器（@Order(1000)）拉起四端。
 *
 * 配置项（application.yml 的 waimai.deps.*）：
 * <ul>
 *   <li>auto-start            是否启用，默认 true（生产环境建议 false）</li>
 *   <li>services              需要接管的 compose 服务名，默认 redis,rabbitmq,ai</li>
 *   <li>compose-file          默认留空：自动从工作目录向上查找 docker-compose.yml</li>
 *   <li>ai-health-url         AI 服务健康检查地址，默认 http://localhost:8000/health</li>
 *   <li>start-timeout-seconds 启动命令与健康检查的超时，默认 120（首次构建镜像会更久）</li>
 *   <li>stop-timeout-seconds  关闭时 docker compose stop 的超时，默认 60</li>
 *   <li>stop-on-shutdown      关闭后端时是否一起停掉这些容器，默认 true</li>
 * </ul>
 *
 * 说明：MySQL 不在这里管理 —— 本机 MySQL 是 Windows 服务（3306），后端能起来就说明它在跑；
 * compose 里的 mysql 服务映射在 3307，只给容器内的 server/ai 用。
 * Docker 未启动、compose 文件找不到等情况下只告警不影响后端启动。
 */
@Slf4j
@Component
@Order(900)
public class DependencyLifecycleManager implements ApplicationRunner {

    /** 是否自动管理 docker 依赖 */
    @Value("${waimai.deps.auto-start:true}")
    private boolean autoStart;

    /** 需要接管的 compose 服务名（逗号分隔） */
    @Value("${waimai.deps.services:redis,rabbitmq,ai}")
    private String services;

    /** 指定 compose 文件路径；留空则自动查找 */
    @Value("${waimai.deps.compose-file:}")
    private String composeFile;

    /** AI 服务健康检查地址；默认跟随 waimai.ai.base-url，改 AI 地址时不会漏改 */
    @Value("${waimai.deps.ai-health-url:${waimai.ai.base-url:http://localhost:8000}/health}")
    private String aiHealthUrl;

    /** 启动命令与健康检查超时（秒） */
    @Value("${waimai.deps.start-timeout-seconds:120}")
    private long startTimeoutSeconds;

    /** 关闭时的停止命令超时（秒） */
    @Value("${waimai.deps.stop-timeout-seconds:60}")
    private long stopTimeoutSeconds;

    /** 关闭后端时是否一起停止依赖容器 */
    @Value("${waimai.deps.stop-on-shutdown:true}")
    private boolean stopOnShutdown;

    /** 健康检查用到的服务名（只有 AI 服务对外暴露 /health） */
    private static final String AI_SERVICE = "ai";

    /** docker 可执行文件的候选位置（Windows 上 IDEA 的 PATH 未必包含 Docker Desktop） */
    private static final String[] DOCKER_CANDIDATES = {
            "docker",
            "C:\\Program Files\\Docker\\Docker\\resources\\bin\\docker.exe"
    };

    /** 只有本进程确认过依赖可用（启动成功或本来就在运行），退出时才去停容器 */
    private volatile boolean managed = false;

    @Override
    public void run(ApplicationArguments args) {
        if (!autoStart) {
            log.info("[依赖] waimai.deps.auto-start=false，跳过自动管理 docker 依赖");
            return;
        }

        List<String> serviceList = parseServices();
        if (serviceList.isEmpty()) {
            log.warn("[依赖] waimai.deps.services 为空，跳过自动管理 docker 依赖");
            return;
        }

        try {
            File compose = locateComposeFile();
            if (compose == null) {
                log.warn("[依赖] 未找到 docker-compose.yml，跳过自动启动 {}. 可手动执行：docker compose up -d --no-deps {}",
                        serviceList, String.join(" ", serviceList));
                return;
            }

            List<String> docker = resolveDocker();
            if (docker == null) {
                log.warn("[依赖] 未检测到可用的 Docker（Docker Desktop 未启动？），跳过自动启动 {}。"
                                + "手动启动：docker compose -f {} up -d --no-deps {}",
                        serviceList, compose.getAbsolutePath(), String.join(" ", serviceList));
                return;
            }

            List<String> composeCmd = resolveComposeCommand(docker);
            if (composeCmd == null) {
                log.warn("[依赖] 未找到 docker compose 命令（需要 Docker Compose v2 或 docker-compose），跳过自动启动依赖");
                return;
            }

            List<String> alreadyRunning = runningServices(composeCmd, compose);
            if (alreadyRunning.containsAll(serviceList)) {
                log.info("[依赖] {} 已在运行，后端关闭时会一并停止（waimai.deps.stop-on-shutdown 控制）",
                        String.join(", ", serviceList));
                managed = true;
                return;
            }

            log.info("[依赖] 后端已就绪，正在启动 docker 依赖：{}（首次构建镜像可能较久）...",
                    String.join(", ", serviceList));
            CmdResult result = exec(upCommand(composeCmd, compose, serviceList, false), startTimeoutSeconds);

            if (result.exit() != 0 && looksLikeMissingImage(result.output())) {
                log.info("[依赖] 镜像不存在，改为构建后启动（--build，可能几分钟）...");
                result = exec(upCommand(composeCmd, compose, serviceList, true), startTimeoutSeconds);
            }

            if (result.exit() != 0) {
                if (looksLikeDockerDown(result.output())) {
                    log.warn("[依赖] Docker 守护进程不可用，依赖未启动。请先启动 Docker Desktop 再重启后端");
                } else {
                    log.error("[依赖] 启动 docker 依赖失败（exit={}）：{}", result.exit(), firstLines(result.output()));
                }
                return;
            }

            managed = true;
            log.info("[依赖] docker 依赖已启动：{}", String.join(", ", serviceList));

            if (serviceList.contains(AI_SERVICE)) {
                waitForAiHealth();
            }
        } catch (Exception e) {
            // 依赖问题不应影响后端本身启动
            log.warn("[依赖] 自动管理 docker 依赖时异常：{}", e.getMessage(), e);
        }
    }

    /**
     * 关闭后端时停止依赖容器。
     * IDE（含 IDEA 的 Stop 按钮）触发 JVM 关闭时会执行 @PreDestroy；
     * 若是「强制结束进程」（Force Kill）则不会执行，需要手动 docker compose stop。
     */
    @PreDestroy
    public void shutdown() {
        if (!managed) {
            return;
        }
        if (!stopOnShutdown) {
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

            CmdResult result = exec(cmd, stopTimeoutSeconds);
            if (result.exit() == 0) {
                log.info("[依赖] docker 依赖已停止：{}", String.join(", ", serviceList));
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
        long deadline = start + startTimeoutSeconds * 1000L;
        long lastLog = 0L;

        while (System.currentTimeMillis() < deadline) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(aiHealthUrl))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    log.info("[依赖] AI 服务已就绪（耗时 {}s）：{} → {}",
                            (System.currentTimeMillis() - start) / 1000, aiHealthUrl, response.body());
                    return;
                }
            } catch (Exception ignored) {
                // 服务还没起来，继续等
            }

            long elapsed = System.currentTimeMillis() - start;
            if (elapsed - lastLog >= 10000) {
                lastLog = elapsed;
                log.info("[依赖] 等待 AI 服务就绪...（{}s/{}s）", elapsed / 1000, startTimeoutSeconds);
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
                startTimeoutSeconds);
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
        return Arrays.stream(services.split(","))
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
}
