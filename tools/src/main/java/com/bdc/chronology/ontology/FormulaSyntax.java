package com.bdc.chronology.ontology;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Typed, total syntax validation before a formula is embedded in generated Java. */
public final class FormulaSyntax {
  private static final Pattern TOKEN =
      Pattern.compile("\\s*(year|true|false|[0-9]+|&&|\\|\\||==|!=|<=|>=|[()+*%<>-])");
  private final List<String> tokens = new ArrayList<>();
  private int position;

  private FormulaSyntax(String expression) {
    var matcher = TOKEN.matcher(expression.strip());
    int end = 0;
    while (matcher.find()) {
      if (matcher.start() != end) throw invalid();
      tokens.add(matcher.group(1));
      end = matcher.end();
    }
    if (end != expression.strip().length()) throw invalid();
  }

  public static void validate(String expression) {
    if (expression == null || expression.isBlank()) return;
    var parser = new FormulaSyntax(expression);
    if (!parser.expression(0) || parser.position != parser.tokens.size()) throw invalid();
  }

  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException(
        "Invalid leap-year formula: expected a boolean expression using year, integer literals and supported operators");
  }

  private static int precedence(String token) {
    return switch (token) {
      case "||" -> 1;
      case "&&" -> 2;
      case "==", "!=", "<", ">", "<=", ">=" -> 3;
      case "+", "-" -> 4;
      case "*", "%" -> 5;
      default -> -1;
    };
  }

  // true = boolean type, false = integer type. Parse both branches even for literal booleans.
  private boolean expression(int minimum) {
    if (position >= tokens.size()) throw invalid();
    String token = tokens.get(position++);
    boolean type;
    if (token.equals("(")) {
      type = expression(0);
      if (position >= tokens.size() || !tokens.get(position++).equals(")")) throw invalid();
    } else if (token.equals("-") || token.equals("+")) {
      type = expression(6);
      if (type) throw invalid();
    } else if (token.equals("true") || token.equals("false")) type = true;
    else if (token.equals("year") || token.matches("[0-9]+")) {
      if (!token.equals("year")) Integer.parseInt(token);
      type = false;
    } else throw invalid();
    while (position < tokens.size()) {
      String operator = tokens.get(position);
      int precedence = precedence(operator);
      if (precedence < minimum) break;
      position++;
      boolean right = expression(precedence + 1);
      if (precedence <= 2) {
        if (!type || !right) throw invalid();
        type = true;
      } else {
        if (type || right) throw invalid();
        type = precedence == 3;
      }
    }
    return type;
  }
}
