package com.musemvp.coreagent.api.dto;

import java.util.List;

/**
 * 平台说明文档，见详细设计 §2.3.7（API-M01-07）。
 *
 * <p>{@code sections} 为空数组时前端渲染空内容区而不报错（CAP-M01-07 异常预期）。
 *
 * @param anchor  章节锚点，前端左侧提纲点击后滚动到对应章节
 * @param title   章节标题
 * @param content 章节正文
 */
public record PlatformGuideVO(String title, List<Section> sections) {

    public record Section(String anchor, String title, String content) {
    }
}
