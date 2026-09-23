package com.musemvp.coreagent.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 单条应用类型取值的出参（详细设计 D-M04-01/C-05、D-M04-02/C-01、D-M04-03/C-01、D-M04-04/C-01）。
 *
 * <p>本记录服务两处形态，由 {@code type} 是否携带区分：
 * <ul>
 *   <li><b>读取路径</b>（D-M04-01/P-05）：取值明细嵌在 {@link AppTypeGroupVO} 内，类型归属由外层分组
 *       承载，故 {@code type} 为 {@code null} 并<b>不序列化</b>，输出 {@code {"id","value","description",
 *       "sortOrder"}}，与 D-M04-01/C-01 的契约样例逐字段一致，也不重复占用载荷；</li>
 *   <li><b>写入路径</b>（D-M04-02/C-01、D-M04-03/C-01）：返回独立记录、无外层分组可承载类型，
 *       故携带 {@code type}，输出 {@code {"id","type","value","description","sortOrder"}}。</li>
 * </ul>
 * {@code type} 上的 {@link JsonInclude} 只作用于该字段，{@code sortOrder} 为 {@code null}（历史数据）
 * 时仍按 D-M04-01/C-05「整数或 null」原样输出 {@code null}，不被省略。
 *
 * <p>不返回 {@code created_at}/{@code updated_at}/逻辑删除标志/版本字段（DEF-M04-11、RC-M04-14）。
 * {@code description} 在服务层已归一为空串，序列化结果恒为字符串（RC-M04-03）。
 */
public record AppTypeValueVO(
        Long id,
        @JsonInclude(JsonInclude.Include.NON_NULL) String type,
        String value,
        String description,
        Integer sortOrder) {

    /** 读取路径构造：类型归属由外层分组承载，不带 {@code type}。 */
    public static AppTypeValueVO forRead(Long id, String value, String description, Integer sortOrder) {
        return new AppTypeValueVO(id, null, value, description, sortOrder);
    }
}
