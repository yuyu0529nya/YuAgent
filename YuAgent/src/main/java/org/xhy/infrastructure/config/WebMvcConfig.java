package org.xhy.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.xhy.infrastructure.auth.UserAuthInterceptor;
import org.xhy.infrastructure.interceptor.AdminAuthInterceptor;

/** Web MVC 配置类，用于配置拦截器、跨域等 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final UserAuthInterceptor userAuthInterceptor;
    private final AdminAuthInterceptor adminAuthInterceptor;

    public WebMvcConfig(UserAuthInterceptor userAuthInterceptor, AdminAuthInterceptor adminAuthInterceptor) {
        this.userAuthInterceptor = userAuthInterceptor;
        this.adminAuthInterceptor = adminAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userAuthInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/login",
                        "/health",
                        "/register",
                        "/auth/config",
                        "/files/image-proxy",
                        "/send-email-code",
                        "/verify-email-code",
                        "/get-captcha",
                        "/reset-password",
                        "/send-reset-password-code",
                        "/oauth/github/authorize",
                        "/oauth/github/callback",
                        "/sso/**",
                        "/widget/**",
                        "/v1/**",
                        "/payments/callback/**");

        registry.addInterceptor(adminAuthInterceptor).addPathPatterns("/admin/**");
    }
}
