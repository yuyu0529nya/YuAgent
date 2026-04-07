package org.xhy.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** YuAgent管理员用户环境变量配置
 * 
 * 直接读取环境变量，解决嵌套配置映射问题
 * 
 * @author xhy */
@Component
public class AdminUserEnvironmentProperties {

    /** 管理员邮箱 */
    @Value("${YUAGENT_ADMIN_EMAIL:admin@yuagent.ai}")
    private String adminEmail;

    /** 管理员密码 */
    @Value("${YUAGENT_ADMIN_PASSWORD:admin123}")
    private String adminPassword;

    /** 管理员昵称 */
    @Value("${YUAGENT_ADMIN_NICKNAME:YuAgent管理员}")
    private String adminNickname;

    /** 是否启用测试用户 */
    @Value("${YUAGENT_TEST_ENABLED:true}")
    private Boolean testEnabled;

    /** 测试用户邮箱 */
    @Value("${YUAGENT_TEST_EMAIL:test@yuagent.ai}")
    private String testEmail;

    /** 测试用户密码 */
    @Value("${YUAGENT_TEST_PASSWORD:test123}")
    private String testPassword;

    /** 测试用户昵称 */
    @Value("${YUAGENT_TEST_NICKNAME:测试用户}")
    private String testNickname;

    public String getAdminEmail() {
        return adminEmail;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public String getAdminNickname() {
        return adminNickname;
    }

    public Boolean getTestEnabled() {
        return testEnabled;
    }

    public String getTestEmail() {
        return testEmail;
    }

    public String getTestPassword() {
        return testPassword;
    }

    public String getTestNickname() {
        return testNickname;
    }
}