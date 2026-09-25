package com.musemvp.coreagent.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musemvp.coreagent.api.dto.ThemeTokenSetVO;
import com.musemvp.coreagent.api.dto.ThemeTokenVO;
import com.musemvp.coreagent.api.dto.ThemeTokenWarningVO;
import com.musemvp.coreagent.support.BizException;
import com.musemvp.coreagent.support.ErrorCode;
import com.musemvp.coreagent.support.ThemeProperties;
import com.musemvp.coreagent.support.TokenGroup;
import com.musemvp.coreagent.support.TokenScope;
import com.musemvp.coreagent.support.TokenValidator;

/**
 * 视觉主题令牌的装载、校验、回退与缓存，见 DESIGN-FT-002 §4 D-003。
 *
 * <p>令牌在启动期一次性装载（{@link #load()}），逐项校验并构建不可变缓存；运行期只读，无锁竞争、
 * 无数据库访问。单个令牌非法时降级为该令牌的回退值并计入 {@code warnings}，页面不失败；
 * 整包级失败（资源缺失／解析失败／主题标识非法／回退令牌不完备）时接口返回 M01-E007，
 * 前端以 {@code css/tokens.css} 静态基线渲染（DD-004 的第三级降级）。
 */
@Service
public class ThemeService {

    private static final Logger log = LoggerFactory.getLogger(ThemeService.class);

    private static final String DEFAULT_THEME = "dark-gold";

    /** 可运维切换的主题标识（D-006/C-01）。 */
    private static final Set<String> SUPPORTED_THEMES = Set.of("dark-gold", "light");

    /**
     * 必填回退令牌集合（D-001/C-05）：每组至少 1 个回退令牌，且清单中的这些必须存在。
     *
     * <p>缺失或自身校验不通过即判定整包失败——回退链路是 DD-004「三级降级不白屏」的最后一环，
     * 缺了它单个令牌非法时就没有可用的兜底取值。
     */
    private static final Set<String> REQUIRED_FALLBACK_TOKENS = Set.of(
            "color-bg-primary",
            "color-border-neutral",
            "color-text-primary",
            "color-accent-gold",
            "color-success",
            "font-family-base",
            "space-2",
            "radius-md",
            "border-gold-hairline",
            "shadow-none",
            "motion-duration-fallback");

    private final ThemeProperties properties;
    private final ResourceLoader resourceLoader;
    private final TokenValidator tokenValidator;
    private final ObjectMapper objectMapper;

    /** 已校验的生效令牌包；为 {@code null} 表示装载失败，接口按 M01-E007 返回。 */
    private volatile ThemeTokenSetVO tokenSet;

