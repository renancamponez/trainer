package com.sub130.strava;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface StravaTokenRepository extends MongoRepository<StravaToken, String> {
}
