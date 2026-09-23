package com.musemvp.coreagent.support;

import org.springframework.http.HttpStatus;

/**
 * 错误码表，见详细设计 §2.7（M01）、D-M02-09（M02）与 D-M04-10（M04）。
 *
 * <p>编码与 HTTP 状态的对应关系是接口契约的一部分，前端按 {@code code} 做差异化提示（如 M01-E001／E002
 * 在项目弹窗内联提示），不得随意调整。M02 与 M04 均只新增码位，既有码位与 HTTP 状态不变
 * （D-M02-09/4.2、D-M04-10/4.6）。
 *
 * <p>M04 新增 {@code M04-E001}…{@code M04-E009} 九个码位，见 D-M04-10/C-01…C-09；其中
 * {@code M04-E006}/{@code M04-E007}/{@code M04-E008} 带动态片段，按 D-M04-10/C-14 由抛出点以
 * {@link BizException#BizException(ErrorCode, String)} 组装，枚举内消息仅作兜底。
 */
public enum ErrorCode {

    /** 新增／重命名项目校验失败，前端在弹窗内联提示，不关闭弹窗。 */
    M01_E001("M01-E001", HttpStatus.BAD_REQUEST, "项目名称不能为空或超过 128 个字符"),

    /** 项目唯一性校验失败。 */
    M01_E002("M01-E002", HttpStatus.CONFLICT, "项目名称已存在"),

    /**
     * 项目 id 无效或已删除，前端刷新项目列表并回退默认项目。
     *
     * <p>M02 按 DEF-M02-17 把消息改为携带请求携带的项目 ID（{@code 项目不存在：{id}}），
     * 码位与 404 状态不变（D-M02-09/C-04）。
     */
    M01_E003("M01-E003", HttpStatus.NOT_FOUND, "项目不存在"),

    /** componentId 无效，前端提示后返回工作台。 */
    M01_E004("M01-E004", HttpStatus.NOT_FOUND, "应用组件不存在"),

    /** repositoryId 与组件不匹配，前端回退该组件默认工程。 */
    M01_E005("M01-E005", HttpStatus.BAD_REQUEST, "工程仓库不存在或不属于当前组件"),

    /** 依赖数据查询异常，前端保留页面骨架并提示失败（不白屏）。 */
    M01_E006("M01-E006", HttpStatus.SERVICE_UNAVAILABLE, "数据加载失败，请稍后重试"),

    /**
     * 主题令牌包缺失、解析失败、主题标识非法或回退令牌不完备（ft-FT-002 §3.7）。
     *
     * <p>前端保留 {@code css/tokens.css} 静态基线渲染并 toast 提示，页面结构与功能不受影响（不白屏）。
     */
    M01_E007("M01-E007", HttpStatus.SERVICE_UNAVAILABLE, "主题令牌加载失败，已使用回退主题渲染"),

    /** M02：项目名称裁剪后为空（RC-M02-01，AC-M02-01）；消息取自 DEF-M02-14，为定值。 */
    M02_E001("M02-E001", HttpStatus.BAD_REQUEST, "项目名称不能为空"),

    /** M02：项目名称裁剪后长度超过 128（RC-M02-02，AC-M02-02/03）；消息为定值。 */
    M02_E002("M02-E002", HttpStatus.BAD_REQUEST, "项目名称不能超过128个字符"),

    /**
     * M02：保存后的阶段集合缺少锁定阶段（RC-M02-07，AC-M02-08）。
     *
     * <p>实际消息由 {@code ProjectStageService#validateLocked} 按缺失项动态组装（列出全部缺失的锁定阶段），
     * 此处仅作为无参构造时的兜底文案。
     */
    M02_E003("M02-E003", HttpStatus.BAD_REQUEST, "工程阶段为锁定阶段，不可删除或改名"),

    /** M04：类型名称裁剪后为空（RC-M04-01，AC-M04-01/02/03）；消息取自 DEF-M04-13，为定值。 */
    M04_E001("M04-E001", HttpStatus.BAD_REQUEST, "请填写类型"),

    /** M04：类型值裁剪后为空（RC-M04-02，AC-M04-04 的为空侧）；消息取自 DEF-M04-13，为定值。 */
    M04_E002("M04-E002", HttpStatus.BAD_REQUEST, "请填写类型值"),

    /**
     * M04：类型名称裁剪后长度超过 64（RC-M04-01，AC-M04-04）。
     *
     * <p>需求仅固定了「为空」类文案，「超限」文案由本设计补充（DD-M04-05、DQ-M04-09）。
     */
    M04_E003("M04-E003", HttpStatus.BAD_REQUEST, "类型不能超过64个字符"),

    /** M04：类型值裁剪后长度超过 64（RC-M04-02，AC-M04-04）；超限文案由本设计补充（DD-M04-05）。 */
    M04_E004("M04-E004", HttpStatus.BAD_REQUEST, "类型值不能超过64个字符"),

    /** M04：值说明裁剪后长度超过 255（RC-M04-03，AC-M04-05）；超限文案由本设计补充（DD-M04-05）。 */
    M04_E005("M04-E005", HttpStatus.BAD_REQUEST, "值说明不能超过255个字符"),

    /**
     * M04：同类型下类型值重复（RC-M04-04 分支a/分支b，AC-M04-06…AC-M04-09）。
     *
     * <p>实际消息由 {@code AppTypeService} 按 {@code 类型「X」下已存在类型值「Y」} 动态组装（X/Y 取裁剪后的
     * 业务值原文，DEF-M04-14）；库级唯一键冲突复用同一文案（DD-M04-06）。此处仅作无参构造的兜底文案。
     */
    M04_E006("M04-E006", HttpStatus.CONFLICT, "类型下已存在该类型值"),

    /**
     * M04：按记录 ID 操作时目标记录不存在（RC-M04-09、RC-M04-24，AC-M04-14/29）。
     *
     * <p>实际消息由抛出点组装为 {@code 应用类型记录不存在：{id}}，X 为请求携带的记录 ID（DQ-M04-10）。
     */
    M04_E007("M04-E007", HttpStatus.NOT_FOUND, "应用类型记录不存在"),

    /**
     * M04：按类型名删除时该类型在字典中不存在（RC-M04-10，AC-M04-15）。
     *
     * <p>实际消息由抛出点组装为 {@code 类型不存在：{type}}，X 为归一后的请求携带类型名。
     */
    M04_E008("M04-E008", HttpStatus.NOT_FOUND, "类型不存在"),

    /** M04：删除整类时未指定类型（含空串与仅空白，RC-M04-11，AC-M04-16）；消息取自 DEF-M04-13，为定值。 */
    M04_E009("M04-E009", HttpStatus.BAD_REQUEST, "请指定类型");

    private final String code;
    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(String code, HttpStatus httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getMessage() {
        return message;
    }
}
