/**
 * Loading of the bundled data: `manifest.json` (release version, calendar index, alias table),
 * `<ID>/metadata.json` (the published metadata plus the resolved weekend policy and event
 * provenance) and `<ID>/holidays.csv` (every non-weekend event row; columns are read by header
 * name, and the trailing `open_time` column of wire schema 2.1 is treated as empty when absent).
 */
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { parseCsv } from "./csv.ts";
import { asIso, compareTime } from "./dates.ts";
import { CalendarNotFoundError } from "./errors.ts";
import type { CoverageInterval, Event, EventDetails, NativeDate } from "./trust.ts";
import { WeekendPolicy } from "./weekend.ts";

const DATA_DIR = join(dirname(fileURLToPath(import.meta.url)), "..", "data");

export interface Manifest {
  schema_version: string;
  data_version: string;
  data_git_sha: string;
  generation_date: string;
  blessed_at?: string;
  calendars: Record<string, { kind?: string; range_start?: string; range_end?: string; checksum?: string }>;
  aliases?: Record<string, string>;
}

let manifestCache: Manifest | null = null;
const calendarCache = new Map<string, CalendarData>();

function readText(...parts: string[]): string {
  return readFileSync(join(DATA_DIR, ...parts), "utf8");
}

export function manifest(): Manifest {
  if (!manifestCache) manifestCache = JSON.parse(readText("manifest.json")) as Manifest;
  return manifestCache;
}

export function calendarIds(): string[] {
  return Object.keys(manifest().calendars).sort();
}

export function aliases(): Record<string, string> {
  return manifest().aliases ?? {};
}

/** Maps a canonical id, a MIC alias or any case/underscore spelling of either onto a bundled id. */
export function resolveId(calendarId: string): string {
  if (typeof calendarId !== "string" || !calendarId.trim()) {
    throw new CalendarNotFoundError(String(calendarId), calendarIds());
  }
  const raw = calendarId.trim();
  const known = manifest().calendars;
  const table = aliases();
  const upper = raw.toUpperCase();
  for (const candidate of [raw, upper, upper.replace(/_/g, "-"), upper.replace(/-/g, "_")]) {
    if (candidate in known) return candidate;
    if (candidate in table) return table[candidate]!;
  }
  throw new CalendarNotFoundError(raw, [...calendarIds(), ...Object.keys(table).sort()]);
}

export interface CalendarData {
  readonly calendarId: string;
  readonly metadata: Record<string, unknown>;
  readonly eventsByDate: ReadonlyMap<string, readonly Event[]>;
  readonly detailsByDate: ReadonlyMap<string, readonly EventDetails[]>;
  readonly weekendPolicy: WeekendPolicy;
  readonly rangeFrom: string;
  readonly rangeTo: string;
  readonly verifiedThrough: string | null;
  readonly coverageIntervals: readonly CoverageInterval[];
  readonly timezone: string | null;
  readonly name: string;
  readonly kind: string;
}

export function eventFromRow(row: Record<string, unknown>): Event {
  const status = (row.status as string) || "CONFIRMED";
  if (status !== "CONFIRMED" && status !== "PROJECTED") {
    throw new TypeError(`event status must be CONFIRMED or PROJECTED, got ${status}`);
  }
  const type = row.type as string;
  const closeTime = (row.close_time as string) || null;
  const openTime = (row.open_time as string) || null;
  if (type === "SPECIAL_SESSION") {
    if (!openTime || !closeTime) throw new TypeError(`a SPECIAL_SESSION on ${row.date} needs open_time and close_time`);
    if (compareTime(openTime, closeTime) >= 0) throw new TypeError(`a SPECIAL_SESSION on ${row.date} must open before it closes`);
  } else if (openTime) {
    throw new TypeError(`open_time is only valid on a SPECIAL_SESSION, got ${type} on ${row.date}`);
  }
  return {
    date: asIso(row.date as string),
    type,
    description: (row.description as string) || "",
    key: (row.key as string) || null,
    sourceModule: (row.source_module as string) || null,
    observedFrom: row.observed_from ? asIso(row.observed_from as string) : null,
    closeTime,
    status,
    openTime,
  };
}

