// Date helpers. All dates are handled as local calendar days (no times, no time zones).

const DAY_MS = 24 * 60 * 60 * 1000;

const ISO_DATE = /^(\d{4})-(\d{2})-(\d{2})$/;
const MONTH_DAY = /^(?:--)?(\d{2})-(\d{2})$/;

export interface Birthday {
  month: number; // 1-12
  day: number; // 1-31
  year?: number;
}

function isValidDay(year: number, month: number, day: number): boolean {
  if (month < 1 || month > 12 || day < 1) return false;
  return day <= new Date(year, month, 0).getDate();
}

/** Parses `YYYY-MM-DD` into a local Date at midnight, or null if invalid. */
export function parseDate(value: string): Date | null {
  const m = ISO_DATE.exec(value.trim());
  if (!m) return null;
  const [year, month, day] = [Number(m[1]), Number(m[2]), Number(m[3])];
  if (!isValidDay(year, month, day)) return null;
  return new Date(year, month - 1, day);
}

/** Parses `YYYY-MM-DD` or `MM-DD` (year unknown). */
export function parseBirthday(value: string): Birthday | null {
  const full = parseDate(value);
  if (full) {
    return { year: full.getFullYear(), month: full.getMonth() + 1, day: full.getDate() };
  }
  const m = MONTH_DAY.exec(value.trim());
  if (!m) return null;
  const [month, day] = [Number(m[1]), Number(m[2])];
  // 2000 is a leap year, so Feb 29 is accepted.
  if (!isValidDay(2000, month, day)) return null;
  return { month, day };
}

export function startOfDay(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

/** Whole calendar days from `a` to `b` (positive when b is later). */
export function daysBetween(a: Date, b: Date): number {
  return Math.round((startOfDay(b).getTime() - startOfDay(a).getTime()) / DAY_MS);
}

function birthdayInYear(b: Birthday, year: number): Date {
  // Feb 29 birthdays are celebrated on Feb 28 in non-leap years.
  const day = isValidDay(year, b.month, b.day) ? b.day : b.day - 1;
  return new Date(year, b.month - 1, day);
}

/** The next occurrence of the birthday, today included. */
export function nextBirthday(b: Birthday, today: Date): Date {
  const t = startOfDay(today);
  const thisYear = birthdayInYear(b, t.getFullYear());
  return thisYear >= t ? thisYear : birthdayInYear(b, t.getFullYear() + 1);
}

export function daysUntilBirthday(b: Birthday, today: Date): number {
  return daysBetween(today, nextBirthday(b, today));
}

/** Age they turn on their next birthday, if the birth year is known. */
export function upcomingAge(b: Birthday, today: Date): number | undefined {
  if (b.year === undefined) return undefined;
  return nextBirthday(b, today).getFullYear() - b.year;
}

const CADENCE_ALIASES: Record<string, number> = {
  weekly: 7,
  biweekly: 14,
  fortnightly: 14,
  monthly: 30,
  bimonthly: 61,
  quarterly: 91,
  biannually: 182,
  "twice a year": 182,
  yearly: 365,
  annually: 365,
};

const CADENCE_UNITS: Record<string, number> = { d: 1, w: 7, m: 30, y: 365 };

/** Converts a cadence like `monthly` or `6w` into a number of days. */
export function cadenceToDays(value: string): number | null {
  const v = value.trim().toLowerCase();
  if (v in CADENCE_ALIASES) return CADENCE_ALIASES[v];
  const m = /^(\d+)\s*([dwmy])$/.exec(v);
  if (!m || Number(m[1]) === 0) return null;
  return Number(m[1]) * CADENCE_UNITS[m[2]];
}

/** "today", "yesterday", "3 days ago", "2 weeks ago", "4 months ago", "1 year ago". */
export function formatDaysAgo(days: number): string {
  if (days <= 0) return "today";
  if (days === 1) return "yesterday";
  if (days < 14) return `${days} days ago`;
  if (days < 60) return `${Math.floor(days / 7)} weeks ago`;
  if (days < 365) return `${Math.floor(days / 30)} months ago`;
  const years = Math.floor(days / 365);
  return years === 1 ? "1 year ago" : `${years} years ago`;
}

/** "today", "tomorrow", "in 5 days", "in 3 weeks", "in 4 months". */
export function formatDaysUntil(days: number): string {
  if (days <= 0) return "today";
  if (days === 1) return "tomorrow";
  if (days < 14) return `in ${days} days`;
  if (days < 60) return `in ${Math.floor(days / 7)} weeks`;
  return `in ${Math.floor(days / 30)} months`;
}

const MONTHS = [
  "Jan", "Feb", "Mar", "Apr", "May", "Jun",
  "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
];

export function formatBirthday(b: Birthday): string {
  const base = `${MONTHS[b.month - 1]} ${b.day}`;
  return b.year === undefined ? base : `${base}, ${b.year}`;
}

export function formatDate(d: Date): string {
  return `${MONTHS[d.getMonth()]} ${d.getDate()}, ${d.getFullYear()}`;
}

export function toIsoDate(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}
