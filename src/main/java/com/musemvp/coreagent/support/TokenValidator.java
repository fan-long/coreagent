package com.musemvp.coreagent.support;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 令牌校验器，见 DESIGN-FT-002 §4 D-002。
 *
 * <p>令牌值最终会被前端写入 {@code <style>} 元素，是本次变更新增的唯一注入面。服务端与前端各做一次
 * 白名单校验（纵深防御，DD-005）：服务端保证下发的数据可信，前端 {@code theme.js} 再做一次同规则校验。
 *
 * <p>本类只回答「合法 / 非法 + 原因码」，令牌级回退与告警由 {@code ThemeService} 处理。
 */
@Component
public class TokenValidator {

    /** 名称不符合 kebab-case 规范。 */
    public static final String REASON_NAME_INVALID = "TOKEN_NAME_INVALID";

    /** 组内名称重复。 */
    public static final String REASON_NAME_DUPLICATED = "TOKEN_NAME_DUPLICATED";

    /** 取值不符合所属组的形态约束。 */
    public static final String REASON_VALUE_INVALID = "TOKEN_VALUE_INVALID";

    /** 语义说明缺失。 */
    public static final String REASON_DESCRIPTION_MISSING = "TOKEN_DESCRIPTION_MISSING";

    /** 适用范围不在枚举内。 */
    public static final String REASON_SCOPE_INVALID = "TOKEN_SCOPE_INVALID";

    /** 分组不在七组枚举内。 */
    public static final String REASON_GROUP_INVALID = "TOKEN_GROUP_INVALID";

    /** 令牌缺失（回退令牌或必填令牌未定义）。 */
    public static final String REASON_MISSING = "TOKEN_MISSING";

    /** 令牌名称：kebab-case，组内唯一（D-002/C-06）。 */
    private static final Pattern NAME = Pattern.compile("^[a-z][a-z0-9]*(-[a-z0-9]+)*$");

    private static final Pattern HEX = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final Pattern RGBA =
            Pattern.compile("^rgba\\(\\d{1,3},\\d{1,3},\\d{1,3},(0|1|0?\\.\\d+)\\)$");
    private static final Pattern GRADIENT_PREFIX_ANGLE = Pattern.compile("^\\d{1,3}deg$");
    private static final Pattern PERCENT = Pattern.compile("^\\d{1,3}%$");

    private static final Pattern PX = Pattern.compile("^\\d+(\\.\\d+)?px$");
    private static final Pattern REM = Pattern.compile("^\\d*(\\.\\d+)?rem$");
    private static final Pattern WEIGHT = Pattern.compile("^\\d{3}$");
    private static final Pattern UNITLESS = Pattern.compile("^\\d+(\\.\\d+)?$");
    private static final Pattern MS = Pattern.compile("^\\d+ms$");
    private static final Pattern EASE = Pattern.compile("^ease(-in|-out|-in-out)?$");
    private static final Pattern CUBIC_BEZIER =
            Pattern.compile("^cubic-bezier\\(([01]|0?\\.\\d+),([01]|0?\\.\\d+),"
                    + "([01]|0?\\.\\d+),([01]|0?\\.\\d+)\\)$");

    private static final Pattern VAR_REF = Pattern.compile("^var\\(--ca-[a-z0-9-]+\\)$");
    private static final Pattern BORDER_SHORTHAND =
            Pattern.compile("^\\d+px (solid|dashed) [^;{}<>]+$");
    private static final Pattern SHADOW_LENGTH = Pattern.compile("^(0|-?\\d+(\\.\\d+)?px)$");
    private static final Pattern FONT_FAMILY = Pattern.compile("^[A-Za-z0-9 ,\\-_.\"']+$");
    private static final Pattern FONT_FEATURE =
            Pattern.compile("^\"[a-z]{4}\"\\s+(0|1|on|off)(,\\s*\"[a-z]{4}\"\\s+(0|1|on|off))*$");

    /** 任何含分号、花括号或尖括号的取值一律拒绝——它们能把令牌值变成一条新的 CSS 规则。 */
    private static final Pattern DANGEROUS = Pattern.compile("[;{}<>]");

    /**
     * 校验单个令牌。返回 {@code null} 表示合法，否则返回 {@link #REASON_NAME_INVALID} 等原因码。
     *
     * <p>调用方须先确认 {@code group} 已由 {@link TokenGroup#from(String)} 解析成功。
     */
    public String validate(TokenGroup group, String name, String value, String description, String scope) {
        if (!StringUtils.hasText(description)) {
            return REASON_DESCRIPTION_MISSING;
        }
        if (TokenScope.from(scope).isEmpty()) {
            return REASON_SCOPE_INVALID;
        }
        if (!StringUtils.hasText(name) || !NAME.matcher(name).matches()) {
            return REASON_NAME_INVALID;
        }
        if (!StringUtils.hasText(value) || DANGEROUS.matcher(value).find()) {
            return REASON_VALUE_INVALID;
        }
        return isValidValue(group, name.trim(), value.trim()) ? null : REASON_VALUE_INVALID;
    }

    /** 按组（必要时再按令牌名细分）校验取值形态，见 D-002/C-02 的取值一列。 */
    public boolean isValidValue(TokenGroup group, String name, String value) {
        return switch (group) {
            case COLOR -> isColor(value) || isLinearGradient(value);
            case FONT -> isFontValue(name, value);
            case SPACE -> isSpace(value);
            case RADIUS -> PX.matcher(value).matches();
            case BORDER -> isBorderValue(name, value);
            case SHADOW -> isShadowValue(value);
            case MOTION -> isMotionValue(name, value);
        };
    }

