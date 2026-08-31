package com.sub130.web;

import com.sub130.dto.StrengthProgram;
import com.sub130.service.StrengthService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/strength")
public class StrengthController {

    private final StrengthService strengthService;

    public StrengthController(StrengthService strengthService) {
        this.strengthService = strengthService;
    }

    /** The whole posture program: weekly layout, gym lifts, mobility, desk routine, checklist. */
    @GetMapping
    public StrengthProgram program() { return strengthService.program(); }

    /** Which strength session pairs with a given date (by weekday). */
    @GetMapping("/day/{date}")
    public StrengthProgram.DaySlot day(@PathVariable String date) { return strengthService.forDate(date); }
}
