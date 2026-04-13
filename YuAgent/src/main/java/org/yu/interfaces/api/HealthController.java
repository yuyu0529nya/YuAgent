package org.yu.interfaces.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yu.interfaces.api.common.Result;

@RequestMapping
@RestController
public class HealthController {

    @GetMapping("/health")
    public Result<Object> health() {
        return Result.success().message("ok");
    }
}
