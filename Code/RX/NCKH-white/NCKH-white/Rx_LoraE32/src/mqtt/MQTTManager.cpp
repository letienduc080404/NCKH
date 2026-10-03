#include "MQTTManager.h"
#include "../config/AppConfig.h"
#include "../common/SharedState.h"
#include "../wifi/WiFiManager.h"
#include "../system/SystemManager.h"
#include <Arduino.h>
#include <PubSubClient.h>
#include <WiFiClientSecure.h>
#include <ArduinoJson.h>

/* MQTT CLIENT */
static WiFiClientSecure secureClient;         // Tạo kết nối TCP có mã hóa TLS
static PubSubClient mqttClient(secureClient); // Tạo MQTTClient sử dụng kết nối TCP đã mã hóa TLS.

static unsigned long lastMqttReconnectAttempt = 0;

/* RTOS QUEUES */
static QueueHandle_t commandQueue;
static QueueHandle_t mqttDataQueue;

/* MQTT Client tự gọi hàm này khi nhận message từ MQTT Server */
static void mqttCallback(char *topic, byte *payload, unsigned int length)
{
    String msg = "";

    for (unsigned int i = 0; i < length; i++)
    {
        msg += (char)payload[i];
    }

    Serial.println("\n===================== MQTT Message Received ==================");
    Serial.print("MQTT Topic: ");
    Serial.print(topic);
    Serial.print(" | MQTT Message: ");
    Serial.println(msg);

    /* PARSE JSON */
    JsonDocument doc;
    DeserializationError error = deserializeJson(doc, msg);

    if (error)
    {
        Serial.print("JSON Error: ");
        Serial.println(error.c_str());
        return;
    }

    /* 1. XỬ LÝ LỆNH CẤU HÌNH NGƯỠNG (THRESHOLDS) TỪ WEB */
    const char *cmd = doc["cmd"];
    if (cmd != nullptr)
    {
        if (strcmp(cmd, "set_thresholds") == 0)
        {
            const char* sensorName = doc["sensor"];
            Serial.printf("[MQTT] Received set_thresholds for sensor: %s\n", sensorName);
            // TODO: Bạn có thể thêm code xử lý lưu Ngưỡng vào Flash/EEPROM tại đây
            return; // Xử lý xong, thoát để không chạy xuống báo lỗi
        }
        else if (strcmp(cmd, "clear_thresholds") == 0)
        {
            const char* sensorName = doc["sensor"];
            Serial.printf("[MQTT] Received clear_thresholds for sensor: %s\n", sensorName);
            // TODO: Code xóa Ngưỡng
            return;
        }
    }

    /* 2. XỬ LÝ LỆNH CHUYỂN CHẾ ĐỘ: {"mode":"AUTO"} or {"mode":"MANUAL"} */
    const char *mode = doc["mode"];
    if (mode != nullptr)
    {
        LoRaCommand modeCommand = {};
        modeCommand.type = COMMAND_MODE;

        if (strcmp(mode, "AUTO") == 0)
        {
            modeCommand.mode = MODE_AUTO;
        }
        else if (strcmp(mode, "MANUAL") == 0)
        {
            modeCommand.mode = MODE_MANUAL;
        }
        else
        {
            Serial.print("[ERROR] Invalid mode: ");
            Serial.println(mode);
            return;
        }

        if (xQueueSend(commandQueue, &modeCommand, 0) == pdPASS)
        {
            Serial.print("[MQTT] CMD queued: MODE=");
            Serial.println(mode);
        }
        else
        {
            Serial.println("[ERROR] CommandQueue FULL");
        }
        return;
    }

    /* 3. XỬ LÝ LỆNH BẬT/TẮT VAN (RELAY) */
    int relay = doc["relay"] | 0;     // Lấy trường relay (1 hoặc 2)
    const char *state = doc["state"]; // Lấy trường state (ON/OFF)

    if ((relay != 1 && relay != 2) || state == nullptr)
    {
        Serial.println("Invalid MQTT command or Command not recognized.");
        return;
    }

    /* CREATE LORA COMMAND */
    LoRaCommand cmdLora = {};
    cmdLora.type = COMMAND_IRRIGATION;
    cmdLora.zone = relay;

    if (strcmp(state, "ON") == 0)
    {
        cmdLora.irr = true;
    }
    else if (strcmp(state, "OFF") == 0)
    {
        cmdLora.irr = false;
    }
    else
    {
        Serial.print("[ERROR] Invalid state: ");
        Serial.println(state);
        return;
    }

    /* Gửi command sang LoRaTask */
    if (xQueueSend(commandQueue, &cmdLora, 0) == pdPASS)
    {
        Serial.print("[MQTT] CMD queued: ZONE=");
        Serial.print(cmdLora.zone);
        Serial.print(" IRR=");
        Serial.println(cmdLora.irr ? "ON" : "OFF");
    }
    else
    {
        Serial.println("[ERROR] CommandQueue FULL");
    }
}

