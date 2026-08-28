package org.yu.infrastructure.sso;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.yu.infrastructure.exception.BusinessException;

class GitHubSsoServiceTest {

    @Test
    void shouldEncodeCallbackUrlAsOneOAuthParameter() throws Exception {
        SsoConfigProvider configProvider = mock(SsoConfigProvider.class);
        when(configProvider.getGitHubConfig()).thenReturn(githubConfig());
        GitHubSsoService service = new GitHubSsoService(configProvider);

        String loginUrl = service.getLoginUrl("https://console.example.com/callback?source=portal&locale=zh-CN");

        String rawQuery = new URI(loginUrl).getRawQuery();
        assertTrue(
                rawQuery.contains("redirect_uri=https://console.example.com/callback?source%3Dportal%26locale%3Dzh-CN"),
                rawQuery);
        assertFalse(rawQuery.contains("&source=portal"));
        assertFalse(rawQuery.contains("&locale=zh-CN"));
        service.close();
    }

    @Test
    void shouldRejectIncompleteConfigBeforeBuildingLoginUrl() {
        SsoConfigProvider configProvider = mock(SsoConfigProvider.class);
        SsoConfigProvider.GitHubSsoConfig config = githubConfig();
        config.setClientSecret(" ");
        when(configProvider.getGitHubConfig()).thenReturn(config);
        GitHubSsoService service = new GitHubSsoService(configProvider);

        assertThrows(BusinessException.class, () -> service.getLoginUrl(null));
        service.close();
    }

    private SsoConfigProvider.GitHubSsoConfig githubConfig() {
        SsoConfigProvider.GitHubSsoConfig config = new SsoConfigProvider.GitHubSsoConfig();
        config.setClientId("client-id");
        config.setClientSecret("client-secret");
        config.setRedirectUri("https://console.example.com/callback");
        return config;
    }
}
