# Payment calendars

The payment calendars answer a bounded, date-only question: whether the named wholesale payment
system has an operating business date. They do not describe intraday sessions, message cutoffs,
optional participant availability, instruments or adjacent services. Each calendar uses
`metadata.kind: payment`; payment-system IDs are independent of exchange MIC aliases.

All three initial calendars cover 2026-01-01 through 2027-12-31 inclusive. Scheduled closures are
`VERIFIED` because an operator rule is paired with exact official dates. `EARLY_CLOSES` and
`UNSCHEDULED_EXCEPTIONS` are `PROJECTED`: the operators' ordinary operating-day rules support a
usable baseline with no additional date-level exceptions, while this dataset does not claim an
exhaustive record of actual operating incidents or day-specific schedule extensions. A projected
answer remains queryable and carries `PROJECTED` effective confidence. Dates outside the
advertised interval are unsupported rather than extrapolated.

## EU-TARGET

`EU-TARGET` means euro-denominated T2 RTGS business dates for customer and interbank payments,
including the T2 central liquidity management calendar on which RTGS depends. It does not mean all
TARGET Services: TIPS remains available continuously, and other currencies can use different T2
calendars.

The current ECB schedule closes T2 on Saturday, Sunday, 1 January, Good Friday, Easter Monday,
1 May, 25 December and 26 December. A named holiday on a weekend has no substitute weekday. For
example, 1 May 2027 is already Saturday and 3 May remains an operating business date. The ECB's
May 2026 roadmap plans a limited weekend or holiday liquidity-transfer window within two years,
without changing value dating; broader RTGS operating-day expansion remains under consideration
and is outside this calendar.

## US-FEDWIRE

`US-FEDWIRE` means funds-transfer business dates for the Fedwire Funds Service. It excludes Fedwire
Securities, the National Settlement Service, FedACH and FedNow. Federal Reserve Banks currently
observe weekends and the published Federal Reserve holiday table. When a holiday is Saturday the
preceding Friday remains open; when it is Sunday the following Monday closes. Thus 3 July 2026 and
18 June 2027 are open, while 5 July 2027 is closed for Independence Day observed from Sunday.

The Federal Reserve has announced Sunday-through-Friday operation, including weekday holidays, for
2028 or 2029. The exact implementation year is not yet fixed, so this calendar stops at the end of
2027 even though the general holiday table continues through 2030.

## GB-CHAPS

`GB-CHAPS` means business dates for the Bank of England's CHAPS high-value sterling payment system.
The operator states that CHAPS normally runs Monday-Friday except bank and public holidays in
England and Wales. The calendar transcribes GOV.UK's complete 2026 and 2027 lists, including Boxing
Day on 28 December 2026 and the Christmas and Boxing Day substitutes on 27-28 December 2027.

CHAPS will open earlier on weekdays from September 2027, which changes hours rather than operating
dates. A May 2026 consultation proposes Sunday and selected bank-holiday settlement no earlier than
2029; it is not an effective calendar change.