static void reconnectMQTT(void)
{
    if (!WiFiManager_IsConnected())
        return;

    if (mqttClient.connected())
        return;

    setMqttDisplayState(false);

    if (lastMqttReconnectAttempt != 0 && millis() - lastMqttReconnectAttempt < MQTT_RECONNECT_INTERVAL)
        return;

    lastMqttReconnectAttempt = millis();

    Serial.println("\n[MQTT] Connecting to HiveMQ Cloud...");

    String clientId = "ESP32_Client-" + String(random(0, 0xffff), HEX);

    if (mqttClient.connect(clientId.c_str(), mqtt_user, mqtt_password))
    {
        Serial.println("[MQTT] Connected!");
        lastMqttReconnectAttempt = 0;

        bool subscribed = mqttClient.subscribe(subscribe_topic);

        Serial.print("[MQTT] Subscribe ");
        Serial.print(subscribe_topic);
        Serial.print(" : ");
        Serial.println(subscribed ? "SUCCESS" : "FAILED");
    }
    else
    {
        Serial.print("[MQTT] Connect failed, rc=");
        Serial.println(mqttClient.state());
    }
}

static void publishData(const SensorData &data)
{
    JsonDocument doc; 

    // CHÚ Ý: Đã thay đổi tên các Key giống hệt với Web App (Có dấu tiếng Việt)
    doc["Nhiệt độ"] = data.temperature;
    doc["Độ ẩm không khí"] = data.humidity;
    doc["Độ ẩm đất 1"] = data.soil1;
    doc["Độ ẩm đất 2"] = data.soil2;

    // Thay đổi trạng thái thành số (1.0 = ON/AUTO, 0.0 = OFF/MANUAL) để Web có thể convert sang Double
    doc["Van 1"]   = (data.valve1 == VALVE_ON) ? 1.0 : 0.0;
    doc["Van 2"]   = (data.valve2 == VALVE_ON) ? 1.0 : 0.0;
    doc["Máy bơm"] = (data.pump == PUMP_ON) ? 1.0 : 0.0;
    doc["Chế độ"]  = (data.irrigationMode == MODE_AUTO) ? 1.0 : 0.0;
    
    // Các thông số còn lại giữ nguyên, Web sẽ tự tạo sensor nếu cần
    doc["battery"] = data.batteryPercent;
    doc["activeZone"] = data.activeZone;
    doc["phase"] = (uint8_t)data.irrigationPhase;
    doc["cycle"] = data.irrigationCycle;
    doc["streaming"] = data.streaming ? 1.0 : 0.0;

    char payload[512]; // Nâng kích thước buffer lên 512 do key tiếng Việt tốn bytes hơn

    serializeJson(doc, payload, sizeof(payload)); 

    bool result = mqttClient.publish(publish_topic, payload);

    Serial.print("\nMQTT Publish: ");
    Serial.print(payload);
    Serial.print(" | Topic: ");
    Serial.println(publish_topic);
    Serial.print("Publish status: ");
    Serial.println(result ? "SUCCESS" : "FAILED");
}

void MQTTManager_Begin(QueueHandle_t commands, QueueHandle_t mqttData)
{
    commandQueue = commands;
    mqttDataQueue = mqttData;
    /* MQTT */
    secureClient.setInsecure();                   // Bỏ kiểm tra CA, TLS vẫn mã hóa
    mqttClient.setServer(mqtt_server, mqtt_port); // Cấu hình địa chỉ và cổng broker.
    mqttClient.setCallback(mqttCallback);         // Đăng ký hàm xử lý callback khi nhận message từ MQTT.
}

void MQTTManager_Run(void *pvParameters)
{
    Serial.println("[RTOS] MQTTTask started");

    SensorData data;

    for (;;)
    {
        if (WiFiManager_Maintain())
        {
            SystemManager_SyncTime();
            lastMqttReconnectAttempt = 0;
        }
        if (!WiFiManager_IsConnected() && mqttClient.connected())
        {
            mqttClient.disconnect();
            Serial.println("[MQTT] Disconnected due to WiFi loss");
        }

        if (WiFiManager_IsConnected())
        {
            if (!mqttClient.connected())
            {
                reconnectMQTT();
            }

            if (mqttClient.connected())
            {
                mqttClient.loop();

                if (xQueueReceive(mqttDataQueue, &data, 0) == pdPASS)
                {
                    publishData(data);
                }
            }
        }

        setMqttDisplayState(WiFiManager_IsConnected() && mqttClient.connected());
        vTaskDelay(pdMS_TO_TICKS(10));
    }
}
