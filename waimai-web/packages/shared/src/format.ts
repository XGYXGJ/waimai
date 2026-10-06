// 展示层格式化（四端共用）

/**
 * 后端 LocalDateTime 未配置 Jackson 时区/格式，序列化出来是 ISO-8601
 * （例如 {@code 2026-10-05T22:36:41}），直接显示很别扭，这里统一截短。
 */
export function formatDateTime(input?: string | number | Date | null): string {
  if (input === null || input === undefined || input === '') return '';
  const d = input instanceof Date ? input : new Date(input);
  if (Number.isNaN(d.getTime())) return String(input);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

/** 金额：¥12.30；空值统一显示 ¥0.00 */
export function formatMoney(v?: number | string | null): string {
  const n = typeof v === 'string' ? Number(v) : v;
  if (n === null || n === undefined || Number.isNaN(n)) return '¥0.00';
  return `¥${n.toFixed(2)}`;
}
