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

/** 确保 access token 有效：过期则尝试刷新 */
async function ensureFreshToken(): Promise<string | null> {
  const token = getToken();
  if (!token) return null;
  if (!isTokenExpired(token)) return token;
  // 过期 → 刷新
  if (!refreshing) {
    refreshing = doRefresh().finally(() => {
      refreshing = null;
    });
  }
  return refreshing;
}

instance.interceptors.request.use(async (config) => {
  const token = getToken();
  if (token) {
    // 若 token 已过期，先尝试刷新再带新 token
    if (isTokenExpired(token)) {
      const fresh = await ensureFreshToken();
      if (fresh) {
        config.headers.Authorization = `Bearer ${fresh}`;
      } else {
        // 刷新失败 → 清除登录态并跳登录页
        clearAuth();
        redirectToLogin();
        return Promise.reject(new Error('登录已过期，请重新登录'));
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

instance.interceptors.response.use(
  (resp) => {
    const r = resp.data as R;
    if (r.code !== 200) {
      return Promise.reject(new Error(r.msg || '请求失败'));
    }
    return r.data;
  },
  (err) => {
    const status = err?.response?.status;
    if (status === 401) {
      clearAuth();
      redirectToLogin();
    }
    const msg = err?.response?.data?.msg || err.message || '网络错误';
    return Promise.reject(new Error(msg));
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
