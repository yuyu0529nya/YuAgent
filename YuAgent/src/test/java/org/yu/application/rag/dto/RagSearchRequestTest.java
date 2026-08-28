package org.yu.application.rag.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RagSearchRequestTest {

    @Test
    void shouldUseDefaultsWhenOptionalTuningFieldsAreNull() {
        RagSearchRequest request = new RagSearchRequest();
        request.setMinScore(null);
        request.setMaxResults(null);
        request.setCandidateMultiplier(null);
        request.setEnableRerank(null);

        assertEquals(0.7, request.getAdjustedMinScore());
        assertEquals(1, request.getAdjustedCandidateMultiplier());
    }

    @Test
    void shouldUseDefaultCandidateMultiplierWhenRerankingIsEnabled() {
        RagSearchRequest request = new RagSearchRequest();
        request.setMaxResults(null);
        request.setCandidateMultiplier(null);
        request.setEnableRerank(true);

        assertEquals(2, request.getAdjustedCandidateMultiplier());
    }
}
