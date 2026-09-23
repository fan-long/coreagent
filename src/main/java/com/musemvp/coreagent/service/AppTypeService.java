package com.musemvp.coreagent.service;

import com.musemvp.coreagent.api.dto.AppTypeDictionaryVO;
import com.musemvp.coreagent.api.dto.AppTypeGroupVO;
import com.musemvp.coreagent.api.dto.AppTypeValueRequest;
import com.musemvp.coreagent.api.dto.AppTypeValueVO;
import com.musemvp.coreagent.domain.ComponentType;
import com.musemvp.coreagent.repository.ComponentTypeRepository;
import com.musemvp.coreagent.support.AppTypeCatalog;
import com.musemvp.coreagent.support.BizException;
import com.musemvp.coreagent.support.ErrorCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 应用类型字典的业务服务，覆盖详细设计 D-M04-01…D-M04-05 的处理逻辑。
 *
 * <p>事务边界（D-M04-11/C-13）：读取方法 {@code @Transactional(readOnly = true)}；四个写入方法
 * {@code @Transactional}——校验、存在性与唯一性判定、写入必须在同一事务内，保证「拒绝时不留半条记录」
 * （D-M04-02/P-09、D-M04-03/P-07）。
 *
 * <p>错误码与文案的映射在本类完成（D-M04-10/C-14）：固定文案取自 {@link ErrorCode}，含动态片段的
 * {@code M04-E006}/{@code M04-E007}/{@code M04-E008} 在抛出点用 {@link BizException} 双参构造组装。
 *
 * <p><b>平台级字典</b>：不接收也不校验 {@code X-Project-Id}，方法签名不含项目上下文（DD-M04-12）。
 */
@Service
public class AppTypeService {

    private static final Logger log = LoggerFactory.getLogger(AppTypeService.class);

    private final ComponentTypeRepository repository;

    public AppTypeService(ComponentTypeRepository repository) {
        this.repository = repository;
    }

    /**
     * 读取分组字典（D-M04-01、D-M04-07）。
     *
     * <p>固定 1 次 SELECT（{@code findAllOrdered}），分组与统计在内存完成，不产生 N+1（DD-M04-02）。
     * 三个统计值取自同一次取数结果（D-M04-01/C-02）。空字典是正常业务状态，返回
     * {@code {"types":[],"typeCount":0,"valueCount":0}} 而非 404/204（D-M04-01/C-04、RC-M04-13）。
     */
    @Transactional(readOnly = true)
    public AppTypeDictionaryVO readDictionary() {
        List<ComponentType> rows = repository.findAllOrdered();
        List<AppTypeGroupVO> groups = AppTypeGrouping.group(rows);
        log.debug("读取应用类型字典：取值行数={}，类型数={}", rows.size(), groups.size());
        return new AppTypeDictionaryVO(groups, groups.size(), rows.size());
    }

    /**
     * 新增取值（D-M04-02）。
     *
     * <p>处理顺序 P-02 → P-03 → P-04 → P-05 → P-06 → P-07 不可交换：三项字段校验与唯一性判定全部先于
     * 写入。类型不做存在性校验——类型由取值记录自然形成（RC-M04-05、DD-M04-11）。
     *
     * @param request 请求体；缺失（{@code null}）时按三字段全 {@code null} 处理（D-M04-02/P-01）
     */
    @Transactional
    public AppTypeValueVO create(AppTypeValueRequest request) {
        String type = validatedType(request);
        String value = validatedValue(request);
        String description = validatedDescription(request);

        if (repository.existsByTypeAndValue(type, value)) {
            throw duplicateValue(type, value);
        }

        int sortOrder = nextSortOrder();
        try {
            ComponentType saved = repository.saveAndFlush(new ComponentType(type, value, description, sortOrder));
            log.debug("新增应用类型取值：id={}，类型={}", saved.getId(), saved.getType());
            return toWrittenVO(saved);
        } catch (DataIntegrityViolationException ex) {
            // 并发写入下应用层判重可能同时通过，由库级唯一键 (type, value) 兜底并复用同一文案（DD-M04-06）
            throw duplicateValue(type, value);
        }
    }

