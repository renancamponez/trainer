package com.sub130.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * intervals.icu credentials, entered on the Settings page. Garmin pushes overnight HRV, resting HR
 * and sleep score to intervals.icu through its official integration; the app reads them from there
 * with this non-expiring API key (no Garmin login on our side).
 */
@Document("wellness_config")
public class WellnessConfig {
    public static final String ID = "intervals";

    @Id
    public String id = ID;

    public String athleteId;   // e.g. "i123456" (Settings -> Developer Settings on intervals.icu)
    public String apiKey;
}
