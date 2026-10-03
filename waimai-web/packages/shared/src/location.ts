// 用户定位缓存：保存「上次刷新的位置」，作为默认位置，直到用户手动刷新
export interface SavedLocation {
  lng: number;
  lat: number;
  /** 展示文案，如「已定位 (39.92, 116.40)」 */
  text?: string;
  /** 保存时间戳（ms） */
  ts: number;
}

const LOCATION_KEY = 'waimai_location';

/** 读取缓存的位置；无缓存或格式非法返回 null */
export function getSavedLocation(): SavedLocation | null {
  try {
    const raw = localStorage.getItem(LOCATION_KEY);
    if (!raw) return null;
    const v = JSON.parse(raw);
    if (typeof v?.lng === 'number' && typeof v?.lat === 'number') {
      return v as SavedLocation;
    }
  } catch {
    /* ignore */
  }
  return null;
}

/** 保存本次刷新到的位置，返回写入后的对象 */
export function setSavedLocation(loc: { lng: number; lat: number; text?: string }): SavedLocation {
  const v: SavedLocation = { lng: loc.lng, lat: loc.lat, text: loc.text, ts: Date.now() };
  try {
    localStorage.setItem(LOCATION_KEY, JSON.stringify(v));
  } catch {
    /* ignore */
  }
  return v;
}

/** 清除缓存的位置 */
export function clearSavedLocation(): void {
  try {
    localStorage.removeItem(LOCATION_KEY);
  } catch {
    /* ignore */
  }
}

/** 统一的展示文案：优先用保存的 text，否则按坐标生成；无位置时返回「默认位置」 */
export function locationLabel(loc: SavedLocation | null): string {
  if (!loc) return '默认位置';
  return loc.text || `已定位 (${loc.lat.toFixed(2)}, ${loc.lng.toFixed(2)})`;
}
