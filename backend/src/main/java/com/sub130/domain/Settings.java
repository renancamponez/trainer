package com.sub130.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Single-document athlete configuration. Everything HR-related keys off {@code lthr}:
 * change that one number and every zone in the app re-rates, exactly like the
 * spreadsheet's Settings!B7 cell.
 */
@Document("settings")
public class Settings {
    public static final String SINGLETON_ID = "settings";

    @Id
    public String id = SINGLETON_ID;

    public int age = 43;
    public int maxHr = 178;          // estimate: 208 - 0.7*age; replace with measured
    public Integer restingHr;        // measured on waking
    public int lthr = 160;           // threshold HR - THE anchor. Field-test in week 2-3.
    public boolean lthrIsMeasured = false;

    public String goal = "1:40 Colorado Half, May 2027 (4:44/km); stretch sub-1:30 (4:16/km)";

    public Settings() {}
}
