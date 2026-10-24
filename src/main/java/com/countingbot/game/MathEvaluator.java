package com.countingbot.game;

public final class MathEvaluator {

    private MathEvaluator() {
    }

    /**
     * Evaluates an arithmetic expression (+ - * / % ^ and parentheses).
     * Returns null when the expression cannot be evaluated.
     */
    public static Double evaluate(String expression) {
        if (expression == null) {
            return null;
        }
        String s = expression
                .replace('×', '*')
                .replace('÷', '/')
                .replace(" ", "");
        if (s.isEmpty()) {
            return null;
        }
        try {
            Parser parser = new Parser(s);
            double value = parser.expression();
            if (!parser.done()) {
                return null;
            }
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return null;
            }
            return value;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static final class Parser {

        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        boolean done() {
            return i >= s.length();
        }

        double expression() {
            double v = term();
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == '+' || c == '-') {
                    i++;
                    double r = term();
                    v = (c == '+') ? v + r : v - r;
                } else {
                    break;
                }
            }
            return v;
        }

        double term() {
            double v = unary();
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == '*' || c == '/' || c == '%') {
                    i++;
                    double r = unary();
                    v = (c == '*') ? v * r : (c == '/') ? v / r : v % r;
                } else {
                    break;
                }
            }
            return v;
        }

        double unary() {
            if (i < s.length()) {
                char c = s.charAt(i);
                if (c == '-' || c == '+') {
                    i++;
                    double v = unary();
                    return c == '-' ? -v : v;
                }
            }
            return power();
        }

        double power() {
            double base = primary();
            if (i < s.length() && s.charAt(i) == '^') {
                i++;
                double exponent = unary();
                return Math.pow(base, exponent);
            }
            return base;
        }

        double primary() {
            if (i >= s.length()) {
                throw new IllegalStateException("unexpected end");
            }
            char c = s.charAt(i);
            if (c == '(') {
                i++;
                double v = expression();
                if (i >= s.length() || s.charAt(i) != ')') {
                    throw new IllegalStateException("missing )");
                }
                i++;
                return v;
            }
            int start = i;
            while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) {
                i++;
            }
            if (start == i) {
                throw new IllegalStateException("expected number");
            }
            return Double.parseDouble(s.substring(start, i));
        }
    }
}
