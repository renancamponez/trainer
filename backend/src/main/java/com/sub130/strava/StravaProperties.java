package com.sub130.strava;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Strava OAuth app credentials, supplied via env (see application.yml). */
@Component
@ConfigurationProperties(prefix = "strava")
public class StravaProperties {
    private String clientId;
    private String clientSecret;
    private String redirectUri = "http://localhost:3000/api/strava/callback";
    private String frontendUrl = "http://localhost:3000";

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
            && clientSecret != null && !clientSecret.isBlank();
    }

    public String getClientId() { return clientId; }
    public void setClientId(String v) { this.clientId = v; }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String v) { this.clientSecret = v; }
    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String v) { this.redirectUri = v; }
    public String getFrontendUrl() { return frontendUrl; }
    public void setFrontendUrl(String v) { this.frontendUrl = v; }
}
