/**
 * 二级导航，见 §3.4、§3.5.3（静态分支）与 §3.5.4（动态分支）。
 *
 * 静态分支：环境管理 4 分组 11 项，来自 nav-config.js 常量，不请求服务端（BR-M01-03）。
 * 动态分支：组件信息侧边栏，由 API-M01-03 下发结构、可用性与角标，前端只渲染不判断（DP-M01-03）。
 *
 * 挂载策略：工作台与平台说明页不展示二级导航（§3.2）；组件切换后重新拉取导航，默认选中组件默认工程；
 * 工程仓库切换后缓存失效并重新拉取（§3.6 时序 3 注）。
 */
import {
  get as getState,
  set as setState,
  subscribe as subscribeStore,
  setComponent,
  setRepository
} from '../core/store.js';
import {
  navigate,
  current as currentRoute,
  subscribe as subscribeRouter,
  componentRoute,
  ROUTE_WORKBENCH
} from '../core/router.js';
import { ENV_NAV_GROUPS } from '../core/nav-config.js';
import { http, withQuery } from '../core/http.js';
import { createNavItem } from '../components/NavItem.js';
import { showToast, renderFailure, renderLoading } from '../core/notice.js';

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

/** 分组标题；functions 分组按设计不下发名称（name 为 null），此时不渲染标题行。 */
function buildGroupHeading(name) {
  return name ? element('p', 'nav-group-name', name) : null;
}

/** 以描述符中的 route 为准，追加当前工程仓库查询参数（BR-M01-12）；route 缺失时按约定拼接兜底。 */
function withRepository(route, componentId, menuKey, repositoryId) {
  const base = route || componentRoute(componentId, menuKey, null);
  const separator = base.includes('?') ? '&' : '?';
  return repositoryId ? `${base}${separator}repositoryId=${encodeURIComponent(repositoryId)}` : base;
}

/**
 * 挂载二级导航容器。
 *
 * @param {HTMLElement} container #side-nav
 * @returns {{destroy: Function}}
 */
