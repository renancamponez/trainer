package com.sub130.service;

import com.sub130.domain.Settings;
import com.sub130.plan.PlanGenerator;
import com.sub130.plan.PlannedSession;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Serves the deterministic plan. The 367-day list never changes, so it is generated
 * once and cached; only the LTHR-derived target-HR band is applied per request.
 */
@Service
public class PlanService {

    private final SettingsService settingsService;
    private final HrZoneService hrZoneService;
    private final List<PlannedSession> base = PlanGenerator.generateAll();
    private final Map<String, PlannedSession> byDate;

    public PlanService(SettingsService settingsService, HrZoneService hrZoneService) {
        this.settingsService = settingsService;
        this.hrZoneService = hrZoneService;
        this.byDate = base.stream().collect(Collectors.toMap(s -> s.date, Function.identity()));
    }

    public List<PlannedSession> all() { return withHr(base); }

    public List<PlannedSession> week(int week) {
        return withHr(base.stream().filter(s -> s.week == week).collect(Collectors.toList()));
    }

    public PlannedSession forDate(String date) {
        PlannedSession s = byDate.get(date);
        return s == null ? null : withHr(List.of(s)).get(0);
    }

    /** Copy each session and stamp the resolved target-HR band from the current LTHR. */
    private List<PlannedSession> withHr(List<PlannedSession> in) {
        int lthr = settingsService.get().lthr;
        return in.stream().map(s -> {
            PlannedSession c = copy(s);
            c.targetHrBpm = s.hrZone == null ? null : hrZoneService.bpmRange(s.hrZone, lthr);
            return c;
        }).collect(Collectors.toList());
    }

    private static PlannedSession copy(PlannedSession s) {
        PlannedSession c = new PlannedSession();
        c.week = s.week; c.date = s.date; c.dayName = s.dayName; c.phase = s.phase;
        c.phaseName = s.phaseName; c.type = s.type; c.session = s.session; c.rawSession = s.rawSession;
        c.warmup = s.warmup; c.cooldown = s.cooldown; c.plannedKm = s.plannedKm;
        c.estMinutes = s.estMinutes; c.paceRange = s.paceRange; c.mphRange = s.mphRange;
        c.hrZone = s.hrZone; c.incline = s.incline; c.checkpoint = s.checkpoint;
        return c;
    }
}
