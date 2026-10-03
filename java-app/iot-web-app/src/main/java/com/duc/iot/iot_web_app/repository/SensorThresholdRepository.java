package com.duc.iot.iot_web_app.repository;

import com.duc.iot.iot_web_app.model.SensorThreshold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SensorThresholdRepository extends JpaRepository<SensorThreshold, Long> {

    List<SensorThreshold> findBySensorIdOrderByDisplayOrderAsc(Long sensorId);

    @Modifying
    @Query("DELETE FROM SensorThreshold t WHERE t.sensor.id = :sensorId")
    void deleteBySensorId(@Param("sensorId") Long sensorId);
}
