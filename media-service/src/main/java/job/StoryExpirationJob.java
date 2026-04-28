package com.connectsphere.media.job;

import com.connectsphere.media.service.StoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StoryExpirationJob {

    private static final Logger logger = LoggerFactory.getLogger(StoryExpirationJob.class);

    private final StoryService storyService;

    public StoryExpirationJob(StoryService storyService) {
        this.storyService = storyService;
    }

    // Run every 5 minutes to satisfy the 24h+5m expiry requirement.
    @Scheduled(fixedDelay = 300000, initialDelay = 30000)
    public void markExpiredStories() {
        logger.info("Executing Story Expiration Job");
        try {
            storyService.expireOldStories();
            logger.info("Successfully marked expired stories as inactive.");
        } catch (Exception e) {
            logger.error("Error occurred while executing Story Expiration Job: ", e);
        }
    }
}
