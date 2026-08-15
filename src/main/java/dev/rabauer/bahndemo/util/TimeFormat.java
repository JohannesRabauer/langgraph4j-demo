package dev.rabauer.bahndemo.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Shared formatting for the Instants shown to the user, fixed to Europe/Berlin for this German-rail demo. */
public final class TimeFormat {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH);

    private TimeFormat() {
    }

    public static String format(Instant instant) {
        return instant == null ? "unknown time" : FORMATTER.format(instant.atZone(ZONE));
    }
}
