package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.CampaignContentHit;
import com.loremind.domain.campaigncontext.ports.CampaignContentSearchRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CampaignContentSearchService {
    private final CampaignContentSearchRepository repository;

    public CampaignContentSearchService(CampaignContentSearchRepository repository) {
        this.repository = repository;
    }

    public List<CampaignContentHit> search(String query) {
        String value = query == null ? "" : query.strip();
        return value.length() < 2 ? List.of() : repository.searchByName(value);
    }
}
