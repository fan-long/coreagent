/**
 * 应用类型页，见详细设计 D-M04-08（M04）。
 *
 * 页面结构：头部四段（面包屑 / 标题 / 说明 / 主操作「新增」）→ 主从两栏（左侧类型列表 + 页脚统计，
 * 右侧取值明细表 + 详情标题行）→ 行内编辑态 / 行内新增行 → 新增界面与删除类型确认浮层。
 *
 * 关键约束：
 * - **不乐观更新**：任何写操作仅在服务端返回成功后重新调用读取接口，由读取响应重建整个主从视图与
 *   页脚统计（D-M04-08/C-15）；失败时不改动本地字典；
 * - **判重不前置**：前端只做「空值 + 长度」的体验性校验，唯一性一律以服务端 `M04-E006` 的结果提示
 *   （D-M04-08/C-09、C-25）；
 * - **本地切换**：点击左侧类型仅重渲染右侧，不发起请求（D-M04-08/C-06、DD-M04-02）；
 * - **不订阅项目会话态**：字典为平台级数据，项目切换不重载本页（D-M04-12/C-06）；
 * - 全部文本以 `textContent` 写入，不拼接 HTML（D-M04-08/4.5）；不输出 `console` 日志。
 */
import { http, withQuery } from '../core/http.js';
import { showToast, renderLoading, renderFailure } from '../core/notice.js';

/**
 * 长度上限与 `component_type` 列宽一致（D-M04-14/C-01、D-M04-08/C-09）；
 * 前端校验为体验优化，不替代服务端校验（与 `ProjectInfo.js` 的 `NAME_MAX_LENGTH` 同口径）。
 */
const TYPE_MAX_LENGTH = 64;
const VALUE_MAX_LENGTH = 64;
const DESCRIPTION_MAX_LENGTH = 255;

/* 页面文案与占位（D-M04-14/C-03…C-06 原文，逐字一致，不得改写）。 */
const BREADCRUMB_GROUP = '环境管理';
const BREADCRUMB_SECTION = '模板管理';
const TITLE = '应用类型';
const SUBTITLE = '维护应用组件的类型分类：左侧选择类型，右侧维护该类型下的类型值与值说明，用于多维度刻画应用组件的特征';
const ADD_LABEL = '新增';
const VALUE_PLACEHOLDER = '类型值，如 金融';
const DESCRIPTION_PLACEHOLDER = '取值含义，可留空';
const EMPTY_NO_TYPE = '暂无应用类型，点击右上角「新增」添加';
const EMPTY_NO_VALUE = '该类型下暂无取值，点击右上角「新增」添加';
const MODAL_TITLE = '新增';
const MODAL_DESC = '在当前所选类型下追加类型值；如需新增类型，修改类型名称即可';
const DELETE_TYPE_LABEL = '删除类型';
const DELETE_TYPE_TITLE = '删除类型';
const DELETE_TYPE_CONFIRM = '将连同该类型下全部取值一并删除，且删除后不可恢复。确认删除该类型？';

/* 表格列（D-M04-08/C-05）。 */
const COL_VALUE = '类型值';
const COL_DESCRIPTION = '值说明';
const COL_ACTION = '操作';

/* 行内与浮层操作按钮文案。 */
const EDIT_LABEL = '编辑';
const DELETE_LABEL = '删除';
const SAVE_LABEL = '保存';
const CANCEL_LABEL = '取消';
const CONFIRM_LABEL = '确认删除';

/* 前端体验性校验文案，口径与服务端 M04-E001…M04-E005 一致（D-M04-08/C-09）。 */
const MSG_TYPE_REQUIRED = '请填写类型';
const MSG_TYPE_TOO_LONG = `类型不能超过${TYPE_MAX_LENGTH}个字符`;
const MSG_VALUE_REQUIRED = '请填写类型值';
const MSG_VALUE_TOO_LONG = `类型值不能超过${VALUE_MAX_LENGTH}个字符`;
const MSG_DESCRIPTION_TOO_LONG = `值说明不能超过${DESCRIPTION_MAX_LENGTH}个字符`;

