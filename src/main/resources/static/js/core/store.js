/**
 * 全局上下文状态与订阅（§3.3）。
 *
 * 当前项目／当前组件／当前工程仓库／当前角色均为前端会话态（§2.4）：后端不持有用户状态，上下文通过
 * 请求头或 URL 显式传递（DP-M01-05）。本模块是唯一写入点，其余模块只读 get() 并订阅变更。
 */
import { navigate } from './router.js';

const STORAGE_KEYS = {
  projectId: 'ca.currentProjectId',
  repositoryId: 'ca.currentRepositoryId',
  role: 'ca.currentRole'
};

export const DEFAULT_ROLE = 'architect';

/**
 * 默认项目名称，与后端 DEF-M02-12 同源取值（DD-M02-11）。
 *
 * 仅用于「会话态中没有任何项目」时的顶栏展示回落（RC-M02-32 分支b），不改变项目数量、
 * 不触发创建——自动创建默认项目行由读取接口在服务端完成（RC-M02-20）。
 */
export const DEFAULT_PROJECT_NAME = 'AI新一代核心系统';

const state = {
  // 当前项目
  projectId: null,
  projectName: null,
  // 项目定义字段（D-M02-11/C-02）：只增不改，既有读取方只取 projectName 与 stages
  projectGoal: null,
  projectScope: null,
  projects: [],
  stages: [],
  // 当前组件与工程仓库
  componentId: null,
  componentNav: null,
  repositoryId: null,
  // 角色与视图
  role: DEFAULT_ROLE,
  roles: [],
  view: 'workbench',
  route: null
};

const listeners = new Set();

export function get() {
  return { ...state };
}

/** 合并更新状态并通知订阅者。 */
export function set(patch) {
  Object.assign(state, patch);
  notify();
}

export function subscribe(listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function notify() {
  const snapshot = get();
  listeners.forEach((listener) => listener(snapshot));
}

/* ------------------------------------------------------------------ 本地存储 */

function readStorage(key) {
  try {
    return window.localStorage.getItem(key);
  } catch (error) {
    return null;
  }
}

function writeStorage(key, value) {
  try {
    window.localStorage.setItem(key, value);
  } catch (error) {
    // 隐私模式等场景写入失败不影响运行，上下文退化为单次会话内有效
  }
}

/** 读取本地存储中的项目／角色，供启动时先于接口返回渲染顶栏。 */
export function readPersistedContext() {
  const rawProjectId = readStorage(STORAGE_KEYS.projectId);
  const projectId = rawProjectId === null ? null : Number.parseInt(rawProjectId, 10);
  return {
    projectId: Number.isNaN(projectId) ? null : projectId,
    role: readStorage(STORAGE_KEYS.role) || DEFAULT_ROLE
  };
}

/** 当前工程仓库按组件维度存 map，切换组件后各自记住上次选择的工程（§2.4）。 */
function readRepositoryMap() {
  const raw = readStorage(STORAGE_KEYS.repositoryId);
  if (!raw) {
    return {};
  }
  try {
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch (error) {
    return {};
  }
}

export function getStoredRepositoryId(componentId) {
  if (!componentId) {
    return null;
  }
  return readRepositoryMap()[componentId] || null;
}

function persistRepositoryId(componentId, repositoryId) {
  if (!componentId || !repositoryId) {
    return;
  }
  const map = readRepositoryMap();
  map[componentId] = repositoryId;
  writeStorage(STORAGE_KEYS.repositoryId, JSON.stringify(map));
}

/* ------------------------------------------------------------------ 变更动作 */

/**
 * 切换当前项目：写 localStorage、清空组件与工程上下文，并回到工作台（§3.3）。
 * 项目变更后顶栏、工作台与环境管理各页面按新项目重载。
 */
export function setProject(projectId) {
  const projects = state.projects || [];
  const matched = projects.find((project) => project.id === projectId);
  writeStorage(STORAGE_KEYS.projectId, String(projectId));
  set({
    projectId,
    projectName: matched ? matched.name : state.projectName,
    // 项目定义字段属于「某个项目」，切换项目后必须清空，避免上一项目的目标与范围残留
    projectGoal: null,
    projectScope: null,
    stages: [],
    componentId: null,
    componentNav: null,
    repositoryId: null
  });
  navigate('#/workbench');
}

/** 项目重命名后同步顶栏与列表（CAP-M01-06）。 */
export function renameProject(projectId, name) {
  const projects = (state.projects || []).map((project) =>
    project.id === projectId ? { ...project, name } : project);
  set({
    projects,
    projectName: state.projectId === projectId ? name : state.projectName
  });
}

export function setProjects(projects) {
  set({ projects });
}

export function setCurrentProject(projectId, projectName, stages) {
  writeStorage(STORAGE_KEYS.projectId, String(projectId));
  set({ projectId, projectName, stages: stages || [] });
}

/**
 * 写入项目定义字段（D-M02-11/C-02、C-03）：名称、目标、范围与阶段。
 *
 * 调用时机只有两个——项目信息页读取成功与保存成功；**保存失败路径不得调用**，否则会出现
 * 「提示失败但顶栏已变」的不一致（D-M02-06/4.4、D-M02-11/P-05）。
 *
 * 同时同步 `projects` 列表中的同名项（若存在），使顶栏、页面表单与项目弹窗列表三处名称一致
 * （D-M02-06/4.4 状态一致性）。
 *
 * @param {{id:number, name:string, goal:(string|null), scope:(string|null), stages:string[]}} definition
 */
export function setProjectDefinition(definition) {
  if (!definition || definition.id === null || definition.id === undefined) {
    return;
  }
  const name = definition.name || state.projectName;
  const projects = (state.projects || []).map((project) =>
    project.id === definition.id ? { ...project, name } : project);
  writeStorage(STORAGE_KEYS.projectId, String(definition.id));
  set({
    projectId: definition.id,
    projectName: name,
    projectGoal: definition.goal === undefined ? null : definition.goal,
    projectScope: definition.scope === undefined ? null : definition.scope,
    stages: definition.stages || [],
    projects
  });
}

/**
 * 切换当前工程仓库：写 localStorage 并失效导航缓存（BR-M01-12）。
 * 导航结构与工程无关，但「服务编排全景／规则库全景」的可用性依赖当前工程的框架能力，
 * 故缓存失效后由 SideNav 重新拉取（§3.6 时序 3 注）。
 */
export function setRepository(repositoryId) {
  persistRepositoryId(state.componentId, repositoryId);
  set({ repositoryId, componentNav: null });
}

/** 切换组件：重新拉取该组件的二级导航并默认选中其默认工程。 */
export function setComponent(componentId) {
  if (state.componentId === componentId && state.componentNav) {
    return;
  }
  set({ componentId, componentNav: null, repositoryId: getStoredRepositoryId(componentId) });
}

/** 角色仅用于展示，不产生权限效果，因此只写状态与本地存储、不触发任何请求（BR-M01-10）。 */
export function setRole(code) {
  writeStorage(STORAGE_KEYS.role, code);
  set({ role: code });
}
