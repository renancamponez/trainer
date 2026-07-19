package com.sub130.config;

import com.sub130.domain.DayLog;
import com.sub130.domain.Settings;
import com.sub130.repo.DayLogRepository;
import com.sub130.repo.SettingsRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds an empty database with the athlete's real starting state, so the app opens
 * showing actual data instead of a blank slate. Idempotent: never overwrites.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final SettingsRepository settingsRepo;
    private final DayLogRepository logRepo;

    public DataSeeder(SettingsRepository settingsRepo, DayLogRepository logRepo) {
        this.settingsRepo = settingsRepo;
        this.logRepo = logRepo;
    }

    @Override
    public void run(String... args) {
        if (settingsRepo.findById(Settings.SINGLETON_ID).isEmpty()) {
            Settings s = new Settings();
            s.age = 43;
            s.maxHr = 184;              // athlete's entered value
            s.lthr = 172;               // athlete's entered value (verify via field test)
            s.lthrIsMeasured = false;
            settingsRepo.save(s);
        }
        // Week 0, Fri 17 Jul: the first logged run.
        if (logRepo.findById("2026-07-17").isEmpty()) {
            DayLog d = new DayLog("2026-07-17");
            d.done = true;
            d.actualKm = 6.0;
            d.actualMinutes = 36;
            d.avgHr = 150;
            d.source = "manual";
            d.notes = "First run of the plan.";
            logRepo.save(d);
        }
    }
}
