package org.yu.application.llm.service;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.yu.domain.llm.service.LLMDomainService;
import org.yu.domain.user.service.UserSettingsDomainService;

class LLMAppServiceTest {

    @Test
    void returnsNullWithoutQueryingWhenNoDefaultModelIsConfigured() {
        LLMDomainService llmDomainService = Mockito.mock(LLMDomainService.class);
        UserSettingsDomainService userSettingsDomainService = Mockito.mock(UserSettingsDomainService.class);
        when(userSettingsDomainService.getUserDefaultModelId("user-1")).thenReturn(null);
        LLMAppService service = new LLMAppService(llmDomainService, userSettingsDomainService);

        assertNull(service.getDefaultModel("user-1"));
        verify(llmDomainService, never()).findModelById(Mockito.any());
    }
}
