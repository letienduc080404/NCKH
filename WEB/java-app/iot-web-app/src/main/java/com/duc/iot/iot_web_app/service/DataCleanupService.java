package com.duc.iot.iot_web_app.service;

import com.duc.iot.iot_web_app.repository.SensorReadingRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DataCleanupService {

    private static final Logger log = LoggerFactory.getLogger(DataCleanupService.class);

    private final SensorReadingRepository readingRepository;

    // Run every day at midnight
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void cleanupOldData() {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        readingRepository.deleteByRecordedAtBefore(thirtyDaysAgo);
        log.info("Cleaned up sensor readings older than: {}", thirtyDaysAgo);
    }
}
