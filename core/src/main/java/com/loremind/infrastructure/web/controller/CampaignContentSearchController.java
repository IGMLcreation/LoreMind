package com.loremind.infrastructure.web.controller;

import com.loremind.application.campaigncontext.CampaignContentSearchService;
import com.loremind.domain.campaigncontext.CampaignContentHit;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/campaigns/search-content")
public class CampaignContentSearchController {
    private final CampaignContentSearchService service;

    public CampaignContentSearchController(CampaignContentSearchService service) {
        this.service = service;
    }

    @GetMapping
    public List<CampaignContentHit> search(@RequestParam("q") String query) {
        return service.search(query);
    }
}
