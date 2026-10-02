// axios 封装：自动带 token、401 跳登录、统一解包 R 结构
import axios, { type AxiosRequestConfig } from 'axios';
import type { R } from './types';

const TOKEN_KEY = 'waimai_token';
const REDIRECT_KEY = 'waimai_redirect';

export const getToken = () => localStorage.getItem(TOKEN_KEY);
export const setToken = (t: string) => localStorage.setItem(TOKEN_KEY, t);
export const clearToken = () => localStorage.removeItem(TOKEN_KEY);

export const getRedirect = () => localStorage.getItem(REDIRECT_KEY);
export const setRedirect = (path: string) => localStorage.setItem(REDIRECT_KEY, path);
export const clearRedirect = () => localStorage.removeItem(REDIRECT_KEY);

export const BASE_URL = import.meta.env.VITE_API_BASE || '/api';

const instance = axios.create({ baseURL: BASE_URL, timeout: 15000 });

instance.interceptors.request.use((config) => {
  const token = getToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

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
      clearToken();
      const path = window.location.pathname + window.location.search;
      setRedirect(path);
      window.location.href = '/login';
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
