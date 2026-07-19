package com.sub130.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * When the backend also serves the built React app (production single-service deploy),
 * client-side routes must fall back to index.html so a hard refresh on e.g. /calendar works.
 * API routes (/api/**) and static assets are handled before this.
 */
@Controller
public class SpaController {
    @RequestMapping({"/", "/calendar", "/analytics", "/settings", "/log/**"})
    public String spa() {
        return "forward:/index.html";
    }
}
