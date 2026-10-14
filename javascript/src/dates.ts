/**
 * Civil-date arithmetic on ISO `YYYY-MM-DD` strings.
 *
 * Dates are plain strings throughout the public API so that no timezone can leak in; the
 * helpers here convert to and from an epoch-day integer (days since 1970-01-01) for arithmetic.
 */

export type DateLike = string | Date;

const ISO = /^(\d{4})-(\d{2})-(\d{2})$/;

/** Normalises a `YYYY-MM-DD` string or a `Date` (taken as its local calendar date). */
export function asIso(value: DateLike, argument = "date"): string {
  if (value instanceof Date) {
    if (Number.isNaN(value.getTime())) throw new TypeError(`${argument} is an invalid Date`);
    return fromParts(value.getFullYear(), value.getMonth() + 1, value.getDate());
  }
  if (typeof value !== "string" || !ISO.test(value)) {
    throw new TypeError(`${argument} must be an ISO date (YYYY-MM-DD) or a Date, got ${String(value)}`);
  }
  const [y, m, d] = parts(value);
  if (m < 1 || m > 12 || d < 1 || d > lengthOfMonth(y, m)) {
    throw new TypeError(`${argument} is not a valid calendar date: ${value}`);
  }
  return value;
}

export function parts(iso: string): [number, number, number] {
  const match = ISO.exec(iso);
  if (!match) throw new TypeError(`not an ISO date: ${iso}`);
  return [Number(match[1]), Number(match[2]), Number(match[3])];
}

export function fromParts(year: number, month: number, day: number): string {
  const pad = (n: number, width: number) => String(n).padStart(width, "0");
  return `${pad(year, 4)}-${pad(month, 2)}-${pad(day, 2)}`;
}

export function isLeap(year: number): boolean {
  return (year % 4 === 0 && year % 100 !== 0) || year % 400 === 0;
}

export function lengthOfYear(year: number): number {
  return isLeap(year) ? 366 : 365;
}

export function lengthOfMonth(year: number, month: number): number {
  return [31, isLeap(year) ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][month - 1]!;
}

/** Days since 1970-01-01 (proleptic Gregorian). */
export function toEpochDay(iso: string): number {
  const [y, m, d] = parts(iso);
  return Math.round(Date.UTC(y, m - 1, d) / 86_400_000);
}

export function fromEpochDay(day: number): string {
  const date = new Date(day * 86_400_000);
  return fromParts(date.getUTCFullYear(), date.getUTCMonth() + 1, date.getUTCDate());
}

export function plusDays(iso: string, days: number): string {
  return fromEpochDay(toEpochDay(iso) + days);
}

/** Days from `start` to `end` (negative when `end` precedes `start`). */
export function daysBetween(start: string, end: string): number {
  return toEpochDay(end) - toEpochDay(start);
}

/** Monday = 0 … Sunday = 6, as in Python's `date.weekday()`. */
export function weekday(iso: string): number {
  // 1970-01-01 was a Thursday (3).
  return (((toEpochDay(iso) + 3) % 7) + 7) % 7;
}

export const DAY_NAMES = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"] as const;
export type DayName = (typeof DAY_NAMES)[number];

export function dayIndex(name: string): number {
  const index = DAY_NAMES.indexOf(name.trim().toUpperCase() as DayName);
  if (index < 0) throw new TypeError(`Unknown weekday: ${name}`);
  return index;
}

/** Civil month arithmetic with the day clipped to the destination month's length. */
export function addMonths(iso: string, months: number): string {
  const [y, m, d] = parts(iso);
  const index = y * 12 + (m - 1) + months;
  const year = Math.floor(index / 12);
  const month = index - year * 12 + 1;
  return fromParts(year, month, Math.min(d, lengthOfMonth(year, month)));
}

export function lastDayOfMonth(iso: string): string {
  const [y, m] = parts(iso);
  return fromParts(y, m, lengthOfMonth(y, m));
}

export function isLastDayOfMonth(iso: string): boolean {
  return iso === lastDayOfMonth(iso);
}

export function sameMonth(a: string, b: string): boolean {
  return a.slice(0, 7) === b.slice(0, 7);
}

export function year(iso: string): number {
  return parts(iso)[0];
}

export function month(iso: string): number {
  return parts(iso)[1];
}

export function dayOfMonth(iso: string): number {
  return parts(iso)[2];
}

/** Lexicographic comparison is chronological for fixed-width ISO dates. */
export function compare(a: string, b: string): number {
  return a < b ? -1 : a > b ? 1 : 0;
}

export function min(a: string, b: string): string {
  return a < b ? a : b;
}

export function max(a: string, b: string): string {
  return a > b ? a : b;
}

/** Validates an `HH:MM` or `HH:MM:SS` local time and returns it unchanged. */
export function asTime(value: string, argument = "time"): string {
  if (!/^\d{2}:\d{2}(:\d{2})?$/.test(value)) throw new TypeError(`${argument} must be HH:MM, got ${value}`);
  return value;
}

/** Compares two `HH:MM[:SS]` strings. */
export function compareTime(a: string, b: string): number {
  const norm = (t: string) => (t.length === 5 ? t + ":00" : t);
  return compare(norm(a), norm(b));
}
