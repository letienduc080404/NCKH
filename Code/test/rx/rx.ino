#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>

// ---------------- WIFI CAU HINH ----------------
static const char *ssid = "Abc";
static const char *password = "letienduc";

// ---------------- HIVEMQ CLOUD ----------------
static const char *mqtt_server = "10cf23427b77452faec8dc86e09f1bc1.s1.eu.hivemq.cloud";
static const int mqtt_port = 8883; // Cong bao mat SSL
static const char *mqtt_user = "tienduc";
static const char *mqtt_password = "D@ucffgh123";

// ---------------- MQTT TOPIC ----------------
static const char *publish_topic = "iot/sensor/data/ae21d58a097942caa8f7";
static const char *subscribe_topic = "iot/device/control/ae21d58a097942caa8f7";

// ---------------- CAU HINH CHAN GPIO RELAY (Tuy chon theo phan cung) ----------------
// #define PUMP_PIN   26  // Chan dieu khien may bom
// #define VALVE1_PIN 27  // Chan dieu khien van 1
// #define VALVE2_PIN 14  // Chan dieu khien van 2

// ---------------- KHOI TAO CLIENT ----------------
WiFiClientSecure espClient;
PubSubClient client(espClient);

// ---------------- TRANG THAI THIET BI ----------------
// ESP32 chi dong vai tro chap hanh, KHONG tu y bat/tat bom hay van o che do AUTO.
// Toan bo logic dieu khien do Web quan ly.
String currentMode = "MANUAL";
String pumpState = "OFF";
String valve1State = "OFF";
String valve2State = "OFF";

float temperature = 28.5;
float humidity = 65.0;
float soil1 = 45.0; // Do am dat khu vuc 1
float soil2 = 42.0; // Do am dat khu vuc 2

unsigned long lastMsgTime = 0;

void setup_wifi() {
  delay(10);
  Serial.println();
  Serial.print("Connecting to WiFi: ");
  Serial.println(ssid);

  WiFi.begin(ssid, password);

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }

  Serial.println("");
  Serial.println("WiFi connected!");
  Serial.print("IP address: ");
  Serial.println(WiFi.localIP());

  // Bo qua kiem tra chung chi SSL tren HiveMQ Cloud
  espClient.setInsecure(); 
}

// ---------------- HAM GUI DU LIEU (TELEMETRY) LEN WEB ----------------
// Chi gui du lieu cam bien va trang thai thuc te cua bom, van len Web
void sendTelemetry() {
  StaticJsonDocument<256> doc;
  doc["temperature"] = temperature;
  doc["humidity"] = humidity;
  doc["soil1"] = soil1;
  doc["soil2"] = soil2;
  doc["mode"] = currentMode;
  doc["pump"] = pumpState;
  doc["valve1"] = valve1State;
  doc["valve2"] = valve2State;
  doc["wifi_rssi"] = WiFi.RSSI();
  
  char jsonBuffer[256];
  serializeJson(doc, jsonBuffer);
  
  // Tham so thu 3 (retain) la false de tranh luu lai tin cu
  client.publish(publish_topic, jsonBuffer, false); 
  Serial.print("Da gui Telemetry len Web: ");
  Serial.println(jsonBuffer);
}

