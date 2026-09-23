package com.musemvp.coreagent.service;

import com.musemvp.coreagent.support.AppTypeCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 字段规范化与判定规则的单元测试，覆盖 V-M04-01…V-M04-05 的纯计算部分（D-M04-06）。
 *
 * <p>本构件为纯函数、不访问数据库，故全部断言在单元层级完成（D-M04-06/4.5）。
 */
class AppTypeRulesTest {

    private static String repeat(char ch, int count) {
        return String.valueOf(ch).repeat(count);
    }

    @Test
    @DisplayName("V-M04-01：null、空串、半角空格、全角空格一律判为未填写")
    void blankInputsAreUnfilled() {
        assertEquals("", AppTypeRules.normalize(null));
        assertEquals("", AppTypeRules.normalize(""));
        assertEquals("", AppTypeRules.normalize("   "));
        // 全角空格 U+3000：trim() 不处理，strip() 处理——M04-TC-01 明确要求判为未填写
        assertEquals("", AppTypeRules.normalize("　"));
        assertEquals("", AppTypeRules.normalize("　 　"));

        assertTrue(AppTypeRules.isUnfilled(AppTypeRules.normalize(null)));
        assertTrue(AppTypeRules.isUnfilled(AppTypeRules.normalize("　")));
        assertFalse(AppTypeRules.isUnfilled(AppTypeRules.normalize("金融")));
    }

    @Test
    @DisplayName("V-M04-02：裁剪前后空白、保留内部空白、不做全半角与大小写换算")
    void trimKeepsInnerWhitespace() {
        assertEquals("金融", AppTypeRules.normalize("  金融  "));
        assertEquals("金融", AppTypeRules.normalize("　金融　"));
        // 内部空白不删除（D-M04-06/C-02）
        assertEquals("a b", AppTypeRules.normalize(" a b "));
        // 不做大小写折叠与全半角换算（DQ-M04-08）
        assertEquals("ABC", AppTypeRules.normalize(" ABC "));
    }

    @Test
    @DisplayName("V-M04-03：值说明空值归一为空串，不返回 null")
    void descriptionNeverNull() {
        assertEquals("", AppTypeRules.normalizeDescription(null));
        assertEquals("", AppTypeRules.normalizeDescription(""));
        assertEquals("", AppTypeRules.normalizeDescription("　"));
        assertEquals("面向金融行业", AppTypeRules.normalizeDescription(" 面向金融行业 "));
    }

    @Test
    @DisplayName("V-M04-04：长度按裁剪后的 UTF-16 码元计数，64/255 允许、65/256 拒绝")
    void lengthBoundaries() {
        String type64 = repeat('类', AppTypeCatalog.TYPE_MAX_LENGTH);
        String type65 = repeat('类', AppTypeCatalog.TYPE_MAX_LENGTH + 1);
        assertFalse(AppTypeRules.isTooLong(type64, AppTypeCatalog.TYPE_MAX_LENGTH));
        assertTrue(AppTypeRules.isTooLong(type65, AppTypeCatalog.TYPE_MAX_LENGTH));

        // 裁剪后计数：64 个字符加前后空白仍不超限（AC-M04-04 的数据范围为 64/65 字符）
        assertFalse(AppTypeRules.isTooLong(AppTypeRules.normalize(" " + type64 + " "), AppTypeCatalog.TYPE_MAX_LENGTH));

        String value64 = repeat('v', AppTypeCatalog.VALUE_MAX_LENGTH);
        assertFalse(AppTypeRules.isTooLong(value64, AppTypeCatalog.VALUE_MAX_LENGTH));
        assertTrue(AppTypeRules.isTooLong(value64 + "v", AppTypeCatalog.VALUE_MAX_LENGTH));

        String desc255 = repeat('d', AppTypeCatalog.DESCRIPTION_MAX_LENGTH);
        assertFalse(AppTypeRules.isTooLong(desc255, AppTypeCatalog.DESCRIPTION_MAX_LENGTH));
        assertTrue(AppTypeRules.isTooLong(desc255 + "d", AppTypeCatalog.DESCRIPTION_MAX_LENGTH));
    }

    @Test
    @DisplayName("V-M04-05：非字符串入参（含 null、数字、布尔）按空串处理")
    void nonStringBecomesEmpty() {
        assertEquals("", AppTypeRules.asText(null));
        assertEquals("", AppTypeRules.asText(12));
        assertEquals("", AppTypeRules.asText(true));
        assertEquals("", AppTypeRules.asText(new Object()));
        assertEquals("金融", AppTypeRules.asText("金融"));
    }

    @Test
    @DisplayName("长度上限常量与 DEF-M04-11 的列宽一致")
    void catalogConstants() {
        assertEquals(64, AppTypeCatalog.TYPE_MAX_LENGTH);
        assertEquals(64, AppTypeCatalog.VALUE_MAX_LENGTH);
        assertEquals(255, AppTypeCatalog.DESCRIPTION_MAX_LENGTH);
        // 预置维度清单只声明已知的 8 项，不编造第 9 个维度（DD-M04-13、DQ-M04-07）
        assertEquals(8, AppTypeCatalog.PRESET_TYPE_NAMES.size());
        assertTrue(AppTypeCatalog.PRESET_TYPE_NAMES.contains("行业领域"));
    }
}
