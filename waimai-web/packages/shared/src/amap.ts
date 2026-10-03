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

export interface GeoPoint {
  lng: number;
  lat: number;
  address?: string;
}

/** 地址 → 经纬度（高德地理编码）。失败返回 null */
export function geocodeAddress(keyword: string): Promise<GeoPoint | null> {
  return new Promise((resolve) => {
    const AMap = (window as any).AMap;
    if (!AMap || !keyword?.trim()) return resolve(null);
    AMap.plugin('AMap.Geocoder', () => {
      try {
        const geocoder = new AMap.Geocoder();
        geocoder.getLocation(keyword.trim(), (status: string, result: any) => {
          const g = result?.geocodes?.[0];
          if (status === 'complete' && g?.location) {
            resolve({ lng: g.location.lng, lat: g.location.lat, address: g.formattedAddress });
          } else {
            resolve(null);
          }
        });
      } catch {
        resolve(null);
      }
    });
  });
}

/** 经纬度 → 地址文字（逆地理编码）。失败返回 null */
export function reverseGeocode(lng: number, lat: number): Promise<string | null> {
  return new Promise((resolve) => {
    const AMap = (window as any).AMap;
    if (!AMap) return resolve(null);
    AMap.plugin('AMap.Geocoder', () => {
      try {
        const geocoder = new AMap.Geocoder();
        geocoder.getAddress([lng, lat], (status: string, result: any) => {
          if (status === 'complete' && result?.regeocode) {
            resolve(result.regeocode.formattedAddress || null);
          } else {
            resolve(null);
          }
        });
      } catch {
        resolve(null);
      }
    });
  });
}