// ---------------- XU LY KHI NHAN LENH TU WEB ----------------
// ESP32 chi ap dung trang thai do Web gui xuong, KHONG tu dong chen logic rieng
void callback(char* topic, byte* payload, unsigned int length) {
  Serial.print("Nhan lenh tu Web [");
  Serial.print(topic);
  Serial.print("]: ");
  
  String messageTemp;
  for (int i = 0; i < length; i++) {
    messageTemp += (char)payload[i];
  }
  Serial.println(messageTemp);

  // Parse chuoi JSON nhan duoc
  StaticJsonDocument<256> doc;
  DeserializationError error = deserializeJson(doc, messageTemp);
  
  if (error) {
    Serial.print("Loi doc JSON: ");
    Serial.println(error.c_str());
    return;
  }

  // 1. Cap nhat che do neu Web gui xuong (de dong bo trang thai voi Web)
  if (doc.containsKey("mode")) {
    currentMode = doc["mode"].as<String>();
    Serial.println(">> Web cap nhat che do: " + currentMode);
  }

  // 2. Cap nhat trang thai Bom / Van theo lenh cua Web
  if (doc.containsKey("pump")) {
    pumpState = doc["pump"].as<String>();
    Serial.println(">> Web dieu khien Bom: " + pumpState);
    // digitalWrite(PUMP_PIN, pumpState == "ON" ? HIGH : LOW);
  }
  if (doc.containsKey("valve1")) {
    valve1State = doc["valve1"].as<String>();
    Serial.println(">> Web dieu khien Van 1: " + valve1State);
    // digitalWrite(VALVE1_PIN, valve1State == "ON" ? HIGH : LOW);
  }
  if (doc.containsKey("valve2")) {
    valve2State = doc["valve2"].as<String>();
    Serial.println(">> Web dieu khien Van 2: " + valve2State);
    // digitalWrite(VALVE2_PIN, valve2State == "ON" ? HIGH : LOW);
  }

  // Ho tro format {"relay": 1, "state": "ON"}
  if (doc.containsKey("relay") && doc.containsKey("state")) {
    int relayNum = doc["relay"].as<int>();
    String state = doc["state"].as<String>();
    if (relayNum == 1) {
      pumpState = state;
      // digitalWrite(PUMP_PIN, pumpState == "ON" ? HIGH : LOW);
    } else if (relayNum == 2) {
      valve1State = state;
      // digitalWrite(VALVE1_PIN, valve1State == "ON" ? HIGH : LOW);
    } else if (relayNum == 3) {
      valve2State = state;
      // digitalWrite(VALVE2_PIN, valve2State == "ON" ? HIGH : LOW);
    }
  }

  // Phan hoi lap tuc trang thai len Web de dong bo nut gat tren giao dien
  sendTelemetry();
}

void reconnect() {
  while (!client.connected()) {
    Serial.print("Dang ket noi toi HiveMQ Cloud...");
    
    // Tao ClientID ngau nhien (tranh bi kick do trung Client ID)
    String clientId = "ESP32-NCKH-";
    clientId += String(random(0xffff), HEX);
    
    if (client.connect(clientId.c_str(), mqtt_user, mqtt_password)) {
      Serial.println(" Thanh cong!");
      
      // Dang ky nhan lenh dieu khien tu Web
      client.subscribe(subscribe_topic);
      Serial.println("Da Subscribe vao topic: " + String(subscribe_topic));
    } else {
      Serial.print(" That bai, ma loi rc=");
      Serial.print(client.state());
      Serial.println(". Thu lai sau 5 giay...");
      delay(5000);
    }
  }
}

void setup() {
  Serial.begin(115200);

  // Cau hinh chan Relay (bo comment khi noi vao phan cung that)
  // pinMode(PUMP_PIN, OUTPUT);
  // pinMode(VALVE1_PIN, OUTPUT);
  // pinMode(VALVE2_PIN, OUTPUT);
  // digitalWrite(PUMP_PIN, LOW);
  // digitalWrite(VALVE1_PIN, LOW);
  // digitalWrite(VALVE2_PIN, LOW);

  setup_wifi();
  
  // Tang buffer size de tranh drop goi tin telemetry JSON
  client.setBufferSize(512);
  client.setServer(mqtt_server, mqtt_port);
  client.setCallback(callback);
}

void loop() {
  if (!client.connected()) {
    reconnect();
  }
  client.loop();

  unsigned long now = millis();
  
  // Gui telemetry dinh ky 10 giay mot lan len Web
  // TUYET DOI KHONG TU BAT/TAT BOM HOAC VAN TAI DAY.
  // Moi quyet dinh tu dong deu do Web quan ly va gui lenh xuong ESP32.
  if (now - lastMsgTime > 10000) {
    lastMsgTime = now;

    // Doc cam bien (gia lap giao dong nhe de test)
    temperature += random(-5, 6) / 10.0; 
    soil1 += random(-2, 3) / 10.0; 
    soil2 += random(-2, 3) / 10.0; 
    
    sendTelemetry();
  }
}
