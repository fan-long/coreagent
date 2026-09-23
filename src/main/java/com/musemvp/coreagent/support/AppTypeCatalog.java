package com.musemvp.coreagent.support;

import java.util.List;

/**
 * 应用类型字典的常量与预置维度清单，见详细设计 D-M04-14（M04）。
 *
 * <p>本类只承载<b>定值</b>：长度上限（D-M04-14/C-01）与预置维度名称（D-M04-14/C-02）。
 * 不含错误文案（由 {@link ErrorCode} 承载，D-M04-10）、不含页面文案（前端常量承载）、
 * 不含状态枚举（本模块无状态字段，D-M04-14/C-06）。
 *
 * <p><b>不生成预置数据</b>：预置维度（现状 9 维度/37 取值）为平台既有数据，本构件只声明已知的
 * 8 个维度名称供顺序核对与自检使用，不写库、不初始化、不锁定（DD-M04-13、DQ-M04-07）。
 * 第 9 个维度名称、37 条取值的完整清单与各维度的预设分类顺序未在需求中给出，本设计不编造。
 */
public final class AppTypeCatalog {

    /** {@code component_type.type} 列宽 {@code varchar(64)}，见 DEF-M04-11（D-M04-14/C-01）。 */
    public static final int TYPE_MAX_LENGTH = 64;

    /** {@code component_type.value} 列宽 {@code varchar(64)}，见 DEF-M04-11。 */
    public static final int VALUE_MAX_LENGTH = 64;

    /** {@code component_type.description} 列宽 {@code varchar(255)}，见 DEF-M04-11。 */
    public static final int DESCRIPTION_MAX_LENGTH = 255;

    /**
     * 已知的预置维度名称（8 项），见 DEF-M04-09 与 D-M04-14/C-02。
     *
     * <p>仅用于预置顺序核对（D-M04-07/P-05）与展示辅助；<b>不参与运行时排序</b>——展示顺序一律由各
     * 取值记录的 {@code sort_order} 承载（RC-M04-06、D-M04-07/C-04）。
     */
    public static final List<String> PRESET_TYPE_NAMES = List.of(
            "行业领域",
            "用户对象",
            "业务处理性质",
            "数据处理模式",
            "技术架构风格",
            "实时性要求",
            "质量属性侧重点",
            "系统生命周期");

    private AppTypeCatalog() {
    }
}
