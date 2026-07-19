package com.sub130.repo;

import com.sub130.domain.DayLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface DayLogRepository extends MongoRepository<DayLog, String> {

    // ISO date strings sort chronologically as plain strings.
    // NB: a derived "Between" is exclusive on both ends, and a derived
    // GreaterThanEqual+LessThanEqual on the same field can't be built by Spring Data's
    // Mongo query deriver — so we spell out an inclusive range explicitly.
    @Query(value = "{ 'date': { $gte: ?0, $lte: ?1 } }", sort = "{ 'date': 1 }")
    List<DayLog> findInDateRange(String startInclusive, String endInclusive);

    List<DayLog> findAllByOrderByDateAsc();
}
