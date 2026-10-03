package com.duc.iot.iot_web_app.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.duc.iot.iot_web_app.model.Device;
import com.duc.iot.iot_web_app.model.Sensor;
import com.duc.iot.iot_web_app.model.SensorReading;
import com.duc.iot.iot_web_app.model.SensorThreshold;
import com.duc.iot.iot_web_app.repository.DeviceRepository;
import com.duc.iot.iot_web_app.repository.FirmwareVersionRepository;
import com.duc.iot.iot_web_app.repository.SensorReadingRepository;
import com.duc.iot.iot_web_app.repository.SensorRepository;
import com.duc.iot.iot_web_app.repository.SensorThresholdRepository;
import com.duc.iot.iot_web_app.service.MqttService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class IotController {

    private static final Logger log = LoggerFactory.getLogger(IotController.class);

    // Topic MQTT gá»­i ngÆ°á»¡ng vá» ESP32
    private static final String CONTROL_TOPIC_PREFIX = "iot/device/control/";

    private final SensorRepository sensorRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final DeviceRepository deviceRepository;
    private final SensorReadingRepository readingRepository;
    private final FirmwareVersionRepository firmwareRepository;
    private final MqttService mqttService;
    private final ObjectMapper objectMapper;
    private final SensorThresholdRepository thresholdRepository;

    // --- TRANG WEB ---
    @GetMapping("/")
    public String home(Model model) {
        List<Device> devices = deviceRepository.findAll();
        long onlineCount = devices.stream().filter(d -> d.getStatus() == Device.Status.ONLINE).count();
        model.addAttribute("devices", devices);
        model.addAttribute("totalDevices", devices.size());
        model.addAttribute("onlineCount", onlineCount);
        model.addAttribute("offlineCount", devices.size() - onlineCount);
        model.addAttribute("firmwares", firmwareRepository.findAll());
        return "dashboard";
    }

    @GetMapping("/devices")
    public String devices(Model model) {
        model.addAttribute("devices", deviceRepository.findAll());
        model.addAttribute("firmwares", firmwareRepository.findAll());
        return "devices";
    }

    @GetMapping("/dashboard/{id}")
    public String dashboard(@PathVariable Long id, Model model) {
        Optional<Device> deviceOpt = deviceRepository.findById(id);
        if (deviceOpt.isEmpty()) {
            return "redirect:/devices";
        }
        Device device = deviceOpt.get();
        List<Sensor> sensors = device.getSensors() != null ? device.getSensors() : new ArrayList<>();
        Map<String, Double> latestReadings = new LinkedHashMap<>();
        for (Sensor s : sensors) {
            String name = s.getSensorName();
            if (name.contains("Ch\u1ebf") || name.contains("b\u01a1m") || name.contains("Van")) continue;
            SensorReading reading = readingRepository.findFirstBySensorIdOrderByRecordedAtDesc(s.getId());
            if (reading != null) {
                latestReadings.put(name, reading.getRawValue());
            }
        }

        // Láº¥y dá»¯ liá»‡u tháº­t cho biá»ƒu Ä‘á»“ Activity
        List<String> chartLabels = new ArrayList<>();
        List<Double> chartData = new ArrayList<>();

        if (!sensors.isEmpty()) {
            Sensor firstSensor = sensors.get(0);
            List<SensorReading> recentReadings = readingRepository.findTop50BySensorIdOrderByRecordedAtDesc(firstSensor.getId());
            for (int i = recentReadings.size() - 1; i >= 0; i--) {
                SensorReading r = recentReadings.get(i);
                chartLabels.add(r.getRecordedAt().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
                chartData.add(r.getRawValue());
            }
        }

        try {
            model.addAttribute("chartLabels", objectMapper.writeValueAsString(chartLabels));
            model.addAttribute("chartData", objectMapper.writeValueAsString(chartData));
        } catch (JsonProcessingException e) {
            log.error("Error serializing chart data for device {}", id, e);
            model.addAttribute("chartLabels", "[]");
            model.addAttribute("chartData", "[]");
        }

        // Build threshold map: sensorName -> list of zones (for frontend)
        Map<String, Object> thresholdsMap = new LinkedHashMap<>();
        for (Sensor s : sensors) {
            String tName = s.getSensorName();
            if (tName.contains("Ch\u1ebf") || tName.contains("b\u01a1m") || tName.contains("Van")) continue;
            List<SensorThreshold> zones = thresholdRepository.findBySensorIdOrderByDisplayOrderAsc(s.getId());
            if (!zones.isEmpty()) {
                thresholdsMap.put(tName, zones);
            }
        }
        try {
            model.addAttribute("thresholdsJson", objectMapper.writeValueAsString(thresholdsMap));
        } catch (JsonProcessingException e) {
            model.addAttribute("thresholdsJson", "{}");
        }

        model.addAttribute("device", device);
        model.addAttribute("sensors", sensors);
        model.addAttribute("latestReadings", latestReadings);
        return "home";
    }

    @GetMapping("/fix-sensors")
    @ResponseBody
    public String fixSensors() {
        java.util.List<Sensor> allSensors = sensorRepository.findAll();
        int deleted = 0;
        int renamed = 0;
        for (Sensor s : allSensors) {
            String name = s.getSensorName();
            if (name.contains("Ã") || name.contains("Ä") || name.contains("A,")) {
                sensorRepository.delete(s);
                deleted++;
            } else {
                String newName = name;
                switch (name) {
                    case "temperature", "Temperature", "T" -> newName = "Nhiệt độ";
                    case "humidity", "Humidity", "H" -> newName = "Độ ẩm không khí";
                    case "soil" -> newName = "Độ ẩm đất";
                    case "soil1", "SM1" -> newName = "Độ ẩm đất 1";
                    case "soil2", "SM2" -> newName = "Độ ẩm đất 2";
                    case "wifi_rssi" -> newName = "Tín hiệu Wi-Fi";
                    case "battery" -> newName = "Pin";
                    case "seq" -> newName = "Bản tin số";
                    case "activeZone" -> newName = "Khu vực HĐ";
                    case "phase" -> newName = "Pha";
                    case "cycle" -> newName = "Chu kỳ";
                    case "mode" -> newName = "Chế độ";
                }
                if (!newName.equals(name)) {
                    s.setSensorName(newName);
                    sensorRepository.save(s);
                    renamed++;
                }
            }
        }
        return "Deleted " + deleted + " corrupted sensors. Renamed " + renamed + " sensors to Vietnamese. Please go back to dashboard.";
    }

    @PostMapping("/devices/add")
    public String addDevice(@RequestParam String deviceName,
            @RequestParam String category,
            @RequestParam String location,
            RedirectAttributes redirectAttributes) {
        try {
            Device device = new Device();
            String generatedToken = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 20);
            device.setDeviceUid(generatedToken);
            device.setDeviceName(deviceName);
            device.setCategory(category);
            device.setLocation(location);
            device.setStatus(Device.Status.OFFLINE);
            device.setCreatedAt(LocalDateTime.now());
            deviceRepository.save(device);
            redirectAttributes.addFlashAttribute("success", "Device " + deviceName + " added successfully!");
        } catch (Exception e) {
            log.error("Failed to add device '{}'", deviceName, e);
            redirectAttributes.addFlashAttribute("error", "Failed to add device.");
        }
        return "redirect:/devices";
    }

    @PostMapping("/devices/delete/{id}")
    public String deleteDevice(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            deviceRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "Device deleted successfully.");
        } catch (Exception e) {
            log.error("Failed to delete device {}", id, e);
            redirectAttributes.addFlashAttribute("error", "Failed to delete device.");
        }
        return "redirect:/devices";
    }

    @PostMapping("/devices/edit/{id}")
    public String editDevice(@PathVariable Long id,
            @RequestParam String deviceName,
            @RequestParam String category,
            @RequestParam String location,
            RedirectAttributes redirectAttributes) {
        Optional<Device> deviceOpt = deviceRepository.findById(id);
        if (deviceOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Device not found.");
            return "redirect:/devices";
        }
        try {
            Device device = deviceOpt.get();
            device.setDeviceName(deviceName);
            device.setCategory(category);
            device.setLocation(location);
            deviceRepository.save(device);
            redirectAttributes.addFlashAttribute("success", "Device updated successfully.");
        } catch (Exception e) {
            log.error("Failed to update device {}", id, e);
            redirectAttributes.addFlashAttribute("error", "Failed to update device.");
        }
        return "redirect:/devices";
    }

    @GetMapping("/analytics")
    public String generalAnalytics() {
        List<Device> devices = deviceRepository.findAll();
        if (devices.isEmpty()) {
            return "redirect:/devices";
        }
        return "redirect:/analytics/" + devices.get(0).getId();
    }

    @GetMapping("/analytics/{id}")
    public String analytics(@PathVariable Long id, Model model) {
        Optional<Device> deviceOpt = deviceRepository.findById(id);
        if (deviceOpt.isEmpty()) {
            return "redirect:/devices";
        }
        Device device = deviceOpt.get();
        List<Sensor> sensors = device.getSensors() != null ? device.getSensors() : new ArrayList<>();

        Map<String, List<Object[]>> historicalData = new LinkedHashMap<>();
        for (Sensor s : sensors) {
            List<SensorReading> readings = readingRepository.findTop200BySensorIdOrderByRecordedAtDesc(s.getId());
            List<Object[]> sensorData = new ArrayList<>();
            for (int i = readings.size() - 1; i >= 0; i--) {
                SensorReading r = readings.get(i);
                long timestamp = r.getRecordedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                sensorData.add(new Object[]{timestamp, r.getRawValue()});
            }
            historicalData.put(s.getSensorName(), sensorData);
        }
        try {
            model.addAttribute("historicalDataJson", objectMapper.writeValueAsString(historicalData));
        } catch (JsonProcessingException e) {
            log.error("Error serializing historical data for device {}", id, e);
            model.addAttribute("historicalDataJson", "{}");
        }

        model.addAttribute("device", device);
        model.addAttribute("sensors", sensors);
        return "analytics";
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("firmwares", firmwareRepository.findAll());
        model.addAttribute("devices", deviceRepository.findAll());
        return "settings";
    }

    // --- APIs ---
    @GetMapping("/api/status")
    @ResponseBody
    public String checkStatus() {
        return "IoT System Ready!";
    }

    @PostMapping("/api/device/{id}/control")
    @ResponseBody
    public ResponseEntity<?> controlDevice(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Optional<Device> deviceOpt = deviceRepository.findById(id);
        if (deviceOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Device device = deviceOpt.get();
        String topic = CONTROL_TOPIC_PREFIX + device.getDeviceUid();
        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);
            mqttService.publishCommand(topic, jsonPayload);
            return ResponseEntity.ok(Map.of("status", "success", "message", "Command sent"));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize command payload for device {}", id, e);
            return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", "Invalid payload format"));
        } catch (RuntimeException e) {
            log.error("Failed to publish command to device {}", id, e);
            return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @GetMapping("/api/devices/all")
    @ResponseBody
    public List<Device> getAllDevices() {
        return deviceRepository.findAll();
    }

    @PostMapping("/api/v1/{deviceToken}/telemetry")
    @ResponseBody
    @org.springframework.transaction.annotation.Transactional
    public String receiveTelemetry(@PathVariable String deviceToken, @RequestBody Map<String, Object> data) {
        Optional<Device> deviceOpt = deviceRepository.findByDeviceUid(deviceToken);
        if (deviceOpt.isEmpty()) {
            return "Error: Invalid Access Token!";
        }
        Device device = deviceOpt.get();
        Long deviceId = device.getId();
        LocalDateTime payloadTime = LocalDateTime.now();
        List<SensorReading> newReadings = new ArrayList<>();

        data.forEach((key, value) -> {
            if (value == null) {
                return;
            }

            switch (key) {
                case "hardware_version" ->
                    device.setHardwareVersion(value.toString());
                case "free_heap" ->
                    device.setFreeHeap(((Number) value).intValue());
                case "wifi_rssi" ->
                    device.setWifiRssi(((Number) value).intValue());
                case "uptime" ->
                    device.setUptime(((Number) value).longValue());
                case "reboot_count" ->
                    device.setRebootCount(((Number) value).intValue());
                case "last_reboot_reason" ->
                    device.setLastRebootReason(value.toString());
                case "mqtt_connected" ->
                    device.setMqttConnected(Boolean.valueOf(String.valueOf(value)));
                default -> {
                    double numVal;
                    try {
                        numVal = value instanceof Number
                                ? ((Number) value).doubleValue()
                                : Double.parseDouble((String) value);
                    } catch (NumberFormatException | ClassCastException e) {
                        return;
                    }

                    String sensorName = key;
                    switch (key) {
                        case "temperature", "Temperature", "T" -> sensorName = "Nhiệt độ";
                        case "humidity", "Humidity", "H" -> sensorName = "Độ ẩm không khí";
                        case "soil" -> sensorName = "Độ ẩm đất";
                        case "soil1", "SM1" -> sensorName = "Độ ẩm đất 1";
                        case "soil2", "SM2" -> sensorName = "Độ ẩm đất 2";
                        case "wifi_rssi" -> sensorName = "Tín hiệu Wi-Fi";
                        case "battery" -> sensorName = "Pin";
                        case "seq" -> sensorName = "Bản tin số";
                        case "activeZone" -> sensorName = "Khu vực HĐ";
                        case "phase" -> sensorName = "Pha";
                        case "cycle" -> sensorName = "Chu kỳ";
                        case "mode" -> sensorName = "Chế độ";
                    }

                    final String finalSensorName = sensorName;
                    Sensor sensor = device.getSensors().stream()
                            .filter(s -> s.getSensorName().equals(finalSensorName))
                            .findFirst()
                            .orElseGet(() -> {
                                Sensor s = new Sensor();
                                s.setSensorName(finalSensorName);
                                s.setDevice(device);
                                s.setSensorType(Sensor.SensorType.CUSTOM);
                                if (key.toLowerCase().contains("temp")) {
                                    s.setSensorType(Sensor.SensorType.TEMPERATURE);
                                } else if (key.toLowerCase().contains("humi")) {
                                    s.setSensorType(Sensor.SensorType.HUMIDITY);
                                }
                                s = sensorRepository.save(s);
                                device.getSensors().add(s);
                                return s;
                            });

                    SensorReading reading = new SensorReading();
                    reading.setSensor(sensor);
                    reading.setRawValue(numVal);
                    reading.setFilteredValue(numVal);
                    reading.setRecordedAt(payloadTime);
                    newReadings.add(reading);
                }
            }
        });

        if (!newReadings.isEmpty()) {
            readingRepository.saveAll(newReadings);
        }

        device.setLastSeen(LocalDateTime.now());
        device.setStatus(Device.Status.ONLINE);
        deviceRepository.save(device);

        data.put("deviceId", deviceId);
        data.put("status", "ONLINE");
        log.info("Broadcasting telemetry update for device {}", deviceId);
        messagingTemplate.convertAndSend("/topic/telemetry-updates", (Object) data);

        return "OK";
    }

    @GetMapping("/api/export/{deviceId}/csv")
    public void exportCsv(@PathVariable Long deviceId, jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        Optional<Device> deviceOpt = deviceRepository.findById(deviceId);
        if (deviceOpt.isEmpty()) {
            response.sendError(404, "Device not found");
            return;
        }

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"device_" + deviceId + "_data.csv\"");

        java.io.PrintWriter writer = response.getWriter();
        writer.write('\ufeff');
        writer.println("NgÃ y,Thá»i gian,TÃªn cáº£m biáº¿n,Äá»™ áº©m Ä‘áº¥t 1,Äá»™ áº©m Ä‘áº¥t 2,Äá»™ áº©m khÃ´ng khÃ­,Nhiá»‡t Ä‘á»™,Cháº¿ Ä‘á»™,MÃ¡y bÆ¡m,Van 1,Van 2");

        Device device = deviceOpt.get();
        if (device.getSensors() != null) {
            java.util.Map<java.time.LocalDateTime, java.util.Map<String, Double>> groupedReadings =
                    new java.util.TreeMap<>(java.util.Collections.reverseOrder());

            for (Sensor s : device.getSensors()) {
                List<SensorReading> readings = readingRepository.findTop20000BySensorIdOrderByRecordedAtDesc(s.getId());
                for (SensorReading r : readings) {
                    int secondBucket = (r.getRecordedAt().getSecond() / 5) * 5;
                    java.time.LocalDateTime timeKey = r.getRecordedAt().withNano(0).withSecond(secondBucket);
                    groupedReadings.putIfAbsent(timeKey, new java.util.HashMap<>());
                    groupedReadings.get(timeKey).put(s.getSensorName(), r.getRawValue());
                }
            }

            java.time.format.DateTimeFormatter dateFormatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
            java.time.format.DateTimeFormatter timeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss");
            String deviceName = device.getDeviceName() != null ? device.getDeviceName() : "Unknown";

            for (java.util.Map.Entry<java.time.LocalDateTime, java.util.Map<String, Double>> entry : groupedReadings.entrySet()) {
                String dateStr = entry.getKey().format(dateFormatter);
                String timeStr = entry.getKey().format(timeFormatter);
                java.util.Map<String, Double> vals = entry.getValue();

                String soil1 = vals.containsKey("Äá»™ áº©m Ä‘áº¥t 1") ? String.valueOf(vals.get("Äá»™ áº©m Ä‘áº¥t 1")) : (vals.containsKey("Äá»™ áº©m Ä‘áº¥t") ? String.valueOf(vals.get("Äá»™ áº©m Ä‘áº¥t")) : "");
                String soil2 = vals.containsKey("Äá»™ áº©m Ä‘áº¥t 2") ? String.valueOf(vals.get("Äá»™ áº©m Ä‘áº¥t 2")) : "";
                String hum = vals.containsKey("Äá»™ áº©m khÃ´ng khÃ­") ? String.valueOf(vals.get("Äá»™ áº©m khÃ´ng khÃ­")) : "";
                String temp = vals.containsKey("Nhiá»‡t Ä‘á»™") ? String.valueOf(vals.get("Nhiá»‡t Ä‘á»™")) : "";
                String mode = vals.containsKey("Cháº¿ Ä‘á»™") ? (Double.valueOf(1.0).equals(vals.get("Cháº¿ Ä‘á»™")) ? "AUTO" : "MANUAL") : "";
                String pump = vals.containsKey("MÃ¡y bÆ¡m") ? (Double.valueOf(1.0).equals(vals.get("MÃ¡y bÆ¡m")) ? "ON" : "OFF") : "";
                String valve1 = vals.containsKey("Van 1") ? (Double.valueOf(1.0).equals(vals.get("Van 1")) ? "ON" : "OFF") : "";
                String valve2 = vals.containsKey("Van 2") ? (Double.valueOf(1.0).equals(vals.get("Van 2")) ? "ON" : "OFF") : "";
                writer.println(dateStr + "," + timeStr + "," + deviceName + "," + soil1 + "," + soil2 + "," + hum + "," + temp + "," + mode + "," + pump + "," + valve1 + "," + valve2);
            }
        }
        writer.flush();
    }

    // =========================================================================
    // THRESHOLD APIs
    // =========================================================================

    /**
     * [GET] Láº¥y toÃ n bá»™ ngÆ°á»¡ng cá»§a má»™t sensor theo sensorName.
     * ESP32 cÃ³ thá»ƒ gá»i API nÃ y khi khá»Ÿi Ä‘á»™ng Ä‘á»ƒ Ä‘á»“ng bá»™ ngÆ°á»¡ng:
     *   GET /api/device/{deviceId}/thresholds?sensorName=Nhiá»‡t+Ä‘á»™
     */
    @GetMapping("/api/device/{deviceId}/thresholds")
    @ResponseBody
    public ResponseEntity<?> getThresholds(@PathVariable Long deviceId,
                                            @RequestParam String sensorName) {
        Optional<Device> deviceOpt = deviceRepository.findById(deviceId);
        if (deviceOpt.isEmpty()) return ResponseEntity.notFound().build();
        Device device = deviceOpt.get();
        Sensor sensor = device.getSensors().stream()
                .filter(s -> s.getSensorName().equals(sensorName))
                .findFirst().orElse(null);
        if (sensor == null) return ResponseEntity.ok(List.of());
        return ResponseEntity.ok(thresholdRepository.findBySensorIdOrderByDisplayOrderAsc(sensor.getId()));
    }

    /**
     * [GET] Láº¥y Táº¤T Cáº¢ ngÆ°á»¡ng cá»§a má»™t thiáº¿t bá»‹ (táº¥t cáº£ sensor) theo deviceUid.
     * ESP32 gá»i API nÃ y khi khá»Ÿi Ä‘á»™ng Ä‘á»ƒ náº¡p toÃ n bá»™ ngÆ°á»¡ng má»™t láº§n:
     *   GET /api/v1/{deviceToken}/thresholds
     * Response:
     * {
     *   "Nhiá»‡t Ä‘á»™": [{"label":"QuÃ¡ láº¡nh","min":0,"max":25,"color":"#3b82f6"}, ...],
     *   "Äá»™ áº©m Ä‘áº¥t 1": [...]
     * }
     */
    @GetMapping("/api/v1/{deviceToken}/thresholds")
    @ResponseBody
    public ResponseEntity<?> getThresholdsByToken(@PathVariable String deviceToken) {
        Optional<Device> deviceOpt = deviceRepository.findByDeviceUid(deviceToken);
        if (deviceOpt.isEmpty()) return ResponseEntity.notFound().build();
        Device device = deviceOpt.get();

        Map<String, Object> result = new LinkedHashMap<>();
        if (device.getSensors() != null) {
            for (Sensor s : device.getSensors()) {
                List<SensorThreshold> zones = thresholdRepository.findBySensorIdOrderByDisplayOrderAsc(s.getId());
                if (!zones.isEmpty()) {
                    // Build compact list cho ESP32
                    List<Map<String, Object>> compactZones = new ArrayList<>();
                    for (SensorThreshold z : zones) {
                        Map<String, Object> zMap = new LinkedHashMap<>();
                        zMap.put("label", z.getLabel());
                        zMap.put("min", z.getMinValue());
                        zMap.put("max", z.getMaxValue());
                        zMap.put("color", z.getColor());
                        compactZones.add(zMap);
                    }
                    result.put(s.getSensorName(), compactZones);
                }
            }
        }
        return ResponseEntity.ok(result);
    }

    /**
     * [POST] LÆ°u ngÆ°á»¡ng vÃ o DB vÃ  gá»­i MQTT vá» ESP32.
     * Topic MQTT: iot/device/control/{deviceUid}
     * Payload gá»­i vá» ESP32:
     * {
     *   "cmd": "set_thresholds",
     *   "sensor": "Nhiá»‡t Ä‘á»™",
     *   "zones": [
     *     {"label":"QuÃ¡ láº¡nh","min":0,"max":25,"color":"#3b82f6"},
     *     {"label":"BÃ¬nh thÆ°á»ng","min":25,"max":35,"color":"#10b981"},
     *     {"label":"QuÃ¡ nÃ³ng","min":35,"max":100,"color":"#ef4444"}
     *   ]
     * }
     */
    @PostMapping("/api/device/{deviceId}/thresholds")
    @ResponseBody
    @Transactional
    public ResponseEntity<?> saveThresholds(@PathVariable Long deviceId,
                                             @RequestParam String sensorName,
                                             @RequestBody List<Map<String, Object>> zones) {
        Optional<Device> deviceOpt = deviceRepository.findById(deviceId);
        if (deviceOpt.isEmpty()) return ResponseEntity.notFound().build();
        Device device = deviceOpt.get();

        Sensor sensor = device.getSensors().stream()
                .filter(s -> s.getSensorName().equals(sensorName))
                .findFirst().orElse(null);
        if (sensor == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Sensor not found: " + sensorName));
        }

        // 1. XÃ³a ngÆ°á»¡ng cÅ© vÃ  lÆ°u ngÆ°á»¡ng má»›i vÃ o DB
        thresholdRepository.deleteBySensorId(sensor.getId());
        thresholdRepository.flush();

        List<SensorThreshold> newZones = new ArrayList<>();
        for (int i = 0; i < zones.size(); i++) {
            Map<String, Object> z = zones.get(i);
            SensorThreshold t = new SensorThreshold();
            t.setSensor(sensor);
            t.setLabel((String) z.get("label"));
            t.setMinValue(((Number) z.get("minValue")).doubleValue());
            t.setMaxValue(((Number) z.get("maxValue")).doubleValue());
            t.setColor((String) z.getOrDefault("color", "#64748b"));
            t.setDisplayOrder(i);
            newZones.add(t);
        }
        thresholdRepository.saveAll(newZones);
        log.info("Saved {} threshold zones for sensor '{}' on device {}", newZones.size(), sensorName, deviceId);

        // 2. Gá»­i ngÆ°á»¡ng vá» ESP32 qua MQTT
        boolean mqttSent = publishThresholdsToDevice(device, sensorName, newZones);

        return ResponseEntity.ok(Map.of(
            "status", "success",
            "count", newZones.size(),
            "mqttSent", mqttSent
        ));
    }

    /**
     * [DELETE] XÃ³a ngÆ°á»¡ng khá»i DB vÃ  gá»­i lá»‡nh clear vá» ESP32.
     */
    @DeleteMapping("/api/device/{deviceId}/thresholds")
    @ResponseBody
    @Transactional
    public ResponseEntity<?> deleteThresholds(@PathVariable Long deviceId,
                                               @RequestParam String sensorName) {
        Optional<Device> deviceOpt = deviceRepository.findById(deviceId);
        if (deviceOpt.isEmpty()) return ResponseEntity.notFound().build();
        Device device = deviceOpt.get();

        Sensor sensor = device.getSensors().stream()
                .filter(s -> s.getSensorName().equals(sensorName))
                .findFirst().orElse(null);
        if (sensor != null) {
            thresholdRepository.deleteBySensorId(sensor.getId());
            // Gá»­i lá»‡nh clear ngÆ°á»¡ng vá» ESP32
            publishThresholdsToDevice(device, sensorName, List.of());
        }
        return ResponseEntity.ok(Map.of("status", "success"));
    }

    // -------------------------------------------------------------------------
    // Helper: build MQTT payload vÃ  publish ngÆ°á»¡ng vá» ESP32
    // -------------------------------------------------------------------------

    /**
     * Gá»­i ngÆ°á»¡ng vá» ESP32 qua topic: iot/device/control/{deviceUid}
     * Payload JSON compact:
     * {
     *   "cmd": "set_thresholds",
     *   "sensor": "Nhiá»‡t Ä‘á»™",
     *   "zones": [{"label":"...","min":0.0,"max":25.0,"color":"#3b82f6"}, ...]
     * }
     * Náº¿u zones rá»—ng â†’ gá»­i lá»‡nh clear:
     * { "cmd": "clear_thresholds", "sensor": "Nhiá»‡t Ä‘á»™" }
     *
     * @return true náº¿u publish thÃ nh cÃ´ng
     */
    private boolean publishThresholdsToDevice(Device device, String sensorName, List<SensorThreshold> zones) {
        try {
            String topic = CONTROL_TOPIC_PREFIX + device.getDeviceUid();
            Map<String, Object> payload = new LinkedHashMap<>();

            if (zones.isEmpty()) {
                payload.put("cmd", "clear_thresholds");
                payload.put("sensor", sensorName);
            } else {
                payload.put("cmd", "set_thresholds");
                payload.put("sensor", sensorName);
                List<Map<String, Object>> compactZones = new ArrayList<>();
                for (SensorThreshold z : zones) {
                    Map<String, Object> zMap = new LinkedHashMap<>();
                    zMap.put("label", z.getLabel());
                    zMap.put("min", z.getMinValue());
                    zMap.put("max", z.getMaxValue());
                    zMap.put("color", z.getColor());
                    compactZones.add(zMap);
                }
                payload.put("zones", compactZones);
            }

            String json = objectMapper.writeValueAsString(payload);
            mqttService.publishCommand(topic, json);
            log.info("Published thresholds to ESP32 [{}] via topic {}: {}", device.getDeviceUid(), topic, json);
            return true;
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize threshold payload for device {}", device.getId(), e);
            return false;
        } catch (Exception e) {
            log.warn("Could not publish threshold to ESP32 for device {} (device may be offline): {}", device.getId(), e.getMessage());
            return false;
        }
    }
}
