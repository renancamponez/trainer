package com.sub130.repo;

import com.sub130.domain.WellnessConfig;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface WellnessConfigRepository extends MongoRepository<WellnessConfig, String> {
}
