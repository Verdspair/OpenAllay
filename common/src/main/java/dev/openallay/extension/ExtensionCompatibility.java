package dev.openallay.extension;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/** Small deterministic comparator for exact versions and Maven-style closed/open ranges. */
final class ExtensionCompatibility {
    private ExtensionCompatibility() {}

    static String requireRange(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        String range = value.strip();
        if ((range.startsWith("[") || range.startsWith("("))
                && (range.endsWith("]") || range.endsWith(")"))
                && range.contains(",")) {
            String body = range.substring(1, range.length() - 1);
            if (body.indexOf(',') != body.lastIndexOf(',')) {
                throw new IllegalArgumentException("Invalid version range: " + range);
            }
            return range;
        }
        if (range.contains(",") || range.startsWith("[") || range.startsWith("(")) {
            throw new IllegalArgumentException("Invalid version range: " + range);
        }
        return range;
    }

    static boolean includes(String range, String version) {
        range = requireRange(range, "range");
        if (!(range.startsWith("[") || range.startsWith("("))) {
            return compare(version, range) == 0;
        }
        String[] bounds = range.substring(1, range.length() - 1).split(",", -1);
        String lower = bounds[0].strip();
        String upper = bounds[1].strip();
        if (!lower.isEmpty()) {
            int compared = compare(version, lower);
            if (compared < 0 || (compared == 0 && range.startsWith("("))) {
                return false;
            }
        }
        if (!upper.isEmpty()) {
            int compared = compare(version, upper);
            if (compared > 0 || (compared == 0 && range.endsWith(")"))) {
                return false;
            }
        }
        return true;
    }

    private static int compare(String left, String right) {
        List<Part> leftParts = parts(left);
        List<Part> rightParts = parts(right);
        int length = Math.max(leftParts.size(), rightParts.size());
        for (int index = 0; index < length; index++) {
            Part leftPart = index < leftParts.size() ? leftParts.get(index) : Part.ZERO;
            Part rightPart = index < rightParts.size() ? rightParts.get(index) : Part.ZERO;
            int compared = leftPart.compareTo(rightPart);
            if (compared != 0) {
                return compared;
            }
        }
        return 0;
    }

    private static List<Part> parts(String value) {
        ArrayList<Part> result = new ArrayList<>();
        for (String token : value.strip().toLowerCase(java.util.Locale.ROOT).split("[._+\\-]")) {
            if (!token.isEmpty()) {
                result.add(Part.of(token));
            }
        }
        return result;
    }

    private record Part(BigInteger number, String text) implements Comparable<Part> {
        private static final Part ZERO = new Part(BigInteger.ZERO, "");

        private static Part of(String value) {
            try {
                return new Part(new BigInteger(value), "");
            } catch (NumberFormatException ignored) {
                return new Part(null, value);
            }
        }

        @Override
        public int compareTo(Part other) {
            if (number != null && other.number != null) {
                return number.compareTo(other.number);
            }
            if (number != null) {
                return 1;
            }
            if (other.number != null) {
                return -1;
            }
            return text.compareTo(other.text);
        }
    }
}
