/**
 * 项目信息页，见详细设计 D-M02-05（M02）。
 *
 * 页面结构固定为：头部四段（面包屑 / 标题 / 说明 / 主操作）→ 提示条 → 名称、目标、范围表单 →
 * 工程阶段表格。头部四段按 M01 `D-M01-14/C-01` 的约定手写实现，待该公共构件落地后替换为构件调用
 * （D-M02-05/4.6 第 2 项）。
 *
 * 关键约束：
 * - 锁定阶段**不本地判定**：只读行由服务端下发的 `lockedStages` 决定，前端不复制锁定阶段清单
 *   （D-M02-05/4.3 末段）；
 * - 阶段不拼接、不解析分隔文本：接口层以字符串数组传输，序列化在服务端完成（DD-M02-08）；
 * - 读取走 `GET /api/projects/current`（无项目时由服务端创建默认项目行），保存按会话态中的项目标识
 *   选择 `PUT /api/projects/{id}` 或 `PUT /api/projects`（D-M02-05/C-11）；
 * - 保存失败不写会话态、不改动已填内容（D-M02-05/C-12、D-M02-11/P-05）。
 */
import {
  get as getState,
  setProjectDefinition
} from '../core/store.js';
import { http } from '../core/http.js';
import { showToast, renderLoading, renderFailure } from '../core/notice.js';

/** 与 DEF-M02-02 的名称上限一致；前端校验为体验优化，不替代服务端校验（RC-M02-01）。 */
const NAME_MAX_LENGTH = 128;

const TITLE = '项目信息';
const SUBTITLE = '配置项目名称、目标、范围与工程阶段；项目名称用于顶栏「当前项目」显示，并生成组件规划Agent的定位提示';
const HINT_BAR = '项目定义会组装进「AI应用组件设计」对话的当前上下文，目标与范围随对话发送给组件规划Agent，修改后新建会话立即生效';
const SAVE_LABEL = '保存配置';
const BREADCRUMB_GROUP = '环境管理';
const BREADCRUMB_SECTION = '项目配置';

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

/** 序号徽标：两位补零；达到或超过两位时按实际位数展示（RC-M02-30，DQ-M02-13）。 */
function indexBadge(index) {
  return String(index).padStart(2, '0');
}

/**
 * 挂载项目信息页。
 *
 * @param {HTMLElement} container #main-view
 * @returns {{destroy: Function}}
 */