function element(tagName, className, text) {
  const node = document.createElement(tagName);
  if (className) {
    node.className = className;
  }
  if (text !== undefined && text !== null) {
    node.textContent = text;
  }
  return node;
}

function buildButton(label, className) {
  const button = element('button', className, label);
  button.type = 'button';
  return button;
}

/** 空值 + 长度的体验性校验；通过返回 null，否则返回提示文案。 */
function checkValue(rawValue, rawDescription) {
  const value = rawValue.trim();
  if (!value) {
    return MSG_VALUE_REQUIRED;
  }
  if (value.length > VALUE_MAX_LENGTH) {
    return MSG_VALUE_TOO_LONG;
  }
  if (rawDescription.trim().length > DESCRIPTION_MAX_LENGTH) {
    return MSG_DESCRIPTION_TOO_LONG;
  }
  return null;
}

/** 新增界面的类型名称字段校验。 */
function checkTypeName(rawType) {
  const type = rawType.trim();
  if (!type) {
    return MSG_TYPE_REQUIRED;
  }
  if (type.length > TYPE_MAX_LENGTH) {
    return MSG_TYPE_TOO_LONG;
  }
  return null;
}

/**
 * 挂载应用类型页。
 *
 * @param {HTMLElement} container #main-view
 * @returns {{destroy: Function}}
 */
