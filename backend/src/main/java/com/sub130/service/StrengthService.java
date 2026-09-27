package com.sub130.service;

import com.sub130.dto.StrengthProgram;
import com.sub130.dto.StrengthProgram.DaySlot;
import com.sub130.dto.StrengthProgram.Exercise;
import com.sub130.dto.StrengthProgram.Milestone;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Serves the posture strength program and resolves which session a given date carries.
 * Scheduling rationale lives in {@link StrengthProgram}.
 */
@Service
public class StrengthService {

    // Ordered as the session is performed: pulls first, leg lifts in the middle, carries last.
    private static final List<Exercise> GYM = List.of(
        new Exercise("face-pull", "Face pull", "3 x 15", true, false, List.of(
            "Pull the rope to your forehead, not your chest.",
            "Finish with elbows level with or above the shoulders, knuckles to the ceiling.",
            "Light load — if the traps shrug up, it is too heavy.")),
        new Exercise("chest-row", "Chest-supported row", "3 x 10-12", true, false, List.of(
            "Chest glued to the incline pad — this takes the lower back out of it.",
            "Drive the elbows back and down, squeeze for a beat at the top.",
            "Let the shoulder blades move; don't lock them.")),
        new Exercise("lat-pulldown", "Half-kneeling single-arm lat pulldown", "3 x 10 / side", true, false, List.of(
            "Back knee down, front foot planted, hips square.",
            "Half-kneeling stops you leaning back to cheat the weight.",
            "Pull the elbow toward your hip, ribs staying down.")),
        new Exercise("ytw", "Prone Y-T-W raise", "2 x 10 each", true, false, List.of(
            "Face down on a low incline, thumbs up throughout.",
            "Lift only a few centimetres — the small range is the point.",
            "If you can load all three letters, you're using momentum.")),
        new Exercise("split-squat", "Bulgarian split squat", "3 x 8 / leg", false, true, List.of(
            "Rear foot on the bench, front shin roughly vertical at the bottom.",
            "Opens the hip flexors under load — the muscles that shorten from sitting.",
            "Torso tall, ribs stacked over hips.")),
        new Exercise("rdl", "Romanian deadlift", "3 x 8", false, true, List.of(
            "Push the hips back; bar stays close, tracking down the legs.",
            "Soft knees, flat back; stop when the hamstrings run out of stretch.",
            "Main builder for holding the pelvis neutral.")),
        new Exercise("step-down", "Eccentric step-down", "3 x 8 / leg", false, true, List.of(
            "Stand on a box on one leg; lower the other heel to the floor over a slow 3-count.",
            "Knee tracks over the toes, hips level — control is the whole exercise.",
            "Builds the quad strength that absorbs descents (Colorado is a canyon run downhill).")),
        new Exercise("calf-raise", "Single-leg calf raise", "3 x 12 / leg", false, true, List.of(
            "On a step edge, full range: heel below the step, then all the way up.",
            "2 s up, 2 s down; hold a dumbbell once 12 feels easy.",
            "Your push-off on climbs comes from here — the part that faded on the km 16-17 hill.")),
        new Exercise("dead-bug", "Dead bug", "3 x 8 / side", true, false, List.of(
            "Lower back pressed flat into the floor the whole set — that's the exercise.",
            "Extend opposite arm and leg slowly, exhaling as you reach.",
            "If the back arches off the floor, shorten the range.")),
        new Exercise("suitcase-carry", "Suitcase carry", "3 x 30 m / side", true, false, List.of(
            "Load one hand only and resist the lean — the resistance is the point.",
            "Shoulders level, ribs down, no twist through the torso.",
            "Go genuinely heavy; it trains the deep core better than most ab work."))
    );

    private static final List<Exercise> MOBILITY = List.of(
        new Exercise("t-spine", "Thoracic extension over a foam roller", "2 min", true, false, List.of(
            "Roller across the upper back, hands cradling the head, hips on the floor.",
            "Exhale and drape the upper back back over the roller; work a few spots.",
            "Keep the ribs from flaring — movement is upper back, not lower.")),
        new Exercise("couch", "Couch stretch (hip flexor)", "90 s / side", true, false, List.of(
            "Rear shin up a wall or bench, knee on a pad.",
            "Tuck the tailbone and squeeze the rear glute — that's what makes it work.",
            "Stay tall; if the lower back arches, move further from the wall.")),
        new Exercise("doorway-pec", "Doorway pec stretch", "60 s / side", true, false, List.of(
            "Forearm on the frame, elbow at 90 and level with the shoulder.",
            "Step through and rotate away gently.",
            "Feel it across the chest, never in the front of the shoulder joint."))
    );

    private static final List<Exercise> DESK = List.of(
        new Exercise("band-pull-apart", "Band pull-apart", "20 reps, 2-3x/day", true, false, List.of(
            "Keep a light band visible on the desk — visible band = band you use.",
            "Arms straight at chest height, pull until it touches your sternum.",
            "Squeeze the shoulder blades and control the return.")),
        new Exercise("chin-tuck", "Chin tuck", "10 reps when you catch yourself craning", true, false, List.of(
            "Slide the head straight back (make a double chin) — a slide, not a nod.",
            "Chin stays level; hold 3 s and release.",
            "Directly counters the forward-head desk position.")),
        new Exercise("workstation", "Fix the workstation", "set up once", true, false, List.of(
            "Top of the monitor at eye level, about an arm's length away.",
            "Elbows and knees near 90, feet flat on the floor.",
            "On a laptop, add an external keyboard — you can't fix its geometry otherwise."))
    );

    private static final List<DaySlot> WEEK = List.of(
        new DaySlot("Mon", "Quality run", "full", "Full gym + mobility",
            "Leg lifting lands on a hard day (fresh off Sunday's rest); Tuesday is easy so legs recover. Run first, lift after."),
        new DaySlot("Tue", "Easy run", "none", "Core 45 + desk routine",
            "Your Core 45 class covers the core work here — keep the run easy and skip the posture gym."),
        new DaySlot("Wed", "Quality run", "full", "Full gym + mobility",
            "Second full session on the other quality day; legs recover on Thursday's easy run."),
        new DaySlot("Thu", "Easy run", "none", "Core 45 + desk routine",
            "Core 45 again — easy run only, no posture gym."),
        new DaySlot("Fri", "Easy run", "upper", "Upper + core (no leg lifts) + mobility",
            "Split squat and RDL dropped here to keep Saturday's long run fresh."),
        new DaySlot("Sat", "Long run", "none", "Desk routine only",
            "Long run is the priority — no lifting."),
        new DaySlot("Sun", "Rest", "none", "Off",
            "Full rest. Walk or cycle if you feel like it; mobility optional.")
    );

    private static final List<Milestone> CHECKLIST = List.of(
        new Milestone("1-2", "All ten lifts with clean technique at light load; 30-min stand timer running; band lives on the desk."),
        new Milestone("3-4", "Noticeably less stiffness at the end of a work day; load up on rows and split squats."),
        new Milestone("5-8", "You catch yourself sitting upright without thinking; Y-T-W raises take a light weight."),
        new Milestone("9-16", "Visible change in how you stand; real strength gains on the RDL and suitcase carry.")
    );

    public StrengthProgram program() {
        return new StrengthProgram(WEEK, GYM, MOBILITY, DESK, CHECKLIST);
    }

    /** The DaySlot for a date (by weekday), so the calendar can show run + gym together. */
    public DaySlot forDate(String date) {
        int dow = LocalDate.parse(date).getDayOfWeek().getValue(); // 1=Mon..7=Sun
        return WEEK.get(dow - 1);
    }
}
