package com.musemvp.coreagent.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.musemvp.coreagent.api.dto.ProjectDetailVO;
import com.musemvp.coreagent.api.dto.ProjectVO;
import com.musemvp.coreagent.domain.ProjectInfo;
import com.musemvp.coreagent.repository.ProjectInfoRepository;
import com.musemvp.coreagent.support.BizException;
import com.musemvp.coreagent.support.ErrorCode;
import com.musemvp.coreagent.support.StageCatalog;

/**
 * 项目信息的读取与保存编排，见详细设计 D-M02-01、D-M02-02（M02）。
 *
 * <p>职责分工：本构件负责读取定位、名称与目标范围校验、缺省定位、默认项目行创建与事务边界；
 * 阶段规则本身由 {@link ProjectStageService} 承担，改名判定与迁移由 {@link AgentCategorySyncService}
 * 承担，本构件只做编排。
 *
 * <p>保存路径的字段语义为<b>部分更新</b>（DD-M02-07）：请求体中未出现的键视为不修改，出现的键按值形态
 * 决定「写值」或「清空」，因此请求体以 {@code Map} 承接而非定长 DTO。
 */
@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    /** {@code project_info.name} 为 {@code varchar(128)}，见 DEF-M02-02。 */
    private static final int NAME_MAX_LENGTH = StageCatalog.PROJECT_NAME_MAX_LENGTH;

    /** {@code updated_at} 的对下格式，口径为稳定的本地时间文本。 */
    private static final DateTimeFormatter UPDATED_AT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ProjectInfoRepository projectInfoRepository;
    private final ProjectStageService projectStageService;
    private final AgentCategorySyncService agentCategorySyncService;

    public ProjectService(ProjectInfoRepository projectInfoRepository,
                          ProjectStageService projectStageService,
                          AgentCategorySyncService agentCategorySyncService) {
        this.projectInfoRepository = projectInfoRepository;
        this.projectStageService = projectStageService;
        this.agentCategorySyncService = agentCategorySyncService;
    }

    /* ------------------------------------------------------------------ 读取 */

    /** 项目列表，排序 {@code id ASC}（RC-M02-12）；{@code current} 由请求头解析出的当前项目判定。 */
    @Transactional(readOnly = true)
    public List<ProjectVO> list(Long currentId) {
        return projectInfoRepository.findAllOrdered().stream()
                .map(project -> new ProjectVO(project.getId(), project.getName(),
                        project.getId().equals(currentId)))
                .toList();
    }

    /**
     * 按项目 ID 读取明细（D-M02-01/C-02）。
     *
     * @throws BizException {@code M01-E003}（404），消息携带请求携带的项目 ID（RC-M02-21）
     */
    @Transactional(readOnly = true)
    public ProjectDetailVO detail(Long id) {
        ProjectInfo project = projectInfoRepository.findById(id)
                .orElseThrow(() -> notFound(id));
        return toDetail(project);
    }

    /**
     * 按当前上下文读取明细（D-M02-01/C-03，RC-M02-20）。
     *
     * <p>平台无任何项目时在<b>同一事务内</b>创建默认项目行后返回；该创建只发生在本读取路径，
     * 项目列表与全局上下文接口均不触发（DD-M02-03）。
     */
    @Transactional
    public ProjectDetailVO current(Long headerProjectId) {
        ProjectInfo project = resolveCurrent(headerProjectId);
        if (project == null) {
            project = createDefaultProject();
        }
        return toDetail(project);
    }

    /* ------------------------------------------------------------------ 保存 */

    /**
     * 新增项目（D-M02-02/C-01、C-08，RC-M02-18、RC-M02-19）。
     *
     * <p>只写入 {@code project_info} 一行；不自动创建组件、工作流与 Agent。名称唯一性沿用既有校验
     * （DD-M02-14）。
     */
    @Transactional
    public ProjectDetailVO create(Map<String, Object> body) {
        Map<String, Object> payload = body == null ? Map.of() : body;

        String name = normalizeName(asText(payload.get("name")));
        if (projectInfoRepository.existsByName(name)) {
            throw new BizException(ErrorCode.M01_E002);
        }

        ProjectInfo project = new ProjectInfo();
        project.setName(name);
        project.setGoal(normalizeOptionalText(asText(payload.get("goal"))));
        project.setScope(normalizeOptionalText(asText(payload.get("scope"))));
        project.setStages(resolveStagesForCreate(payload));
        project.setUpdatedAt(LocalDateTime.now());
        ProjectInfo saved = projectInfoRepository.save(project);
        log.debug("新增项目 id={} name={}", saved.getId(), saved.getName());
        return toDetail(saved);
    }

    /**
     * 按项目 ID 更新（D-M02-02/C-02，RC-M02-13 分支a、RC-M02-21）。
     *
     * <p>携带的 ID 不存在时拒绝并提示，<b>不静默回落</b>为更新首个项目。
     */
    @Transactional
    public ProjectDetailVO update(Long id, Map<String, Object> body) {
        ProjectInfo project = projectInfoRepository.findById(id)
                .orElseThrow(() -> notFound(id));
        return applyUpdate(project, body);
    }

    /**
     * 未携带项目 ID 的缺省更新（D-M02-02/C-03，RC-M02-13 分支b）。
     *
     * <p>定位规则与读取一致（D-M02-01/C-07）：请求头命中优先，否则取 {@code id} 最小的一条；
     * 平台无任何项目时不创建记录，返回 {@code M01-E003}（DD-M02-03；文案形态见 DQ-M02-20）。
     */
    @Transactional
    public ProjectDetailVO updateDefault(Long headerProjectId, Map<String, Object> body) {
        ProjectInfo project = resolveCurrent(headerProjectId);
        if (project == null) {
            throw new BizException(ErrorCode.M01_E003, headerProjectId == null
                    ? "项目不存在：当前项目"
                    : "项目不存在：" + headerProjectId);
        }
        return applyUpdate(project, body);
    }

    /**
     * 解析生效的当前项目（§2.4 后端兜底策略，D-M02-01/C-07 的单点实现）。
     *
     * <p>{@code X-Project-Id} 缺失、非法或指向已删除项目时回退 {@code project_info} 中 {@code id} 最小的一条；
     * 无任何项目时返回 {@code null}。该策略保证任何入口直接访问页面都不会白屏。
     */
    @Transactional(readOnly = true)
    public ProjectInfo resolveCurrent(Long headerProjectId) {
        if (headerProjectId != null) {
            Optional<ProjectInfo> resolved = projectInfoRepository.findById(headerProjectId);
            if (resolved.isPresent()) {
                return resolved.get();
            }
            log.debug("X-Project-Id={} 未命中项目，回退默认项目", headerProjectId);
        }
        return projectInfoRepository.findFirstByOrderByIdAsc().orElse(null);
    }

    /* ------------------------------------------------------------------ 内部实现 */

    /**
     * 部分更新的字段出现性编排（DD-M02-07，D-M02-02/P-07…P-10）。
     *
     * <p>顺序不可调换：名称校验 → 阶段规范化 → <b>锁定阶段校验</b> → 改名判定与迁移 → 写入项目行。
     * 锁定校验必须先于改名判定，否则「删除或改名锁定阶段」会触发 Agent 归属迁移（RC-M02-07 优先于 RC-M02-09）。
     */
    private ProjectDetailVO applyUpdate(ProjectInfo project, Map<String, Object> rawBody) {
        Map<String, Object> body = rawBody == null ? Map.of() : rawBody;

        if (body.containsKey("name")) {
            String name = normalizeName(asText(body.get("name")));
            if (projectInfoRepository.existsByNameAndIdNot(name, project.getId())) {
                throw new BizException(ErrorCode.M01_E002);
            }
            project.setName(name);
        }
        if (body.containsKey("goal")) {
            project.setGoal(normalizeOptionalText(asText(body.get("goal"))));
        }
        if (body.containsKey("scope")) {
            project.setScope(normalizeOptionalText(asText(body.get("scope"))));
        }

        List<String> targetStages = null;
        if (body.containsKey("stages") && body.get("stages") != null) {
            targetStages = projectStageService.normalize(readStageList(body.get("stages")));
            projectStageService.validateLocked(targetStages);
        }
        if (targetStages != null) {
            // 基准取库中存储值（DD-M02-04）；回落值仅用于展示，不作比对基准，也不回写存储
            List<String> storedStages = projectStageService.parse(project.getStages());
            agentCategorySyncService.sync(storedStages, targetStages);
            project.setStages(projectStageService.serialize(targetStages));
        }

        project.setUpdatedAt(LocalDateTime.now());
        ProjectInfo saved = projectInfoRepository.save(project);
        log.debug("更新项目 id={} name={} stagesPresent={}", saved.getId(), saved.getName(),
                targetStages != null);
        return toDetail(saved);
    }

    /**
     * 新增项目的阶段取值（D-M02-02/C-08，RC-M02-19）。
     *
     * <p>{@code stages} 未出现或为 {@code null} 时取默认五阶段；出现时按保存路径规范化并校验锁定阶段。
     * 与读取回落（RC-M02-05）分别适用，不得互相替代。
     */
    private String resolveStagesForCreate(Map<String, Object> payload) {
        Object rawStages = payload.get("stages");
        if (!payload.containsKey("stages") || rawStages == null) {
            return projectStageService.serialize(StageCatalog.DEFAULT_STAGES);
        }
        List<String> stages = projectStageService.normalize(readStageList(rawStages));
        projectStageService.validateLocked(stages);
        return projectStageService.serialize(stages);
    }

    /** 平台无任何项目时创建默认项目行（D-M02-01/C-08，RC-M02-20）。 */
    private ProjectInfo createDefaultProject() {
        ProjectInfo project = new ProjectInfo();
        project.setName(StageCatalog.DEFAULT_PROJECT_NAME);
        // 需求未给出默认目标与范围的文本，本设计不编造，按空值处理（DQ-M02-05）
        project.setGoal(null);
        project.setScope(null);
        project.setStages(projectStageService.serialize(StageCatalog.DEFAULT_STAGES));
        project.setUpdatedAt(LocalDateTime.now());
        ProjectInfo saved = projectInfoRepository.save(project);
        log.info("平台无任何项目，自动创建默认项目行 id={} name={}", saved.getId(), saved.getName());
        return saved;
    }

    /** 装配明细响应：字段映射 + 锁定阶段常量 + 阶段回落（D-M02-01/C-04…C-06）。 */
    private ProjectDetailVO toDetail(ProjectInfo project) {
        List<String> stages = projectStageService.fallback(projectStageService.parse(project.getStages()));
        return new ProjectDetailVO(
                project.getId(),
                project.getName(),
                project.getGoal(),
                project.getScope(),
                stages,
                StageCatalog.LOCKED_STAGES,
                project.getUpdatedAt() == null ? null : UPDATED_AT_FORMATTER.format(project.getUpdatedAt()));
    }

    /**
     * 名称规范化与校验：先裁剪、后判长（AC-M02-03）。
     *
     * <p>长度按「裁剪后 + {@code String.length()}`（UTF-16 码元）」实现，计数口径待 Q-M02-14 裁决（DQ-M02-14）。
     */
    private String normalizeName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty()) {
            throw new BizException(ErrorCode.M02_E001);
        }
        if (normalized.length() > NAME_MAX_LENGTH) {
            throw new BizException(ErrorCode.M02_E002);
        }
        return normalized;
    }

    /**
     * 目标 / 范围的规范化（D-M02-02/C-06，RC-M02-03 分支b、c）。
     *
     * <p>裁剪首尾空白；裁剪后为空串按空值（{@code null}）存储，「清空」由此可达；不触发名称类校验。
     */
    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 把请求体中的阶段值读为列表：接受数组形态（DD-M02-08），亦容忍分隔文本。 */
    private List<String> readStageList(Object rawStages) {
        if (rawStages instanceof List<?> list) {
            List<String> stages = new ArrayList<>(list.size());
            list.forEach(item -> stages.add(item == null ? null : String.valueOf(item)));
            return stages;
        }
        return projectStageService.parse(String.valueOf(rawStages));
    }

    /** 请求体字段取值：非字符串标量按文本处理，避免数字等类型直接抛绑定异常。 */
    private String asText(Object value) {
        return value == null ? null : (value instanceof String text ? text : String.valueOf(value));
    }

    private BizException notFound(Long id) {
        return new BizException(ErrorCode.M01_E003, "项目不存在：" + id);
    }
}
