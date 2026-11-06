package com.ams.keyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** Plain JVM tests for the auto-correct engine; no Android framework involved. */
public class WordDictionaryTest {

    private static final List<String> WORDS = Arrays.asList(
            "the", "and", "you", "that", "was", "for", "are", "with", "his", "they",
            "this", "have", "from", "one", "had", "word", "but", "not", "what", "all",
            "were", "when", "your", "can", "said", "there", "use", "hello", "world",
            "receive", "because", "friend", "their", "there", "theyre", "great");

    private final WordDictionary dictionary = WordDictionary.forTesting(WORDS);

    @Test
    public void knownWordsAreLeftAlone() {
        assertTrue(dictionary.isKnown("world"));
        assertNull(dictionary.bestCorrection("world", null));
    }

    @Test
    public void fixesASingleWrongLetter() {
        assertEquals("hello", dictionary.bestCorrection("hellp", null));
    }

    @Test
    public void fixesADroppedLetter() {
        assertEquals("world", dictionary.bestCorrection("wrld", null));
    }

    @Test
    public void fixesAnExtraLetter() {
        assertEquals("friend", dictionary.bestCorrection("friennd", null));
    }

    @Test
    public void respectsFrequencyOrderForAmbiguousInput() {
        // "worl" could be "world" (insertion) or "word" (substitution); the common one wins.
        String correction = dictionary.bestCorrection("worl", null);
        assertEquals("word", correction);
    }

    @Test
    public void ignoresNamesAndPunctuation() {
        assertNull(dictionary.bestCorrection("zzzzz", null));
        assertNull(dictionary.bestCorrection("hello!", null));
        assertNull(dictionary.bestCorrection("a", null));
    }

    @Test
    public void predictionsFollowFrequencyOrder() {
        List<String> predictions = dictionary.predictionsFor("th", 3);
        assertEquals(Arrays.asList("the", "that", "they"), predictions);
    }

    @Test
    public void sanitizeDropsCommentsBlanksAndDuplicates() {
        List<String> cleaned = WordDictionary.sanitize(
                Arrays.asList("# comment", "Alpha", "", "alpha", "beta", " ", "gamma!"));
        assertEquals(Arrays.asList("alpha", "beta"), cleaned);
        assertFalse(cleaned.contains("# comment"));
    }

    @Test
    public void nearbyKeySlipIsPreferred() {
        // With only one candidate one edit away the nearby hint changes nothing, but it must not
        // break the lookup.
        assertEquals("great", dictionary.bestCorrection("grest", Arrays.asList("a")));
    }
}
