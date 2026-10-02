// 高德地图 JS API 2.0 统一加载器（三端复用）：
// 1. 从后端 /common/config/map 拉取 jsKey + securityCode（安全密钥）
// 2. JS API 2.0 必须在脚本加载前设置 window._AMapSecurityConfig，否则加载失败
// 3. 幂等：多次调用不会重复注入脚本
import { get } from './request';

let pending: Promise<boolean> | null = null;

/**
 * 加载高德 JS API。返回 true 表示 AMap 可用。
 * @param plugins 需要的插件列表，如 ['AMap.Scale']，加载时一次性挂载
 */
export function loadAmap(plugins: string[] = []): Promise<boolean> {
  if ((window as any).AMap) return Promise.resolve(true);
  if (pending) return pending;
  pending = (async () => {
    const cfg: any = await get('/common/config/map').catch(() => null);
    const jsKey: string = cfg?.jsKey || '';
    const securityCode: string = cfg?.securityCode || '';
    if (!jsKey) return false;
    if (securityCode) {
      (window as any)._AMapSecurityConfig = { securityJsCode: securityCode };
    }
    await new Promise<void>((resolve) => {
      const script = document.createElement('script');
      const ps = plugins.length ? `&plugin=${plugins.join(',')}` : '';
      script.src = `https://webapi.amap.com/maps?v=2.0&key=${jsKey}${ps}`;
      script.onload = () => resolve();
      script.onerror = () => resolve();
      document.head.appendChild(script);
    });
    return !!(window as any).AMap;
  })();
  return pending;
}
