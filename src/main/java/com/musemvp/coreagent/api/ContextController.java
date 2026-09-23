package com.musemvp.coreagent.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.musemvp.coreagent.api.dto.GlobalContextVO;
import com.musemvp.coreagent.service.ContextService;
import com.musemvp.coreagent.support.ApiHeaders;

/**
 * 全局上下文接口，见详细设计 §2.3.1（API-M01-01）。
 */
@RestController
@RequestMapping("/api")
public class ContextController {

    private final ContextService contextService;

    public ContextController(ContextService contextService) {
        this.contextService = contextService;
    }

    /**
     * 获取全局上下文：当前项目、可选项目、角色选项。
     *
     * <p>{@code X-Project-Id} 以字符串接收并宽松解析——非法值按缺失处理并回退默认项目（§2.4），不返回 4xx，
     * 保证直连 URL 可用。
     */
    @GetMapping("/context")
    public GlobalContextVO context(
            @RequestHeader(value = ApiHeaders.PROJECT_ID, required = false) String rawProjectId) {
        return contextService.getContext(ApiHeaders.parseProjectId(rawProjectId));
    }
}