    /** 颜色：{@code #RRGGBB} 或 {@code rgba()}（D-002/C-02）。 */
    public boolean isColor(String value) {
        return HEX.matcher(value).matches() || RGBA.matcher(value).matches();
    }

    /**
     * 金色渐变（{@code gradient-gold}）。D-001/C-04 把 {@code gradient-gold} 归入色板组，但其取值形态为
     * {@code linear-gradient()}，是 D-002/C-02「色板取值为 #RRGGBB 或 rgba()」的一条显式扩展：
     * 角度段固定为 {@code {n}deg}，色标段固定为「合法色值 + 百分比」，不含任何自由文本，
     * 因此不扩大注入面。
     */
    public boolean isLinearGradient(String value) {
        if (!value.startsWith("linear-gradient(") || !value.endsWith(")")) {
            return false;
        }
        String body = value.substring("linear-gradient(".length(), value.length() - 1);
        List<String> parts = splitTopLevel(body);
        if (parts.size() < 3 || !GRADIENT_PREFIX_ANGLE.matcher(parts.get(0).trim()).matches()) {
            return false;
        }
        for (String stop : parts.subList(1, parts.size())) {
            String[] pair = stop.trim().split("\\s+");
            if (pair.length != 2 || !isColor(pair[0]) || !PERCENT.matcher(pair[1]).matches()) {
                return false;
            }
        }
        return true;
    }

    private boolean isFontValue(String name, String value) {
        if (name.contains("font-family")) {
            return FONT_FAMILY.matcher(value).matches() && value.chars().anyMatch(Character::isLetter);
        }
        if (name.contains("font-size")) {
            return REM.matcher(value).matches();
        }
        if (name.contains("font-weight")) {
            if (!WEIGHT.matcher(value).matches()) {
                return false;
            }
            int weight = Integer.parseInt(value);
            return weight >= 100 && weight <= 900;
        }
        if (name.contains("font-line-height")) {
            return UNITLESS.matcher(value).matches();
        }
        if (name.contains("font-numeric-feature")) {
            return FONT_FEATURE.matcher(value).matches();
        }
        return false;
    }

    /** 间距须为 px，且为 4 的正整数倍（D-002/C-02 的 4px 基数梯度）。 */
    private boolean isSpace(String value) {
        if (!PX.matcher(value).matches()) {
            return false;
        }
        double px = Double.parseDouble(value.substring(0, value.length() - 2));
        return px > 0 && px % 4 == 0;
    }

    /** 线宽为 px；线型简写为「宽度 + 线型 + 合法颜色（{@code var()} 引用或色值）」。 */
    private boolean isBorderValue(String name, String value) {
        if (name.startsWith("border-width-")) {
            return PX.matcher(value).matches();
        }
        if (!BORDER_SHORTHAND.matcher(value).matches()) {
            return false;
        }
        String color = value.substring(value.lastIndexOf(' ') + 1);
        return VAR_REF.matcher(color).matches() || isColor(color);
    }

    /** 阴影：{@code none}，或逗号分隔的「偏移 + 模糊 [+ 扩散] + 色值」组合。 */
    private boolean isShadowValue(String value) {
        if ("none".equals(value)) {
            return true;
        }
        for (String layer : splitTopLevel(value)) {
            String[] parts = layer.trim().split("\\s+");
            if (parts.length < 3 || parts.length > 5) {
                return false;
            }
            for (int index = 0; index < parts.length - 1; index += 1) {
                if (!SHADOW_LENGTH.matcher(parts[index]).matches()) {
                    return false;
                }
            }
            if (!isColor(parts[parts.length - 1])) {
                return false;
            }
        }
        return true;
    }

    /** 动效：时长为 ms；缓动为 {@code ease*} 或各分量 ∈ [0,1] 的 {@code cubic-bezier()}。 */
    private boolean isMotionValue(String name, String value) {
        if (name.contains("duration")) {
            return MS.matcher(value).matches();
        }
        if (name.contains("ease")) {
            return EASE.matcher(value).matches() || CUBIC_BEZIER.matcher(value).matches();
        }
        return false;
    }

    /**
     * 按括号深度切分顶层逗号。
     *
     * <p>{@code rgba(…)}、{@code var(…)} 内部也含逗号，直接 {@code split(",")} 会把一个色值切成两段
     * 而误判为非法，故此处只在括号外切分。
     */
    private List<String> splitTopLevel(String value) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int index = 0; index < value.length(); index += 1) {
            char current = value.charAt(index);
            if (current == '(') {
                depth += 1;
            } else if (current == ')') {
                depth -= 1;
            } else if (current == ',' && depth == 0) {
                parts.add(value.substring(start, index));
                start = index + 1;
            }
        }
        parts.add(value.substring(start));
        return parts;
    }

    /** 供 {@code ThemeService} 做整包校验时复用的名称规范判断。 */
    public boolean isValidName(String name) {
        return StringUtils.hasText(name) && NAME.matcher(name).matches();
    }

    /** 令牌值是否可安全写入 {@code <style>}：不含分号、花括号、尖括号。 */
    public boolean isSafeValue(String value) {
        return StringUtils.hasText(value) && !DANGEROUS.matcher(value).find();
    }

    /** 暴露名称正则，便于测试与前端规则对齐时引用。 */
    public Matcher nameMatcher(String name) {
        return NAME.matcher(name);
    }
}
