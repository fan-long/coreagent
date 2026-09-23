/**
 * hash 路由与视图挂载（§3.2、§3.4）。
 *
 * 路由表与详细设计 §3.2 一一对应；未匹配或缺失 hash 时回退工作台。同一时刻仅展示一个功能页面由本模块
 * 保证：每次跳转先由订阅者卸载当前视图（调用其 destroy），再挂载新视图（BR-M01-11）。
 */

export const ROUTE_WORKBENCH = '#/workbench';
export const ROUTE_GUIDE = '#/guide';
export const ROUTE_ENV_DEFAULT = '#/env/project';

const DEFAULT_ROUTE = '#/workbench';

const listeners = new Set();
let currentRoute = null;

/**
 * 解析 hash 为路由对象。
 *
 * @returns {{name: string, path: string, params: Object, query: Object, hash: string}}
 *          name 取值：workbench | env | component | guide
 */
export function parse(hash) {
  const raw = (hash || '').replace(/^#/, '') || '/workbench';
  const separatorIndex = raw.indexOf('?');
  const path = separatorIndex === -1 ? raw : raw.slice(0, separatorIndex);
  const queryString = separatorIndex === -1 ? '' : raw.slice(separatorIndex + 1);
  const query = Object.fromEntries(new URLSearchParams(queryString));

  const segments = path.split('/').filter(Boolean);
  const route = { name: 'workbench', path, params: {}, query, hash: `#${raw}` };

  if (segments[0] === 'workbench') {
    route.name = 'workbench';
  } else if (segments[0] === 'guide') {
    route.name = 'guide';
  } else if (segments[0] === 'env' && segments[1]) {
    route.name = 'env';
    route.params.pageKey = segments[1];
  } else if (segments[0] === 'component' && segments[1] && segments[2]) {
    route.name = 'component';
    route.params.componentId = segments[1];
    route.params.menuKey = segments[2];
  }
  return route;
}

export function current() {
  return currentRoute;
}

/** 跳转；目标与当前一致时手动派发一次，保证重复点击也能触发视图重载。 */
export function navigate(target) {
  const hash = target && target.startsWith('#') ? target : `#${target || ''}`;
  if (window.location.hash === hash) {
    dispatch();
    return;
  }
  window.location.hash = hash;
}

function dispatch() {
  const route = parse(window.location.hash);
  currentRoute = route;
  listeners.forEach((listener) => listener(route));
}

export function subscribe(listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/** 启动路由：无 hash 时先补默认路由，避免首屏空转。 */
export function start() {
  if (!window.location.hash) {
    window.location.hash = DEFAULT_ROUTE;
  }
  window.addEventListener('hashchange', dispatch);
  dispatch();
}

/** 构造组件功能页路由，供工作台卡片与侧边栏复用。 */
export function componentRoute(componentId, menuKey, repositoryId) {
  const base = `#/component/${encodeURIComponent(componentId)}/${encodeURIComponent(menuKey)}`;
  return repositoryId ? `${base}?repositoryId=${encodeURIComponent(repositoryId)}` : base;
}
