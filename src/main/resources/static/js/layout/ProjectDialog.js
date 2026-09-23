/**
 * 项目切换弹窗，见 §3.5.6 与 UI-M01-06。
 *
 * 交互规则：
 * - 点击行首切换按钮 → store.setProject(id)，关闭弹窗（CAP-M01-06）；
 * - 当前行渲染「当前」徽标而不渲染切换按钮；
 * - 编辑保存调用 PUT /api/projects/{id}，名称非法（M01-E001／M01-E002）时保留编辑态并内联提示；
 * - 新增项目调用 POST /api/projects，成功后插入列表。
 */
import {
  get as getState,
  setProjects,
  renameProject,
  setProject
} from '../core/store.js';
import { http } from '../core/http.js';
import { showToast } from '../core/notice.js';

const MAX_NAME_LENGTH = 128;

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

let activeDialog = null;

/**
 * 打开项目切换弹窗；已打开时先关闭，避免叠加。
 */
export function openProjectDialog() {
  if (activeDialog) {
    activeDialog.close();
  }

  const overlay = element('div', 'modal-overlay');
  const modal = element('div', 'modal');
  modal.setAttribute('role', 'dialog');
  modal.setAttribute('aria-modal', 'true');
  modal.setAttribute('aria-labelledby', 'project-dialog-title');

  const head = element('header', 'modal-head');
  const title = element('h2', 'modal-title', '项目列表');
  title.id = 'project-dialog-title';
  const closeButton = element('button', 'modal-close', '×');
  closeButton.type = 'button';
  closeButton.title = '关闭';
  closeButton.setAttribute('aria-label', '关闭');
  head.appendChild(title);
  head.appendChild(closeButton);

  const desc = element('p', 'modal-desc', '点击行首按钮切换当前项目；点击编辑后可修改项目名称');
  const body = element('div', 'modal-body');
  const foot = element('footer', 'modal-foot');

  modal.appendChild(head);
  modal.appendChild(desc);
  modal.appendChild(body);
  modal.appendChild(foot);
  overlay.appendChild(modal);
  document.getElementById('dialog-root').appendChild(overlay);

  let editingId = null;
  let creating = false;
  let inlineError = '';
  let closed = false;

  function close() {
    if (closed) {
      return;
    }
    closed = true;
    document.removeEventListener('keydown', onKeydown);
    overlay.remove();
    if (activeDialog === instance) {
      activeDialog = null;
    }
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

  /* ---------------------------------------------------------------- 列表渲染 */

  function currentProjects() {
    return getState().projects || [];
  }

  function buildRow(project) {
    const row = element('div', 'project-row');
    const currentId = getState().projectId;
    const isCurrent = project.id === currentId;

    if (isCurrent) {
      row.classList.add('is-current');
      row.appendChild(element('span', 'badge-current', '当前'));
    } else {
      const switchButton = element('button', 'button button-secondary', '切换');
      switchButton.type = 'button';
      switchButton.addEventListener('click', () => {
        // CAP-M01-06：切项目为纯前端动作，仅写 localStorage 并回工作台
        setProject(project.id);
        close();
      });
      row.appendChild(switchButton);
    }

    if (editingId === project.id) {
      const form = element('div', 'project-edit');
      const input = element('input', 'text-input');
      input.type = 'text';
      input.value = project.name;
      input.maxLength = MAX_NAME_LENGTH;
      input.setAttribute('aria-label', '项目名称');

      const saveButton = element('button', 'button button-primary', '保存');
      saveButton.type = 'button';
      const cancelButton = element('button', 'button button-ghost', '取消');
      cancelButton.type = 'button';

      const error = element('p', 'field-error', inlineError);
      error.hidden = !inlineError;

      saveButton.addEventListener('click', async () => {
        const name = input.value.trim();
        if (!name) {
          inlineError = '项目名称不能为空';
          error.textContent = inlineError;
          error.hidden = false;
          return;
        }
        if (name.length > MAX_NAME_LENGTH) {
          inlineError = `项目名称不能超过 ${MAX_NAME_LENGTH} 个字符`;
          error.textContent = inlineError;
          error.hidden = false;
          return;
        }
        saveButton.disabled = true;
        try {
          const updated = await http.put(`/api/projects/${project.id}`, { name });
          renameProject(project.id, (updated && updated.name) || name);
          editingId = null;
          inlineError = '';
          render();
        } catch (requestError) {
          // 名称非法（M01-E001／M01-E002）保留编辑态并内联提示，不关闭弹窗
          inlineError = requestError.message || '保存失败，请稍后重试';
          error.textContent = inlineError;
          error.hidden = false;
          saveButton.disabled = false;
        }
      });

      cancelButton.addEventListener('click', () => {
        editingId = null;
        inlineError = '';
        render();
      });

      input.addEventListener('keydown', (event) => {
        if (event.key === 'Enter') {
          saveButton.click();
        }
      });

      form.appendChild(input);
      form.appendChild(saveButton);
      form.appendChild(cancelButton);
      form.appendChild(error);
      row.appendChild(form);
      window.setTimeout(() => input.focus(), 0);
    } else {
      row.appendChild(element('span', 'project-name', project.name));
      const editButton = element('button', 'button button-ghost', '编辑');
      editButton.type = 'button';
      editButton.addEventListener('click', () => {
        editingId = project.id;
        creating = false;
        inlineError = '';
        render();
      });
      row.appendChild(editButton);
    }
    return row;
  }

  function buildCreateForm() {
    const form = element('div', 'project-create');
    const input = element('input', 'text-input');
    input.type = 'text';
    input.placeholder = '请输入项目名称';
    input.maxLength = MAX_NAME_LENGTH;
    input.setAttribute('aria-label', '新项目名称');

    const confirmButton = element('button', 'button button-primary', '确定');
    confirmButton.type = 'button';
    const cancelButton = element('button', 'button button-ghost', '取消');
    cancelButton.type = 'button';

    const error = element('p', 'field-error', inlineError);
    error.hidden = !inlineError;

    confirmButton.addEventListener('click', async () => {
      const name = input.value.trim();
      if (!name) {
        inlineError = '项目名称不能为空';
        error.textContent = inlineError;
        error.hidden = false;
        return;
      }
      confirmButton.disabled = true;
      try {
        const created = await http.post('/api/projects', { name });
        if (created) {
          setProjects([...currentProjects(), created]);
        }
        creating = false;
        inlineError = '';
        showToast('项目已创建', 'info');
        render();
      } catch (requestError) {
        // M01-E002：同名项目已存在，保留输入态
        inlineError = requestError.message || '创建失败，请稍后重试';
        error.textContent = inlineError;
        error.hidden = false;
        confirmButton.disabled = false;
      }
    });

    cancelButton.addEventListener('click', () => {
      creating = false;
      inlineError = '';
      render();
    });

    input.addEventListener('keydown', (event) => {
      if (event.key === 'Enter') {
        confirmButton.click();
      }
    });

    form.appendChild(input);
    form.appendChild(confirmButton);
    form.appendChild(cancelButton);
    form.appendChild(error);
    window.setTimeout(() => input.focus(), 0);
    return form;
  }

  function render() {
    body.textContent = '';
    const projects = currentProjects();
    if (projects.length === 0) {
      body.appendChild(element('p', 'empty-state', '暂无项目，点击下方「新增项目」添加'));
    } else {
      projects.forEach((project) => body.appendChild(buildRow(project)));
    }

    foot.textContent = '';
    if (creating) {
      foot.appendChild(buildCreateForm());
    } else {
      const createButton = element('button', 'button button-primary', '新增项目');
      createButton.type = 'button';
      createButton.addEventListener('click', () => {
        creating = true;
        editingId = null;
        inlineError = '';
        render();
      });
      foot.appendChild(createButton);
    }
  }

  /** 打开时拉取最新项目列表；失败则沿用缓存，保证弹窗可用（§3.7 不空白）。 */
  async function refresh() {
    try {
      const projects = await http.get('/api/projects');
      if (!closed) {
        setProjects(projects);
      }
    } catch (error) {
      showToast('项目列表加载失败，展示本地缓存', 'warn');
    }
    if (!closed) {
      render();
    }
  }

  const instance = { close };
  activeDialog = instance;

  render();
  refresh();
  return instance;
}