    /**
     * 编辑取值（D-M04-03）。
     *
     * <p>字段校验（P-02）先于记录存在性（P-03）：两者均为拒绝路径，且字段校验不访问数据库；
     * 记录不存在（P-03）优先于唯一性判定与更新（P-04、P-05）——故「记录不存在 + 字段为空」返回
     * {@code M04-E007} 而非 {@code M04-E001}，且绝不静默转为新增（RC-M04-24）。
     *
     * <p>编辑是<b>整行迁移</b>（DD-M04-08）：只设置 {@code type}/{@code value}/{@code description}，
     * 不触碰 {@code sort_order}（D-M04-03/C-06）；类型改名即记录迁入目标类型，目标类型不存在时自然形成。
     */
    @Transactional
    public AppTypeValueVO update(Long id, AppTypeValueRequest request) {
        String type = validatedType(request);
        String value = validatedValue(request);
        String description = validatedDescription(request);

        ComponentType entity = repository.findById(id).orElseThrow(() -> valueNotFound(id));

        if (repository.existsByTypeAndValueAndIdNot(type, value, id)) {
            throw duplicateValue(type, value);
        }

        entity.setType(type);
        entity.setValue(value);
        entity.setDescription(description);
        try {
            ComponentType saved = repository.saveAndFlush(entity);
            log.debug("更新应用类型取值：id={}，类型={}", saved.getId(), saved.getType());
            return toWrittenVO(saved);
        } catch (DataIntegrityViolationException ex) {
            throw duplicateValue(type, value);
        }
    }

    /**
     * 删除单条取值（D-M04-04）。
     *
     * <p>先 {@code findById} 后删除——Spring Data 的 {@code deleteById} 在目标不存在时静默返回，
     * 仅凭它无法满足 RC-M04-09 的拒绝要求（D-M04-11/C-09）；此处以显式存在性判定保证
     * {@code M04-E007} 的先决地位。
     *
     * <p>不校验、不清理 {@code specgroup_comptype_rel}，也<b>不产生</b>「被关联不可删除」类拒绝分支
     * （D-M04-04/C-04，结构性保证）。
     */
    @Transactional
    public Map<String, Object> deleteValue(Long id) {
        ComponentType entity = repository.findById(id).orElseThrow(() -> valueNotFound(id));
        repository.delete(entity);
        log.debug("删除应用类型取值：id={}", id);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", entity.getId());
        return body;
    }

    /**
     * 删除整个类型（D-M04-05）。
     *
     * <p>优先关系：未指定（{@code M04-E009}）优先于类型不存在（{@code M04-E008}），类型不存在优先于删除。
     * 类型名归一后为空（{@code null}/空串/仅空白）即「未指定」（RC-M04-11）。
     *
     * <p>{@code deletedCount} 取 P-03 批量删除的<b>实际受影响行数</b>，与 P-02 的计数在无并发时必然相等；
     * 取实际行数可保证提示文案中的 N 与删除前该类型取值条数一致（D-M04-05/P-03 的取舍，AC-M04-13/38）。
     */
    @Transactional
    public Map<String, Object> deleteType(String rawType) {
        String type = AppTypeRules.normalize(rawType);
        if (AppTypeRules.isUnfilled(type)) {
            throw new BizException(ErrorCode.M04_E009);
        }
        long count = repository.countByType(type);
        if (count == 0) {
            throw new BizException(ErrorCode.M04_E008, "类型不存在：" + type);
        }
        int deleted = repository.deleteByType(type);
        log.debug("删除应用类型：类型={}，删除前计数={}，实际删除条数={}", type, count, deleted);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", type);
        body.put("deletedCount", deleted);
        return body;
    }

