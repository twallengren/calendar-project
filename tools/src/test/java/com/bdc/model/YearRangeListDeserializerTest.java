package com.bdc.model;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class YearRangeListDeserializerTest {

  private ObjectMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new ObjectMapper();
  }

  /** Helper to deserialize JSON into a TestWrapper containing the year ranges. */
  private List<EventSource.YearRange> deserialize(String json) throws JsonProcessingException {
    return mapper.readValue(json, TestWrapper.class).activeYears();
  }

  /** Wrapper class to test the deserializer in context. */
  record TestWrapper(
      @com.fasterxml.jackson.annotation.JsonProperty("active_years")
          @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
              using = YearRangeListDeserializer.class)
          List<EventSource.YearRange> activeYears) {}

  @Nested
  class SingleYearParsing {

    @Test
    void parseSingleYear() throws Exception {
      List<EventSource.YearRange> ranges = deserialize("{\"active_years\": [1972]}");

      assertEquals(1, ranges.size());
      assertEquals(Integer.valueOf(1972), ranges.get(0).start());
      assertEquals(Integer.valueOf(1972), ranges.get(0).end());
    }

    @Test
    void parseMultipleSingleYears() throws Exception {
      List<EventSource.YearRange> ranges = deserialize("{\"active_years\": [1972, 1976, 1980]}");

      assertEquals(3, ranges.size());
      assertEquals(Integer.valueOf(1972), ranges.get(0).start());
      assertEquals(Integer.valueOf(1976), ranges.get(1).start());
      assertEquals(Integer.valueOf(1980), ranges.get(2).start());
    }

    @Test
    void parseYearZero() throws Exception {
      List<EventSource.YearRange> ranges = deserialize("{\"active_years\": [0]}");

      assertEquals(1, ranges.size());
      assertEquals(Integer.valueOf(0), ranges.get(0).start());
      assertEquals(Integer.valueOf(0), ranges.get(0).end());
    }

    @Test
    void parseNegativeYear() throws Exception {
      // BCE years could be represented as negative
      List<EventSource.YearRange> ranges = deserialize("{\"active_years\": [-500]}");

      assertEquals(1, ranges.size());
      assertEquals(Integer.valueOf(-500), ranges.get(0).start());
      assertEquals(Integer.valueOf(-500), ranges.get(0).end());
    }
  }

  @Nested
  class ArrayRangeParsing {

    @Test
    void parseClosedRange() throws Exception {
      List<EventSource.YearRange> ranges = deserialize("{\"active_years\": [[1990, 2000]]}");

      assertEquals(1, ranges.size());
      assertEquals(Integer.valueOf(1990), ranges.get(0).start());
      assertEquals(Integer.valueOf(2000), ranges.get(0).end());
    }

    @Test
    void parseOpenEndedRangeFromInception() throws Exception {
      List<EventSource.YearRange> ranges = deserialize("{\"active_years\": [[null, 1968]]}");

      assertEquals(1, ranges.size());
      assertNull(ranges.get(0).start());
      assertEquals(Integer.valueOf(1968), ranges.get(0).end());
    }

    @Test
    void parseOpenEndedRangeToFuture() throws Exception {
      List<EventSource.YearRange> ranges = deserialize("{\"active_years\": [[2020, null]]}");

      assertEquals(1, ranges.size());
      assertEquals(Integer.valueOf(2020), ranges.get(0).start());
      assertNull(ranges.get(0).end());
    }

    @Test
    void parseFullyOpenRange() throws Exception {
      // Both null - matches all years
      List<EventSource.YearRange> ranges = deserialize("{\"active_years\": [[null, null]]}");

      assertEquals(1, ranges.size());
      assertNull(ranges.get(0).start());
      assertNull(ranges.get(0).end());
    }

    @Test
    void parseSameStartAndEnd() throws Exception {
      List<EventSource.YearRange> ranges = deserialize("{\"active_years\": [[2020, 2020]]}");

      assertEquals(1, ranges.size());
      assertEquals(Integer.valueOf(2020), ranges.get(0).start());
      assertEquals(Integer.valueOf(2020), ranges.get(0).end());
    }
  }

  @Nested
  class MixedFormatParsing {

    @Test
    void parseMixedFormats() throws Exception {
      String json =
          "{\"active_years\": [[null, 1968], 1972, 1976, 1980, [1990, 2000], [2020, null]]}";
      List<EventSource.YearRange> ranges = deserialize(json);

      assertEquals(6, ranges.size());

      // [null, 1968]
      assertNull(ranges.get(0).start());
      assertEquals(Integer.valueOf(1968), ranges.get(0).end());

      // 1972
      assertEquals(Integer.valueOf(1972), ranges.get(1).start());
      assertEquals(Integer.valueOf(1972), ranges.get(1).end());

      // 1976
      assertEquals(Integer.valueOf(1976), ranges.get(2).start());

      // 1980
      assertEquals(Integer.valueOf(1980), ranges.get(3).start());

      // [1990, 2000]
      assertEquals(Integer.valueOf(1990), ranges.get(4).start());
      assertEquals(Integer.valueOf(2000), ranges.get(4).end());

      // [2020, null]
      assertEquals(Integer.valueOf(2020), ranges.get(5).start());
      assertNull(ranges.get(5).end());
    }
  }

  @Nested
  class EmptyAndNullHandling {

    @Test
    void parseEmptyArray() throws Exception {
      List<EventSource.YearRange> ranges = deserialize("{\"active_years\": []}");

      assertNotNull(ranges);
      assertTrue(ranges.isEmpty());
    }

    @Test
    void parseNullActiveYears() throws Exception {
      TestWrapper wrapper = mapper.readValue("{\"active_years\": null}", TestWrapper.class);

      assertNull(wrapper.activeYears());
    }

    @Test
    void parseMissingActiveYears() throws Exception {
      TestWrapper wrapper = mapper.readValue("{}", TestWrapper.class);

      assertNull(wrapper.activeYears());
    }
  }

  @Nested
  class ValidationErrors {

    @Test
    void rejectInvalidRangeStartGreaterThanEnd() {
      JsonProcessingException ex =
          assertThrows(
              JsonProcessingException.class,
              () -> deserialize("{\"active_years\": [[2000, 1990]]}"));

      assertTrue(ex.getMessage().contains("start") && ex.getMessage().contains("end"));
    }

    @Test
    void rejectArrayWithOneElement() {
      JsonProcessingException ex =
          assertThrows(
              JsonProcessingException.class, () -> deserialize("{\"active_years\": [[1990]]}"));

      assertTrue(ex.getMessage().contains("2 elements"));
    }

    @Test
    void rejectArrayWithThreeElements() {
      JsonProcessingException ex =
          assertThrows(
              JsonProcessingException.class,
              () -> deserialize("{\"active_years\": [[1990, 2000, 2010]]}"));

      assertTrue(ex.getMessage().contains("2 elements"));
    }

    @Test
    void rejectArrayWithZeroElements() {
      JsonProcessingException ex =
          assertThrows(
              JsonProcessingException.class, () -> deserialize("{\"active_years\": [[]]}"));

      assertTrue(ex.getMessage().contains("2 elements"));
    }

    @Test
    void rejectStringInsteadOfInteger() {
      assertThrows(
          JsonProcessingException.class, () -> deserialize("{\"active_years\": [\"1972\"]}"));
    }

    @Test
    void rejectStringInArrayRange() {
      assertThrows(
          JsonProcessingException.class,
          () -> deserialize("{\"active_years\": [[\"1990\", 2000]]}"));
    }

    @Test
    void rejectObjectInsteadOfIntegerOrArray() {
      JsonProcessingException ex =
          assertThrows(
              JsonProcessingException.class,
              () -> deserialize("{\"active_years\": [{\"year\": 1972}]}"));

      assertTrue(ex.getMessage().contains("integer or [start, end] array"));
    }

    @Test
    void rejectNonArrayActiveYears() {
      JsonProcessingException ex =
          assertThrows(
              JsonProcessingException.class, () -> deserialize("{\"active_years\": 1972}"));

      assertTrue(ex.getMessage().contains("must be an array"));
    }

    @Test
    void rejectNestedArrays() {
      // [[1990, 2000]] is an array with 1 element (the inner array), not 2
      JsonProcessingException ex =
          assertThrows(
              JsonProcessingException.class,
              () -> deserialize("{\"active_years\": [[[1990, 2000]]]}"));

      assertTrue(ex.getMessage().contains("2 elements"));
    }

    @Test
    void rejectArrayInRangeElement() {
      // [1990, [2000]] - second element is an array, not an integer
      JsonProcessingException ex =
          assertThrows(
              JsonProcessingException.class,
              () -> deserialize("{\"active_years\": [[1990, [2000]]]}"));

      assertTrue(ex.getMessage().contains("integer or null"));
    }
  }

  @Nested
  class BoundaryConditions {

    @Test
    void parseMinIntegerYear() throws Exception {
      List<EventSource.YearRange> ranges =
          deserialize("{\"active_years\": [" + Integer.MIN_VALUE + "]}");

      assertEquals(1, ranges.size());
      assertEquals(Integer.valueOf(Integer.MIN_VALUE), ranges.get(0).start());
    }

    @Test
    void parseMaxIntegerYear() throws Exception {
      List<EventSource.YearRange> ranges =
          deserialize("{\"active_years\": [" + Integer.MAX_VALUE + "]}");

      assertEquals(1, ranges.size());
      assertEquals(Integer.valueOf(Integer.MAX_VALUE), ranges.get(0).start());
    }

    @Test
    void parseRangeAtIntegerBoundaries() throws Exception {
      String json = "{\"active_years\": [[" + Integer.MIN_VALUE + ", " + Integer.MAX_VALUE + "]]}";
      List<EventSource.YearRange> ranges = deserialize(json);

      assertEquals(1, ranges.size());
      assertEquals(Integer.valueOf(Integer.MIN_VALUE), ranges.get(0).start());
      assertEquals(Integer.valueOf(Integer.MAX_VALUE), ranges.get(0).end());
    }

    @Test
    void parseConsecutiveYears() throws Exception {
      List<EventSource.YearRange> ranges = deserialize("{\"active_years\": [[1999, 2000]]}");

      assertEquals(1, ranges.size());
      assertEquals(Integer.valueOf(1999), ranges.get(0).start());
      assertEquals(Integer.valueOf(2000), ranges.get(0).end());
    }
  }
}
