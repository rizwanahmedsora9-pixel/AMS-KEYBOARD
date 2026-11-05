package com.ams.keyboard;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Small, dependency-free English word list used for suggestions and auto-correct.
 *
 * <p>Words are stored in frequency order, so index 0 is the most common word. Everything is kept
 * in memory (a few thousand short strings), and lookups are plain hash-map hits, which keeps the
 * keyboard responsive without pulling in a multi-megabyte prediction engine.
 */
final class WordDictionary {

    /** Longer tokens are punctuation soup, URLs or pasted text, so they are never corrected. */
    private static final int MAX_CORRECTABLE_LENGTH = 20;

    private static WordDictionary sInstance;

    private final List<String> mWords;
    private final Map<String, Integer> mRank;
    private final Set<String> mKnown;
    private final int mShortestWordLength;

    private WordDictionary(List<String> words) {
        mWords = Collections.unmodifiableList(words);
        mRank = new HashMap<>(words.size() * 2);
        mKnown = new HashSet<>(words.size() * 2);
        int shortest = Integer.MAX_VALUE;
        for (int index = 0; index < words.size(); index++) {
            String word = words.get(index);
            mRank.put(word, index);
            mKnown.add(word);
            shortest = Math.min(shortest, word.length());
        }
        mShortestWordLength = shortest == Integer.MAX_VALUE ? 1 : shortest;
    }

