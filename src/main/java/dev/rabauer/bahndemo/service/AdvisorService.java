package dev.rabauer.bahndemo.service;

import dev.rabauer.bahndemo.client.dto.JourneyDto;

import java.util.List;

public interface AdvisorService {

    /** Recommends a specific alternative (or none) given the delay and the found alternatives. */
    AdvisorRecommendation recommend(int delaySeconds, List<JourneyDto> alternatives);
}
