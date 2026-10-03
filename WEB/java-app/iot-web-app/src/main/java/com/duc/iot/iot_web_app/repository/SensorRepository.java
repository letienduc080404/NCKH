package com.duc.iot.iot_web_app.repository;

import com.duc.iot.iot_web_app.model.Sensor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface SensorRepository extends JpaRepository<Sensor, Long> {
    Optional<Sensor> findBySensorNameAndDevice_Id(String sensorName, Long deviceId);
}