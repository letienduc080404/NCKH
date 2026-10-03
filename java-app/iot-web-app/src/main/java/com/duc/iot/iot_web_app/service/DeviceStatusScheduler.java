package com.duc.iot.iot_web_app.service;

import com.duc.iot.iot_web_app.model.Device;
import com.duc.iot.iot_web_app.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DeviceStatusScheduler {

    private static final Logger log = LoggerFactory.getLogger(DeviceStatusScheduler.class);

    private final DeviceRepository deviceRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // Chạy mỗi 30 giây để kiểm tra
    @Scheduled(fixedRate = 30000)
    @Transactional
    public void checkDeviceStatus() {
        // Thiết bị được coi là offline nếu không gửi dữ liệu trong vòng 60 giây qua
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(60);

        List<Device> offlineDevices = deviceRepository.findByStatusAndLastSeenBeforeOrNull(Device.Status.ONLINE, threshold);

        if (offlineDevices.isEmpty()) {
            return;
        }

        for (Device device : offlineDevices) {
            device.setStatus(Device.Status.OFFLINE);
            log.warn("Thiết bị '{}' (id={}) đã chuyển sang trạng thái OFFLINE", device.getDeviceName(), device.getId());
        }

        deviceRepository.saveAll(offlineDevices);

        // Gửi thông báo qua WebSocket để cập nhật giao diện
        for (Device device : offlineDevices) {
            Map<String, Object> statusUpdate = new HashMap<>();
            statusUpdate.put("deviceId", device.getId());
            statusUpdate.put("status", "OFFLINE");
            messagingTemplate.convertAndSend("/topic/telemetry-updates", (Object) statusUpdate);
        }
    }
}
