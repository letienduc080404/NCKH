package com.duc.iot.iot_web_app.repository;

import com.duc.iot.iot_web_app.model.OtaHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OtaHistoryRepository extends JpaRepository<OtaHistory, Long> {
    List<OtaHistory> findByDeviceIdOrderByStartedAtDesc(Long deviceId);
}
