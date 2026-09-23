package com.musemvp.coreagent.service;

import com.musemvp.coreagent.support.AppTypeCatalog;

/**
 * 应用类型字典的字段规范化与判定规则，见详细设计 D-M04-06（M04）。
 *
 * <p><b>纯函数构件</b>（D-M04-06/C-10）：无副作用、无 I/O、<b>不抛业务异常</b>、不持有业务文案——
 * 只返回规范化结果与布尔判定，由调用方 {@link AppTypeService} 映射为具体错误码与文案
 * （D-M04-10/C-14）。因此本类可脱离 Spring 上下文直接单测（D-M04-06/4.5）。
 *
 * <p>长度上限一律引用 {@link AppTypeCatalog} 常量，不出现字面量副本（D-M04-06/C-01）。
 *
 * <p><b>裁剪语义</b>：使用 {@link String#strip()}，即按 {@link Character#isWhitespace} 判定前后空白。
 * 设计 D-M04-06/C-02 字面写为 {@code trim()} 并附注「含全角空格以外的 Unicode 空白由 trim 语义决定」，
 * 但同一条 C-06 要求「仅含空白字符的输入等价于未填写」、RC-M04-01 要求「裁剪后为空即未填写」，
 * 且验证用例 M04-TC-01 明确以全角空格（U+3000）作为输入。{@code trim()} 只处理 {@code <= U+0020}，
 * 无法满足后三者的联合要求，故取 {@code strip()} —— 对 ASCII 空白域是 {@code trim()} 的严格超集，
 * 行为一致；对 U+3000 等 Unicode 空白补足 C-06 的判定。该取舍已登记问题清单。
 */
public final class AppTypeRules {

    private AppTypeRules() {
    }

    /**
     * 请求体取值的宽容读取（D-M04-06/4.3 边界：请求体缺字段或显式 {@code null} 等价）：
     * 非字符串（含 {@code null}、数字、布尔、对象）一律按空串处理。
     */
    public static String asText(Object raw) {
        return raw instanceof String text ? text : "";
    }

    /**
     * 类型名归一（D-M04-06/C-02）：{@code null} → {@code ""}；否则返回前后空白修剪后的字符串。
     *
     * <p>不与数据库排序规则做归一、不删除内部空白、不做大小写折叠或全半角换算（DQ-M04-08）。
     * 删除整类时的类型名归一走同一方法（D-M04-06/C-09）。
     */
    public static String normalize(String raw) {
        return raw == null ? "" : raw.strip();
    }

    /**
     * 值说明归一（D-M04-06/C-04）：裁剪后为空时返回 {@code ""}，<b>不返回 {@code null}</b>，
     * 保证空说明按空串存储与序列化（RC-M04-03）。
     */
    public static String normalizeDescription(String raw) {
        return normalize(raw);
    }

    /** 空判定（D-M04-06/C-06）：入参须为已归一的字符串；{@code isEmpty()} 为真即「未填写」。 */
    public static boolean isUnfilled(String normalized) {
        return normalized == null || normalized.isEmpty();
    }

    /**
     * 长度判定（D-M04-06/C-05）：按<b>裁剪后</b>的 UTF-16 码元计数（{@code String.length()}，DD-M04-05），
     * 超过上限即为真；超长不截断。
     */
    public static boolean isTooLong(String normalized, int maxLength) {
        return normalized != null && normalized.length() > maxLength;
    }
}