export function mountAppType(container) {
  container.textContent = '';

  /* ---------------------------------------------------------------- 头部四段 */

  const page = element('section', 'page');
  const header = element('header', 'page-header');

  // 面包屑：环境管理形态「环境管理 / 模板管理」（D-M04-08/C-02）
  const breadcrumb = element('nav', 'breadcrumb');
  breadcrumb.setAttribute('aria-label', '面包屑');
  breadcrumb.appendChild(element('span', 'breadcrumb-item', BREADCRUMB_GROUP));
  breadcrumb.appendChild(element('span', 'breadcrumb-sep', '/'));
  breadcrumb.appendChild(element('span', 'breadcrumb-item is-current', BREADCRUMB_SECTION));

  const titleArea = element('div', 'page-title-area');
  titleArea.appendChild(element('h1', 'page-title', TITLE));
  titleArea.appendChild(element('p', 'page-subtitle', SUBTITLE));

  // 右上角主操作「新增」：空字典下同样可用（RC-M04-17、D-M04-08/C-12）
  const actions = element('div', 'page-actions');
  const addButton = buildButton(ADD_LABEL, 'button button-primary');
  actions.appendChild(addButton);

  header.appendChild(breadcrumb);
  header.appendChild(titleArea);
  header.appendChild(actions);
  page.appendChild(header);

  const body = element('div', 'page-body');
  page.appendChild(body);
  container.appendChild(page);

  /* ---------------------------------------------------------------- 本地状态 */

  /** 最近一次读取响应的只读快照；写操作成功后整体重建（C-15）。 */
  let snapshot = { types: [], typeCount: 0, valueCount: 0 };
  /** 当前选中类型名；无选中类型时为 null（空字典）。 */
  let selectedType = null;
  /** 编辑态：{ kind: 'none' } | { kind: 'edit', id, value, description } | { kind: 'add', value, description }。 */
  let editState = { kind: 'none' };
  /** 挂载代次：卸载或在途请求被新一次取数取代后，过期响应不再改写界面（P-13）。 */
  let generation = 0;
  /** 本页创建的浮层关闭函数集合，卸载时统一关闭（含文档级 keydown 监听）。 */
  const closeDialogs = [];

  function currentGroup() {
    return snapshot.types.find((group) => group.type === selectedType) || null;
  }

  function resetEditState() {
    editState = { kind: 'none' };
  }

  /* ---------------------------------------------------------------- 渲染：左列表 */

  function renderMasterList() {
    column.textContent = '';

    if (snapshot.types.length === 0) {
      // C-12：无类型时不渲染列表项与页脚计数项，右侧展示空态
      return;
    }

    const list = element('div', 'master-list');
    snapshot.types.forEach((group) => {
      const item = element('button', 'master-list-item');
      if (group.type === selectedType) {
        item.classList.add('is-active');
        item.setAttribute('aria-current', 'true');
      }
      item.appendChild(element('span', 'master-list-name', group.type));
      item.appendChild(element('span', 'master-list-count', String(group.valueCount)));
      item.addEventListener('click', () => {
        // P-04：仅本地更新选中态并重渲染右侧，不发请求（DD-M04-02）
        selectedType = group.type;
        resetEditState();
        renderMasterList();
        renderDetail();
      });
      list.appendChild(item);
    });
    column.appendChild(list);

    // 页脚统计取同一次读取响应的 typeCount/valueCount（C-04）
    column.appendChild(
      element('p', 'master-footer', `${snapshot.typeCount}个类型 · ${snapshot.valueCount}条取值`)
    );
  }

  /* ---------------------------------------------------------------- 渲染：右明细 */

  /** 文本态行：类型值 / 值说明 / 操作（编辑、删除）。 */
  function buildTextRow(item) {
    const row = element('tr');
    row.appendChild(element('td', 'value-cell', item.value));
    row.appendChild(element('td', 'description-cell', item.description || ''));

    const actionCell = element('td', 'col-action');
    const group = element('div', 'row-actions');
    const editButton = buildButton(EDIT_LABEL, 'button button-ghost row-edit-button');
    editButton.addEventListener('click', () => {
      // C-07：同一时刻至多一行处于编辑态
      editState = {
        kind: 'edit',
        id: item.id,
        value: item.value,
        description: item.description || ''
      };
      renderDetail();
    });
    const deleteButton = buildButton(DELETE_LABEL, 'button button-ghost');
    // P-07：行内删除无二次确认（DD-M04-10）
    deleteButton.addEventListener('click', () => removeValue(item.id));
    group.appendChild(editButton);
    group.appendChild(deleteButton);
    actionCell.appendChild(group);
    row.appendChild(actionCell);

    // C-07：点击该行亦可进入编辑态（操作按钮除外）
    row.addEventListener('click', (event) => {
      if (event.target.closest('button')) {
        return;
      }
      editState = {
        kind: 'edit',
        id: item.id,
        value: item.value,
        description: item.description || ''
      };
      renderDetail();
    });
    return row;
  }

  /** 行内编辑态行：类型值输入框 + 值说明输入框 + 保存 + 删除（C-07）。 */
  function buildEditRow(item, state) {
    const row = element('tr', 'is-editing');

    const valueInput = element('input', 'text-input row-input');
    valueInput.type = 'text';
    valueInput.value = state.value;
    valueInput.placeholder = VALUE_PLACEHOLDER;
    valueInput.maxLength = VALUE_MAX_LENGTH;
    valueInput.setAttribute('aria-label', COL_VALUE);
    valueInput.addEventListener('input', () => {
      state.value = valueInput.value;
    });

    const descriptionInput = element('input', 'text-input row-input');
    descriptionInput.type = 'text';
    descriptionInput.value = state.description;
    descriptionInput.placeholder = DESCRIPTION_PLACEHOLDER;
    descriptionInput.maxLength = DESCRIPTION_MAX_LENGTH;
    descriptionInput.setAttribute('aria-label', COL_DESCRIPTION);
    descriptionInput.addEventListener('input', () => {
      state.description = descriptionInput.value;
    });

    const saveButton = buildButton(SAVE_LABEL, 'button button-primary');
    saveButton.addEventListener('click', () => saveEditedRow(item.id, state, saveButton));

    const deleteButton = buildButton(DELETE_LABEL, 'button button-ghost');
    deleteButton.addEventListener('click', () => removeValue(item.id));

    [valueInput, descriptionInput].forEach((input) => {
      input.addEventListener('keydown', (event) => {
        if (event.key === 'Enter') {
          saveButton.click();
        }
      });
    });

    const cells = [element('td'), element('td')];
    cells[0].appendChild(valueInput);
    cells[1].appendChild(descriptionInput);
    row.appendChild(cells[0]);
    row.appendChild(cells[1]);

    const actionCell = element('td', 'col-action');
    const group = element('div', 'row-actions');
    group.appendChild(saveButton);
    group.appendChild(deleteButton);
    actionCell.appendChild(group);
    row.appendChild(actionCell);

    window.setTimeout(() => valueInput.focus(), 0);
    return row;
  }

  /** 行内新增行：类型值 + 值说明 + 保存 + **取消**（C-08，取消必须提供）。 */
  function buildAddRow(state) {
    const row = element('tr', 'is-adding');

    const valueInput = element('input', 'text-input row-input');
    valueInput.type = 'text';
    valueInput.value = state.value;
    valueInput.placeholder = VALUE_PLACEHOLDER;
    valueInput.maxLength = VALUE_MAX_LENGTH;
    valueInput.setAttribute('aria-label', COL_VALUE);
    valueInput.addEventListener('input', () => {
      state.value = valueInput.value;
    });

    const descriptionInput = element('input', 'text-input row-input');
    descriptionInput.type = 'text';
    descriptionInput.value = state.description;
    descriptionInput.placeholder = DESCRIPTION_PLACEHOLDER;
    descriptionInput.maxLength = DESCRIPTION_MAX_LENGTH;
    descriptionInput.setAttribute('aria-label', COL_DESCRIPTION);
    descriptionInput.addEventListener('input', () => {
      state.description = descriptionInput.value;
    });

    const saveButton = buildButton(SAVE_LABEL, 'button button-primary');
    saveButton.addEventListener('click', () => saveNewRow(state, saveButton));

    const cancelButton = buildButton(CANCEL_LABEL, 'button button-secondary');
    // 取消：移除该行且不发请求（C-08）
    cancelButton.addEventListener('click', () => {
      resetEditState();
      renderDetail();
    });

    [valueInput, descriptionInput].forEach((input) => {
      input.addEventListener('keydown', (event) => {
        if (event.key === 'Enter') {
          saveButton.click();
        }
      });
    });

    const cells = [element('td'), element('td')];
    cells[0].appendChild(valueInput);
    cells[1].appendChild(descriptionInput);
    row.appendChild(cells[0]);
    row.appendChild(cells[1]);

    const actionCell = element('td', 'col-action');
    const group = element('div', 'row-actions');
    group.appendChild(saveButton);
    group.appendChild(cancelButton);
    actionCell.appendChild(group);
    row.appendChild(actionCell);

    window.setTimeout(() => valueInput.focus(), 0);
    return row;
  }

  function renderDetail() {
    detail.textContent = '';

    if (snapshot.types.length === 0) {
      // C-12：空态文案，且不展示「删除类型」入口（C-22）
      detail.appendChild(element('p', 'empty-state', EMPTY_NO_TYPE));
      return;
    }

    const group = currentGroup();
    if (!group) {
      detail.appendChild(element('p', 'empty-state', EMPTY_NO_TYPE));
      return;
    }

    // 详情标题行：当前类型名 + 「删除类型」（危险样式，C-22）
    const titleRow = element('div', 'detail-title-row');
    titleRow.appendChild(element('h2', 'detail-title', group.type));
    const deleteTypeButton = buildButton(DELETE_TYPE_LABEL, 'button button-danger');
    deleteTypeButton.addEventListener('click', () => openDeleteTypeDialog(group.type));
    const titleActions = element('div', 'detail-actions');
    titleActions.appendChild(deleteTypeButton);
    titleRow.appendChild(titleActions);
    detail.appendChild(titleRow);

    // 表格区「新增」入口：在当前所选类型下追加取值（C-08，行内新增无法形成新类型）
    const toolbar = element('div', 'detail-actions');
    const addValueButton = buildButton(ADD_LABEL, 'button button-secondary');
    addValueButton.addEventListener('click', () => {
      editState = { kind: 'add', value: '', description: '' };
      renderDetail();
    });
    toolbar.appendChild(addValueButton);
    detail.appendChild(toolbar);

    const values = Array.isArray(group.values) ? group.values : [];
    // C-13：所选类型无取值为防御分支（类型由记录派生，实际不可达）
    if (values.length === 0 && editState.kind !== 'add') {
      detail.appendChild(element('p', 'empty-state', EMPTY_NO_VALUE));
      return;
    }

    const table = element('table', 'data-table value-table');
    const thead = element('thead');
    const headRow = element('tr');
    headRow.appendChild(element('th', 'col-value', COL_VALUE));
    headRow.appendChild(element('th', 'col-description', COL_DESCRIPTION));
    headRow.appendChild(element('th', 'col-action', COL_ACTION));
    thead.appendChild(headRow);
    table.appendChild(thead);

    const tbody = element('tbody');
    values.forEach((item) => {
      tbody.appendChild(
        editState.kind === 'edit' && editState.id === item.id
          ? buildEditRow(item, editState)
          : buildTextRow(item)
      );
    });
    if (editState.kind === 'add') {
      tbody.appendChild(buildAddRow(editState));
    }
    table.appendChild(tbody);
    detail.appendChild(table);
  }

  /* ---------------------------------------------------------------- 取数 */

  async function load() {
    const ticket = ++generation;
    renderLoading(body, '正在加载应用类型…');
    try {
      const response = await http.get('/api/app-types');
      if (ticket !== generation) {
        return;
      }
      snapshot = {
        types: Array.isArray(response && response.types) ? response.types : [],
        typeCount: (response && response.typeCount) || 0,
        valueCount: (response && response.valueCount) || 0
      };
      resetEditState();
      // C-06：默认选中排序首位类型；空字典不选中
      selectedType = snapshot.types.length > 0 ? snapshot.types[0].type : null;
      renderBody();
    } catch (error) {
      if (ticket !== generation) {
        return;
      }
      // C-14：失败块 + 重试，不展示空态文案（RC-M04-19 优先）
      renderFailure(body, `应用类型加载失败：${error.message}`, load);
    }
  }

  /** 写操作成功后统一重新取数，由读取响应重建主从视图与页脚统计（C-15）。 */
  async function refresh() {
    await load();
  }

  /* ---------------------------------------------------------------- 单条写入 */

  async function saveEditedRow(id, state, saveButton) {
    const message = checkValue(state.value, state.description);
    if (message) {
      showToast(message, 'error');
      return;
    }
    saveButton.disabled = true;
    try {
      await http.put(`/api/app-types/values/${id}`, {
        type: selectedType,
        value: state.value.trim(),
        description: state.description.trim()
      });
      resetEditState();
      showToast('保存成功', 'info');
      await refresh();
    } catch (error) {
      // C-17：编辑失败退出编辑态并回显修改前内容；失败原因以浮动提示给出
      resetEditState();
      showToast(error.message || '保存失败，请稍后重试', 'error');
      await refresh();
    }
  }

  async function saveNewRow(state, saveButton) {
    const message = checkValue(state.value, state.description);
    if (message) {
      showToast(message, 'error');
      return;
    }
    saveButton.disabled = true;
    try {
      await http.post('/api/app-types/values', {
        type: selectedType,
        value: state.value.trim(),
        description: state.description.trim()
      });
      resetEditState();
      showToast('新增成功', 'info');
      await refresh();
    } catch (error) {
      // C-10：行内新增失败时保留该行及其输入，仅提示失败原因（RC-M04-21）
      showToast(error.message || '新增失败，请稍后重试', 'error');
      saveButton.disabled = false;
    }
  }

  async function removeValue(id) {
    try {
      await http.del(`/api/app-types/values/${id}`);
      resetEditState();
      showToast('删除成功', 'info');
    } catch (error) {
      showToast(error.message || '删除失败，请稍后重试', 'error');
    }
    // 成功与失败均重新取数，保证界面回显库中真实状态（状态与请求映射表）
    await refresh();
  }

  async function removeType(type) {
    try {
      const result = await http.del(withQuery('/api/app-types', { type }));
      const deletedCount = (result && result.deletedCount) || 0;
      const deletedType = (result && result.type) || type;
      selectedType = null;
      showToast(`类型「${deletedType}」及其 ${deletedCount} 条取值已删除`, 'info');
    } catch (error) {
      showToast(error.message || '删除失败，请稍后重试', 'error');
    }
    await refresh();
  }

  /* ---------------------------------------------------------------- 浮层构件 */

  /**
   * 构建浮层骨架（复用既有 `.modal-*` 结构，与 `ProjectDialog.js` 同范式）。
   *
   * @returns {{overlay: HTMLElement, body: HTMLElement, foot: HTMLElement, close: Function}}
   */
  function openDialog({ title, desc, labelledBy }) {
    const overlay = element('div', 'modal-overlay');
    const modal = element('div', 'modal');
    modal.setAttribute('role', 'dialog');
    modal.setAttribute('aria-modal', 'true');
    modal.setAttribute('aria-labelledby', labelledBy);

    const head = element('header', 'modal-head');
    const heading = element('h2', 'modal-title', title);
    heading.id = labelledBy;
    const closeButton = element('button', 'modal-close', '×');
    closeButton.type = 'button';
    closeButton.title = '关闭';
    closeButton.setAttribute('aria-label', '关闭');
    head.appendChild(heading);
    head.appendChild(closeButton);

    const bodyNode = element('div', 'modal-body');
    const foot = element('footer', 'modal-foot');

    modal.appendChild(head);
    if (desc) {
      modal.appendChild(element('p', 'modal-desc', desc));
    }
    modal.appendChild(bodyNode);
    modal.appendChild(foot);
    overlay.appendChild(modal);
    document.getElementById('dialog-root').appendChild(overlay);

    let closed = false;
    function close() {
      if (closed) {
        return;
      }
      closed = true;
      document.removeEventListener('keydown', onKeydown);
      overlay.remove();
    }
    function onKeydown(event) {
      if (event.key === 'Escape') {
        close();
      }
    }
    document.addEventListener('keydown', onKeydown);
    overlay.addEventListener('mousedown', (event) => {
      if (event.target === overlay) {
        close();
      }
    });
    closeButton.addEventListener('click', close);

    const instance = { overlay, body: bodyNode, foot, close };
    closeDialogs.push(close);
    return instance;
  }

  /** 新增界面：类型名称 + 类型值 + 值说明 + 保存/取消（C-18…C-21）。 */
  function openAddDialog() {
    const dialog = openDialog({ title: MODAL_TITLE, desc: MODAL_DESC, labelledBy: 'app-type-dialog-title' });

    // 类型名称默认回填当前所选类型名；无选中类型时为空（C-18）
    const typeInput = element('input', 'text-input');
    typeInput.type = 'text';
    typeInput.id = 'app-type-name';
    typeInput.value = selectedType || '';
    typeInput.maxLength = TYPE_MAX_LENGTH;
    typeInput.autocomplete = 'off';

    const valueInput = element('input', 'text-input');
    valueInput.type = 'text';
    valueInput.id = 'app-type-value';
    valueInput.placeholder = VALUE_PLACEHOLDER;
    valueInput.maxLength = VALUE_MAX_LENGTH;
    valueInput.autocomplete = 'off';

    const descriptionInput = element('input', 'text-input');
    descriptionInput.type = 'text';
    descriptionInput.id = 'app-type-description';
    descriptionInput.placeholder = DESCRIPTION_PLACEHOLDER;
    descriptionInput.maxLength = DESCRIPTION_MAX_LENGTH;
    descriptionInput.autocomplete = 'off';

    function buildField(labelText, control) {
      const field = element('div', 'modal-field');
      const label = element('label', 'form-label', labelText);
      label.setAttribute('for', control.id);
      field.appendChild(label);
      field.appendChild(control);
      return field;
    }

    dialog.body.appendChild(buildField('类型名称', typeInput));
    dialog.body.appendChild(buildField(COL_VALUE, valueInput));
    dialog.body.appendChild(buildField(COL_DESCRIPTION, descriptionInput));

    const cancelButton = buildButton(CANCEL_LABEL, 'button button-secondary');
    // C-20：取消或关闭不发送任何请求、不写本地字典、不产生提示
    cancelButton.addEventListener('click', () => dialog.close());

    const saveButton = buildButton(SAVE_LABEL, 'button button-primary');
    saveButton.addEventListener('click', async () => {
      const typeMessage = checkTypeName(typeInput.value);
      if (typeMessage) {
        showToast(typeMessage, 'error');
        return;
      }
      const valueMessage = checkValue(valueInput.value, descriptionInput.value);
      if (valueMessage) {
        showToast(valueMessage, 'error');
        return;
      }
      // 保存中禁用按钮，避免重复提交（C-19；不构成服务端幂等承诺）
      saveButton.disabled = true;
      try {
        await http.post('/api/app-types/values', {
          type: typeInput.value.trim(),
          value: valueInput.value.trim(),
          description: descriptionInput.value.trim()
        });
        dialog.close();
        showToast('新增成功', 'info');
        await refresh();
      } catch (error) {
        // C-21：界面保持打开、输入保留，仅提示失败原因
        showToast(error.message || '新增失败，请稍后重试', 'error');
        saveButton.disabled = false;
      }
    });

    dialog.foot.appendChild(cancelButton);
    dialog.foot.appendChild(saveButton);

    [typeInput, valueInput, descriptionInput].forEach((input) => {
      input.addEventListener('keydown', (event) => {
        if (event.key === 'Enter') {
          saveButton.click();
        }
      });
    });

    window.setTimeout(() => typeInput.focus(), 0);
  }

  /** 删除类型确认浮层：说明文案明确「将连同该类型下全部取值一并删除」（C-23、RC-M04-33）。 */
  function openDeleteTypeDialog(type) {
    const dialog = openDialog({
      title: DELETE_TYPE_TITLE,
      desc: DELETE_TYPE_CONFIRM,
      labelledBy: 'app-type-delete-title'
    });

    const cancelButton = buildButton(CANCEL_LABEL, 'button button-secondary');
    // 取消则不发送请求（C-23）
    cancelButton.addEventListener('click', () => dialog.close());

    const confirmButton = buildButton(CONFIRM_LABEL, 'button button-danger');
    confirmButton.addEventListener('click', async () => {
      confirmButton.disabled = true;
      dialog.close();
      await removeType(type);
    });

    dialog.foot.appendChild(cancelButton);
    dialog.foot.appendChild(confirmButton);
    window.setTimeout(() => confirmButton.focus(), 0);
  }

  /* ---------------------------------------------------------------- 组装与启动 */

  const layout = element('div', 'master-detail');
  const column = element('div', 'master-column');
  const detail = element('div', 'master-detail-body');
  layout.appendChild(column);
  layout.appendChild(detail);

  function renderBody() {
    body.textContent = '';
    body.appendChild(layout);
    renderMasterList();
    renderDetail();
  }

  addButton.addEventListener('click', () => {
    // P-08：空字典下同样可打开新增界面（类型名称字段为空）
    openAddDialog();
  });

  renderLoading(body, '正在加载应用类型…');
  load();

  return {
    destroy() {
      // P-13：置空容器、关闭本页浮层、递增代次使在途响应失效
      generation += 1;
      closeDialogs.splice(0).forEach((close) => close());
      container.textContent = '';
    }
  };
}
