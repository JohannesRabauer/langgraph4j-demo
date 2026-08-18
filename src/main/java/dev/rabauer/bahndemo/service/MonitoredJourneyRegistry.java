package dev.rabauer.bahndemo.service;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory registry of journeys currently being monitored. Fine for a single-process demo. */
@Component
public class MonitoredJourneyRegistry {

    private final Map<String, MonitoredJourney> journeys = new ConcurrentHashMap<>();

    public void register(MonitoredJourney journey) {
        journeys.put(journey.getId(), journey);
    }

    public Optional<MonitoredJourney> find(String id) {
        return Optional.ofNullable(journeys.get(id));
    }

    public Collection<MonitoredJourney> active() {
        return journeys.values();
    }
}
