package com.selfcheckout.analytics;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Serves {@code GET /analytics/popular-items}. */
@RestController
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/analytics/popular-items")
    public Map<String, Object> getPopularItems(@RequestParam(required = false) Integer limit) {
        return analyticsService.popularItems(limit);
    }
}
