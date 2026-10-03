package com.duc.iot.iot_web_app.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.duc.iot.iot_web_app.model.Device;
import com.duc.iot.iot_web_app.model.Sensor;
import com.duc.iot.iot_web_app.model.SensorReading;
import com.duc.iot.iot_web_app.repository.DeviceRepository;
import com.duc.iot.iot_web_app.repository.SensorReadingRepository;
import com.duc.iot.iot_web_app.repository.SensorRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class MqttService implements MqttCallback {

    private static final Logger log = LoggerFactory.getLogger(MqttService.class);

    @Value("${mqtt.broker.url}")
    private String brokerUrl;

    @Value("${mqtt.topic.sensor}")
    private String topicSensor;

    @Value("${mqtt.username:}")
    private String mqttUsername;

    @Value("${mqtt.password:}")
    private String mqttPassword;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private SensorRepository sensorRepository;

    @Autowired
    private SensorReadingRepository sensorReadingRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private MqttClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        try {
            log.info("Connecting to MQTT Broker: {}", brokerUrl);
            client = new MqttClient(brokerUrl, MqttClient.generateClientId());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(10);
            
            if (mqttUsername != null && !mqttUsername.isEmpty()) {
                options.setUserName(mqttUsername);
            }
            if (mqttPassword != null && !mqttPassword.isEmpty()) {
                options.setPassword(mqttPassword.toCharArray());
            }

            client.setCallback(this);
            client.connect(options);
            client.subscribe(topicSensor);
            log.info("Connected and subscribed to: {}", topicSensor);
        } catch (MqttException e) {
            log.error("Failed to connect to MQTT Broker", e);
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("Lost connection to MQTT broker: {}", cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        String payload = new String(message.getPayload(), java.nio.charset.StandardCharsets.UTF_8);
        log.info("Received message on topic {}: {}", topic, payload);

        try {
            // Extract deviceUid from topic (e.g. iot/sensor/data/61c8310806c14648b912)
            String[] topicParts = topic.split("/");
            if (topicParts.length < 4) {
                log.warn("Topic format invalid, expected iot/sensor/data/{deviceUid}");
                return;
            }
            String deviceUid = topicParts[3];

            Optional<Device> deviceOpt = deviceRepository.findByDeviceUid(deviceUid);
            if (deviceOpt.isEmpty()) {
                log.warn("Device with UID {} not found in database.", deviceUid);
                return;
            }
            Device device = deviceOpt.get();

            JsonNode data = objectMapper.readTree(payload);
            ObjectNode objectNode = objectMapper.createObjectNode();
            LocalDateTime payloadTime = LocalDateTime.now();

            java.util.List<SensorReading> newReadings = new java.util.ArrayList<>();

            for (java.util.Map.Entry<String, JsonNode> entry : data.properties()) {
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                if (value.isNumber()) {
                    String sensorName = key;
                    Sensor.SensorType type = Sensor.SensorType.CUSTOM;
                    switch (key) {
                        case "temperature", "Temperature", "T" -> { sensorName = "Nhiệt độ"; type = Sensor.SensorType.TEMPERATURE; }
                        case "humidity", "Humidity", "H"    -> { sensorName = "Độ ẩm không khí"; type = Sensor.SensorType.HUMIDITY; }
                        case "soil"        -> { sensorName = "Độ ẩm đất"; }
                        case "soil1", "SM1"       -> { sensorName = "Độ ẩm đất 1"; }
                        case "soil2", "SM2"       -> { sensorName = "Độ ẩm đất 2"; }
                        case "wifi_rssi" -> { sensorName = "Tín hiệu Wi-Fi"; }
                        case "battery" -> { sensorName = "Pin"; }
                        case "seq" -> { sensorName = "Bản tin số"; }
                        case "activeZone" -> { sensorName = "Khu vực HĐ"; }
                        case "phase" -> { sensorName = "Pha"; }
                        case "cycle" -> { sensorName = "Chu kỳ"; }
                        case "mode" -> { sensorName = "Chế độ"; }
                    }
                        case "humidity"    -> { sensorName = "Ã„ÂÃ¡Â»â„¢ Ã¡ÂºÂ©m khÃƒÂ´ng khÃƒÂ­"; type = Sensor.SensorType.HUMIDITY; }
                        case "soil"        -> { sensorName = "Ã„ÂÃ¡Â»â„¢ Ã¡ÂºÂ©m Ã„â€˜Ã¡ÂºÂ¥t"; }
                        case "soil1"       -> { sensorName = "Ã„ÂÃ¡Â»â„¢ Ã¡ÂºÂ©m Ã„â€˜Ã¡ÂºÂ¥t 1"; }
                        case "soil2"       -> { sensorName = "Ã„ÂÃ¡Â»â„¢ Ã¡ÂºÂ©m Ã„â€˜Ã¡ÂºÂ¥t 2"; }
                        // Short-form keys sent by ESP32 firmware (MQTT_PublishSensorData)
                        case "T"   -> { sensorName = "NhiÃ¡Â»â€¡t Ã„â€˜Ã¡Â»â„¢"; type = Sensor.SensorType.TEMPERATURE; }
                        case "H"   -> { sensorName = "Ã„ÂÃ¡Â»â„¢ Ã¡ÂºÂ©m khÃƒÂ´ng khÃƒÂ­"; type = Sensor.SensorType.HUMIDITY; }
                        case "SM1" -> { sensorName = "Ã„ÂÃ¡Â»â„¢ Ã¡ÂºÂ©m Ã„â€˜Ã¡ÂºÂ¥t 1"; }
                        case "SM2" -> { sensorName = "Ã„ÂÃ¡Â»â„¢ Ã¡ÂºÂ©m Ã„â€˜Ã¡ÂºÂ¥t 2"; }
                    }
                    
                    newReadings.add(createReading(device, sensorName, type, value.asDouble(), payloadTime));
                    objectNode.put(sensorName, value.asDouble());
                } else if (value.isTextual()) {
                    objectNode.set(key, value);
                } else {
                    objectNode.set(key, value);
                }
            }

            if (!newReadings.isEmpty()) {
                sensorReadingRepository.saveAll(newReadings);
            }

            // Update device status and last seen
            device.setStatus(Device.Status.ONLINE);
            device.setLastSeen(LocalDateTime.now());
            deviceRepository.save(device);

            // Push to WebSocket for live feed
            objectNode.put("deviceId", device.getId());
            objectNode.put("status", "ONLINE");
            messagingTemplate.convertAndSend("/topic/telemetry-updates", objectNode.toString());

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Error parsing JSON payload from MQTT message", e);
        } catch (RuntimeException e) {
            log.error("Error processing MQTT message", e);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Not used
    }

    public void publishCommand(String topic, String payload) {
        try {
            if (client != null && client.isConnected()) {
                MqttMessage message = new MqttMessage(payload.getBytes());
                message.setQos(1);
                client.publish(topic, message);
                log.info("Published command to {}: {}", topic, payload);
            } else {
                log.warn("MQTT client not connected, cannot publish command");
            }
        } catch (MqttException e) {
            log.error("Failed to publish command", e);
        }
    }

    private SensorReading createReading(Device device, String sensorName, Sensor.SensorType type, double value, LocalDateTime timestamp) {
        Sensor sensor = device.getSensors().stream()
                .filter(s -> s.getSensorName().equals(sensorName))
                .findFirst()
                .orElseGet(() -> {
                    Sensor s = new Sensor();
                    s.setSensorName(sensorName);
                    s.setSensorType(type);
                    s.setDevice(device);
                    s.setIsEnabled(true);
                    s = sensorRepository.save(s);
                    device.getSensors().add(s);
                    return s;
                });

        SensorReading newSensorReading = new SensorReading();
        newSensorReading.setSensor(sensor);
        newSensorReading.setRawValue(value);
        newSensorReading.setFilteredValue(value);
        newSensorReading.setRecordedAt(timestamp);
        return newSensorReading;
    }
}

