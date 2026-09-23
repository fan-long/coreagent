package com.musemvp.coreagent.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.util.StringUtils;

import com.musemvp.coreagent.domain.ComponentRepository;
import com.musemvp.coreagent.support.BizException;
import com.musemvp.coreagent.support.ErrorCode;

/**
 * 工程仓库选取口径单点（DP-M01-02）。
 *
 * <p>{@code component_repository} 未建 {@code (component_id, is_default)} 唯一约束，「默认工程唯一」由应用层
 * 保证（PQ-05）。工作台卡片与组件信息侧边栏共用本类，避免同组件多默认工程时两处取值漂移。
 */
public final class RepositorySelection {

    /** 多默认兜底：同组件存在多条 {@code is_default} 时取 {@code updated_at} 最新的一条（PQ-05）。 */
    private static final Comparator<ComponentRepository> BY_UPDATED_AT_DESC =
            Comparator.comparing(ComponentRepository::getUpdatedAt,
                    Comparator.nullsFirst(Comparator.naturalOrder()));

    private RepositorySelection() {
    }

    /**
     * 默认工程：{@code is_default = b'1'} 者；不存在时返回空（调用方按「默认工程不可用」降级）。
     */
    public static Optional<ComponentRepository> defaultOf(List<ComponentRepository> repositories) {
        return repositories.stream()
                .filter(repository -> Boolean.TRUE.equals(repository.getIsDefault()))
                .max(BY_UPDATED_AT_DESC);
    }

    /**
     * 当前工程：显式指定优先且须属于该组件，否则取默认工程，再退化为最近更新的一个。
     *
     * <p>退化分支只用于「组件未标记任何默认工程」的异常数据，保证组件信息视图仍可进入（BR-M01-04 只覆盖
     * 「无任何工程仓库」的降级，不覆盖此情形）。
     *
     * @param repositoryId 请求指定的工程仓库；空表示取默认
     * @throws BizException M01-E005 指定的工程仓库不存在或不属于该组件
     */
    public static ComponentRepository currentOf(List<ComponentRepository> repositories, String repositoryId) {
        if (StringUtils.hasText(repositoryId)) {
            return repositories.stream()
                    .filter(repository -> repositoryId.equals(repository.getId()))
                    .findFirst()
                    .orElseThrow(() -> new BizException(ErrorCode.M01_E005));
        }
        return defaultOf(repositories)
                .or(() -> repositories.stream().max(BY_UPDATED_AT_DESC))
                .orElse(null);
    }
}
