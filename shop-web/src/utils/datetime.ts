/**
 * 管理端统一时间格式化。
 *
 * 后端 LocalDateTime 由 Jackson 序列化为 ISO 字符串（2026-09-16T18:07:08.592），
 * 此前多处直接渲染或只用 replace('T',' ') 简单替换，页面上会出现带 T 与毫秒的原始值。
 *
 * 这里按字符串归一化，不做时区换算，保证展示值与服务端墙上时间一致；
 * 只有带时区后缀（Z / +08:00）的值才交给 Date 按本地时间换算。
 */

/** 匹配 `2026-09-16T18:07:08.592` 或 `2026-09-16 18:07:08`。 */
const DATE_TIME_PATTERN = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2}))?/;
/** 匹配纯日期 `2026-09-16`。 */
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;
/** 判断是否携带时区信息，需按本地时间换算。 */
const TIMEZONE_SUFFIX = /(?:[Zz]|[+-]\d{2}:?\d{2})$/;

function pad(value: number): string {
  return value < 10 ? `0${value}` : String(value);
}

function fromDate(date: Date): { date: string; time: string; minute: string } {
  const datePart = `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
  const timePart = `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
  return { date: datePart, time: `${datePart} ${timePart}`, minute: `${datePart} ${pad(date.getHours())}:${pad(date.getMinutes())}` };
}

/**
 * 格式化为 `YYYY-MM-DD HH:mm:ss`，空值或无法识别时返回占位符。
 *
 * @param value 后端返回的时间字符串
 * @param fallback 空值占位符，默认 `—`
 */
export function formatDateTime(value?: string | null, fallback = '—'): string {
  if (!value) return fallback;
  if (TIMEZONE_SUFFIX.test(value)) {
    const parsed = new Date(value);
    return Number.isNaN(parsed.getTime()) ? value : fromDate(parsed).time;
  }
  const matched = DATE_TIME_PATTERN.exec(value);
  if (matched) {
    return `${matched[1]}-${matched[2]}-${matched[3]} ${matched[4]}:${matched[5]}:${matched[6] || '00'}`;
  }
  if (DATE_PATTERN.test(value)) return `${value} 00:00:00`;
  return value;
}

/**
 * 格式化为 `YYYY-MM-DD`，用于只关心日期的场景。
 *
 * @param value 后端返回的时间字符串
 * @param fallback 空值占位符，默认 `—`
 */
export function formatDate(value?: string | null, fallback = '—'): string {
  if (!value) return fallback;
  if (DATE_PATTERN.test(value)) return value;
  if (TIMEZONE_SUFFIX.test(value)) {
    const parsed = new Date(value);
    return Number.isNaN(parsed.getTime()) ? value : fromDate(parsed).date;
  }
  const matched = DATE_TIME_PATTERN.exec(value);
  if (matched) return `${matched[1]}-${matched[2]}-${matched[3]}`;
  return value;
}

/**
 * 格式化为 `YYYY-MM-DD HH:mm`，用于活动起止时间等精确到分钟的场景。
 *
 * @param value 后端返回的时间字符串
 * @param fallback 空值占位符，默认 `—`
 */
export function formatMinute(value?: string | null, fallback = '—'): string {
  if (!value) return fallback;
  if (TIMEZONE_SUFFIX.test(value)) {
    const parsed = new Date(value);
    return Number.isNaN(parsed.getTime()) ? value : fromDate(parsed).minute;
  }
  const matched = DATE_TIME_PATTERN.exec(value);
  if (matched) return `${matched[1]}-${matched[2]}-${matched[3]} ${matched[4]}:${matched[5]}`;
  if (DATE_PATTERN.test(value)) return `${value} 00:00`;
  return value;
}
