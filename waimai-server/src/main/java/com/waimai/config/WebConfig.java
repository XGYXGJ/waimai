package com.waimai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.waimai.interceptor.AuthInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Value("${waimai.upload.dir:./uploads}")
    private String uploadDir;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Bean
    public RestTemplate restTemplate() {
        // new RestTemplate() 默认没有任何超时，AI 服务一旦卡住会把后端线程永久挂住。
        // zen 免费模型单次可能十几秒、本地 Ollama 更久，所以读超时给足但要封顶。
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(120_000);
        return new RestTemplate(factory);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        // 认证（无需登录）
                        "/api/auth/login", "/api/auth/register", "/api/auth/refresh",
                        // 通用
                        "/api/common/config/map", "/api/common/health");
        // 其余公开浏览接口（商家详情、推荐、评价等）通过 @PublicApi 注解标记，
        // 由 AuthInterceptor 在方法级判断，避免用宽泛通配符误伤需登录的接口。
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + (uploadDir.endsWith("/") ? uploadDir : uploadDir + "/"));
    }
}
