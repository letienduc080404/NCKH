package com.duc.iot.iot_web_app.repository;

import com.duc.iot.iot_web_app.model.DeviceGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceGroupRepository extends JpaRepository<DeviceGroup, Long> {
}
