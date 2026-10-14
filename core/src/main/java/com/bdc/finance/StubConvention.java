package com.bdc.finance;

/** Where an irregular period goes when a schedule's end date is not on the regular cycle. */
public enum StubConvention {
  /** Every period must be regular; an end date off the cycle is an error. */
  NONE,
  /** Roll forward from the start; a final period shorter than the regular one absorbs the rest. */
  SHORT_FINAL,
  /** Roll forward from the start; the final period is extended to absorb the rest. */
  LONG_FINAL,
  /** Roll backward from the end; a first period shorter than the regular one absorbs the rest. */
  SHORT_INITIAL,
  /** Roll backward from the end; the first period is extended to absorb the rest. */
  LONG_INITIAL
}
