package com.musemvp.coreagent.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.musemvp.coreagent.api.dto.PlatformGuideVO;
import com.musemvp.coreagent.service.GuideService;

/**
 * 平台说明接口，见详细设计 §2.3.7（API-M01-07）。
 */
@RestController
@RequestMapping("/api")
public class GuideController {

    private final GuideService guideService;

    public GuideController(GuideService guideService) {
        this.guideService = guideService;
    }

    /** 说明文档缺失时返回空 {@code sections}，前端渲染空内容区而不报错（CAP-M01-07）。 */
    @GetMapping("/platform-guide")
    public PlatformGuideVO platformGuide() {
        return guideService.getGuide();
    }
}
