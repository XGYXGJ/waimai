// axios 封装：自动带 token、token 过期判断、refresh 自动续期、401 跳登录、统一解包 R 结构
import axios, { type AxiosRequestConfig } from 'axios';
import type { R } from './types';

const TOKEN_KEY = 'waimai_token';
const REFRESH_KEY = 'waimai_refresh_token';
const REDIRECT_KEY = 'waimai_redirect';

export const getToken = () => localStorage.getItem(TOKEN_KEY);
export const setToken = (t: string) => localStorage.setItem(TOKEN_KEY, t);
export const clearToken = () => localStorage.removeItem(TOKEN_KEY);

export const getRefreshToken = () => localStorage.getItem(REFRESH_KEY);
export const setRefreshToken = (t: string) => localStorage.setItem(REFRESH_KEY, t);
export const clearRefreshToken = () => localStorage.removeItem(REFRESH_KEY);

/** 登录成功后同时保存 access + refresh token */
export const setAuth = (accessToken: string, refreshToken?: string) => {
  setToken(accessToken);
  if (refreshToken) setRefreshToken(refreshToken);
};

export const clearAuth = () => {
  clearToken();
  clearRefreshToken();
};

export const getRedirect = () => localStorage.getItem(REDIRECT_KEY);
export const setRedirect = (path: string) => localStorage.setItem(REDIRECT_KEY, path);
export const clearRedirect = () => localStorage.removeItem(REDIRECT_KEY);

/** 解析 JWT payload，返回 { exp, ... }；解析失败返回 null */
export function parseJwt(token: string | null): { exp?: number } | null {
  if (!token) return null;
  try {
    const part = token.split('.')[1];
    if (!part) return null;
    const base64 = part.replace(/-/g, '+').replace(/_/g, '/');
    const json = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    );
    return JSON.parse(json);
  } catch {
    return null;
  }
}

/** token 是否已过期（无 token 视为已过期） */
export function isTokenExpired(token: string | null): boolean {
  const payload = parseJwt(token);
  if (!payload || !payload.exp) return true;
  return payload.exp * 1000 <= Date.now();
}

/** access token 剩余有效期不足该值时提前续期，避免「用着用着突然 401」 */
const REFRESH_SKEW_MS = 5 * 60 * 1000;

/** token 是否即将过期（剩余不足 skewMs；无 token 视为即将过期） */
export function isTokenExpiringSoon(token: string | null, skewMs = REFRESH_SKEW_MS): boolean {
  const payload = parseJwt(token);
  if (!payload || !payload.exp) return true;
  return payload.exp * 1000 - Date.now() <= skewMs;
}

/** refresh token 是否仍在有效期内（决定还能不能静默续期） */
export function isRefreshValid(): boolean {
  const rt = getRefreshToken();
  if (!rt) return false;
  const payload = parseJwt(rt);
  return !!payload?.exp && payload.exp * 1000 > Date.now();
}

/**
 * 本地会话是否仍然可用：access token 未过期，或 refresh token 尚未过期（可由拦截器静默续期）。
 * 路由守卫必须用它 —— 只判断 access token 会在 2 小时后误杀 7/30 天的登录态，
 * 并且 clearAuth() 会把 refresh token 一起销毁，导致永远无法续期。
 */
export function hasValidSession(): boolean {
  const token = getToken();
  if (token && !isTokenExpired(token)) return true;
  return isRefreshValid();
}

export const BASE_URL = import.meta.env.VITE_API_BASE || '/api';

const instance = axios.create({ baseURL: BASE_URL, timeout: 15000 });

/** 刷新中的 Promise，避免并发重复刷新 */
let refreshing: Promise<string | null> | null = null;

