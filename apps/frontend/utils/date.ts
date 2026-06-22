const TAIPEI_TZ = 'Asia/Taipei';
const DATE_KEY_RE = /^\d{4}-\d{2}-\d{2}/;

const taipeiDateFormatter = new Intl.DateTimeFormat('en-CA', {
  timeZone: TAIPEI_TZ,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit'
});

const taipeiDisplayFormatter = new Intl.DateTimeFormat('zh-TW', {
  timeZone: TAIPEI_TZ,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit'
});

export const toDateKey = (value?: string | Date | null) => {
  if (!value) return '';
  if (value instanceof Date) {
    return taipeiDateFormatter.format(value);
  }
  if (DATE_KEY_RE.test(value) && value.length === 10) {
    return value.slice(0, 10);
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return '';
  return taipeiDateFormatter.format(parsed);
};

export const toTaipeiDate = (value?: string | Date | null) => {
  const key = toDateKey(value);
  if (!key) return null;
  return new Date(`${key}T00:00:00+08:00`);
};

export const formatTaipeiDate = (value?: string | Date | null) => {
  const date = toTaipeiDate(value);
  if (!date) return '';
  return taipeiDisplayFormatter.format(date);
};

export const getTaipeiTimestamp = (value?: string | Date | null) => {
  const date = toTaipeiDate(value);
  return date ? date.getTime() : 0;
};
