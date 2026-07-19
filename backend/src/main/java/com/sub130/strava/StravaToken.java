package com.sub130.strava;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Stored OAuth tokens for the connected athlete (single document). */
@Document("strava_token")
public class StravaToken {
    public static final String ID = "strava";

    @Id
    public String id = ID;

    public String accessToken;
    public String refreshToken;
    public long expiresAt;        // epoch seconds
    public Long athleteId;
    public String athleteName;
    public String lastSync;       // ISO instant of the last successful sync
}
