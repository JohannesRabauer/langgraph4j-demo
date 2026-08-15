package dev.rabauer.bahndemo.service;

import dev.rabauer.bahndemo.client.dto.JourneyDto;

import java.util.List;

public interface AdvisorService {

    /** Returns a short human-readable recommendation given the delay and the found alternatives. */
    String recommend(int delaySeconds, List<JourneyDto> alternatives);
}
