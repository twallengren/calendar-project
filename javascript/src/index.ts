/**
 * bdc-calendars: business-day and trading calendars with provenance and confidence.
 *
 * The data is generated from the YAML specs in the calendar-project repository and shipped
 * inside this package, so queries are offline, deterministic and reproducible for a given
 * package version. Dates are ISO `YYYY-MM-DD` strings (a `Date` is accepted as input and taken
 * as its local calendar date).
 *
 *     import { getCalendar } from "bdc-calendars";
 *     const nyse = getCalendar("XNYS");
 *     nyse.isBusinessDay("2021-12-31");            // true
 *     nyse.closeTime("2025-07-03");                // "13:00"
 *     nyse.isTradingDay("2025-07-04");             // false: closed, and no special session
 *     nyse.addTenor("2024-01-31", "3M");            // "2024-04-30"
 *     nyse.assessment("2028-06-01").businessDayConfidence; // "PROJECTED"
 */
export { BusinessCalendar, JointCalendar, SingleCalendar, getCalendar, getJointCalendar, MAX_SEARCH_DAYS, type DateRange } from "./calendar.ts";
export { BdcCalendarError, CalendarNotFoundError, OutsideCoverageError, UnresolvedDateError } from "./errors.ts";
export { calendarIds as listCalendars, aliases as listAliases, manifest, resolveId } from "./loader.ts";
export { WeekendPolicy, type WeekendPeriod } from "./weekend.ts";
export {
  COMPLETENESS_SCOPES,
  QUESTION_SCOPES,
  incompleteScopes,
  isBusinessDayKnown,
  type CompletenessScope,
  type Confidence,
  type CoverageInterval,
  type CoverageQuality,
  type DateQuestion,
  type DayAssessment,
  type DayState,
  type Event,
  type EventDetails,
  type NativeDate,
  type SpecialSession,
} from "./trust.ts";
export {
  DAY_COUNTS,
  FREQUENCIES,
  Tenor,
  dayCount,
  dayCountDays,
  frequency,
  immDate,
  isCalendarFree,
  isImm,
  isQuarterlyImm,
  nextImm,
  nextQuarterlyImm,
  roll,
  yearFraction,
  type DayCount,
  type Frequency,
  type FrequencyName,
  type RollConvention,
  type StubConvention,
  type TenorUnit,
  type YearFractionOptions,
} from "./finance.ts";
export {
  BUSINESS_DAY_CONVENTIONS,
  convention,
  unadjustedScheduleDates,
  type BusinessDayConvention,
  type DateOperation,
  type DateOperationResult,
  type MemberClose,
  type Schedule,
  type ScheduleDefinition,
  type SchedulePeriod,
  type YearFractionResult,
} from "./operations.ts";
export { asIso, type DateLike } from "./dates.ts";
import { manifest as readManifest } from "./loader.ts";

/** Release version of the bundled calendar data, e.g. `"13.0.0"`. */
export function dataVersion(): string {
  return readManifest().data_version;
}

/** Git SHA of the calendar-project commit the data was generated from. */
export function dataGitSha(): string {
  return readManifest().data_git_sha;
}
