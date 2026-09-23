package com.musemvp.coreagent.support;

import java.util.List;

/**
 * 阶段与项目常量单点声明，见详细设计 D-M02-08（M02）。
 *
 * <p>锁定阶段清单、默认阶段序列、默认项目行名称、名称长度上限与阶段分隔符集中在此声明，
 * 供 {@code ProjectStageService}、{@code ProjectService} 与 {@code AgentCategorySyncService} 引用，
 * 避免同一取值在多处各写一份导致漂移。
 *
 * <p>常量的<b>顺序</b>是契约的一部分：锁定阶段的缺失项列举顺序、默认阶段序列的存储顺序都会外显，
 * 重排即改变可观察结果（D-M02-08/4.4）。
 */
public final class StageCatalog {

    /** 锁定阶段清单，顺序固定（DEF-M02-06）；RC-M02-07 的判定依据。 */
    public static final List<String> LOCKED_STAGES = List.of("需求分析", "技术设计", "代码生成", "知识沉淀");

    /**
     * 默认阶段序列，顺序固定（DEF-M02-07）。
     *
     * <p>同时是读取回落值（RC-M02-05）与新增项目的预设值（RC-M02-19），两处必须引用同一常量。
     */
    public static final List<String> DEFAULT_STAGES = List.of("需求分析", "技术设计", "代码生成", "测试验证", "知识沉淀");

    /** 默认项目行名称（DEF-M02-12），仅用于 RC-M02-20 的自动创建；前端顶栏回落取同一取值。 */
    public static final String DEFAULT_PROJECT_NAME = "AI新一代核心系统";

    /** 项目名称长度上限（DEF-M02-02，对应 {@code project_info.name varchar(128)}）。 */
    public static final int PROJECT_NAME_MAX_LENGTH = 128;

    /** 阶段写入分隔符，恒为英文逗号（RC-M02-04）。 */
    public static final String STAGE_SEPARATOR = ",";

    /** 阶段读取容忍的分隔符集合（技术选择，用于兼容存量非规范数据；写入侧不使用）。 */
    public static final String STAGE_SPLIT_REGEX = "[,;，；\\n]";

    private StageCatalog() {
    }
}
