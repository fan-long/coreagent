/**
 * 顶栏，见 §3.5.1 与 UI-M01-01。
 *
 * 元素标识与数据绑定严格遵循设计表：.brand-mark / .brand-name / [data-nav] / #current-project /
 * #guide-button / #role-select。角色切换仅更新顶栏文本，不触发任何请求、不改变功能可见性（BR-M01-10）。
 */
import {
  get as getState,
  subscribe as subscribeStore,
  setRole,
  DEFAULT_PROJECT_NAME
} from '../core/store.js';
import {
  navigate,
  current as currentRoute,
  subscribe as subscribeRouter,
  ROUTE_GUIDE,
  ROUTE_WORKBENCH,
  ROUTE_ENV_DEFAULT
} from '../core/router.js';
import { ROLE_OPTIONS } from '../core/nav-config.js';
import { openProjectDialog } from './ProjectDialog.js';

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

/** 一级导航高亮：工作台自身；环境管理与组件功能页同属「环境管理」上下文。 */
function activePrimaryNav(route) {
  if (!route) {
    return null;
  }
  if (route.name === 'workbench') {
    return 'workbench';
  }
  if (route.name === 'env' || route.name === 'component') {
    return 'env';
  }
  return null;
}

/**
 * 挂载顶栏。
 *
 * @param {HTMLElement} container #app-header
 * @returns {{destroy: Function}}
 */
export function mountAppHeader(container) {
  container.textContent = '';

  const brand = element('div', 'brand');
  brand.appendChild(element('span', 'brand-mark', 'CA'));
  brand.appendChild(element('span', 'brand-name', 'Core-Agent 工程平台'));

  const primaryNav = element('nav', 'primary-nav');
  primaryNav.setAttribute('aria-label', '一级导航');
  const workbenchButton = element('button', 'primary-nav-item', '工作台');
  workbenchButton.type = 'button';
  workbenchButton.dataset.nav = 'workbench';
  workbenchButton.addEventListener('click', () => navigate(ROUTE_WORKBENCH));
  const envButton = element('button', 'primary-nav-item', '环境管理');
  envButton.type = 'button';
  envButton.dataset.nav = 'env';
  // CAP-M01-03：点击「环境管理」固定进入项目信息页
  envButton.addEventListener('click', () => navigate(ROUTE_ENV_DEFAULT));
  primaryNav.appendChild(workbenchButton);
  primaryNav.appendChild(envButton);

  const right = element('div', 'header-right');
  const projectButton = element('button', 'current-project');
  projectButton.type = 'button';
  projectButton.id = 'current-project';
  projectButton.title = '切换项目';
  projectButton.addEventListener('click', () => openProjectDialog());

  const guideButton = element('button', 'icon-button');
  guideButton.type = 'button';
  guideButton.id = 'guide-button';
  guideButton.textContent = '?';
  guideButton.title = '平台说明';
  guideButton.setAttribute('aria-label', '平台说明');
  // CAP-M01-07
  guideButton.addEventListener('click', () => navigate(ROUTE_GUIDE));

  const roleSelect = element('select', 'role-select');
  roleSelect.id = 'role-select';
  roleSelect.title = '当前角色（仅用于展示）';
  roleSelect.setAttribute('aria-label', '当前角色');
  roleSelect.addEventListener('change', () => setRole(roleSelect.value));

  right.appendChild(projectButton);
  right.appendChild(guideButton);
  right.appendChild(roleSelect);

  container.appendChild(brand);
  container.appendChild(primaryNav);
  container.appendChild(right);

  let renderedRoleSignature = null;

  function applyRoute(route) {
    const active = activePrimaryNav(route);
    [workbenchButton, envButton].forEach((button) => {
      button.classList.toggle('is-active', button.dataset.nav === active);
    });
  }

  function applyState(state) {
    // 项目数量为 0 时展示默认项目名称（RC-M02-32 分支b，DD-M02-11）；
    // 名称保存成功后由 store 的通知驱动本方法重绘，不重新请求上下文（D-M02-06/C-04）
    projectButton.textContent = state.projectName || DEFAULT_PROJECT_NAME;

    const roles = state.roles && state.roles.length > 0 ? state.roles : ROLE_OPTIONS;
    const signature = roles.map((role) => role.code).join(',');
    if (signature !== renderedRoleSignature) {
      roleSelect.textContent = '';
      roles.forEach((role) => {
        const option = element('option', null, role.name);
        option.value = role.code;
        roleSelect.appendChild(option);
      });
      renderedRoleSignature = signature;
    }
    roleSelect.value = state.role;
  }

  const unsubscribeStore = subscribeStore(applyState);
  const unsubscribeRouter = subscribeRouter(applyRoute);
  applyState(getState());
  applyRoute(currentRoute());

  return {
    destroy() {
      unsubscribeStore();
      unsubscribeRouter();
      container.textContent = '';
    }
  };
}
