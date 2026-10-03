package com.duc.iot.iot_web_app.repository;

import com.duc.iot.iot_web_app.model.FirmwareVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface FirmwareVersionRepository extends JpaRepository<FirmwareVersion, Long> {
    Optional<FirmwareVersion> findByVersion(String version);
}
