package com.waimai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

/**
 * 依赖服务「启动前置」：在 Spring 容器 refresh 之前（ApplicationPreparedEvent）
 * 就把 docker 依赖（默认 Redis / RabbitMQ / AI）拉起来，并在后端关闭时一并停止。
 *
 * <p>这样做的原因：AMQP 监听器容器是在容器 refresh 阶段启动的，如果 RabbitMQ 那时候还没就绪，
 * 启动日志里会出现 {@code AmqpConnectException: Connection refused}（监听器随后会自行重试连上，
 * 但重启后的头几秒内下单会因发不出延迟消息而失败）。把它提前到 refresh 之前可以从根上消除这个窗口。
 *
 * <p>由 {@link com.waimai.WaimaiApplication#main} 手动注册，不做成 Spring Bean：
 * 一个 Bean 是无法在「自己所属的容器 refresh 之前」被调用的。
 * 手动注册也意味着 `@SpringBootTest` 等不走 main 的启动方式不会触发依赖管理。
 *
 * <p>关闭方向：启动成功且 {@code waimai.deps.stop-on-shutdown=true} 时注册 JVM 关闭钩子，
 * IDEA 的 Stop 按钮 / Ctrl+C / System.exit 都会触发（Force Kill 不会，需手动 docker compose stop）。
 */
@Slf4j
public class DependencyPreflight implements ApplicationListener<ApplicationPreparedEvent> {

    /** 关闭钩子命名，便于在 JVM 线程列表里识别 */
    private static final String SHUTDOWN_HOOK_NAME = "waimai-deps-shutdown";

    @Override
    public void onApplicationEvent(ApplicationPreparedEvent event) {
        Environment env = event.getApplicationContext().getEnvironment();
        DependencyLifecycleManager.Config config = DependencyLifecycleManager.Config.from(env);

        DependencyLifecycleManager manager = new DependencyLifecycleManager(config);
        if (!manager.start()) {
            // 依赖没起来（Docker 未启动、找不到 compose 文件等）：后端照常启动，相关功能降级
            return;
        }

        if (config.stopOnShutdown()) {
            Runtime.getRuntime().addShutdownHook(new Thread(manager::stop, SHUTDOWN_HOOK_NAME));
            log.info("[依赖] 已注册关闭钩子（{}）：后端退出时会一并停止 {}",
                    SHUTDOWN_HOOK_NAME, config.services());
        }
    }
}
