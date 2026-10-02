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
import java.util.ArrayList;
import java.util.List;

/**
 * 前端生命周期管理器：后端启动完成后自动拉起四端 Vite dev server，
 * 后端关闭（含 IDEA 的 Stop 按钮触发 JVM 关闭）时自动关闭四端进程。
 *
 * 通过配置 waimai.frontend.auto-start 控制是否启用（默认 true，生产环境建议关闭）。
 * 依赖：本机已安装 Node.js，且 waimai-web 已执行过 npm install。
 */
@Slf4j
@Component
@Order(1000)
public class FrontendLifecycleManager implements ApplicationRunner {

    /** 是否自动启动前端（生产环境置 false） */
    @Value("${waimai.frontend.auto-start:true}")
    private boolean autoStart;

    /** 四端 dev 端口，与各 app 的 vite.config.ts 保持一致 */
    private static final int[] PORTS = {5173, 5174, 5175, 5176};

    /** 记录已启动的前端进程，用于关闭时精确终止 */
    private final List<Process> childProcesses = new ArrayList<>();

    @Override
    public void run(ApplicationArguments args) {
        if (!autoStart) {
            log.info("[前端] waimai.frontend.auto-start=false，跳过自动启动前端");
            return;
        }

        // 若四端端口已被占用，说明前端已在运行，不再重复拉起
        if (allPortsInUse()) {
            log.info("[前端] 检测到四端端口已被占用，前端应已在运行，跳过自动启动");
            return;
        }

        File webDir = locateWebDir();
        if (webDir == null) {
            log.warn("[前端] 未找到前端目录 waimai-web，跳过自动启动前端。请确认项目结构完整。");
            return;
        }

        log.info("[前端] 后端已就绪，自动启动四端 Vite dev server（端口 {}）...",
                String.join(", ", portStrings()));

        try {
            for (int port : PORTS) {
                Process p = startDevServer(webDir, port);
                if (p != null) {
                    childProcesses.add(p);
                }
            }
            log.info("[前端] 四端 Vite dev server 已启动完成。"
                    + "用户端 http://localhost:5173 / 骑手端 :5174 / 商户端 :5175 / 管理端 :5176");
        } catch (Exception e) {
            log.error("[前端] 自动启动前端失败：{}", e.getMessage(), e);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (childProcesses.isEmpty()) {
            return;
        }
        log.info("[前端] 后端关闭，正在停止四端 Vite dev server...");
        for (Process p : childProcesses) {
            stopProcessTree(p);
        }
        childProcesses.clear();
        log.info("[前端] 四端 dev server 已停止");
    }

    /**
     * 启动某个端口的 dev server。
     * Windows 下通过 cmd /c 运行 npm，确保能正确解析 npm 命令并管理进程树。
     */
    private Process startDevServer(File webDir, int port) throws IOException {
        String appName = appNameByPort(port);
        ProcessBuilder pb = new ProcessBuilder();
        pb.directory(webDir);

        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        if (isWindows) {
            pb.command("cmd.exe", "/c", "npm", "run", "dev", "-w", appName);
        } else {
            pb.command("npm", "run", "dev", "-w", appName);
        }

        pb.redirectErrorStream(true);
        File logDir = new File(webDir, "logs");
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
        pb.redirectOutput(new File(logDir, "port-" + port + ".log"));

        Process p = pb.start();
        log.info("[前端] 已启动 {}（端口 {}，PID {})", appName, port, pidOf(p));
        return p;
    }

    /** 终止进程及其子进程树（Vite 会派生 node 子进程，需一并结束） */
    private void stopProcessTree(Process p) {
        if (p == null || !p.isAlive()) {
            return;
        }
        long pid = pidOf(p);
        try {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            if (isWindows) {
                // 强杀整个进程树，避免残留孤儿 node/vite 进程
                new ProcessBuilder("taskkill", "/F", "/T", "/PID", String.valueOf(pid))
                        .redirectErrorStream(true)
                        .start()
                        .waitFor();
            } else {
                p.descendants().forEach(ProcessHandle::destroyForcibly);
                p.destroyForcibly();
            }
        } catch (Exception e) {
            log.warn("[前端] 停止进程 {} 时异常：{}", pid, e.getMessage());
        }
    }

    /** 定位前端目录 waimai-web（与后端同级的 waimai/waimai-web） */
    private File locateWebDir() {
        // 工作目录可能为项目根、waimai-server 或 target/classes 等，向上逐级查找
        File dir = new File(System.getProperty("user.dir"));
        for (int i = 0; i < 6; i++) {
            File candidate = new File(dir, "waimai-web");
            if (candidate.isDirectory() && new File(candidate, "package.json").exists()) {
                return candidate;
            }
            File candidate2 = new File(dir.getParentFile() == null ? dir : dir.getParentFile(), "waimai-web");
            if (candidate2.isDirectory() && new File(candidate2, "package.json").exists()) {
                return candidate2;
            }
            dir = dir.getParentFile();
            if (dir == null) {
                break;
            }
        }
        return null;
    }

    private boolean allPortsInUse() {
        for (int port : PORTS) {
            if (!isPortInUse(port)) {
                return false;
            }
        }
        return true;
    }

    private boolean isPortInUse(int port) {
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress("localhost", port), 200);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private String appNameByPort(int port) {
        switch (port) {
            case 5173: return "apps/user-h5";
            case 5174: return "apps/rider-h5";
            case 5175: return "apps/merchant-web";
            case 5176: return "apps/admin-web";
            default: throw new IllegalArgumentException("未知端口 " + port);
        }
    }

    private long pidOf(Process p) {
        try {
            return p.pid();
        } catch (Exception e) {
            return -1;
        }
    }

    private String[] portStrings() {
        String[] arr = new String[PORTS.length];
        for (int i = 0; i < PORTS.length; i++) {
            arr[i] = String.valueOf(PORTS[i]);
        }
        return arr;
    }
}
