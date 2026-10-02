package com.duc.iot.iot_web_app.repository;

import com.duc.iot.iot_web_app.model.Device;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {
    @EntityGraph(attributePaths = {"sensors"})
    Optional<Device> findByDeviceUid(String deviceUid);

    @EntityGraph(attributePaths = {"sensors"})
    Optional<Device> findById(Long id);

    @Query("SELECT d FROM Device d WHERE d.status = :status AND (d.lastSeen < :time OR d.lastSeen IS NULL)")
    List<Device> findByStatusAndLastSeenBeforeOrNull(@Param("status") Device.Status status, @Param("time") LocalDateTime time);
}
