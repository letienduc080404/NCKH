package com.duc.iot.iot_web_app.repository;

import com.duc.iot.iot_web_app.model.DeviceCommand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DeviceCommandRepository extends JpaRepository<DeviceCommand, Long> {
    List<DeviceCommand> findByDeviceIdAndStatus(Long deviceId, DeviceCommand.CommandStatus status);
}