export function mountSideNav(container) {
  let lastRenderKey = null;
  let loadToken = 0;
  let destroyed = false;

  function clearContainer() {
    container.textContent = '';
    container.hidden = true;
    container.dataset.mode = '';
  }

  /* -------------------------------------------------------------- 静态分支 */

  function renderEnv(pageKey) {
    container.hidden = false;
    container.dataset.mode = 'env';
    container.textContent = '';

    const nav = element('nav', 'side-nav-inner');
    nav.setAttribute('aria-label', '环境管理二级导航');
    ENV_NAV_GROUPS.forEach((group) => {
      const groupNode = element('div', 'nav-group');
      const heading = buildGroupHeading(group.name);
      if (heading) {
        groupNode.appendChild(heading);
      }
      const list = element('ul', 'nav-list');
      group.items.forEach((item) => {
        list.appendChild(createNavItem(
          { key: item.key, name: item.name, enabled: true },
          { activeKey: pageKey, onSelect: () => navigate(item.route) }
        ));
      });
      groupNode.appendChild(list);
      nav.appendChild(groupNode);
    });
    container.appendChild(nav);
  }

  /* -------------------------------------------------------------- 动态分支 */

  function renderComponentNav(componentNav, repositoryId) {
    container.hidden = false;
    container.dataset.mode = 'component';
    container.textContent = '';

    const component = componentNav.component || {};
    const repositories = componentNav.repositories || [];
    const route = currentRoute();
    const menuKey = route && route.params ? route.params.menuKey : null;

    const nav = element('nav', 'side-nav-inner');
    nav.setAttribute('aria-label', '组件信息二级导航');

    const head = element('div', 'side-component');
    const icon = element('span', 'card-icon', component.name ? component.name.slice(0, 1) : '组');
    if (component.icon) {
      icon.dataset.icon = component.icon;
    }
    const headText = element('div', 'side-component-text');
    headText.appendChild(element('span', 'side-component-name', component.name || ''));
    headText.appendChild(element('span', 'side-component-meta', `${repositories.length} 个工程仓库`));
    head.appendChild(icon);
    head.appendChild(headText);
    nav.appendChild(head);

    // 工程仓库数为 0 时不渲染选择器，仅渲染组件级菜单（BR-M01-04）
    if (repositories.length > 0) {
      const select = element('select', 'repo-select');
      select.id = 'repo-select';
      select.setAttribute('aria-label', '当前工程仓库');
      repositories.forEach((repository) => {
        const option = element('option', null,
          repository.version ? `${repository.name}（${repository.version}）` : repository.name);
        option.value = String(repository.id);
        select.appendChild(option);
      });
      select.value = String(repositoryId);
      select.addEventListener('change', () => {
        // BR-M01-12：保留当前 menuKey，追加 repositoryId 查询参数
        const selected = select.value;
        setRepository(selected);
        const currentMenuKey = currentRoute() && currentRoute().params
          ? currentRoute().params.menuKey
          : null;
        navigate(componentRoute(component.id, currentMenuKey || 'task', selected));
      });
      nav.appendChild(select);
    }

    (componentNav.groups || []).forEach((group) => {
      const groupNode = element('div', 'nav-group');
      const heading = buildGroupHeading(group.name);
      if (heading) {
        groupNode.appendChild(heading);
      }
      const list = element('ul', 'nav-list');
      (group.items || []).forEach((item) => {
        list.appendChild(createNavItem(item, {
          activeKey: menuKey,
          onSelect: (selected) => {
            // 菜单以描述符下发（DP-M01-04）：优先使用后端 route，仅追加工程维度参数（BR-M01-12）
            navigate(withRepository(selected.route, component.id, selected.key, repositoryId));
          }
        }));
      });
      groupNode.appendChild(list);
      nav.appendChild(groupNode);
    });

    container.appendChild(nav);
  }

  async function loadComponentNav(componentId, repositoryId) {
    const token = ++loadToken;
    container.hidden = false;
    container.dataset.mode = 'component';
    renderLoading(container, '正在加载组件导航…');
    try {
      const componentNav = await http.get(withQuery('/api/navigation/component', {
        componentId,
        repositoryId
      }));
      if (destroyed || token !== loadToken) {
        return;
      }
      // 同步会话态：默认选中后端判定出的当前工程（组件默认工程）
      setState({
        componentNav,
        repositoryId: componentNav.currentRepositoryId === null
          ? null
          : String(componentNav.currentRepositoryId)
      });
      renderComponentNav(componentNav, getState().repositoryId);
    } catch (error) {
      if (destroyed || token !== loadToken) {
        return;
      }
      // M01-E004：组件不存在，提示后返回工作台
      if (error.code === 'M01-E004') {
        showToast(error.message || '组件不存在', 'error');
        navigate(ROUTE_WORKBENCH);
        return;
      }
      // M01-E005：工程仓库非法，回退该组件默认工程并提示
      if (error.code === 'M01-E005' && repositoryId) {
        showToast('当前工程仓库不可用，已回退到默认工程', 'warn');
        loadComponentNav(componentId, null);
        return;
      }
      renderFailure(container, error.message || '导航加载失败，请稍后重试', () => {
        loadComponentNav(componentId, repositoryId);
      });
    }
  }

  /* ------------------------------------------------------------ 渲染调度 */

  function render() {
    const route = currentRoute();
    if (!route) {
      return;
    }

    if (route.name === 'env') {
      const key = `env|${route.params.pageKey}`;
      if (key !== lastRenderKey) {
        lastRenderKey = key;
        renderEnv(route.params.pageKey);
      }
      return;
    }

    if (route.name === 'component') {
      const componentId = route.params.componentId;
      const state = getState();

      // 组件变更：重置组件上下文（内部按组件维度恢复上次选择的工程）
      if (state.componentId !== componentId) {
        setComponent(componentId);
      }

      const stored = getState();
      const repositoryId = route.query.repositoryId
        || (stored.repositoryId === null || stored.repositoryId === undefined
          ? null
          : String(stored.repositoryId));

      if (stored.componentNav) {
        const key = `component|${componentId}|${repositoryId}|cached`;
        if (key !== lastRenderKey) {
          lastRenderKey = key;
          renderComponentNav(stored.componentNav, repositoryId);
        }
        return;
      }

      const key = `component|${componentId}|${repositoryId}`;
      if (key !== lastRenderKey) {
        lastRenderKey = key;
        loadComponentNav(componentId, repositoryId);
      }
      return;
    }

    // 工作台与平台说明页不展示二级导航（§3.2）
    if (lastRenderKey !== 'none') {
      lastRenderKey = 'none';
      loadToken += 1;
      clearContainer();
    }
  }

  const unsubscribeRouter = subscribeRouter(render);
  const unsubscribeStore = subscribeStore(render);
  render();

  return {
    destroy() {
      destroyed = true;
      loadToken += 1;
      unsubscribeRouter();
      unsubscribeStore();
      container.textContent = '';
    }
  };
}
