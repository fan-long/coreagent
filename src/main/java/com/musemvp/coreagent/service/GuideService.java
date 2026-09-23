package com.musemvp.coreagent.service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.musemvp.coreagent.api.dto.PlatformGuideVO;

/**
 * 平台说明文档读取，见详细设计 §2.5.4（CAP-M01-07）。
 *
 * <p>文档以 Markdown 存放于应用类路径，一级标题为文档标题，二级标题切分章节。文档缺失或不可读时返回空
 * {@code sections} 而非抛异常——前端渲染空内容区（CAP-M01-07 异常预期）。
 */
@Service
public class GuideService {

    private static final Logger log = LoggerFactory.getLogger(GuideService.class);

    private static final String GUIDE_LOCATION = "classpath:platform-guide.md";
    private static final String DEFAULT_TITLE = "Core-Agent 平台说明";

    /** 支持 {@code ## 标题 {#anchor}} 显式指定锚点；未指定时以标题文本作为锚点。 */
    private static final Pattern EXPLICIT_ANCHOR = Pattern.compile("^(.*?)\\s*\\{#([^}]+)}\\s*$");

    private final ResourceLoader resourceLoader;

    public GuideService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public PlatformGuideVO getGuide() {
        Resource resource = resourceLoader.getResource(GUIDE_LOCATION);
        if (!resource.exists()) {
            log.warn("平台说明文档不存在，返回空内容：{}", GUIDE_LOCATION);
            return new PlatformGuideVO(DEFAULT_TITLE, List.of());
        }
        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            return parse(reader);
        } catch (IOException ex) {
            log.warn("平台说明文档读取失败，返回空内容：{}", ex.getMessage());
            return new PlatformGuideVO(DEFAULT_TITLE, List.of());
        }
    }

    private PlatformGuideVO parse(Reader reader) throws IOException {
        StringBuilder raw = new StringBuilder();
        char[] buffer = new char[4096];
        int read;
        while ((read = reader.read(buffer)) != -1) {
            raw.append(buffer, 0, read);
        }

        String title = DEFAULT_TITLE;
        List<PlatformGuideVO.Section> sections = new ArrayList<>();
        String anchor = null;
        String sectionTitle = null;
        StringBuilder content = new StringBuilder();

        for (String line : raw.toString().split("\\R")) {
            if (line.startsWith("## ")) {
                appendSection(sections, anchor, sectionTitle, content);
                String heading = line.substring(3).trim();
                Matcher matcher = EXPLICIT_ANCHOR.matcher(heading);
                if (matcher.matches()) {
                    sectionTitle = matcher.group(1).trim();
                    anchor = matcher.group(2).trim();
                } else {
                    sectionTitle = heading;
                    anchor = heading;
                }
                content.setLength(0);
            } else if (line.startsWith("# ") && sections.isEmpty() && sectionTitle == null) {
                title = line.substring(2).trim();
            } else if (sectionTitle != null) {
                content.append(line).append('\n');
            }
        }
        appendSection(sections, anchor, sectionTitle, content);
        return new PlatformGuideVO(title, sections);
    }

    private void appendSection(List<PlatformGuideVO.Section> sections, String anchor, String title,
                               StringBuilder content) {
        if (!StringUtils.hasText(title)) {
            return;
        }
        sections.add(new PlatformGuideVO.Section(anchor, title, content.toString().trim()));
    }
}
