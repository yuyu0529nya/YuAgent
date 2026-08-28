package org.yu.domain.tool.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.yu.application.tool.service.ToolStateStateMachineAppService;
import org.yu.domain.tool.model.ToolEntity;

@SpringBootTest
@Disabled("依赖已部署的审核容器和指定数据库数据；保留为手工集成验证")
public class ToolTest {

    @Autowired
    private ToolStateStateMachineAppService toolStateStateMachine;

    @Autowired
    private ToolDomainService toolDomainService;

    @Test
    public void testToolState() {
        ToolEntity tool = toolDomainService.getTool("fcf8589b869aada08e4fe7c29121ddb8");
        toolStateStateMachine.submitToolForProcessing(tool);

        // 简化测试逻辑，避免无限循环
        // while (true) {
        // }
    }
}
