package com.sub130.strava;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Strava API credentials entered through the app UI (overrides the .env fallback). */
@Document("strava_config")
public class StravaConfig {
    public static final String ID = "strava-config";

    @Id
    public String id = ID;

    public String clientId;
    public String clientSecret;
}
