package com.bdc.chronology;

import com.ibm.icu.util.Calendar;
import com.ibm.icu.util.HebrewCalendar;
import com.ibm.icu.util.TimeZone;
import com.ibm.icu.util.ULocale;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Fixed arithmetic Hebrew calendar, civil midnight mapping; no sunset instants. */
public final class HebrewChronologyProvider implements ChronologyProvider {
  private static final List<String> MONTHS =
      List.of(
          "TISHRI", "HESHVAN", "KISLEV", "TEVET", "SHEVAT", "ADAR_I", "ADAR_II", "NISAN", "IYAR",
          "SIVAN", "TAMUZ", "AV", "ELUL");
  private static final ChronologyDescriptor DESCRIPTOR =
      new ChronologyDescriptor(
          "HEBREW",
          "fixed-arithmetic-hebrew-civil",
          "ICU4J 78.3",
          LocalDate.of(1901, 1, 1),
          LocalDate.of(2100, 12, 31),
          385);

  private static HebrewCalendar calendar() {
    HebrewCalendar calendar = new HebrewCalendar(TimeZone.getTimeZone("UTC"), ULocale.ROOT);
    calendar.setLenient(false);
    calendar.clear();
    return calendar;
  }

  private static boolean leap(int year) {
    return Math.floorMod(7L * year + 1, 19) < 7;
  }

  private static void requireYear(int year) {
    // Boundary years intersect the advertised ISO range. Conversion checks the precise date.
    if (year < 5661 || year > 5861)
      throw new UnsupportedChronologyRangeException("HEBREW unsupported native year: " + year);
  }

  public ChronologyDescriptor descriptor() {
    return DESCRIPTOR;
  }

  public void validateMonthCode(String code) {
    if (!MONTHS.contains(code) && !"ADAR".equals(code))
      throw new IllegalArgumentException("Unknown Hebrew month code: " + code);
  }

  public List<String> months(int year) {
    requireYear(year);
    if (leap(year)) return MONTHS;
    List<String> months = new ArrayList<>(MONTHS);
    months.remove("ADAR_I");
    months.set(months.indexOf("ADAR_II"), "ADAR");
    return List.copyOf(months);
  }

  private static int monthIndex(String code) {
    return "ADAR".equals(code) ? HebrewCalendar.ADAR : MONTHS.indexOf(code);
  }

  private HebrewCalendar at(int year, String monthCode, int day) {
    validateMonthCode(monthCode);
    if (!months(year).contains(monthCode))
      throw new IllegalArgumentException("Month " + monthCode + " absent in Hebrew year " + year);
    HebrewCalendar calendar = calendar();
    calendar.set(year, monthIndex(monthCode), day);
    calendar.getTimeInMillis();
    return calendar;
  }

  public int monthLength(int year, String monthCode) {
    return at(year, monthCode, 1).getActualMaximum(Calendar.DAY_OF_MONTH);
  }

  public LocalDate monthStart(int year, String monthCode) {
    return LocalDate.ofEpochDay(
        Math.floorDiv(at(year, monthCode, 1).getTimeInMillis(), 86_400_000L));
  }

  public int maximumDayOfMonth(String monthCode) {
    validateMonthCode(monthCode);
    return 30;
  }

  public LocalDate toIso(NativeDate date) {
    if (!"HEBREW".equals(date.chronologyId()))
      throw new IllegalArgumentException("Expected HEBREW date");
    LocalDate iso =
        LocalDate.ofEpochDay(
            Math.floorDiv(
                at(date.year(), date.monthCode(), date.day()).getTimeInMillis(), 86_400_000L));
    DESCRIPTOR.requireSupported(iso);
    return iso;
  }

  public NativeDate fromIso(LocalDate date) {
    DESCRIPTOR.requireSupported(date);
    HebrewCalendar calendar = calendar();
    calendar.setTimeInMillis(Math.multiplyExact(date.toEpochDay(), 86_400_000L));
    int year = calendar.get(Calendar.YEAR);
    String month = MONTHS.get(calendar.get(Calendar.MONTH));
    if (month.equals("ADAR_II") && !leap(year)) month = "ADAR";
    return new NativeDate("HEBREW", year, month, calendar.get(Calendar.DAY_OF_MONTH));
  }
}