    public ThemeService(ThemeProperties properties, ResourceLoader resourceLoader,
                        TokenValidator tokenValidator, ObjectMapper objectMapper) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
        this.tokenValidator = tokenValidator;
        this.objectMapper = objectMapper;
    }

    /**
     * 启动期装载令牌包（D-003/P-01…P-08）。
     *
     * <p>失败不阻断应用启动：令牌是呈现层配置，业务功能不依赖它。失败仅把缓存置空，接口随后返回
     * M01-E007，前端沿用静态基线包着色。
     */
    @PostConstruct
    public void load() {
        String active = properties.getActive();
        // 主题标识非法属整包级失败（D-003/C-03），**不**静默回退默认主题：静默回退会把运维的配置
        // 错误藏起来，全站仍是黑金、无人察觉。此处判定失败，前端按 M01-E007 降级到 tokens.css
        // 静态基线（同为黑金）并 toast 提示，观感一致但失败可见（DD-004 不静默失败）。
        // 该白名单同时是路径安全边界：active 只可能取到此处的枚举值，不会拼出越界的资源路径。
        if (!SUPPORTED_THEMES.contains(active)) {
            this.tokenSet = null;
            log.error("主题标识非法，判定整包失败，接口将返回 M01-E007：active={} 支持取值={}",
                    active, SUPPORTED_THEMES);
            return;
        }
        if (!DEFAULT_THEME.equals(active)) {
            // 审计：便于排查「为何全站是浅色」（D-003/P-02、DD-004）
            log.info("主题回退开关生效：active={}，全站渲染为浅色基线", active);
        }

        String location = resolveLocation(active);
        try {
            TokenPack pack = readPack(location);
            ThemeTokenSetVO loaded = build(pack, active);
            this.tokenSet = loaded;
            log.info("主题令牌装载成功：theme={} version={} tokens={} warnings={}",
                    loaded.theme(), loaded.version(), loaded.tokens().size(), loaded.warnings().size());
        } catch (RuntimeException | IOException ex) {
            this.tokenSet = null;
            log.error("主题令牌装载失败，接口将返回 M01-E007，前端沿用静态基线包：location={}", location, ex);
        }
    }

    /**
     * 返回当前生效令牌包（含 {@code warnings}）。
     *
     * @throws BizException M01-E007（503）：令牌包缺失、解析失败、主题标识非法或回退令牌不完备
     */
    public ThemeTokenSetVO getTokenSet() {
        ThemeTokenSetVO current = tokenSet;
        if (current == null) {
            throw new BizException(ErrorCode.M01_E007);
        }
        return current;
    }

    /** 按名称取令牌，供后续服务端渲染场景复用（D-003）。 */
    public Optional<ThemeTokenVO> resolve(String name) {
        ThemeTokenSetVO current = tokenSet;
        if (current == null || !StringUtils.hasText(name)) {
            return Optional.empty();
        }
        return current.tokens().stream().filter(token -> name.equals(token.name())).findFirst();
    }

    private String resolveLocation(String active) {
        String location = properties.getTokenLocation();
        if (!StringUtils.hasText(location)) {
            location = "classpath:theme/";
        }
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        return location + active + ".json";
    }

    private TokenPack readPack(String location) throws IOException {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IOException("令牌包资源不存在：" + location);
        }
        try (InputStream input = resource.getInputStream()) {
            return objectMapper.readValue(input, TokenPack.class);
        }
    }

    /**
     * 逐令牌校验 → 回退 → 汇编为不可变令牌包（D-003/P-05…P-08）。
     *
     * @throws IllegalStateException 整包级失败，由 {@link #load()} 捕获并降级为 M01-E007
     */
    private ThemeTokenSetVO build(TokenPack pack, String active) {
        if (pack == null || pack.tokens() == null || pack.tokens().isEmpty()) {
            throw new IllegalStateException("令牌包为空");
        }
        if (StringUtils.hasText(pack.theme()) && !active.equals(pack.theme())) {
            log.warn("令牌包内主题标识与配置不一致：active={} pack={}", active, pack.theme());
        }

        List<Entry> entries = validateAll(pack.tokens());
        Map<TokenGroup, String> groupFallbacks = resolveGroupFallbacks(entries);
        verifyRequiredFallbacks(entries, groupFallbacks);

        List<ThemeTokenVO> tokens = new ArrayList<>();
        List<ThemeTokenWarningVO> warnings = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.reason == null) {
                tokens.add(entry.toToken(entry.value));
                continue;
            }
            if (entry.dropped) {
                // 分组未知或名称非法：无法确定安全的 CSS 变量名，整条丢弃（前端亦会按名称白名单跳过）
                warnings.add(ThemeTokenWarningVO.dropped(entry.rawGroup, entry.name, entry.reason));
                continue;
            }
            String fallbackValue = groupFallbacks.get(entry.group);
            warnings.add(new ThemeTokenWarningVO(entry.group.getKey(), entry.name, entry.reason, fallbackValue));
            tokens.add(entry.toToken(fallbackValue));
            log.warn("令牌非法，已降级为同组回退令牌：group={} name={} reason={} fallbackValue={}",
                    entry.group.getKey(), entry.name, entry.reason, fallbackValue);
        }

        if (properties.isStrict() && !warnings.isEmpty()) {
            throw new IllegalStateException("strict 模式下存在非法令牌，判定整包失败：warnings=" + warnings.size());
        }

        String version = StringUtils.hasText(pack.version()) ? pack.version() : "unknown";
        String themeName = StringUtils.hasText(pack.themeName()) ? pack.themeName() : active;
        return new ThemeTokenSetVO(active, themeName, version, Instant.now(), tokens, warnings);
    }

    private List<Entry> validateAll(List<TokenDefinition> definitions) {
        List<Entry> entries = new ArrayList<>(definitions.size());
        Set<String> seenNames = new HashSet<>();
        for (TokenDefinition definition : definitions) {
            TokenGroup group = TokenGroup.from(definition.group()).orElse(null);
            if (group == null) {
                entries.add(Entry.dropped(definition, null, TokenValidator.REASON_GROUP_INVALID));
                continue;
            }
            String reason = tokenValidator.validate(group, definition.name(), definition.value(),
                    definition.description(), definition.scope());
            if (reason == null && !seenNames.add(group.getKey() + ':' + definition.name())) {
                reason = TokenValidator.REASON_NAME_DUPLICATED;
            }
            if (TokenValidator.REASON_GROUP_INVALID.equals(reason)
                    || TokenValidator.REASON_NAME_INVALID.equals(reason)
                    || TokenValidator.REASON_NAME_DUPLICATED.equals(reason)) {
                entries.add(Entry.dropped(definition, group, reason));
            } else {
                entries.add(Entry.of(definition, group, reason));
            }
        }
        return entries;
    }

    /**
     * 取每组的回退取值：该组中 {@code fallback = true} 且自身校验通过的令牌。
     *
     * @throws IllegalStateException 某组没有可用回退令牌 → 整包失败
     */
    private Map<TokenGroup, String> resolveGroupFallbacks(List<Entry> entries) {
        Map<TokenGroup, String> fallbacks = new HashMap<>();
        for (Entry entry : entries) {
            if (entry.group != null && entry.reason == null && entry.fallback) {
                fallbacks.putIfAbsent(entry.group, entry.value);
            }
        }
        for (TokenGroup group : TokenGroup.values()) {
            if (!fallbacks.containsKey(group)) {
                throw new IllegalStateException("分组缺少可用回退令牌，判定整包失败：" + group.getKey());
            }
        }
        return fallbacks;
    }

    /** 必填回退令牌必须存在且自身合法，否则整包失败（D-003/C-05）。 */
    private void verifyRequiredFallbacks(List<Entry> entries, Map<TokenGroup, String> groupFallbacks) {
        Set<String> validNames = new HashSet<>();
        for (Entry entry : entries) {
            if (entry.reason == null && entry.group != null) {
                validNames.add(entry.name);
            }
        }
        for (String required : REQUIRED_FALLBACK_TOKENS) {
            if (!validNames.contains(required)) {
                throw new IllegalStateException("必填回退令牌缺失或非法，判定整包失败：" + required);
            }
        }
    }

    /** 令牌包文件结构（D-001/C-01）：顶层元信息 + 令牌扁平数组。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenPack(String theme, String themeName, String version, List<TokenDefinition> tokens) {
    }

    /** 令牌 JSON 条目；取值与枚举均按字符串读入，由 {@link TokenValidator} 统一校验。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenDefinition(String group, String name, String value, String description,
                           String scope, Boolean fallback) {
    }

    /** 校验中间态：承载原始定义、解析出的分组与非法原因。 */
    private static final class Entry {

        private final TokenGroup group;
        private final String rawGroup;
        private final String name;
        private final String value;
        private final String description;
        private final boolean fallback;
        private final String reason;
        private final boolean dropped;

        private Entry(TokenGroup group, String rawGroup, String name, String value, String description,
                      boolean fallback, String reason, boolean dropped) {
            this.group = group;
            this.rawGroup = rawGroup;
            this.name = name;
            this.value = value;
            this.description = description;
            this.fallback = fallback;
            this.reason = reason;
            this.dropped = dropped;
        }

        static Entry of(TokenDefinition definition, TokenGroup group, String reason) {
            return new Entry(group, definition.group(), definition.name(), definition.value(),
                    definition.description(), Boolean.TRUE.equals(definition.fallback()), reason, false);
        }

        static Entry dropped(TokenDefinition definition, TokenGroup group, String reason) {
            return new Entry(group, definition.group(), definition.name(), definition.value(),
                    definition.description(), Boolean.TRUE.equals(definition.fallback()), reason, true);
        }

        /**
         * 转为下发的令牌对象。
         *
         * @param effectiveValue 实际生效取值：合法令牌为其自身取值，非法令牌为同组回退取值
         */
        ThemeTokenVO toToken(String effectiveValue) {
            // 适用范围非法时归一为 GLOBAL 而不是沿用非法值：本期下发的令牌全部为全局令牌（D-001/C-02），
            // 前端也只接受 GLOBAL，下发一个不可识别的取值只会让该令牌在前端被二次丢弃。
            String text = StringUtils.hasText(description) ? description : name;
            return new ThemeTokenVO(group == null ? rawGroup : group.getKey(), name, effectiveValue, text,
                    TokenScope.GLOBAL.name(), fallback);
        }
    }
}
