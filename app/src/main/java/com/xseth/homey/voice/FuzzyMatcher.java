package com.xseth.homey.voice;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Multi-stage fuzzy string matching with German language support
 */
public class FuzzyMatcher {

    private static final double MATCH_THRESHOLD = 0.65;
    private static final double COMPOUND_MATCH_SCORE_MULTIPLIER = 0.95;

    /**
     * Match result containing score and match type
     */
    public static class MatchResult {
        private double score;
        private String matchType;
        private String matchedId;

        public MatchResult(double score, String matchType, String matchedId) {
            this.score = score;
            this.matchType = matchType;
            this.matchedId = matchedId;
        }

        public double getScore() { return score; }
        public String getMatchType() { return matchType; }
        public String getMatchedId() { return matchedId; }
    }

    /**
     * Normalize German umlauts and special characters
     */
    public static String normalizeGerman(String text) {
        if (text == null) return "";
        
        return text.toLowerCase()
                .replace("ä", "ae")
                .replace("ö", "oe")
                .replace("ü", "ue")
                .replace("ß", "ss")
                .trim();
    }

    /**
     * Split German compound words (basic heuristic)
     */
    public static List<String> splitCompound(String word) {
        List<String> parts = new ArrayList<>();
        
        // Common German compound separators
        String[] commonParts = {
            "wohn", "zimmer", "schlaf", "kinder", "bade", "ess",
            "arbeits", "büro", "wohnzimmer", "schlafzimmer", "küche",
            "bad", "flur", "keller", "garage", "garten", "licht"
        };
        
        String normalized = normalizeGerman(word);
        parts.add(normalized);
        
        // Try to split based on known parts
        for (String part : commonParts) {
            if (normalized.contains(part) && normalized.length() > part.length()) {
                String remainder = normalized.replace(part, "");
                if (remainder.length() > 0) {
                    parts.add(part);
                    parts.add(remainder);
                }
            }
        }
        
        return parts;
    }

    /**
     * Calculate Jaro-Winkler distance
     */
    public static double jaroWinkler(String s1, String s2) {
        s1 = normalizeGerman(s1);
        s2 = normalizeGerman(s2);
        
        if (s1.equals(s2)) return 1.0;
        
        int len1 = s1.length();
        int len2 = s2.length();
        
        if (len1 == 0 || len2 == 0) return 0.0;
        
        int matchDistance = Math.max(len1, len2) / 2 - 1;
        boolean[] s1Matches = new boolean[len1];
        boolean[] s2Matches = new boolean[len2];
        
        int matches = 0;
        int transpositions = 0;
        
        // Find matches
        for (int i = 0; i < len1; i++) {
            int start = Math.max(0, i - matchDistance);
            int end = Math.min(i + matchDistance + 1, len2);
            
            for (int j = start; j < end; j++) {
                if (s2Matches[j] || s1.charAt(i) != s2.charAt(j)) continue;
                s1Matches[i] = true;
                s2Matches[j] = true;
                matches++;
                break;
            }
        }
        
        if (matches == 0) return 0.0;
        
        // Find transpositions
        int k = 0;
        for (int i = 0; i < len1; i++) {
            if (!s1Matches[i]) continue;
            while (!s2Matches[k]) k++;
            if (s1.charAt(i) != s2.charAt(k)) transpositions++;
            k++;
        }
        
        double jaro = (matches / (double) len1 + 
                      matches / (double) len2 + 
                      (matches - transpositions / 2.0) / matches) / 3.0;
        
        // Winkler modification
        int prefix = 0;
        for (int i = 0; i < Math.min(4, Math.min(len1, len2)); i++) {
            if (s1.charAt(i) == s2.charAt(i)) prefix++;
            else break;
        }
        
        return jaro + (prefix * 0.1 * (1.0 - jaro));
    }

    /**
     * Token set matching
     */
    public static double tokenSetMatch(String s1, String s2) {
        Set<String> tokens1 = new HashSet<>(Arrays.asList(normalizeGerman(s1).split("\\s+")));
        Set<String> tokens2 = new HashSet<>(Arrays.asList(normalizeGerman(s2).split("\\s+")));
        
        Set<String> intersection = new HashSet<>(tokens1);
        intersection.retainAll(tokens2);
        
        Set<String> union = new HashSet<>(tokens1);
        union.addAll(tokens2);
        
        if (union.isEmpty()) return 0.0;
        
        return (double) intersection.size() / union.size();
    }

    /**
     * Perform multi-stage fuzzy matching
     */
    public static MatchResult findBestMatch(String query, List<String> candidates, List<String> candidateIds) {
        if (query == null || candidates == null || candidates.isEmpty()) {
            return null;
        }
        
        String normalizedQuery = normalizeGerman(query);
        double bestScore = 0.0;
        String bestMatchType = "none";
        String bestMatchedId = null;
        int bestIndex = -1;
        
        for (int i = 0; i < candidates.size(); i++) {
            String candidate = candidates.get(i);
            String candidateId = (candidateIds != null && i < candidateIds.size()) ? candidateIds.get(i) : null;
            String normalizedCandidate = normalizeGerman(candidate);
            
            // Stage 1: Exact match
            if (normalizedQuery.equals(normalizedCandidate)) {
                return new MatchResult(1.0, "exact", candidateId);
            }
            
            // Stage 2: Contains match
            double containsScore = 0.0;
            if (normalizedCandidate.contains(normalizedQuery)) {
                containsScore = 0.85 + (0.05 * (1.0 - (normalizedCandidate.length() - normalizedQuery.length()) / (double) normalizedCandidate.length()));
            } else if (normalizedQuery.contains(normalizedCandidate)) {
                containsScore = 0.80;
            }
            
            if (containsScore > bestScore) {
                bestScore = containsScore;
                bestMatchType = "contains";
                bestMatchedId = candidateId;
                bestIndex = i;
            }
            
            // Stage 3: Token set match
            double tokenScore = tokenSetMatch(query, candidate) * 0.80;
            if (tokenScore > bestScore) {
                bestScore = tokenScore;
                bestMatchType = "token_set";
                bestMatchedId = candidateId;
                bestIndex = i;
            }
            
            // Stage 4: Jaro-Winkler fuzzy match
            double jaroScore = jaroWinkler(query, candidate);
            if (jaroScore > 0.75 && jaroScore > bestScore) {
                bestScore = jaroScore;
                bestMatchType = "fuzzy";
                bestMatchedId = candidateId;
                bestIndex = i;
            }
            
            // Stage 5: Compound word matching
            List<String> queryParts = splitCompound(query);
            List<String> candidateParts = splitCompound(candidate);
            
            for (String qPart : queryParts) {
                for (String cPart : candidateParts) {
                    double partScore = jaroWinkler(qPart, cPart);
                    if (partScore > 0.80 && partScore > bestScore * COMPOUND_MATCH_SCORE_MULTIPLIER) {
                        bestScore = partScore * 0.75;
                        bestMatchType = "compound";
                        bestMatchedId = candidateId;
                        bestIndex = i;
                    }
                }
            }
        }
        
        if (bestScore >= MATCH_THRESHOLD && bestMatchedId != null) {
            return new MatchResult(bestScore, bestMatchType, bestMatchedId);
        }
        
        return null;
    }

    /**
     * Check if query matches any candidate above threshold
     */
    public static boolean matches(String query, String candidate) {
        if (query == null || candidate == null) return false;
        
        List<String> candidates = Arrays.asList(candidate);
        List<String> ids = Arrays.asList(candidate);
        MatchResult result = findBestMatch(query, candidates, ids);
        
        return result != null;
    }
}
