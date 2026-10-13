/**
 * Effective-dated weekend policies. Weekend rows are not shipped in the data; they are rebuilt
 * from the calendar's declared policy. For a given date the *last* period covering it decides
 * which weekdays are weekend days; a date covered by no period has no weekend days.
 */
import { dayIndex, weekday } from "./dates.ts";

export interface WeekendPeriod {
  /** Weekday indices, Monday = 0 … Sunday = 6. */
  readonly days: ReadonlySet<number>;
  readonly from?: string;
  readonly to?: string;
}

export interface WeekendPolicyJson {
  days?: string[];
  periods?: { days?: string[]; from?: string | null; to?: string | null }[];
}

export class WeekendPolicy {
  readonly periods: readonly WeekendPeriod[];
  /** The union of every period's weekend days. */
  readonly days: ReadonlySet<number>;

  constructor(periods: WeekendPeriod[]) {
    this.periods = periods.length ? periods : [{ days: new Set() }];
    const union = new Set<number>();
    for (const period of this.periods) for (const day of period.days) union.add(day);
    this.days = union;
  }

  static fromJson(payload: WeekendPolicyJson | null | undefined): WeekendPolicy {
    if (!payload) return new WeekendPolicy([]);
    const raw = payload.periods ?? [];
    if (raw.length) {
      return new WeekendPolicy(
        raw.map((p) => ({
          days: new Set((p.days ?? []).map(dayIndex)),
          ...(p.from ? { from: p.from } : {}),
          ...(p.to ? { to: p.to } : {}),
        })),
      );
    }
    return new WeekendPolicy([{ days: new Set((payload.days ?? []).map(dayIndex)) }]);
  }

  /** The weekend weekdays in effect on `date` (last matching period wins). */
  daysOn(date: string): ReadonlySet<number> {
    for (let i = this.periods.length - 1; i >= 0; i--) {
      const period = this.periods[i]!;
      if ((period.from === undefined || date >= period.from) && (period.to === undefined || date <= period.to)) {
        return period.days;
      }
    }
    return new Set();
  }

  isWeekend(date: string): boolean {
    return this.daysOn(date).has(weekday(date));
  }

  isEffectiveDated(): boolean {
    if (this.periods.length > 1) return true;
    const only = this.periods[0]!;
    return only.from !== undefined || only.to !== undefined;
  }
}
