package com.musemvp.coreagent.api.dto;

import java.util.List;

/**
 * 一个应用类型分组（含其下取值列表）的出参（详细设计 D-M04-01/C-02）。
 *
 * <p>字段顺序固定为 {@code type, valueCount, values}，对应契约样例：
 * {@code {"type":"行业领域","valueCount":5,"values":[…]}}。
 *
 * <p>{@code valueCount} 为<b>派生值</b>，等于 {@code values.size()}，由服务端读时计算，库中无此列
 * （RC-M04-14、TC-26）。类型不是实体，本分组即「类型」在出参中的全部表示（DEF-M04-02、DD-M04-11）。
 */
public record AppTypeGroupVO(String type, int valueCount, List<AppTypeValueVO> values) {
}
