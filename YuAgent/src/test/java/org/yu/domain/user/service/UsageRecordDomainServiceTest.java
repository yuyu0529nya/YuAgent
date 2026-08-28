package org.yu.domain.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.yu.domain.user.model.UsageRecordEntity;
import org.yu.domain.user.repository.UsageRecordRepository;
import org.yu.infrastructure.exception.BusinessException;

class UsageRecordDomainServiceTest {

    @Test
    void shouldDelegateTotalCostAggregationToRepository() {
        UsageRecordRepository repository = Mockito.mock(UsageRecordRepository.class);
        when(repository.sumCostByUserId("user-1")).thenReturn(new BigDecimal("12.34"));

        BigDecimal total = new UsageRecordDomainService(repository).getUserTotalCost("user-1");

        assertEquals(new BigDecimal("12.34"), total);
        verify(repository).sumCostByUserId("user-1");
    }

    @Test
    void shouldRejectBlankUserIdBeforeQuerying() {
        UsageRecordRepository repository = Mockito.mock(UsageRecordRepository.class);

        assertThrows(BusinessException.class, () -> new UsageRecordDomainService(repository).getUserTotalCost(" "));

        verifyNoInteractions(repository);
    }

    @Test
    void shouldRejectDuplicateRequestIdsWithinTheSameBatchBeforeQuerying() {
        UsageRecordRepository repository = Mockito.mock(UsageRecordRepository.class);
        UsageRecordDomainService service = new UsageRecordDomainService(repository);

        assertThrows(BusinessException.class,
                () -> service.batchRecordUsage(List.of(record("request-1"), record("request-1"))));

        verifyNoInteractions(repository);
    }

    @Test
    void shouldRejectExistingRequestIdWithOneBatchLookup() {
        UsageRecordRepository repository = Mockito.mock(UsageRecordRepository.class);
        when(repository.<String>selectObjs(Mockito.<Wrapper<UsageRecordEntity>>any())).thenReturn(List.of("request-2"));
        UsageRecordDomainService service = new UsageRecordDomainService(repository);

        assertThrows(BusinessException.class,
                () -> service.batchRecordUsage(List.of(record("request-1"), record("request-2"))));

        verify(repository).selectObjs(Mockito.<Wrapper<UsageRecordEntity>>any());
        verify(repository, never()).insert(Mockito.<List<UsageRecordEntity>>any());
    }

    @Test
    void shouldInsertNonDuplicateRecordsAsOneBatch() {
        UsageRecordRepository repository = Mockito.mock(UsageRecordRepository.class);
        when(repository.<String>selectObjs(Mockito.<Wrapper<UsageRecordEntity>>any())).thenReturn(List.of());
        UsageRecordDomainService service = new UsageRecordDomainService(repository);
        List<UsageRecordEntity> records = List.of(record("request-1"), record("request-2"));

        service.batchRecordUsage(records);

        verify(repository).insert(records);
    }

    private UsageRecordEntity record(String requestId) {
        UsageRecordEntity record = new UsageRecordEntity();
        record.setUserId("user-1");
        record.setProductId("product-1");
        record.setQuantityData(Map.of("tokens", 1));
        record.setCost(BigDecimal.ONE);
        record.setRequestId(requestId);
        return record;
    }
}
