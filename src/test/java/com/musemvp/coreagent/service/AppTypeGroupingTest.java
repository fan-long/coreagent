package com.musemvp.coreagent.service;

import com.musemvp.coreagent.api.dto.AppTypeGroupVO;
import com.musemvp.coreagent.api.dto.AppTypeValueVO;
import com.musemvp.coreagent.domain.ComponentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内存分组的单元测试，覆盖 V-M04-10、V-M04-18、V-M04-20、V-M04-21 的装配部分（D-M04-07）。
 *
 * <p>入参按 {@code findAllOrdered()} 的契约构造为「已按 {@code sort_order ASC, id ASC} 排定」的行列表，
 * 本构件只解释分组与统计口径，不排序（D-M04-07/4.3 关键约束）。
 */
class AppTypeGroupingTest {

    private static ComponentType row(long id, String type, String value, String description, Integer sortOrder) {
        ComponentType entity = new ComponentType(type, value, description, sortOrder);
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    @Test
    @DisplayName("空行列表 → 空分组（对应空字典 types=[]）")
    void emptyRows() {
        assertTrue(AppTypeGrouping.group(List.of()).isEmpty());
    }

    @Test
    @DisplayName("V-M04-20：分组顺序由组内首条记录的位置决定，同类型内保持输入顺序")
    void groupOrderFollowsFirstOccurrence() {
        // 排序号 2,1,3,3 + 同排序号的 id 升序（AC-M04-10、M04-TC-16 的构造数据）
        List<ComponentType> rows = List.of(
                row(5, "行业领域", "金融", "面向金融行业", 1),
                row(6, "行业领域", "制造", "", 2),
                row(7, "用户对象", "个人用户", "", 3),
                row(8, "用户对象", "企业用户", "", 3),
                row(9, "行业领域", "能源", null, 4));

        List<AppTypeGroupVO> groups = AppTypeGrouping.group(rows);

        assertEquals(2, groups.size());
        assertEquals("行业领域", groups.get(0).type());
        assertEquals("用户对象", groups.get(1).type());
        assertEquals(3, groups.get(0).valueCount());
        assertEquals(2, groups.get(1).valueCount());
        assertEquals(List.of("金融", "制造", "能源"), valuesOf(groups.get(0)));
        assertEquals(List.of("个人用户", "企业用户"), valuesOf(groups.get(1)));
    }

    @Test
    @DisplayName("description 为 null 的历史数据输出空串；sortOrder 为 null 时原样保留")
    void nullDescriptionBecomesEmptyString() {
        List<AppTypeGroupVO> groups = AppTypeGrouping.group(List.of(
                row(1, "行业领域", "金融", null, null)));

        AppTypeValueVO value = groups.get(0).values().get(0);
        assertEquals("", value.description());
        assertNull(value.sortOrder());
        // 读取路径不携带 type（类型归属由外层分组承载，D-M04-01/C-05）
        assertNull(value.type());
    }

    @Test
    @DisplayName("valueCount 为派生值，等于组内行数；全局统计由调用方按同一次取数计算")
    void valueCountIsDerived() {
        List<ComponentType> rows = new ArrayList<>();
        rows.add(row(1, "行业领域", "金融", "", 1));
        rows.add(row(2, "行业领域", "制造", "", 2));
        rows.add(row(3, "用户对象", "个人用户", "", 3));

        List<AppTypeGroupVO> groups = AppTypeGrouping.group(rows);

        int total = groups.stream().mapToInt(AppTypeGroupVO::valueCount).sum();
        assertEquals(rows.size(), total);
        assertEquals(2, groups.size());
    }

    private static List<String> valuesOf(AppTypeGroupVO group) {
        return group.values().stream().map(AppTypeValueVO::value).toList();
    }
}