    // ------------------------------------------------------------------ 字段校验

    /** P-02：类型字段——先空后长，空为 {@code M04-E001}、超限为 {@code M04-E003}（D-M04-02/C-02）。 */
    private String validatedType(AppTypeValueRequest request) {
        String type = AppTypeRules.normalize(AppTypeRules.asText(textOf(request).type()));
        if (AppTypeRules.isUnfilled(type)) {
            throw new BizException(ErrorCode.M04_E001);
        }
        if (AppTypeRules.isTooLong(type, AppTypeCatalog.TYPE_MAX_LENGTH)) {
            throw new BizException(ErrorCode.M04_E003);
        }
        return type;
    }

    /** P-03：类型值字段——空为 {@code M04-E002}、超限为 {@code M04-E004}（D-M04-02/C-03）。 */
    private String validatedValue(AppTypeValueRequest request) {
        String value = AppTypeRules.normalize(AppTypeRules.asText(textOf(request).value()));
        if (AppTypeRules.isUnfilled(value)) {
            throw new BizException(ErrorCode.M04_E002);
        }
        if (AppTypeRules.isTooLong(value, AppTypeCatalog.VALUE_MAX_LENGTH)) {
            throw new BizException(ErrorCode.M04_E004);
        }
        return value;
    }

    /** P-04：值说明字段——空归为空串、超限为 {@code M04-E005}，<b>不做</b>必填拒绝（D-M04-02/C-04）。 */
    private String validatedDescription(AppTypeValueRequest request) {
        String description = AppTypeRules.normalizeDescription(AppTypeRules.asText(textOf(request).description()));
        if (AppTypeRules.isTooLong(description, AppTypeCatalog.DESCRIPTION_MAX_LENGTH)) {
            throw new BizException(ErrorCode.M04_E005);
        }
        return description;
    }

    /** 请求体缺失（{@code null}）时按三字段全 {@code null} 处理（D-M04-02/P-01）。 */
    private AppTypeValueRequest textOf(AppTypeValueRequest request) {
        return request == null ? new AppTypeValueRequest(null, null, null) : request;
    }

    // ------------------------------------------------------------------ 派生口径

    /**
     * P-06：排序号赋值（D-M04-07/C-07）——{@code max == null} 取 1，否则 {@code max + 1}；
     * 已达 {@link Integer#MAX_VALUE} 时不再加一（避免溢出为负数）并输出 {@code warn}。
     */
    private int nextSortOrder() {
        Integer max = repository.findMaxSortOrder();
        if (max == null) {
            return 1;
        }
        if (max == Integer.MAX_VALUE) {
            log.warn("应用类型排序号已达上限，新增记录沿用最大值：max={}", max);
            return Integer.MAX_VALUE;
        }
        return max + 1;
    }

    // ------------------------------------------------------------------ 出参与文案

    /** 写入路径出参：无外层分组承载类型，故携带 {@code type}（D-M04-02/C-01、D-M04-03/C-01）。 */
    private AppTypeValueVO toWrittenVO(ComponentType entity) {
        String description = entity.getDescription() == null ? "" : entity.getDescription();
        return new AppTypeValueVO(
                entity.getId(), entity.getType(), entity.getValue(), description, entity.getSortOrder());
    }

    /** {@code M04-E006} 动态文案：X 为裁剪后类型名、Y 为裁剪后类型值（D-M04-10/C-06、C-14）。 */
    private BizException duplicateValue(String type, String value) {
        return new BizException(ErrorCode.M04_E006, "类型「" + type + "」下已存在类型值「" + value + "」");
    }

    /** {@code M04-E007} 动态文案：X 为请求携带的记录 ID（D-M04-10/C-07）。 */
    private BizException valueNotFound(Long id) {
        return new BizException(ErrorCode.M04_E007, "应用类型记录不存在：" + id);
    }
}
