package com.waimai;

import com.waimai.config.DependencyPreflight;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@MapperScan("com.waimai.mapper")
public class WaimaiApplication {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(WaimaiApplication.class);
        // 依赖服务（Redis / RabbitMQ / AI）在容器 refresh 之前拉起，避免启动期的连接报错
        application.addListeners(new DependencyPreflight());
        application.run(args);
    }
}
