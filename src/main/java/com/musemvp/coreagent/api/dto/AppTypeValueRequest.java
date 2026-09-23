package com.musemvp.coreagent.api.dto;

/**
 * 应用类型取值的写入请求体（新增与编辑共用一个结构，详细设计 D-M04-04/C-01、D-M04-08/C-01）。
 *
 * <p>字段与 {@code POST /api/app-types/values}、{@code PUT /api/app-types/values/{id}} 的请求体一一对应：
 * {@code type}、{@code value}、{@code description}。编辑场景三个字段全部可选传，未传字段按「未填写」
 * 参与校验（D-M04-08/C-01：编辑是整行迁移，不是局部补丁）。
 *
 * <p>本记录<b>不参与 JSON 序列化出参</b>，仅作出参；因此不承载 {@code id} 与 {@code sortOrder}
 * ——{@code id} 来自路径变量，{@code sort_order} 由服务端维护且编辑不改写（RC-M04-06、D-M04-08/C-04）。
 */
public record AppTypeValueRequest(String type, String value, String description) {
}
