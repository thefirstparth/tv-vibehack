package com.gathin.tvhome;

/**
 * Where the other side is in a sport line, so the side Parth follows (the feed's "ours": "a" or "b") stands out by
 * brightness only. The line is worded as Words.sides words it: "A v B", "A 2–1 B", "A 245/3 v B 120/4". Pure, so it is
 * unit-tested. Each range is {start, end} in the line; none when "ours" is not set.
 */
final class Opponent {

    private Opponent() {
    }

    /** The other side's name and score (if shown) in a line worded like Words.sides. */
    static int[][] inLine(String sp, String a, String b, String sa, String sb, String ours) {
        if (b.isEmpty() || !("a".equals(ours) || "b".equals(ours))) return new int[0][];
        boolean dimA = "b".equals(ours);
        if ("cricket".equals(sp)) {
            String partA = a + (sa.isEmpty() ? "" : " " + sa), partB = b + (sb.isEmpty() ? "" : " " + sb);
            int startB = partA.length() + 3;
            return new int[][]{dimA ? new int[]{0, partA.length()} : new int[]{startB, startB + partB.length()}};
        }
        if (!sa.isEmpty() && !sb.isEmpty()) {
            // "A 2–1 B": the other side's name and its own number in the score
            int scoreA = a.length() + 1, scoreB = scoreA + sa.length() + 1, nameB = scoreB + sb.length() + 1;
            return dimA ? new int[][]{{0, a.length()}, {scoreA, scoreA + sa.length()}}
                    : new int[][]{{scoreB, scoreB + sb.length()}, {nameB, nameB + b.length()}};
        }
        int startB = a.length() + 3;
        return new int[][]{dimA ? new int[]{0, a.length()} : new int[]{startB, startB + b.length()}};
    }

    /** The other side's number in a football score drawn on its own ("2–1"), or none. */
    static int[] inScore(String sa, String sb, String ours) {
        if ("a".equals(ours)) return new int[]{sa.length() + 1, sa.length() + 1 + sb.length()};
        if ("b".equals(ours)) return new int[]{0, sa.length()};
        return null;
    }
}
