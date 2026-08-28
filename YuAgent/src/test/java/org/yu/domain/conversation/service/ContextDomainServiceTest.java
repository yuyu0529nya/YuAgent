package org.yu.domain.conversation.service;

import org.junit.jupiter.api.Test;
import org.yu.domain.conversation.model.ContextEntity;
import org.yu.domain.conversation.repository.ContextRepository;
import org.yu.infrastructure.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.springframework.dao.DuplicateKeyException;

class ContextDomainServiceTest {

    private final ContextRepository contextRepository = mock(ContextRepository.class);
    private final ContextDomainService service = new ContextDomainService(contextRepository);

    @Test
    void shouldInsertNewContext() {
        ContextEntity context = new ContextEntity();
        context.setSessionId("session-1");

        assertSame(context, service.insertOrUpdate(context));

        verify(contextRepository).insert(context);
    }

    @Test
    void shouldUpdateContextWithAnExistingId() {
        ContextEntity context = new ContextEntity();
        context.setId("context-1");
        context.setSessionId("session-1");

        assertSame(context, service.insertOrUpdate(context));

        verify(contextRepository).checkedUpdateById(context);
    }

    @Test
    void shouldRecoverFromConcurrentContextCreation() {
        ContextEntity context = new ContextEntity();
        context.setSessionId("session-1");
        doThrow(new DuplicateKeyException("duplicate session")).when(contextRepository).insert(context);
        ContextEntity existing = new ContextEntity();
        existing.setId("existing-context");
        existing.setSessionId("session-1");
        when(contextRepository.selectOne(any())).thenReturn(existing);

        assertSame(context, service.insertOrUpdate(context));

        assertEquals("existing-context", context.getId());
        verify(contextRepository).checkedUpdateById(context);
    }

    @Test
    void shouldFailWhenSavingContextFails() {
        ContextEntity context = new ContextEntity();
        context.setSessionId("session-1");
        doThrow(new RuntimeException("database unavailable")).when(contextRepository).insert(context);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.insertOrUpdate(context));

        assertEquals("保存消息上下文失败", exception.getMessage());
    }
}