/** 用 refresh token 换新的 access token；失败返回 null */
async function doRefresh(): Promise<string | null> {
  const rt = getRefreshToken();
  if (!rt) return null;
  try {
    const resp = await axios.post<{ accessToken: string; refreshToken?: string }>(
      `${BASE_URL}/auth/refresh`,
      { refreshToken: rt }
    );
    const data = resp.data?.data ?? resp.data;
    const newAt = (data as any)?.accessToken;
    if (newAt) {
      setToken(newAt);
      if ((data as any)?.refreshToken) setRefreshToken((data as any).refreshToken);
      return newAt;
    }
    return null;
  } catch {
    return null;
  }
}

/**
 * 确保拿到可用的 access token。
 * @param force true = 即使当前 token 还没过期也去换一个新的（提前续期 / 401 重试用）
 */
async function ensureFreshToken(force = false): Promise<string | null> {
  const token = getToken();
  if (!force && token && !isTokenExpired(token)) return token;
  // 需要刷新 → 复用同一个 Promise，避免并发重复刷新
  if (!refreshing) {
    refreshing = doRefresh().finally(() => {
      refreshing = null;
    });
  }
  const fresh = await refreshing;
  if (fresh) return fresh;
  // 刷新失败：只要原 token 还没过期就先用着，避免把「其实还能用」的会话直接踢掉
  return token && !isTokenExpired(token) ? token : null;
}

instance.interceptors.request.use(async (config) => {
  const token = getToken();
  if (token) {
    // 已过期、或即将过期（不足 5 分钟）→ 先静默续期，避免请求途中失效
    if (isTokenExpired(token) || isTokenExpiringSoon(token)) {
      const fresh = await ensureFreshToken(true);
      if (fresh) {
        config.headers.Authorization = `Bearer ${fresh}`;
      } else if (isTokenExpired(token)) {
        // 真的续不上了 → 清除登录态并跳登录页
        clearAuth();
        redirectToLogin();
        return Promise.reject(new Error('登录已过期，请重新登录'));
      } else {
        // 续期失败但原 token 还没过期：先用着，不掉线
        config.headers.Authorization = `Bearer ${token}`;
      }
    } else {
      config.headers.Authorization = `Bearer ${token}`;
    }
  }
  return config;
});

function redirectToLogin() {
  if (window.location.pathname === '/login') return;
  setRedirect(window.location.pathname + window.location.search);
  window.location.href = '/login';
}

/** 业务错误：把后端 R.code 带出来，前端才能按错误类型分流处理（而不只是弹一句 msg） */
export class ApiError extends Error {
  code: number;
  constructor(message: string, code: number) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
  }
}

instance.interceptors.response.use(
  (resp) => {
    const r = resp.data as R;
    if (r.code !== 200) {
      return Promise.reject(new ApiError(r.msg || '请求失败', r.code));
    }
    return r.data;
  },
  async (err) => {
    const status = err?.response?.status;
    const cfg = err?.config;
    if (status === 401) {
      // 401 多半是「access token 刚过期」：先刷新再重放原请求一次，用户无感
      if (cfg && !cfg.__retried) {
        cfg.__retried = true;
        const fresh = await ensureFreshToken(true);
        if (fresh) {
          cfg.headers = { ...(cfg.headers || {}), Authorization: `Bearer ${fresh}` };
          return instance.request(cfg);
        }
      }
      clearAuth();
      redirectToLogin();
    }
    const msg = err?.response?.data?.msg || err.message || '网络错误';
    // 网络层错误也带上 code（HTTP 状态码，无响应时为 0），方便上层区分「连不上」和「业务失败」
    return Promise.reject(new ApiError(msg, status || 0));
  }
);

export function get<T = any>(url: string, params?: any, config?: AxiosRequestConfig): Promise<T> {
  return instance.get(url, { params, ...config }) as unknown as Promise<T>;
}

export function post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
  return instance.post(url, data, config) as unknown as Promise<T>;
}

export function put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
  return instance.put(url, data, config) as unknown as Promise<T>;
}

export function del<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return instance.delete(url, config) as unknown as Promise<T>;
}

export default instance;
export { instance as http };
