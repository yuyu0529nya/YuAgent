package org.yu.interfaces.api.portal.account;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.yu.application.account.dto.AccountDTO;
import org.yu.application.account.service.AccountAppService;
import org.yu.infrastructure.auth.UserContext;
import org.yu.interfaces.api.common.Result;
import org.yu.interfaces.dto.account.request.AddCreditRequest;
import org.yu.interfaces.dto.account.request.RechargeRequest;

import java.math.BigDecimal;

/** 账户管理控制层 提供用户账户管理的API接口 */
@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountAppService accountAppService;

    public AccountController(AccountAppService accountAppService) {
        this.accountAppService = accountAppService;
    }

    /** 获取当前用户账户信息
     * 
     * @return 账户信息 */
    @GetMapping("/current")
    public Result<AccountDTO> getCurrentUserAccount() {
        String userId = UserContext.getCurrentUserId();
        AccountDTO account = accountAppService.getUserAccount(userId);
        return Result.success(account);
    }
}