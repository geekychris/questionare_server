package com.example.questionnaire.config;

import com.example.questionnaire.service.CampaignService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Campaigns created before share links existed (or by seed SQL) get a token at startup. */
@Component
public class ShareTokenBackfill {
    private static final Logger log = LoggerFactory.getLogger(ShareTokenBackfill.class);

    private final CampaignService campaigns;

    public ShareTokenBackfill(CampaignService campaigns) {
        this.campaigns = campaigns;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void backfill() {
        int n = campaigns.backfillShareTokens();
        if (n > 0) {
            log.info("generated share tokens for {} existing campaign(s)", n);
        }
    }
}
