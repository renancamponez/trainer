package com.sub130.service;

import com.sub130.domain.Settings;
import com.sub130.repo.SettingsRepository;
import org.springframework.stereotype.Service;

@Service
public class SettingsService {

    private final SettingsRepository repo;

    public SettingsService(SettingsRepository repo) { this.repo = repo; }

    public Settings get() {
        return repo.findById(Settings.SINGLETON_ID).orElseGet(() -> repo.save(new Settings()));
    }

    public Settings update(Settings incoming) {
        Settings s = get();
        s.age = incoming.age;
        s.maxHr = incoming.maxHr;
        s.restingHr = incoming.restingHr;
        s.lthr = incoming.lthr;
        s.lthrIsMeasured = incoming.lthrIsMeasured;
        if (incoming.goal != null) s.goal = incoming.goal;
        return repo.save(s);
    }
}
