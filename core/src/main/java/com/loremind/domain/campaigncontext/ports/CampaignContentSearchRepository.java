package com.loremind.domain.campaigncontext.ports;

import com.loremind.domain.campaigncontext.CampaignContentHit;
import java.util.List;

public interface CampaignContentSearchRepository {
    List<CampaignContentHit> searchByName(String query);
}
