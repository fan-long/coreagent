package com.musemvp.coreagent.api;

import com.musemvp.coreagent.api.dto.AppTypeDictionaryVO;
import com.musemvp.coreagent.api.dto.AppTypeValueRequest;
import com.musemvp.coreagent.api.dto.AppTypeValueVO;
import com.musemvp.coreagent.service.AppTypeService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用类型字典接口，见详细设计 D-M04-01…D-M04-05（M04）。
 *
 * <p>五个端点：
 * <ul>
 *   <li>{@code GET /api/app-types} —— 读取分组字典（D-M04-01/C-01）；</li>
 *   <li>{@code POST /api/app-types/values} —— 新增取值（D-M04-02/C-01）；</li>
 *   <li>{@code PUT /api/app-types/values/{id}} —— 编辑取值（D-M04-03/C-01）；</li>
 *   <li>{@code DELETE /api/app-types/values/{id}} —— 删除单条（D-M04-04/C-01）；</li>
 *   <li>{@code DELETE /api/app-types?type=X} —— 删除整个类型（D-M04-05/C-01）。</li>
 * </ul>
 *
 * <p><b>平台级接口</b>：不要求 {@code X-Project-Id}，缺失或非法均不影响响应（DD-M04-12、
 * D-M04-01/C-07）。控制器不含业务判定——校验、存在性、唯一性与文案组装全部在
 * {@link AppTypeService}，异常由 {@code GlobalExceptionHandler} 映射为统一 {@code ApiError}。
 *
 * <p>{@code id} 用路径变量、类型名用查询参数，依据 DD-M04-03；{@code id} 非数字由平台既有的参数绑定
 * 失败映射返回 {@code M01-E001}（400），无需本类额外处理。
 */
@RestController
@RequestMapping("/api/app-types")
public class AppTypeController {

    private final AppTypeService service;

    public AppTypeController(AppTypeService service) {
        this.service = service;
    }

    /** 读取分组字典；空字典返回 {@code 200} + 空数组（D-M04-01/C-04）。 */
    @GetMapping
    public AppTypeDictionaryVO read() {
        return service.readDictionary();
    }

    /** 新增取值；成功返回 {@code 201} + 创建后的记录（含 {@code type}，D-M04-02/C-01）。 */
    @PostMapping("/values")
    @ResponseStatus(HttpStatus.CREATED)
    public AppTypeValueVO create(@RequestBody(required = false) AppTypeValueRequest request) {
        return service.create(request);
    }

    /** 编辑取值；成功返回 {@code 200} + 更新后的记录（含 {@code type}，D-M04-03/C-01）。 */
    @PutMapping("/values/{id}")
    public AppTypeValueVO update(
            @PathVariable("id") Long id,
            @RequestBody(required = false) AppTypeValueRequest request) {
        return service.update(id, request);
    }

    /** 删除单条取值；成功返回 {@code 200} + {@code {"id":X}}（D-M04-04/C-01）。 */
    @DeleteMapping("/values/{id}")
    public Map<String, Object> deleteValue(@PathVariable("id") Long id) {
        return service.deleteValue(id);
    }

    /**
     * 删除整个类型；成功返回 {@code 200} + {@code {"type":X,"deletedCount":N}}（D-M04-05/C-01）。
     *
     * <p>{@code type} 为查询参数且非必填——未携带与传空/仅空白等价，均由服务层判为「未指定」
     * 并返回 {@code M04-E009}（D-M04-05/C-02）。
     */
    @DeleteMapping
    public Map<String, Object> deleteType(@RequestParam(name = "type", required = false) String type) {
        return service.deleteType(type);
    }
}
