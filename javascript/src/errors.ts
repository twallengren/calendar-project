/** Errors raised by bdc-calendars. */

export class BdcCalendarError extends Error {
  constructor(message: string) {
    super(message);
    this.name = new.target.name;
  }
}

/** No calendar (or alias) with the requested id is bundled with this release. */
export class CalendarNotFoundError extends BdcCalendarError {
  readonly calendarId: string;
  readonly known: string[];

  constructor(calendarId: string, known: string[] = []) {
    super(
      `Unknown calendar id: ${JSON.stringify(calendarId)}` +
        (known.length ? `. Known ids: ${known.join(", ")}` : ""),
    );
    this.calendarId = calendarId;
    this.known = [...known];
  }
}

/**
 * The date falls outside the window a calendar can answer for. Outside the range the absence of
 * a closure row means "not known", never "open", so every operation except `status` refuses to
 * answer rather than implying a trading day.
 */
export class OutsideCoverageError extends BdcCalendarError {
  readonly calendarId: string;
  readonly date: string;
  readonly rangeFrom: string;
  readonly rangeTo: string;

  constructor(calendarId: string, date: string, rangeFrom: string, rangeTo: string, message?: string) {
    super(message ?? `${date} is outside the covered range of ${calendarId} (${rangeFrom} to ${rangeTo})`);
    this.calendarId = calendarId;
    this.date = date;
    this.rangeFrom = rangeFrom;
    this.rangeTo = rangeTo;
  }
}

/** The date is in range but a coverage scope the question depends on is unresolved. */
export class UnresolvedDateError extends OutsideCoverageError {
  readonly incompleteScopes: string[];

  constructor(calendarId: string, date: string, rangeFrom: string, rangeTo: string, incompleteScopes: string[]) {
    super(
      calendarId,
      date,
      rangeFrom,
      rangeTo,
      `${date} has incomplete calendar coverage for ${calendarId}: ${incompleteScopes.join(", ")}`,
    );
    this.incompleteScopes = [...incompleteScopes];
  }
}
