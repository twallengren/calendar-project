package com.bdc.calendar;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal JSON reader for the bundled calendar metadata.
 *
 * <p>The core jar carries no third-party dependencies so that adding it to an application can never
 * collide with the Jackson (or Gson, or Moshi) version that application already uses. The only JSON
 * it ever reads is the metadata it ships itself — a known, flat shape — so a small
 * recursive-descent parser is enough. It is deliberately strict: anything the emitter would not
 * write is an error.
 *
 * <p>Objects become {@link LinkedHashMap}, arrays {@link List}, numbers {@link Double}, and {@code
 * null} stays null.
 */
final class Json {

  private final String text;
  private int pos;

  private Json(String text) {
    this.text = text;
  }

  /** Parses a whole JSON document. */
  static Object parse(String text) {
    Json parser = new Json(text);
    parser.skipWhitespace();
    Object value = parser.readValue();
    parser.skipWhitespace();
    if (parser.pos != text.length()) {
      throw parser.error("trailing content");
    }
    return value;
  }

  /** Parses a document that must be a JSON object. */
  @SuppressWarnings("unchecked")
  static Map<String, Object> parseObject(String text) {
    Object value = parse(text);
    if (!(value instanceof Map)) {
      throw new IllegalArgumentException("Expected a JSON object, got " + describe(value));
    }
    return (Map<String, Object>) value;
  }

  private Object readValue() {
    if (pos >= text.length()) {
      throw error("unexpected end of input");
    }
    char c = text.charAt(pos);
    switch (c) {
      case '{':
        return readObject();
      case '[':
        return readArray();
      case '"':
        return readString();
      case 't':
        expect("true");
        return Boolean.TRUE;
      case 'f':
        expect("false");
        return Boolean.FALSE;
      case 'n':
        expect("null");
        return null;
      default:
        return readNumber();
    }
  }

  private Map<String, Object> readObject() {
    Map<String, Object> result = new LinkedHashMap<>();
    pos++; // '{'
    skipWhitespace();
    if (peek() == '}') {
      pos++;
      return result;
    }
    while (true) {
      skipWhitespace();
      String key = readString();
      skipWhitespace();
      if (peek() != ':') {
        throw error("expected ':' after object key");
      }
      pos++;
      skipWhitespace();
      result.put(key, readValue());
      skipWhitespace();
      char c = peek();
      pos++;
      if (c == '}') {
        return result;
      }
      if (c != ',') {
        throw error("expected ',' or '}' in object");
      }
    }
  }

  private List<Object> readArray() {
    List<Object> result = new ArrayList<>();
    pos++; // '['
    skipWhitespace();
    if (peek() == ']') {
      pos++;
      return result;
    }
    while (true) {
      skipWhitespace();
      result.add(readValue());
      skipWhitespace();
      char c = peek();
      pos++;
      if (c == ']') {
        return result;
      }
      if (c != ',') {
        throw error("expected ',' or ']' in array");
      }
    }
  }

  private String readString() {
    if (peek() != '"') {
      throw error("expected a string");
    }
    pos++;
    StringBuilder out = new StringBuilder();
    while (true) {
      if (pos >= text.length()) {
        throw error("unterminated string");
      }
      char c = text.charAt(pos++);
      if (c == '"') {
        return out.toString();
      }
      if (c != '\\') {
        out.append(c);
        continue;
      }
      char escape = text.charAt(pos++);
      switch (escape) {
        case '"' -> out.append('"');
        case '\\' -> out.append('\\');
        case '/' -> out.append('/');
        case 'b' -> out.append('\b');
        case 'f' -> out.append('\f');
        case 'n' -> out.append('\n');
        case 'r' -> out.append('\r');
        case 't' -> out.append('\t');
        case 'u' -> {
          out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
          pos += 4;
        }
        default -> throw error("unknown escape \\" + escape);
      }
    }
  }

  private Double readNumber() {
    int start = pos;
    while (pos < text.length() && "+-.eE0123456789".indexOf(text.charAt(pos)) >= 0) {
      pos++;
    }
    if (start == pos) {
      throw error("expected a value");
    }
    return Double.valueOf(text.substring(start, pos));
  }

  private char peek() {
    if (pos >= text.length()) {
      throw error("unexpected end of input");
    }
    return text.charAt(pos);
  }

  private void expect(String literal) {
    if (!text.startsWith(literal, pos)) {
      throw error("expected " + literal);
    }
    pos += literal.length();
  }

  private void skipWhitespace() {
    while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
      pos++;
    }
  }

  private IllegalArgumentException error(String message) {
    return new IllegalArgumentException("Malformed JSON at offset " + pos + ": " + message);
  }

  private static String describe(Object value) {
    return value == null ? "null" : value.getClass().getSimpleName();
  }
}
