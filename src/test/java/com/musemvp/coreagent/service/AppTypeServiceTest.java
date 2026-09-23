package com.musemvp.coreagent.service;

import com.musemvp.coreagent.api.dto.AppTypeDictionaryVO;
import com.musemvp.coreagent.api.dto.AppTypeValueRequest;
import com.musemvp.coreagent.api.dto.AppTypeValueVO;
import com.musemvp.coreagent.domain.ComponentType;
import com.musemvp.coreagent.repository.ComponentTypeRepository;
import com.musemvp.coreagent.support.BizException;
import com.musemvp.coreagent.support.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 服务层校验、判重与文案的单元测试，覆盖 V-M04-01…V-M04-09、V-M04-24…V-M04-29 的处理逻辑分支。
 *
 * <p>仓储以 Mockito 替身注入，断言不依赖数据库；服务端真实读写路径由契约层级验证覆盖。
 */
class AppTypeServiceTest {

    private final ComponentTypeRepository repository = mock(ComponentTypeRepository.class);
    private final AppTypeService service = new AppTypeService(repository);

    private static BizException bizExceptionOf(org.junit.jupiter.api.function.Executable executable) {
        return assertThrows(BizException.class, executable);
    }

    private ComponentType persisted(long id, String type, String value, String description, Integer sortOrder) {
        ComponentType entity = new ComponentType(type, value, description, sortOrder);
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    /* ------------------------------------------------------------------ 读取 */

    @Test
    @DisplayName("V-M04-10：读取装配 typeCount/valueCount 与分组，单次取数")
    void readDictionaryAssemblesCounts() {
        when(repository.findAllOrdered()).thenReturn(List.of(
                persisted(1, "行业领域", "金融", "面向金融行业", 1),
                persisted(2, "用户对象", "个人用户", "", 2)));

        AppTypeDictionaryVO dictionary = service.readDictionary();

        assertEquals(2, dictionary.typeCount());
        assertEquals(2, dictionary.valueCount());
        assertEquals("行业领域", dictionary.types().get(0).type());
        assertEquals("金融", dictionary.types().get(0).values().get(0).value());
    }

    @Test
    @DisplayName("V-M04-10：空字典返回 200 语义的空结构，而非错误")
    void readEmptyDictionary() {
        when(repository.findAllOrdered()).thenReturn(List.of());

        AppTypeDictionaryVO dictionary = service.readDictionary();

        assertTrue(dictionary.types().isEmpty());
        assertEquals(0, dictionary.typeCount());
        assertEquals(0, dictionary.valueCount());
    }

    /* ------------------------------------------------------------------ 新增 */

    @Test
    @DisplayName("V-M04-25：新增成功——写库后返回创建记录，排序号取 MAX+1（含 type）")
    void createAssignsNextSortOrder() {
        when(repository.existsByTypeAndValue("行业领域", "金融")).thenReturn(false);
        when(repository.findMaxSortOrder()).thenReturn(37);
        when(repository.saveAndFlush(any(ComponentType.class)))
                .thenAnswer(invocation -> {
                    ComponentType entity = invocation.getArgument(0);
                    ReflectionTestUtils.setField(entity, "id", 38L);
                    return entity;
                });

        AppTypeValueVO created = service.create(new AppTypeValueRequest("  行业领域 ", " 金融 ", " 面向金融行业 "));

        assertEquals(38L, created.id());
        assertEquals("行业领域", created.type());
        assertEquals("金融", created.value());
        assertEquals("面向金融行业", created.description());
        assertEquals(38, created.sortOrder());

        ArgumentCaptor<ComponentType> captor = ArgumentCaptor.forClass(ComponentType.class);
        verify(repository).saveAndFlush(captor.capture());
        assertEquals("行业领域", captor.getValue().getType());
        assertEquals("金融", captor.getValue().getValue());
        assertEquals(38, captor.getValue().getSortOrder());
    }

    @Test
    @DisplayName("V-M04-25：空表新增取排序号 1")
    void createOnEmptyTableUsesOne() {
        when(repository.findMaxSortOrder()).thenReturn(null);
        when(repository.saveAndFlush(any(ComponentType.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AppTypeValueVO created = service.create(new AppTypeValueRequest("行业领域", "金融", null));

        assertEquals(1, created.sortOrder());
        assertEquals("", created.description());
    }

    @Test
    @DisplayName("V-M04-01/02/03：新增的字段拒绝分支与码位")
    void createRejectsInvalidFields() {
        assertEquals(ErrorCode.M04_E001, bizExceptionOf(() -> service.create(new AppTypeValueRequest("", "金融", ""))).getErrorCode());
        assertEquals(ErrorCode.M04_E001, bizExceptionOf(() -> service.create(new AppTypeValueRequest("　", "金融", ""))).getErrorCode());
        assertEquals(ErrorCode.M04_E002, bizExceptionOf(() -> service.create(new AppTypeValueRequest("行业领域", "  ", ""))).getErrorCode());
        assertEquals(ErrorCode.M04_E003, bizExceptionOf(() -> service.create(new AppTypeValueRequest("类".repeat(65), "金融", ""))).getErrorCode());
        assertEquals(ErrorCode.M04_E004, bizExceptionOf(() -> service.create(new AppTypeValueRequest("行业领域", "v".repeat(65), ""))).getErrorCode());
        assertEquals(ErrorCode.M04_E005, bizExceptionOf(() -> service.create(new AppTypeValueRequest("行业领域", "金融", "d".repeat(256)))).getErrorCode());
        // V-M04-01：请求体缺失按三字段全 null 处理，先命中类型为空（D-M04-02/P-01、字段校验次序）
        assertEquals(ErrorCode.M04_E001, bizExceptionOf(() -> service.create(null)).getErrorCode());
        // 三字段联合拒绝时逐项按顺序命中：类型与类型值同时为空返回 M04-E001（TC-43）
        assertEquals(ErrorCode.M04_E001, bizExceptionOf(() -> service.create(new AppTypeValueRequest("", "", ""))).getErrorCode());
        // 任一拒绝均不写库（P-09）
        verify(repository, never()).saveAndFlush(any(ComponentType.class));
    }

    @Test
    @DisplayName("V-M04-06/07：新增的同类型重复与跨类型同名")
    void createRejectsDuplicateWithinSameTypeOnly() {
        when(repository.existsByTypeAndValue("行业领域", "金融")).thenReturn(true);
        BizException duplicate = bizExceptionOf(() -> service.create(new AppTypeValueRequest("行业领域", "金融", "")));
        assertEquals(ErrorCode.M04_E006, duplicate.getErrorCode());
        assertEquals("类型「行业领域」下已存在类型值「金融」", duplicate.getMessage());

        // 跨类型同名不判为重复（RC-M04-04）：不同类型下同名允许写入
        when(repository.existsByTypeAndValue("用户对象", "金融")).thenReturn(false);
        when(repository.findMaxSortOrder()).thenReturn(2);
        when(repository.saveAndFlush(any(ComponentType.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        AppTypeValueVO created = service.create(new AppTypeValueRequest("用户对象", "金融", ""));
        assertEquals("用户对象", created.type());
    }

    @Test
    @DisplayName("V-M04-42：库级唯一键冲突映射为 M04-E006，文案与应用层判重一致")
    void createMapsDataIntegrityViolation() {
        when(repository.existsByTypeAndValue(anyString(), anyString())).thenReturn(false);
        when(repository.findMaxSortOrder()).thenReturn(1);
        when(repository.saveAndFlush(any(ComponentType.class)))
                .thenThrow(new DataIntegrityViolationException("UKfw0xjbfi99vvfscn6wfhq69sj"));

        BizException exception = bizExceptionOf(() -> service.create(new AppTypeValueRequest("行业领域", "金融", "")));

        assertEquals(ErrorCode.M04_E006, exception.getErrorCode());
        assertEquals("类型「行业领域」下已存在类型值「金融」", exception.getMessage());
    }

    /* ------------------------------------------------------------------ 编辑 */

    @Test
    @DisplayName("V-M04-27：编辑成功——字段迁移且排序号不变，记录 ID 不变")
    void updateKeepsSortOrderAndId() {
        ComponentType entity = persisted(12, "行业领域", "金融", "面向金融行业", 3);
        when(repository.findById(12L)).thenReturn(Optional.of(entity));
        when(repository.existsByTypeAndValueAndIdNot("用户对象", "金融", 12L)).thenReturn(false);
        when(repository.saveAndFlush(any(ComponentType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AppTypeValueVO updated = service.update(12L, new AppTypeValueRequest("用户对象", "金融", "面向金融行业"));

        assertEquals(12L, updated.id());
        assertEquals("用户对象", updated.type());
        assertEquals(3, updated.sortOrder());
        assertEquals("用户对象", entity.getType());
    }

    @Test
    @DisplayName("V-M04-29：记录不存在返回 M04-E007；字段校验先于存在性判定")
    void updateRejectsMissingRecord() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        BizException missing = bizExceptionOf(() -> service.update(99L, new AppTypeValueRequest("行业领域", "金融", "")));
        assertEquals(ErrorCode.M04_E007, missing.getErrorCode());
        assertEquals("应用类型记录不存在：99", missing.getMessage());
        // 不得静默转为新增（RC-M04-24）
        verify(repository, never()).saveAndFlush(any(ComponentType.class));

        // 字段校验先于存在性：空类型先返回 M04-E001（D-M04-03/P-02 与 P-03 的次序）
        assertEquals(ErrorCode.M04_E001,
                bizExceptionOf(() -> service.update(99L, new AppTypeValueRequest("", "金融", ""))).getErrorCode());
    }

    @Test
    @DisplayName("V-M04-08：编辑判重排除自身，改回原值不判为重复")
    void updateExcludesSelfFromDuplicateCheck() {
        ComponentType entity = persisted(12, "行业领域", "金融", "面向金融行业", 1);
        when(repository.findById(12L)).thenReturn(Optional.of(entity));
        when(repository.existsByTypeAndValueAndIdNot("行业领域", "金融", 12L)).thenReturn(false);
        when(repository.saveAndFlush(any(ComponentType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AppTypeValueVO updated = service.update(12L, new AppTypeValueRequest("行业领域", "金融", "面向金融行业"));

        assertEquals("金融", updated.value());
        verify(repository).existsByTypeAndValueAndIdNot("行业领域", "金融", 12L);

        when(repository.existsByTypeAndValueAndIdNot("用户对象", "个人用户", 12L)).thenReturn(true);
        BizException duplicate = bizExceptionOf(
                () -> service.update(12L, new AppTypeValueRequest("用户对象", "个人用户", "")));
        assertEquals(ErrorCode.M04_E006, duplicate.getErrorCode());
        assertEquals("类型「用户对象」下已存在类型值「个人用户」", duplicate.getMessage());
    }

    @Test
    @DisplayName("V-M04-42：编辑路径的库级唯一键冲突同样映射为 M04-E006")
    void updateMapsDataIntegrityViolation() {
        ComponentType entity = persisted(12, "行业领域", "金融", "", 1);
        when(repository.findById(12L)).thenReturn(Optional.of(entity));
        when(repository.saveAndFlush(any(ComponentType.class)))
                .thenThrow(new DataIntegrityViolationException("UKfw0xjbfi99vvfscn6wfhq69sj"));

        BizException exception = bizExceptionOf(
                () -> service.update(12L, new AppTypeValueRequest("用户对象", "个人用户", "")));

        assertEquals(ErrorCode.M04_E006, exception.getErrorCode());
    }

    /* ------------------------------------------------------------------ 删除单条 */

    @Test
    @DisplayName("V-M04-14：删除单条返回 {\"id\":X}；记录不存在返回 M04-E007 且不执行删除")
    void deleteValueContract() {
        ComponentType entity = persisted(12, "行业领域", "金融", "", 1);
        when(repository.findById(12L)).thenReturn(Optional.of(entity));

        Map<String, Object> body = service.deleteValue(12L);

        assertEquals(12L, body.get("id"));
        verify(repository).delete(entity);

        when(repository.findById(13L)).thenReturn(Optional.empty());
        BizException missing = bizExceptionOf(() -> service.deleteValue(13L));
        assertEquals(ErrorCode.M04_E007, missing.getErrorCode());
        assertEquals("应用类型记录不存在：13", missing.getMessage());
        verify(repository, never()).deleteById(anyLong());
    }

    /* ------------------------------------------------------------------ 删除整类 */

    @Test
    @DisplayName("V-M04-15/16：删除整类的未指定与类型不存在判定")
    void deleteTypeRejectsUnspecifiedAndUnknown() {
        assertEquals(ErrorCode.M04_E009, bizExceptionOf(() -> service.deleteType(null)).getErrorCode());
        assertEquals(ErrorCode.M04_E009, bizExceptionOf(() -> service.deleteType("")).getErrorCode());
        assertEquals(ErrorCode.M04_E009, bizExceptionOf(() -> service.deleteType("　 ")).getErrorCode());
        // 未指定优先于类型不存在，且不触发计数与删除
        verify(repository, never()).countByType(anyString());
        verify(repository, never()).deleteByType(anyString());

        when(repository.countByType("不存在类型")).thenReturn(0L);
        BizException unknown = bizExceptionOf(() -> service.deleteType(" 不存在类型 "));
        assertEquals(ErrorCode.M04_E008, unknown.getErrorCode());
        assertEquals("类型不存在：不存在类型", unknown.getMessage());
        verify(repository, never()).deleteByType(anyString());
    }

    @Test
    @DisplayName("V-M04-13/38：删除整类返回 type 与实际删除条数，N 与删除前该类型条数一致")
    void deleteTypeReturnsActualDeletedCount() {
        when(repository.countByType("行业领域")).thenReturn(3L);
        when(repository.deleteByType("行业领域")).thenReturn(3);

        Map<String, Object> body = service.deleteType(" 行业领域 ");

        assertEquals("行业领域", body.get("type"));
        assertEquals(3, body.get("deletedCount"));
    }
}
