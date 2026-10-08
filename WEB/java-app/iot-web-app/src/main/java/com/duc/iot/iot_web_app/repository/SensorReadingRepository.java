package com.duc.iot.iot_web_app.repository;

import com.duc.iot.iot_web_app.model.SensorReading;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SensorReadingRepository extends JpaRepository<SensorReading, Long> {
    List<SensorReading> findTop2000BySensorIdOrderByRecordedAtDesc(Long sensorId);
    SensorReading findFirstBySensorIdOrderByRecordedAtDesc(Long sensorId);
    List<SensorReading> findTop50BySensorIdOrderByRecordedAtDesc(Long sensorId);
    List<SensorReading> findTop200BySensorIdOrderByRecordedAtDesc(Long sensorId);
    @Modifying
    @Query("DELETE FROM SensorReading r WHERE r.recordedAt < :threshold")
    void deleteByRecordedAtBefore(@Param("threshold") LocalDateTime threshold);
}
