/** Completeness scopes, questions and the assessment shapes, mirroring Java `com.bdc.trust`. */

export const SCHEDULED_CLOSURES = "SCHEDULED_CLOSURES";
export const EARLY_CLOSES = "EARLY_CLOSES";
export const UNSCHEDULED_EXCEPTIONS = "UNSCHEDULED_EXCEPTIONS";
export const COMPLETENESS_SCOPES = [SCHEDULED_CLOSURES, EARLY_CLOSES, UNSCHEDULED_EXCEPTIONS] as const;
export type CompletenessScope = (typeof COMPLETENESS_SCOPES)[number];

export type CoverageQuality = "VERIFIED" | "PROJECTED" | "INCOMPLETE";
export type Confidence = "CONFIRMED" | "PROJECTED" | "UNKNOWN";
export type DayState = "OPEN" | "CLOSED" | "EARLY_CLOSE" | "UNKNOWN";

/**
 * The question being asked about a date decides which scopes must be resolved first. Whether a
 * market trades depends only on scheduled closures and unscheduled exceptions: an early close is
 * still a business day. Session detail (close times) additionally needs early closes.
 */
export type DateQuestion = "BUSINESS_DAY" | "SESSION";
export const QUESTION_SCOPES: Record<DateQuestion, readonly CompletenessScope[]> = {
  BUSINESS_DAY: [SCHEDULED_CLOSURES, UNSCHEDULED_EXCEPTIONS],
  SESSION: COMPLETENESS_SCOPES,
};

export interface CoverageInterval {
  readonly scope: CompletenessScope;
  readonly from: string;
  readonly to: string;
  readonly quality: CoverageQuality;
  readonly evidenceIds: readonly string[];
}

export interface NativeDate {
  readonly chronologyId: string;
  readonly year: number;
  readonly monthCode: string;
  readonly day: number;
}

export interface Event {
  /** ISO civil date in the market's own timezone. */
  readonly date: string;
  readonly type: "CLOSED" | "EARLY_CLOSE" | "NOTABLE" | "PERIOD_MARKER" | "WEEKEND" | "SPECIAL_SESSION" | string;
  readonly description: string;
  readonly key: string | null;
  readonly sourceModule: string | null;
  readonly observedFrom: string | null;
  /** Local close time `HH:MM` of a shortened session (`EARLY_CLOSE`) or a `SPECIAL_SESSION`. */
  readonly closeTime: string | null;
  readonly status: "CONFIRMED" | "PROJECTED";
  /** Local open time `HH:MM` of a `SPECIAL_SESSION`; null (or absent) on every other type. */
  readonly openTime?: string | null;
}

/**
 * A trading session on a date that is not a business day (a `SPECIAL_SESSION` event), with the
 * calendar and timezone its local `HH:MM` times belong to; `timezone` is null only for legacy
 * metadata. Mirrors Java `com.bdc.stream.SpecialSession`.
 */
export interface SpecialSession {
  readonly calendarId: string;
  readonly timezone: string | null;
  readonly openTime: string;
  readonly closeTime: string;
  readonly key: string | null;
  readonly description: string;
  readonly status: "CONFIRMED" | "PROJECTED";
}

export interface EventDetails {
  readonly event: Event;
  readonly rawStatus: "CONFIRMED" | "PROJECTED";
  readonly effectiveStatus: Confidence;
  readonly evidenceIds: readonly string[];
  readonly nominalNativeDate: NativeDate | null;
  readonly chronologyProfile: string | null;
  readonly chronologyProvider: string | null;
  readonly observationLineage: readonly string[];
}

/**
 * A non-throwing explanation of one date. `state`/`effectiveConfidence` answer the full session
 * question and are UNKNOWN as soon as any scope is incomplete; `businessDayState` (never
 * `EARLY_CLOSE`) and `businessDayConfidence` answer "is this a business day?" alone.
 */
export interface DayAssessment {
  readonly date: string;
  readonly state: DayState;
  readonly scheduledState: DayState;
  readonly effectiveConfidence: Confidence;
  readonly completeness: Readonly<Record<CompletenessScope, CoverageQuality>>;
  readonly evidenceIds: readonly string[];
  readonly events: readonly EventDetails[];
  readonly businessDayState: DayState;
  readonly businessDayConfidence: Confidence;
}

export function incompleteScopes(assessment: DayAssessment, question: DateQuestion = "SESSION"): CompletenessScope[] {
  return QUESTION_SCOPES[question].filter((scope) => (assessment.completeness[scope] ?? "INCOMPLETE") === "INCOMPLETE");
}

export function isBusinessDayKnown(assessment: DayAssessment): boolean {
  return assessment.businessDayState !== "UNKNOWN";
}
