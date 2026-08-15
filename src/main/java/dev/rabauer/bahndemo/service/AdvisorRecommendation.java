package dev.rabauer.bahndemo.service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * What an AdvisorService recommends: a specific alternative (by index into the alternatives list passed to
 * recommend()) plus a short rationale to show the user. recommendedIndex is null when no alternative should be
 * recommended (e.g. no alternatives were found).
 */
public record AdvisorRecommendation(
        @JsonPropertyDescription("0-based index into the alternatives list this recommends, or null if none")
        Integer recommendedIndex,
        @JsonPropertyDescription("One or two short sentences explaining the recommendation")
        String rationale) {
}