function identity(event: Event): string {
  return JSON.stringify([event.date, event.type, event.description, event.key, event.sourceModule, event.observedFrom, event.closeTime, event.status, event.openTime ?? null]);
}

function coverageIntervals(rows: unknown): CoverageInterval[] {
  if (rows == null) return [];
  if (!Array.isArray(rows)) throw new TypeError("coverage.quality must be an array");
  return rows.map((row: Record<string, unknown>) => {
    const evidence = row.evidence_ids ?? row.evidenceIds ?? [];
    if (!Array.isArray(evidence)) throw new TypeError("coverage evidence_ids must be an array");
    return {
      scope: row.scope as CoverageInterval["scope"],
      from: asIso(row.from as string),
      to: asIso(row.to as string),
      quality: row.quality as CoverageInterval["quality"],
      evidenceIds: [...(evidence as string[])],
    };
  });
}

function nativeDate(raw: Record<string, unknown> | null | undefined): NativeDate | null {
  if (!raw) return null;
  return { chronologyId: raw.chronology_id as string, year: raw.year as number, monthCode: raw.month_code as string, day: raw.day as number };
}

export function loadCalendar(calendarId: string): CalendarData {
  const cached = calendarCache.get(calendarId);
  if (cached) return cached;
  const metadata = JSON.parse(readText(calendarId, "metadata.json")) as Record<string, unknown>;
  const rows = parseCsv(readText(calendarId, "holidays.csv")).map(eventFromRow);
  rows.sort((a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : 0));
  const eventsByDate = new Map<string, Event[]>();
  for (const event of rows) {
    const list = eventsByDate.get(event.date);
    if (list) list.push(event);
    else eventsByDate.set(event.date, [event]);
  }
  const detailsByDate = new Map<string, EventDetails[]>();
  if (Array.isArray(metadata.event_details)) {
    const remaining = new Map<string, Record<string, unknown>[]>();
    for (const detail of metadata.event_details as Record<string, unknown>[]) {
      const key = identity(eventFromRow(detail));
      const queue = remaining.get(key);
      if (queue) queue.push(detail);
      else remaining.set(key, [detail]);
    }
    for (const event of rows) {
      const queue = remaining.get(identity(event));
      const detail = queue?.shift();
      if (!detail) throw new TypeError(`Missing event provenance occurrence: ${identity(event)}`);
      const value: EventDetails = {
        event,
        rawStatus: event.status,
        effectiveStatus: event.status,
        evidenceIds: [...((detail.evidence_ids as string[]) ?? [])],
        nominalNativeDate: nativeDate(detail.nominal_native_date as Record<string, unknown> | null),
        chronologyProfile: (detail.chronology_profile as string) ?? null,
        chronologyProvider: (detail.chronology_provider as string) ?? null,
        observationLineage: [...((detail.observation_lineage as string[]) ?? [])],
      };
      const list = detailsByDate.get(event.date);
      if (list) list.push(value);
      else detailsByDate.set(event.date, [value]);
    }
    for (const queue of remaining.values()) {
      if (queue.length) throw new TypeError("Event provenance contains extra occurrences");
    }
  }
  const coverage = (metadata.coverage ?? {}) as Record<string, unknown>;
  if (typeof coverage !== "object" || Array.isArray(coverage)) throw new TypeError("coverage must be an object");
  const data: CalendarData = {
    calendarId,
    metadata,
    eventsByDate,
    detailsByDate,
    weekendPolicy: WeekendPolicy.fromJson(metadata.weekend_policy as never),
    rangeFrom: asIso(metadata.range_start as string),
    rangeTo: asIso(metadata.range_end as string),
    verifiedThrough: coverage.verified_through ? asIso(coverage.verified_through as string) : null,
    coverageIntervals: coverageIntervals(coverage.quality),
    timezone: (metadata.timezone as string) ?? null,
    name: (metadata.calendar_name as string) ?? calendarId,
    kind: (metadata.kind as string) ?? "market",
  };
  calendarCache.set(calendarId, data);
  return data;
}
