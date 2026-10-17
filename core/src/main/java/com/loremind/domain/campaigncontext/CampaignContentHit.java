package com.loremind.domain.campaigncontext;

/** Lightweight search projection with the ancestors needed to open a result. */
public record CampaignContentHit(String kind, String id, String name,
        String campaignId, String campaignName, String arcId, String arcName,
        String chapterId, String chapterName) {}
