# Financial date operations contract

Implementation follows the TASE and HKEX integration gates. Keep Java `DateStream` and Python `BusinessCalendar` behavior aligned, and expose the same operations through CLI and MCP.

Conventions are `UNADJUSTED`, `FOLLOWING`, `MODIFIED_FOLLOWING`, `PRECEDING` and `MODIFIED_PRECEDING`. Following/preceding return the input when it is a resolved business date; otherwise search in the named direction. Modified conventions first search in their named direction and reverse from the original input if the first result crosses its calendar month. Unknown dates encountered in either search raise the coverage-compatible unresolved-date exception.

Unadjusted is an identity operation. Preserve existing zero business-day offset behavior. Neither identity operation implies the result is a business date. Other operations retain inclusive coverage checks and the joint requirement that every member be open.

Month advancement uses explicit integer calendar months, clips the nominal day to the destination month's length, then applies the selected adjustment convention. An explicit `preserve_end_of_month` flag means: if the original date is the source month's last business date, return the destination month's last business date. Do not infer this flag from the input. Document this precise business-month-end definition, including nonbusiness dates at calendar month end. Add an explicit last-business-day-of-month operation.

Rich operation results include the original date, resulting date, convention/operation parameters, effective confidence and the dates examined (or equivalent evidence sufficient to explain the confidence). Confidence includes every date used in a decision, including a failed-direction search for a modified convention and an end-of-month check. It must not be copied only from the destination. Identity operations may return UNKNOWN confidence without pretending to have established a business date.

Retain existing constructors and Python event tuples. Deprecate the earliest-local-time joint-close interpretation; add member close results carrying calendar ID, timezone and local time. These are existing early-close values, not a new session schedule. Rename generic settlement-helper explanations to business-date offsets and state that instrument eligibility and intraday cutoffs are outside the API.

Required boundary examples include month/year rollover, leap February, negative month increments, zero offsets, month-end preservation, modified-convention reversal across a long closure, incomplete dates on the search path, and joint calendars with different weekends/timezones. Check parity with recorded Java fixtures and actual browser execution for any browser operation introduced.