export function mountProjectInfo(container) {
  container.textContent = '';

  /* ---------------------------------------------------------------- 头部四段 */

  const page = element('section', 'page');
  const header = element('header', 'page-header');

  // 面包屑：环境管理形态「环境管理 / 项目配置」（D-M02-05/C-01）。
  // 「环境管理」无独立落地页，故此处为纯文本；上级可点击返回由 M01 公共头部构件统一提供。
  const breadcrumb = element('nav', 'breadcrumb');
  breadcrumb.setAttribute('aria-label', '面包屑');
  breadcrumb.appendChild(element('span', 'breadcrumb-item', BREADCRUMB_GROUP));
  breadcrumb.appendChild(element('span', 'breadcrumb-sep', '/'));
  breadcrumb.appendChild(element('span', 'breadcrumb-item is-current', BREADCRUMB_SECTION));

  const titleArea = element('div', 'page-title-area');
  titleArea.appendChild(element('h1', 'page-title', TITLE));
  titleArea.appendChild(element('p', 'page-subtitle', SUBTITLE));

  const actions = element('div', 'page-actions');
  const saveButton = element('button', 'button button-primary', SAVE_LABEL);
  saveButton.type = 'button';
  actions.appendChild(saveButton);

  header.appendChild(breadcrumb);
  header.appendChild(titleArea);
  header.appendChild(actions);
  page.appendChild(header);

  const body = element('div', 'page-body');
  page.appendChild(body);
  container.appendChild(page);

  /* ---------------------------------------------------------------- 表单构件 */

  /** 表单区在读取成功后构建一次，之后只在保存成功时回填，不重建（避免丢失用户输入焦点）。 */
  let form = null;
  let nameInput = null;
  let goalInput = null;
  let scopeInput = null;
  let stageBody = null;
  let stageError = null;
  let lockedStages = [];
  /** 本地阶段列表：非锁定阶段为可编辑行，锁定阶段行只读且无删除入口（RC-M02-08、RC-M02-30）。 */
  let stageRows = [];

  function buildField(labelText, control) {
    const field = element('div', 'form-field');
    const label = element('label', 'form-label', labelText);
    const controlId = control.id;
    label.setAttribute('for', controlId);
    field.appendChild(label);
    field.appendChild(control);
    return field;
  }

  function buildForm() {
    form = element('form', 'project-form');
    form.addEventListener('submit', (event) => event.preventDefault());

    const hintBar = element('p', 'hint-bar', HINT_BAR);
    form.appendChild(hintBar);

    nameInput = element('input', 'text-input');
    nameInput.type = 'text';
    nameInput.id = 'project-name';
    nameInput.maxLength = NAME_MAX_LENGTH;
    nameInput.autocomplete = 'off';

    goalInput = element('textarea', 'text-input textarea-input');
    goalInput.id = 'project-goal';
    goalInput.rows = 3;

    scopeInput = element('textarea', 'text-input textarea-input');
    scopeInput.id = 'project-scope';
    scopeInput.rows = 3;

    form.appendChild(buildField('项目名称', nameInput));
    form.appendChild(buildField('项目目标', goalInput));
    form.appendChild(buildField('项目范围', scopeInput));

    const stageSection = element('section', 'stage-section');
    const heading = element('div', 'section-heading');
    heading.appendChild(element('h2', 'section-title', '项目工程阶段定义'));

    // RC-M02-30：主操作为表格上方的「新增阶段」
    const addButton = element('button', 'button button-secondary', '新增阶段');
    addButton.type = 'button';
    addButton.addEventListener('click', () => {
      stageRows.push({ name: '', locked: false });
      renderStageRows();
      const inputs = stageBody.querySelectorAll('.stage-name-input');
      const last = inputs[inputs.length - 1];
      if (last) {
        last.focus();
      }
    });
    heading.appendChild(addButton);
    stageSection.appendChild(heading);

    const table = element('table', 'data-table stage-table');
    const thead = element('thead');
    const headRow = element('tr');
    headRow.appendChild(element('th', 'col-index', '序号'));
    headRow.appendChild(element('th', 'col-name', '阶段名称'));
    headRow.appendChild(element('th', 'col-action', '操作'));
    thead.appendChild(headRow);
    table.appendChild(thead);

    stageBody = element('tbody');
    table.appendChild(stageBody);
    stageSection.appendChild(table);

    // 校验失败的浮动提示（RC-M02-31：不阻断页面其它操作），同时保留行内错误位以供就地反馈
    stageError = element('p', 'field-error');
    stageError.hidden = true;
    stageSection.appendChild(stageError);

    form.appendChild(stageSection);
    body.textContent = '';
    body.appendChild(form);
  }

  function renderStageRows() {
    stageBody.textContent = '';
    stageRows.forEach((row, index) => {
      const tr = element('tr');
      if (row.locked) {
        tr.classList.add('is-locked');
      }
      tr.appendChild(element('td', 'stage-index', indexBadge(index + 1)));

      const nameCell = element('td', 'stage-name');
      if (row.locked) {
        // RC-M02-08：锁定阶段渲染为只读加粗文本，不渲染输入框与删除按钮
        nameCell.appendChild(element('span', 'stage-name-locked', row.name));
      } else {
        const input = element('input', 'text-input stage-name-input');
        input.type = 'text';
        input.value = row.name;
        input.setAttribute('aria-label', `阶段名称 ${index + 1}`);
        input.addEventListener('input', () => {
          row.name = input.value;
        });
        nameCell.appendChild(input);
      }
      tr.appendChild(nameCell);

      const actionCell = element('td', 'stage-action');
      if (!row.locked) {
        // RC-M02-30：行内「删除阶段」；删除即时从表格移除，未保存前不产生服务端变更
        const removeButton = element('button', 'button button-ghost', '删除阶段');
        removeButton.type = 'button';
        removeButton.addEventListener('click', () => {
          stageRows.splice(index, 1);
          renderStageRows();
        });
        actionCell.appendChild(removeButton);
      }
      tr.appendChild(actionCell);
      stageBody.appendChild(tr);
    });
  }

  function applyDetail(detail) {
    const stages = Array.isArray(detail.stages) ? detail.stages : [];
    lockedStages = Array.isArray(detail.lockedStages) ? detail.lockedStages : [];
    stageRows = stages.map((name) => ({ name, locked: lockedStages.includes(name) }));
    if (!form) {
      buildForm();
    }
    nameInput.value = detail.name || '';
    goalInput.value = detail.goal || '';
    scopeInput.value = detail.scope || '';
    renderStageRows();
  }

  /* ---------------------------------------------------------------- 读取 */

  async function load() {
    renderLoading(body, '正在加载项目信息…');
    try {
      // 无项目时由服务端在同一事务内创建默认项目行后返回（RC-M02-20）
      const detail = await http.get('/api/projects/current');
      applyDetail(detail);
      // 读取成功后同步会话态（D-M02-11/P-03）
      setProjectDefinition(detail);
    } catch (error) {
      // RC-M02-25：失败块替换内容区，头部保留，不渲染空白表单、不白屏
      renderFailure(body, `项目信息加载失败：${error.message}`, load);
    }
  }

  /* ---------------------------------------------------------------- 保存 */

  /**
   * 校验失败反馈（RC-M02-31、DD-M02-10）：浮动提示给出文案，且**不阻断**页面其它操作。
   *
   * 同时保留行内错误位，便于在表单上下文内定位问题；行内位不承载交互、不遮挡任何控件。
   */
  function reportValidation(message) {
    showToast(message, 'error');
    if (stageError) {
      stageError.textContent = message;
      stageError.hidden = false;
    }
  }

  function clearStageError() {
    if (stageError) {
      stageError.textContent = '';
      stageError.hidden = true;
    }
  }

  async function save() {
    clearStageError();

    // 前端校验（D-M02-05/C-03）：空白名称就地浮动提示且**不发起请求**；
    // 文案口径与服务端 M02-E001 / M02-E002 保持一致（DEF-M02-14）
    const name = nameInput.value.trim();
    if (!name) {
      reportValidation('项目名称不能为空');
      nameInput.focus();
      return;
    }
    if (name.length > NAME_MAX_LENGTH) {
      reportValidation(`项目名称不能超过 ${NAME_MAX_LENGTH} 个字符`);
      nameInput.focus();
      return;
    }

    // 阶段：丢弃空白行（RC-M02-06 分支c 的前端口径），锁定行按原值提交
    const stages = stageRows
      .map((row) => ({ ...row, name: row.name.trim() }))
      .filter((row) => row.name !== '')
      .map((row) => row.name);

    // 主操作在保存中禁用，防重复提交（D-M02-05/C-01、C-11）
    saveButton.disabled = true;
    const definition = {
      name,
      goal: goalInput.value.trim(),
      scope: scopeInput.value.trim(),
      stages
    };
    try {
      const { projectId } = getState();
      const saved = projectId === null || projectId === undefined
        ? await http.put('/api/projects', definition)
        : await http.put(`/api/projects/${projectId}`, definition);
      applyDetail(saved);
      // 会话态写入必须发生在保存成功之后（D-M02-11/P-04、D-M02-06/P-01）
      setProjectDefinition(saved);
      showToast('项目信息已保存', 'info');
    } catch (error) {
      // RC-M02-26：浮动提示失败原因，不新增本地记录、不改动已填内容、不关闭页面
      showToast(error.message || '保存失败，请稍后重试', 'error');
    } finally {
      saveButton.disabled = false;
    }
  }

  saveButton.addEventListener('click', save);

  load();

  return {
    destroy() {
      container.textContent = '';
    }
  };
}
