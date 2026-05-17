package dev.bee.fsrs;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public final class FsrsEngineFixtureTest {
    private static final double TOLERANCE = 1.0e-9;

    @Test
    public void generatedReferenceFixtureIsTheEngineOracle() throws Exception {
        Map<String, Object> root = object(Json.parse(Files.readString(referenceCasesPath())));
        List<Object> cases = list(root.get("cases"));

        assertEquals(38, cases.size());
        for (Object rawCase : cases) {
            Map<String, Object> testCase = object(rawCase);
            String name = text(testCase, "name");
            switch (text(testCase, "kind")) {
                case "initial":
                    assertInitialCase(name, testCase);
                    break;
                case "review":
                    assertReviewCase(name, testCase);
                    break;
                case "interval":
                    assertIntervalCase(name, testCase);
                    break;
                case "shortTerm":
                    assertShortTermCase(name, testCase);
                    break;
                case "validation":
                    assertValidationCase(name, testCase);
                    break;
                default:
                    fail("Unknown fixture case kind for " + name);
            }
        }
    }

    private static void assertInitialCase(String name, Map<String, Object> testCase) {
        FsrsMemoryState state = engine(testCase).initialState(rating(testCase));
        assertState(name, state, object(testCase.get("expectedInitialState")));
    }

    private static void assertReviewCase(String name, Map<String, Object> testCase) {
        Map<String, Object> previous = object(testCase.get("previousState"));
        FsrsReviewOutput output = engine(testCase).review(new FsrsReviewInput(
                new FsrsMemoryState(number(previous, "stability"), number(previous, "difficulty")),
                rating(testCase),
                integer(testCase, "elapsedDays"),
                number(testCase, "desiredRetention"),
                integer(testCase, "maximumInterval")
        ));

        assertState(name, output.nextState(), object(testCase.get("expectedNextState")));
        assertEquals(name + " retrievability", number(testCase, "expectedRetrievability"),
                output.retrievability(), TOLERANCE);
        assertEquals(name + " interval", integer(testCase, "expectedNextIntervalDays"), output.nextIntervalDays());
    }

    private static void assertIntervalCase(String name, Map<String, Object> testCase) {
        int interval = engine(testCase).nextIntervalDays(
                number(testCase, "stability"),
                number(testCase, "desiredRetention"),
                integer(testCase, "maximumInterval")
        );

        assertEquals(name + " interval", integer(testCase, "expectedNextIntervalDays"), interval);
    }

    private static void assertShortTermCase(String name, Map<String, Object> testCase) {
        double stability = engine(testCase).shortTermStability(number(testCase, "stability"), rating(testCase));

        assertEquals(name + " short-term stability", number(testCase, "expectedShortTermStability"),
                stability, TOLERANCE);
    }

    private static void assertValidationCase(String name, Map<String, Object> testCase) {
        Map<String, Object> payload = object(testCase.get("payload"));
        expectIllegalArgument(name, () -> {
            switch (text(testCase, "target")) {
                case "FsrsParameters.of":
                    FsrsParameters.of(doubleArray(list(payload.get("parameters"))));
                    break;
                case "FsrsEngine.nextState":
                    Map<String, Object> previous = object(payload.get("previousState"));
                    engine(testCase).nextState(
                            new FsrsMemoryState(number(previous, "stability"), number(previous, "difficulty")),
                            rating(payload),
                            integer(payload, "elapsedDays")
                    );
                    break;
                case "FsrsMemoryState":
                    new FsrsMemoryState(number(payload, "stability"), number(payload, "difficulty"));
                    break;
                case "FsrsEngine.nextIntervalDays":
                    engine(testCase).nextIntervalDays(
                            number(payload, "stability"),
                            number(payload, "desiredRetention"),
                            integer(payload, "maximumInterval")
                    );
                    break;
                default:
                    fail("Unknown validation target for " + name);
            }
        });
    }

    private static FsrsEngine engine(Map<String, Object> testCase) {
        return FsrsEngine.create(FsrsParameters.of(doubleArray(list(testCase.get("parameters")))));
    }

    private static FsrsRating rating(Map<String, Object> values) {
        return FsrsRating.valueOf(text(values, "rating"));
    }

    private static void assertState(String name, FsrsMemoryState state, Map<String, Object> expected) {
        assertEquals(name + " stability", number(expected, "stability"), state.stability(), TOLERANCE);
        assertEquals(name + " difficulty", number(expected, "difficulty"), state.difficulty(), TOLERANCE);
    }

    private static Path referenceCasesPath() {
        Path modulePath = Path.of("testdata", "upstream-reference-cases.json");
        if (Files.exists(modulePath)) {
            return modulePath;
        }
        return Path.of("fsrs-java", "testdata", "upstream-reference-cases.json");
    }

    private static double[] doubleArray(List<Object> values) {
        double[] out = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            out[i] = number(values.get(i));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        return (List<Object>) value;
    }

    private static String text(Map<String, Object> values, String key) {
        return (String) values.get(key);
    }

    private static int integer(Map<String, Object> values, String key) {
        return (int) Math.round(number(values, key));
    }

    private static double number(Map<String, Object> values, String key) {
        return number(values.get(key));
    }

    private static double number(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if ("NaN".equals(value)) {
            return Double.NaN;
        }
        throw new IllegalArgumentException("Expected number but got " + value);
    }

    private static void expectIllegalArgument(String name, ThrowingRunnable runnable) {
        try {
            runnable.run();
            fail("Expected IllegalArgumentException for " + name);
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    private interface ThrowingRunnable {
        void run();
    }

    private static final class Json {
        static Object parse(String text) {
            return new Parser(text).parse();
        }

        private static final class Parser {
            private final String text;
            private int index;

            Parser(String text) {
                this.text = text;
            }

            Object parse() {
                Object value = parseValue();
                skipWhitespace();
                if (index != text.length()) {
                    throw new IllegalArgumentException("Unexpected trailing JSON at " + index);
                }
                return value;
            }

            private Object parseValue() {
                skipWhitespace();
                char c = text.charAt(index);
                if (c == '{') {
                    return parseObject();
                }
                if (c == '[') {
                    return parseArray();
                }
                if (c == '"') {
                    return parseString();
                }
                if (c == 't') {
                    consume("true");
                    return Boolean.TRUE;
                }
                if (c == 'f') {
                    consume("false");
                    return Boolean.FALSE;
                }
                if (c == 'n') {
                    consume("null");
                    return null;
                }
                return parseNumber();
            }

            private Map<String, Object> parseObject() {
                Map<String, Object> out = new LinkedHashMap<>();
                index++;
                skipWhitespace();
                if (peek('}')) {
                    index++;
                    return out;
                }
                while (true) {
                    String key = parseString();
                    skipWhitespace();
                    consume(":");
                    out.put(key, parseValue());
                    skipWhitespace();
                    if (peek('}')) {
                        index++;
                        return out;
                    }
                    consume(",");
                    skipWhitespace();
                }
            }

            private List<Object> parseArray() {
                List<Object> out = new ArrayList<>();
                index++;
                skipWhitespace();
                if (peek(']')) {
                    index++;
                    return out;
                }
                while (true) {
                    out.add(parseValue());
                    skipWhitespace();
                    if (peek(']')) {
                        index++;
                        return out;
                    }
                    consume(",");
                }
            }

            private String parseString() {
                consume("\"");
                StringBuilder out = new StringBuilder();
                while (index < text.length()) {
                    char c = text.charAt(index++);
                    if (c == '"') {
                        return out.toString();
                    }
                    if (c == '\\') {
                        out.append(parseEscape());
                    } else {
                        out.append(c);
                    }
                }
                throw new IllegalArgumentException("Unterminated JSON string");
            }

            private char parseEscape() {
                char escaped = text.charAt(index++);
                switch (escaped) {
                    case '"', '\\', '/':
                        return escaped;
                    case 'b':
                        return '\b';
                    case 'f':
                        return '\f';
                    case 'n':
                        return '\n';
                    case 'r':
                        return '\r';
                    case 't':
                        return '\t';
                    case 'u':
                        int codePoint = Integer.parseInt(text.substring(index, index + 4), 16);
                        index += 4;
                        return (char) codePoint;
                    default:
                        throw new IllegalArgumentException("Unknown JSON escape: " + escaped);
                }
            }

            private Double parseNumber() {
                int start = index;
                while (index < text.length()) {
                    char c = text.charAt(index);
                    if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                        index++;
                    } else {
                        break;
                    }
                }
                return Double.valueOf(text.substring(start, index));
            }

            private void consume(String expected) {
                if (!text.startsWith(expected, index)) {
                    throw new IllegalArgumentException("Expected " + expected + " at " + index);
                }
                index += expected.length();
            }

            private boolean peek(char expected) {
                return index < text.length() && text.charAt(index) == expected;
            }

            private void skipWhitespace() {
                while (index < text.length()) {
                    char c = text.charAt(index);
                    if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                        index++;
                    } else {
                        return;
                    }
                }
            }
        }
    }
}