    /** Loads the bundled list once per process. */
    static synchronized WordDictionary getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new WordDictionary(loadWords(context));
        }
        return sInstance;
    }

    private static List<String> loadWords(Context context) {
        List<String> words = new ArrayList<>(2048);
        try (InputStream input = context.getResources().openRawResource(R.raw.english_words);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                words.add(line);
            }
        } catch (IOException | RuntimeException ignored) {
            // A missing or unreadable asset only disables suggestions, never typing itself.
        }
        return sanitize(words);
    }

    /** Drops comments, blanks and duplicates while keeping the frequency order. */
    static List<String> sanitize(List<String> rawWords) {
        List<String> words = new ArrayList<>(rawWords.size());
        Set<String> seen = new HashSet<>();
        for (String raw : rawWords) {
            if (raw == null) {
                continue;
            }
            String word = raw.trim().toLowerCase(java.util.Locale.US);
            if (word.isEmpty() || word.charAt(0) == '#' || !word.chars().allMatch(Character::isLetter)) {
                continue;
            }
            if (seen.add(word)) {
                words.add(word);
            }
        }
        return words;
    }

    /** Test seam: builds a dictionary from an explicit word list, most frequent first. */
    static WordDictionary forTesting(List<String> words) {
        return new WordDictionary(sanitize(words));
    }

    boolean isKnown(String word) {
        return word != null && mKnown.contains(word);
    }

    /** Frequency rank of a word: lower is more common, {@link Integer#MAX_VALUE} when unknown. */
    int rankOf(String word) {
        Integer rank = word == null ? null : mRank.get(word);
        return rank == null ? Integer.MAX_VALUE : rank;
    }

    boolean isEmpty() {
        return mWords.isEmpty();
    }

    /** Case-insensitive "starts with" predictions, most frequent first. */
    List<String> predictionsFor(String prefix, int limit) {
        List<String> result = new ArrayList<>(limit);
        if (prefix == null || prefix.isEmpty() || mWords.isEmpty()) {
            return result;
        }
        String needle = prefix.toLowerCase(java.util.Locale.US);
        for (String word : mWords) {
            if (word.length() > needle.length() && word.startsWith(needle)) {
                result.add(word);
                if (result.size() == limit) {
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Finds a likely replacement for a mistyped word.
     *
     * <p>Scoring, cheapest first:
     * <ul>
     *   <li>0 edits: the word is known, so it is left alone entirely.</li>
     *   <li>1 edit (substitution / insertion / deletion) or one swapped pair ("recieve" to
     *       "receive"): corrected, and corrected more eagerly when the wrong key is next to the
     *       right one on QWERTY.</li>
     *   <li>2 edits on words of six letters or more ("becuase" to "because"): corrected only when
     *       exactly one candidate fits, because two-letter fixes on short words guess too much.</li>
     * </ul>
     */
    String bestCorrection(String typed, List<String> nearbyChars) {
        if (typed == null) {
            return null;
        }
        String lower = typed.toLowerCase(java.util.Locale.US);
        int length = lower.length();
        if (length < 2 || length > MAX_CORRECTABLE_LENGTH || !isAllLetters(lower)) {
            return null;
        }
        if (mKnown.contains(lower)) {
            return null;
        }

        boolean allowTwoEdits = length >= 6;
        Candidate best = new Candidate();
        int twoEditCandidates = 0;
        String twoEditWord = null;
        for (String word : mWords) {
            int difference = word.length() - length;
            if (difference < -2 || difference > 2) {
                continue;
            }
            int distance;
            if (difference == 0) {
                if (isSingleSwap(lower, word)) {
                    distance = 1;
                } else {
                    distance = editDistance(lower, word, allowTwoEdits ? 2 : 1);
                }
            } else if (difference == 1) {
                distance = insertionCount(lower, word) == 1
                        ? 1 : (allowTwoEdits ? editDistance(lower, word, 2) : 2);
            } else if (difference == -1) {
                distance = insertionCount(word, lower) == 1
                        ? 1 : (allowTwoEdits ? editDistance(lower, word, 2) : 2);
            } else {
                distance = allowTwoEdits ? editDistance(lower, word, 2) : 2;
            }
            if (distance > 2 || (distance == 2 && !allowTwoEdits)) {
                continue;
            }
            if (distance == 2) {
                twoEditCandidates++;
                twoEditWord = word;
                continue;
            }
            double score = mRank.get(word) / 400.0;
            // Typing a key next to the intended one is a normal fat-finger slip; reward it.
            if (nearbyChars != null && isNearby(lower, word, nearbyChars)) {
                score -= 2.0;
            }
            if (score < best.score) {
                best.score = score;
                best.word = word;
            }
        }
        if (best.word == null && twoEditCandidates == 1) {
            // Exactly one plausible candidate: safe to fix even though it is two edits away.
            best.word = twoEditWord;
        }
        return best.word;
    }

    /**
     * True when only one character differs and that character sits in {@code nearbyChars}
     * (the neighbours of the key the user actually hit).
     */
    private static boolean isNearby(String typed, String candidate, List<String> nearbyChars) {
        int typedLength = typed.length();
        int candidateLength = candidate.length();
        int mismatches = 0;
        int typedIndex = -1;
        for (int index = 0; index < Math.min(typedLength, candidateLength); index++) {
            if (typed.charAt(index) != candidate.charAt(index)) {
                mismatches++;
                typedIndex = index;
            }
        }
        if (mismatches != 1 || typedIndex < 0) {
            return false;
        }
        return nearbyChars.contains(String.valueOf(typed.charAt(typedIndex)));
    }

    /** True when two equal-length strings differ by exactly one adjacent swap. */
    private static boolean isSingleSwap(String first, String second) {
        if (first.length() != second.length()) {
            return false;
        }
        int firstDiff = -1;
        for (int index = 0; index < first.length(); index++) {
            if (first.charAt(index) != second.charAt(index)) {
                if (firstDiff < 0) {
                    firstDiff = index;
                } else if (index != firstDiff + 1
                        || first.charAt(firstDiff) != second.charAt(index)
                        || first.charAt(index) != second.charAt(firstDiff)) {
                    return false;
                }
            }
        }
        return firstDiff >= 0 && firstDiff + 1 < first.length()
                && first.charAt(firstDiff) == second.charAt(firstDiff + 1)
                && first.charAt(firstDiff + 1) == second.charAt(firstDiff);
    }

    /** Levenshtein distance, capped at {@code limit} comparisons for speed. */
    private static int editDistance(String first, String second, int limit) {
        int firstLength = first.length();
        int secondLength = second.length();
        if (Math.abs(firstLength - secondLength) > limit) {
            return limit + 1;
        }
        int[] previous = new int[secondLength + 1];
        int[] current = new int[secondLength + 1];
        for (int index = 0; index <= secondLength; index++) {
            previous[index] = index;
        }
        for (int firstIndex = 1; firstIndex <= firstLength; firstIndex++) {
            current[0] = firstIndex;
            int rowMinimum = current[0];
            for (int secondIndex = 1; secondIndex <= secondLength; secondIndex++) {
                int substitution = previous[secondIndex - 1]
                        + (first.charAt(firstIndex - 1) == second.charAt(secondIndex - 1) ? 0 : 1);
                current[secondIndex] = Math.min(
                        substitution,
                        Math.min(previous[secondIndex] + 1, current[secondIndex - 1] + 1));
                rowMinimum = Math.min(rowMinimum, current[secondIndex]);
            }
            if (rowMinimum > limit) {
                return limit + 1;
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[secondLength];
    }

    private static int substitutionCount(String first, String second) {
        int differences = 0;
        for (int index = 0; index < first.length(); index++) {
            if (first.charAt(index) != second.charAt(index) && ++differences > 1) {
                return differences;
            }
        }
        return differences;
    }

    /** Number of single-character insertions needed to turn {@code shorter} into {@code longer}. */
    private static int insertionCount(String shorter, String longer) {
        if (longer.length() != shorter.length() + 1) {
            return 2;
        }
        int index = 0;
        while (index < shorter.length() && shorter.charAt(index) == longer.charAt(index)) {
            index++;
        }
        // Everything after the skipped character must line up again.
        for (int offset = index; offset < shorter.length(); offset++) {
            if (shorter.charAt(offset) != longer.charAt(offset + 1)) {
                return 2;
            }
        }
        return 1;
    }

    static boolean isAllLetters(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isLetter(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static final class Candidate {
        String word;
        double score = 5.0;
    }
}
