/**
 * 工作台，见 §3.5.2 与 UI-M01-02。
 *
 * 页面结构（标题 + 管理组件按钮）常驻，仅卡片区随加载状态切换，保证异常时页面不空白
 * （DP-M01-06、M01-E006）。当前项目变更时按新项目重载（§3.3 状态变更传播规则）。
 */
import { get as getState, subscribe as subscribeStore } from '../core/store.js';
import { navigate, componentRoute } from '../core/router.js';
import { http } from '../core/http.js';
import { createComponentCard } from '../components/ComponentCard.js';
import { renderFailure, renderLoading } from '../core/notice.js';

/** 点击卡片进入的默认功能页：任务管理（§3.6 时序 2）。 */
const DEFAULT_MENU_KEY = 'task';

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

/**
 * 挂载工作台页面。
 *
 * @param {HTMLElement} container #main-view
 * @returns {{destroy: Function}}
 */
export function mountWorkbench(container) {
  container.textContent = '';

  const page = element('section', 'page');
  const header = element('header', 'page-header');
  const titleArea = element('div', 'page-title-area');
  const title = element('h1', 'page-title', '工作台');
  const subtitle = element('p', 'page-subtitle', '应用组件');
  titleArea.appendChild(title);
  titleArea.appendChild(subtitle);

  const actions = element('div', 'page-actions');
  const manageButton = element('button', 'button button-primary', '管理组件');
  manageButton.type = 'button';
  // CAP-M01-02：跳转应用组件管理
  manageButton.addEventListener('click', () => navigate('#/env/component'));
  actions.appendChild(manageButton);

  header.appendChild(titleArea);
  header.appendChild(actions);
  page.appendChild(header);

  const cardArea = element('div', 'card-area');
  page.appendChild(cardArea);
  container.appendChild(page);

  let loadToken = 0;
  let destroyed = false;
  let loadedProjectId = null;

  function renderCards(components, projectName) {
    cardArea.textContent = '';
    if (!components || components.length === 0) {
      cardArea.appendChild(
        element('p', 'empty-state', '当前项目暂无应用组件，点击「管理组件」添加')
      );
      return;
    }
    const grid = element('div', 'component-grid');
    components.forEach((card) => {
      grid.appendChild(createComponentCard(card, {
        onOpen: () => {
          const state = getState();
          navigate(componentRoute(card.id, DEFAULT_MENU_KEY, state.repositoryId));
        }
      }));
    });
    cardArea.appendChild(grid);
    if (projectName) {
      cardArea.dataset.projectName = projectName;
    }
  }

  async function load() {
    const token = ++loadToken;
    const state = getState();
    loadedProjectId = state.projectId;
    title.textContent = `${state.projectName || '未选择项目'} / 工作台`;
    renderLoading(cardArea, '正在加载组件…', 6);
    try {
      const payload = await http.get('/api/workbench/components');
      if (destroyed || token !== loadToken) {
        return;
      }
      title.textContent = `${(payload && payload.projectName) || state.projectName || '未选择项目'} / 工作台`;
      renderCards(payload ? payload.components : [], payload ? payload.projectName : null);
    } catch (error) {
      if (destroyed || token !== loadToken) {
        return;
      }
      renderFailure(cardArea, error.message || '工作台加载失败，请稍后重试', load);
    }
  }

  // 项目变更后按新项目重载；其余状态变更（角色等）不影响本页
  const unsubscribeStore = subscribeStore(() => {
    if (getState().projectId !== loadedProjectId) {
      load();
    }
  });

  load();

  return {
    destroy() {
      destroyed = true;
      loadToken += 1;
      unsubscribeStore();
      container.textContent = '';
    }
  };
}
