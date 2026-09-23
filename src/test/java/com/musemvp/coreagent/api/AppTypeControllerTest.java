package com.musemvp.coreagent.api;

import com.musemvp.coreagent.api.dto.AppTypeDictionaryVO;
import com.musemvp.coreagent.api.dto.AppTypeGroupVO;
import com.musemvp.coreagent.api.dto.AppTypeValueRequest;
import com.musemvp.coreagent.api.dto.AppTypeValueVO;
import com.musemvp.coreagent.service.AppTypeService;
import com.musemvp.coreagent.support.BizException;
import com.musemvp.coreagent.support.ErrorCode;
import com.musemvp.coreagent.support.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 接口契约层测试，覆盖 V-M04-10、V-M04-14、V-M04-15、V-M04-25…V-M04-28 的 HTTP 状态与响应体形态。
 *
 * <p>以 MockMvc 独立搭建：挂载真实控制器与平台既有 {@link GlobalExceptionHandler}，服务层以替身注入，
 * 从而在不依赖数据库的前提下核对「路径、方法、状态码、字段顺序与命名」这四类契约要素。
 */
class AppTypeControllerTest {

    private final AppTypeService service = mock(AppTypeService.class);

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(new AppTypeController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("V-M04-10：GET /api/app-types 返回 200 + types/typeCount/valueCount，取值项不含 type")
    void readDictionary() throws Exception {
        when(service.readDictionary()).thenReturn(new AppTypeDictionaryVO(
                List.of(new AppTypeGroupVO("行业领域", 1, List.of(
                        AppTypeValueVO.forRead(12L, "金融", "面向金融行业", 1)))),
                1,
                1));

        MvcResult result = mockMvc().perform(get("/api/app-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.typeCount").value(1))
                .andExpect(jsonPath("$.valueCount").value(1))
                .andExpect(jsonPath("$.types[0].type").value("行业领域"))
                .andExpect(jsonPath("$.types[0].valueCount").value(1))
                .andExpect(jsonPath("$.types[0].values[0].id").value(12))
                .andExpect(jsonPath("$.types[0].values[0].value").value("金融"))
                .andExpect(jsonPath("$.types[0].values[0].description").value("面向金融行业"))
                .andExpect(jsonPath("$.types[0].values[0].sortOrder").value(1))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        // 读取路径的取值项不重复携带 type（D-M04-01/C-05）
        assertFalse(body.contains("\"type\":null"), body);
        assertEquals(1, countOf(body, "\"type\""), body);
    }

    @Test
    @DisplayName("空字典返回 200 + 空数组，而非 404/204")
    void readEmptyDictionary() throws Exception {
        when(service.readDictionary()).thenReturn(new AppTypeDictionaryVO(List.of(), 0, 0));

        MvcResult result = mockMvc().perform(get("/api/app-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.types").isArray())
                .andExpect(jsonPath("$.typeCount").value(0))
                .andExpect(jsonPath("$.valueCount").value(0))
                .andReturn();

        assertEquals("{\"types\":[],\"typeCount\":0,\"valueCount\":0}", result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("V-M04-25：POST /api/app-types/values 返回 201 + 创建记录（写入路径携带 type）")
    void createValue() throws Exception {
        when(service.create(any(AppTypeValueRequest.class)))
                .thenReturn(new AppTypeValueVO(38L, "行业领域", "金融", "面向金融行业", 38));

        MvcResult result = mockMvc().perform(post("/api/app-types/values")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"行业领域\",\"value\":\"金融\",\"description\":\"面向金融行业\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(38))
                .andExpect(jsonPath("$.type").value("行业领域"))
                .andExpect(jsonPath("$.sortOrder").value(38))
                .andReturn();

        assertEquals(
                "{\"id\":38,\"type\":\"行业领域\",\"value\":\"金融\",\"description\":\"面向金融行业\",\"sortOrder\":38}",
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("V-M04-01：字段校验失败返回 400 + M04-E001 与兜底文案")
    void createRejectsEmptyType() throws Exception {
        when(service.create(any(AppTypeValueRequest.class))).thenThrow(new BizException(ErrorCode.M04_E001));

        mockMvc().perform(post("/api/app-types/values")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"\",\"value\":\"金融\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("M04-E001"))
                .andExpect(jsonPath("$.message").value("请填写类型"));
    }

    @Test
    @DisplayName("V-M04-26：重复返回 409 + M04-E006 与动态文案")
    void createRejectsDuplicate() throws Exception {
        when(service.create(any(AppTypeValueRequest.class)))
                .thenThrow(new BizException(ErrorCode.M04_E006, "类型「行业领域」下已存在类型值「金融」"));

        mockMvc().perform(post("/api/app-types/values")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"行业领域\",\"value\":\"金融\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("M04-E006"))
                .andExpect(jsonPath("$.message").value("类型「行业领域」下已存在类型值「金融」"));
    }

    @Test
    @DisplayName("请求体不可解析返回平台既有 M01-E001（400）")
    void createRejectsUnparsableBody() throws Exception {
        mockMvc().perform(post("/api/app-types/values")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("M01-E001"))
                .andExpect(jsonPath("$.message").value("请求参数不合法"));
    }

    @Test
    @DisplayName("V-M04-27/28：PUT /api/app-types/values/{id} 返回 200；不存在返回 404 + M04-E007")
    void updateValue() throws Exception {
        when(service.update(anyLong(), any(AppTypeValueRequest.class)))
                .thenReturn(new AppTypeValueVO(12L, "用户对象", "金融", "面向金融行业", 1));

        mockMvc().perform(put("/api/app-types/values/12")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"用户对象\",\"value\":\"金融\",\"description\":\"面向金融行业\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.type").value("用户对象"));

        when(service.update(anyLong(), any(AppTypeValueRequest.class)))
                .thenThrow(new BizException(ErrorCode.M04_E007, "应用类型记录不存在：99"));

        mockMvc().perform(put("/api/app-types/values/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"行业领域\",\"value\":\"金融\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("M04-E007"))
                .andExpect(jsonPath("$.message").value("应用类型记录不存在：99"));
    }

    @Test
    @DisplayName("V-M04-14：DELETE /api/app-types/values/{id} 返回 200 + {\"id\":X}")
    void deleteValue() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", 12L);
        when(service.deleteValue(12L)).thenReturn(body);

        MvcResult result = mockMvc().perform(delete("/api/app-types/values/12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(12))
                .andReturn();

        assertEquals("{\"id\":12}", result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("V-M04-16：路径变量非数字按绑定失败返回 M01-E001（400）")
    void deleteValueRejectsNonNumericId() throws Exception {
        mockMvc().perform(delete("/api/app-types/values/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("M01-E001"));
    }

    @Test
    @DisplayName("V-M04-13：DELETE /api/app-types?type=X 返回 200 + type/deletedCount")
    void deleteType() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "行业领域");
        body.put("deletedCount", 5);
        when(service.deleteType("行业领域")).thenReturn(body);

        MvcResult result = mockMvc().perform(delete("/api/app-types").param("type", "行业领域"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("行业领域"))
                .andExpect(jsonPath("$.deletedCount").value(5))
                .andReturn();

        assertEquals("{\"type\":\"行业领域\",\"deletedCount\":5}", result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("V-M04-16：未携带 type 参数时以 null 调用服务层，由服务层判为未指定")
    void deleteTypeWithoutParamPassesNull() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "行业领域");
        body.put("deletedCount", 5);
        when(service.deleteType(isNull())).thenReturn(body);

        mockMvc().perform(delete("/api/app-types")).andExpect(status().isOk());

        verify(service).deleteType(isNull());
    }

    private static int countOf(String text, String needle) {
        int count = 0;
        int cursor = text.indexOf(needle);
        while (cursor >= 0) {
            count += 1;
            cursor = text.indexOf(needle, cursor + needle.length());
        }
        return count;
    }
}
