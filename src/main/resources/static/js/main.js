/**
 * 启动入口，见 §3.1、§3.6 时序 1。
 *
 * 启动顺序：
 * 1. 读取 localStorage 中的项目／角色，先渲染顶栏，避免首屏空白；
 * 2. 挂载顶栏与二级导航；
 * 3. 订阅路由，按路由卸载旧视图、挂载新视图（BR-M01-11，主区域同时仅一个功能页面）；
 * 4. 启动路由（无 hash 时回退 #/workbench）；
 * 5. 非阻塞装载视觉主题令牌（ft-FT-002 §4.2），与请求 GET /api/context 并行；
 *    首帧已由 tokens.css 静态基线着色，故此步失败不影响渲染与业务功能。
 */
import { mountAppHeader } from './layout/AppHeader.js';
import { mountSideNav } from './layout/SideNav.js';
import { mountWorkbench } from './pages/Workbench.js';
import { mountPlatformGuide } from './pages/PlatformGuide.js';
import { mountProjectInfo } from './pages/ProjectInfo.js';
import { mountAppType } from './pages/AppType.js';
import { mountPlaceholder } from './pages/Placeholder.js';
import { start as startRouter, subscribe as subscribeRouter } from './core/router.js';
import {
  get as getState,
  set as setState,
  readPersistedContext,
  setProjects,
  setCurrentProject
} from './core/store.js';
import { http } from './core/http.js';
import { showToast, renderFailure } from './core/notice.js';
import { loadTheme } from './core/theme.js';

const headerContainer = document.getElementById('app-header');
const sideNavContainer = document.getElementById('side-nav');
const mainView = document.getElementById('main-view');

let currentView = null;

/**
 * 环境管理页分派（D-M02-11/C-01、D-M04-12/C-01）：已实现的页按 pageKey 挂载对应组件，
 * 其余 pageKey 维持既有的占位页渲染。
 */
function mountEnvPage(route) {
  switch (route.params.pageKey) {
    case 'project':
      return mountProjectInfo(mainView);
    case 'app-type':
      return mountAppType(mainView);
    default:
      return mountPlaceholder(mainView, route);
  }
}

/** 卸载当前视图后再挂载新视图，保证同一时刻仅展示一个功能页面（BR-M01-11）。 */
function mountView(route) {
  if (currentView && typeof currentView.destroy === 'function') {
    currentView.destroy();
  }
  currentView = null;
  mainView.textContent = '';

  switch (route.name) {
    case 'workbench':
      currentView = mountWorkbench(mainView);
      break;
    case 'guide':
      currentView = mountPlatformGuide(mainView);
      break;
    case 'env':
      // 环境管理按 pageKey 细分：项目信息页与应用类型页已实现，其余 9 页继续由占位页承载
      // （D-M02-11/C-01、D-M04-12/C-01）
      currentView = mountEnvPage(route);
      break;
    case 'component':
      currentView = mountPlaceholder(mainView, route);
      break;
    default:
      currentView = mountWorkbench(mainView);
      break;
  }
}

/** 校正项目上下文：请求头缺失／非法或项目已删除时，后端回退默认项目并在此同步到本地状态。 */
async function loadContext() {
  try {
    const context = await http.get('/api/context');
    if (!context) {
      return;
    }
    // 角色为前端会话态（BR-M01-10）：仅取角色清单做下拉选项，不覆盖用户已选角色
    setState({ roles: context.roles || [] });
    setProjects(context.projects || []);
    if (context.currentProject) {
      setCurrentProject(
        context.currentProject.id,
        context.currentProject.name,
        context.currentProject.stages
      );
    }
  } catch (error) {
    showToast(`上下文加载失败：${error.message}`, 'warn');
  }
}

function bootstrap() {
  const persisted = readPersistedContext();
  setState({ projectId: persisted.projectId, role: persisted.role });

  mountAppHeader(headerContainer);
  mountSideNav(sideNavContainer);

  subscribeRouter(mountView);
  startRouter();

  // 非阻塞：不 await，也不参与 mountView 的路由流程——令牌是全局视觉配置，与视图生命周期无关（§4.2）
  loadTheme();
  loadContext();
}

if (headerContainer && sideNavContainer && mainView) {
  try {
    bootstrap();
  } catch (error) {
    renderFailure(mainView, `应用启动失败：${error.message}`, () => window.location.reload());
  }
} else {
  showToast('页面骨架缺失，应用无法启动', 'error');
}

// 供端到端排查使用：当前会话态只读快照
window.__caState = getState;
