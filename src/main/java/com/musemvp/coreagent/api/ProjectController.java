package com.musemvp.coreagent.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.musemvp.coreagent.api.dto.ProjectDetailVO;
import com.musemvp.coreagent.api.dto.ProjectVO;
import com.musemvp.coreagent.domain.ProjectInfo;
import com.musemvp.coreagent.service.ProjectService;
import com.musemvp.coreagent.support.ApiHeaders;

/**
 * 项目读取与保存接口，见详细设计 D-M02-01、D-M02-02（M02）。
 *
 * <p>与 M01 的关系：{@code GET /api/projects}、{@code POST /api/projects}、
 * {@code PUT /api/projects/{id}} 为 M01 既有接口，M02 在原路径上扩展（列表排序、请求体字段、
 * 响应体形态），新增 {@code GET /api/projects/{id}}、{@code GET /api/projects/current} 与
 * 无路径段 {@code PUT /api/projects}（RQ-M02-17/18/19，DQ-M02-15）。
 *
 * <p>请求体以 {@code Map} 承接：保存语义为按<b>键出现性</b>的部分更新（DD-M02-07），
 * 定长 DTO 无法区分「未提交该字段」与「提交为 null」。
 *
 * <p>控制器只负责请求头解析与路由，定位、校验、事务均在 {@link ProjectService}。
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    /** 项目列表，排序 {@code id ASC}（RC-M02-12）；{@code current} 为按请求头判定的生效项目标记。 */
    @GetMapping
    public List<ProjectVO> list(
            @RequestHeader(value = ApiHeaders.PROJECT_ID, required = false) String rawProjectId) {
        return projectService.list(currentProjectId(rawProjectId));
    }

    /**
     * 按项目 ID 读取明细。
     *
     * <p>映射优先级：本方法为字面路径，{@code /current} 的字面映射优先于本方法的路径变量，
     * 因此 {@code GET /api/projects/current} 不会进入本方法（D-M02-01/C-09，DD-M02-02）。
     */
    @GetMapping("/{id}")
    public ProjectDetailVO detail(@PathVariable("id") Long id) {
        return projectService.detail(id);
    }

    /**
     * 按当前上下文读取明细；平台无任何项目时创建并返回默认项目行（RC-M02-20）。
     *
     * <p>{@code current} 为保留字面量，不是项目 ID（DD-M02-02）。
     */
    @GetMapping("/current")
    public ProjectDetailVO current(
            @RequestHeader(value = ApiHeaders.PROJECT_ID, required = false) String rawProjectId) {
        return projectService.current(ApiHeaders.parseProjectId(rawProjectId));
    }

    /** 新增项目；成功返回 201 与项目明细。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectDetailVO create(@RequestBody(required = false) Map<String, Object> body) {
        return projectService.create(body);
    }

    /**
     * 未携带项目 ID 的缺省更新。
     *
     * <p>定位规则与读取一致（D-M02-01/C-07）：请求头命中优先，否则取 {@code id} 最小的一条；
     * 无任何项目时不创建记录并返回 {@code M01-E003}（DD-M02-03）。
     */
    @PutMapping
    public ProjectDetailVO updateDefault(
            @RequestHeader(value = ApiHeaders.PROJECT_ID, required = false) String rawProjectId,
            @RequestBody(required = false) Map<String, Object> body) {
        return projectService.updateDefault(ApiHeaders.parseProjectId(rawProjectId), body);
    }

    /** 按项目 ID 更新；携带的 ID 不存在时返回 {@code M01-E003}，不静默回落（RC-M02-13 分支a）。 */
    @PutMapping("/{id}")
    public ProjectDetailVO update(@PathVariable("id") Long id,
                                  @RequestBody(required = false) Map<String, Object> body) {
        return projectService.update(id, body);
    }

    private Long currentProjectId(String rawProjectId) {
        ProjectInfo current = projectService.resolveCurrent(ApiHeaders.parseProjectId(rawProjectId));
        return current == null ? null : current.getId();
    }
}
