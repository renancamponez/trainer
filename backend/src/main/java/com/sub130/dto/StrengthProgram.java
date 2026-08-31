package com.sub130.dto;

import java.util.List;

/**
 * The posture / desk-athlete strength program, arranged to sit alongside the run plan without
 * blunting the key sessions: leg lifting lands on the two quality-run days (Tue/Thu) so easy
 * days stay easy, Friday is upper/core only to protect Saturday's long run, and Sunday is off.
 */
public record StrengthProgram(
        List<DaySlot> week,          // Mon..Sun
        List<Exercise> gym,          // the 8-lift session (in order)
        List<Exercise> mobility,     // 5-minute finisher
        List<Exercise> desk,         // daily desk routine
        List<Milestone> checklist) {

    /** One weekday: what strength work pairs with that day's run. gymType is full | upper | none. */
    public record DaySlot(String day, String runContext, String gymType, String label, String note) {}

    /** upperOnly=false lifts are dropped from the Friday upper/core session. */
    public record Exercise(String key, String name, String dose, boolean upperOnly, boolean legLift,
                           List<String> cues) {}

    public record Milestone(String weeks, String target) {}
}
