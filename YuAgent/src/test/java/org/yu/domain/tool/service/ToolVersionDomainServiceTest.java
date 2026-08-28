package org.yu.domain.tool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.yu.domain.tool.model.ToolVersionEntity;
import org.yu.domain.tool.repository.ToolVersionRepository;
import org.yu.interfaces.dto.tool.request.QueryToolRequest;

class ToolVersionDomainServiceTest {

    @Test
    void shouldDelegateFilteringAndPaginationToRepository() {
        ToolVersionRepository repository = Mockito.mock(ToolVersionRepository.class);
        Page<ToolVersionEntity> expected = new Page<>(1, 1, 2);
        expected.setRecords(List.of(new ToolVersionEntity()));
        when(repository.selectLatestPublicToolVersions(Mockito.<Page<ToolVersionEntity>>any(), Mockito.eq("天气")))
                .thenReturn(expected);

        QueryToolRequest request = new QueryToolRequest();
        request.setToolName("天气");
        request.setPage(1);
        request.setPageSize(1);

        var page = new ToolVersionDomainService(repository).listToolVersion(request);

        assertEquals(expected, page);
        assertEquals(1, page.getRecords().size());
        ArgumentCaptor<Page<ToolVersionEntity>> pageCaptor = ArgumentCaptor.captor();
        verify(repository).selectLatestPublicToolVersions(pageCaptor.capture(), Mockito.eq("天气"));
        assertEquals(1, pageCaptor.getValue().getCurrent());
        assertEquals(1, pageCaptor.getValue().getSize());
    }
}
