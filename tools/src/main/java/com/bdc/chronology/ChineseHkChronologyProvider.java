package com.bdc.chronology;

import com.ibm.icu.util.Calendar;
import com.ibm.icu.util.ChineseCalendar;
import com.ibm.icu.util.TimeZone;
import com.ibm.icu.util.ULocale;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Modern Chinese lunisolar calendar at the civil midnight of fixed UTC+08:00. */
public final class ChineseHkChronologyProvider implements ChronologyProvider {
  private static final ZoneOffset PROFILE_OFFSET = ZoneOffset.ofHours(8);
  private static final TimeZone ICU_ZONE = TimeZone.getTimeZone("GMT+08:00");
  private static final ChronologyDescriptor DESCRIPTOR =
      new ChronologyDescriptor(
          "CHINESE_HK",
          "modern-chinese-fixed-utc+08:00",
          "ICU4J 78.3",
          LocalDate.of(1929, 1, 1),
          LocalDate.of(2100, 12, 31),
          385);
  private static final Map<Integer, YearData> YEARS = new ConcurrentHashMap<>();
  private static final Map<Integer, LocalDate> NEW_YEARS = new ConcurrentHashMap<>();

  private record MonthData(LocalDate start, int length) {}

  private record YearData(int extendedYear, List<String> months, Map<String, MonthData> data) {}

  private static ChineseCalendar calendar() {
    ChineseCalendar calendar = new ChineseCalendar(ICU_ZONE, ULocale.ROOT);
    calendar.setLenient(false);
    calendar.clear();
    return calendar;
  }

  private static ChineseCalendar atIso(LocalDate date) {
    ChineseCalendar calendar = calendar();
    // Sample the civil date at noon. ICU's astronomical calculations use the profile's fixed
    // GMT+8 zone, while noon keeps the lookup away from an instant exactly on a civil boundary.
    calendar.setTimeInMillis(date.atTime(LocalTime.NOON).toInstant(PROFILE_OFFSET).toEpochMilli());
    return calendar;
  }

  private static String monthCode(ChineseCalendar calendar) {
    int month = calendar.get(Calendar.MONTH) + 1;
    return "M%02d%s".formatted(month, calendar.get(Calendar.IS_LEAP_MONTH) == 1 ? "L" : "");
  }

  private static LocalDate findNewYear(int gregorianYear) {
    for (LocalDate date = LocalDate.of(gregorianYear, 1, 20);
        !date.isAfter(LocalDate.of(gregorianYear, 2, 21));
        date = date.plusDays(1)) {
      ChineseCalendar calendar = atIso(date);
      if (calendar.get(Calendar.MONTH) == 0
          && calendar.get(Calendar.IS_LEAP_MONTH) == 0
          && calendar.get(Calendar.DAY_OF_MONTH) == 1) return date;
    }
    throw new IllegalStateException("ICU did not find Chinese New Year in " + gregorianYear);
  }

  private static LocalDate newYear(int gregorianYear) {
    return NEW_YEARS.computeIfAbsent(gregorianYear, ChineseHkChronologyProvider::findNewYear);
  }

  private static YearData loadYear(int nativeYear) {
    if (nativeYear < 1928 || nativeYear > 2100)
      throw new UnsupportedChronologyRangeException(
          "CHINESE_HK unsupported native year: " + nativeYear);
    LocalDate start = newYear(nativeYear);
    LocalDate end = newYear(nativeYear + 1);
    ChineseCalendar first = atIso(start);
    int extendedYear = first.get(Calendar.EXTENDED_YEAR);
    Map<String, LocalDate> starts = new LinkedHashMap<>();
    for (LocalDate date = start; date.isBefore(end); date = date.plusDays(1)) {
      ChineseCalendar calendar = atIso(date);
      if (calendar.get(Calendar.EXTENDED_YEAR) != extendedYear)
        throw new IllegalStateException("ICU Chinese extended year changed before New Year");
      if (calendar.get(Calendar.DAY_OF_MONTH) == 1) starts.put(monthCode(calendar), date);
    }
    List<String> months = List.copyOf(starts.keySet());
    Map<String, MonthData> data = new LinkedHashMap<>();
    for (int i = 0; i < months.size(); i++) {
      LocalDate monthStart = starts.get(months.get(i));
      LocalDate next = i + 1 < months.size() ? starts.get(months.get(i + 1)) : end;
      data.put(
          months.get(i),
          new MonthData(monthStart, Math.toIntExact(next.toEpochDay() - monthStart.toEpochDay())));
    }
    return new YearData(extendedYear, months, Map.copyOf(data));
  }

  private static YearData year(int nativeYear) {
    return YEARS.computeIfAbsent(nativeYear, ChineseHkChronologyProvider::loadYear);
  }

  @Override
  public ChronologyDescriptor descriptor() {
    return DESCRIPTOR;
  }

  @Override
  public void validateMonthCode(String code) {
    if (code == null || !code.matches("M(?:0[1-9]|1[0-2])L?"))
      throw new IllegalArgumentException("Unknown Chinese month code: " + code);
  }

  @Override
  public List<String> months(int nativeYear) {
    return year(nativeYear).months();
  }

  private static MonthData requireMonth(int nativeYear, String monthCode) {
    YearData year = year(nativeYear);
    MonthData month = year.data().get(monthCode);
    if (month == null)
      throw new IllegalArgumentException(
          "Month " + monthCode + " absent in Chinese year " + nativeYear);
    return month;
  }

  @Override
  public int monthLength(int nativeYear, String monthCode) {
    validateMonthCode(monthCode);
    return requireMonth(nativeYear, monthCode).length();
  }

  @Override
  public LocalDate monthStart(int nativeYear, String monthCode) {
    validateMonthCode(monthCode);
    return requireMonth(nativeYear, monthCode).start();
  }

  @Override
  public int maximumDayOfMonth(String monthCode) {
    validateMonthCode(monthCode);
    return 30;
  }

  @Override
  public LocalDate toIso(NativeDate date) {
    if (!"CHINESE_HK".equals(date.chronologyId()))
      throw new IllegalArgumentException("Expected CHINESE_HK date");
    validateMonthCode(date.monthCode());
    MonthData month = requireMonth(date.year(), date.monthCode());
    if (date.day() < 1 || date.day() > month.length())
      throw new IllegalArgumentException(
          "Invalid Chinese date: " + date.year() + "-" + date.monthCode() + "-" + date.day());
    LocalDate iso = month.start().plusDays(date.day() - 1L);
    DESCRIPTOR.requireSupported(iso);
    NativeDate roundTrip = fromIso(iso);
    if (!roundTrip.equals(date))
      throw new IllegalStateException("ICU Chinese conversion did not preserve date identity");
    return iso;
  }

  @Override
  public NativeDate fromIso(LocalDate date) {
    DESCRIPTOR.requireSupported(date);
    int nativeYear = date.isBefore(newYear(date.getYear())) ? date.getYear() - 1 : date.getYear();
    ChineseCalendar calendar = atIso(date);
    YearData year = year(nativeYear);
    if (calendar.get(Calendar.EXTENDED_YEAR) != year.extendedYear())
      throw new IllegalStateException("ICU Chinese native-year mapping mismatch on " + date);
    NativeDate result =
        new NativeDate(
            "CHINESE_HK", nativeYear, monthCode(calendar), calendar.get(Calendar.DAY_OF_MONTH));
    MonthData month = year.data().get(result.monthCode());
    if (month == null || !month.start().plusDays(result.day() - 1L).equals(date))
      throw new IllegalStateException("ICU Chinese conversion did not preserve civil date " + date);
    return result;
  }
}
