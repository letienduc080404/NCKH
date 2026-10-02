import requests
import time
import random
import sys

# Thay thế bằng Access Token (Device UID) thực tế của thiết bị bạn đã tạo trên web
DEVICE_TOKEN = "uid-esp32-001" # Mặc định dùng của ESP32-GreenHouse

SERVER_URL = f"http://127.0.0.1:8080/api/v1/{DEVICE_TOKEN}/telemetry"

def generate_payload():
    return {
        "free_heap": random.randint(150000, 250000),
        "receive_date": time.strftime("%d/%m/%Y"),
        "receive_time": time.strftime("%H:%M:%S"),
        "wifi_rssi": random.randint(-85, -40),
        "Temperature": round(random.uniform(22.0, 35.0), 1),
        "Humidity": round(random.uniform(40.0, 80.0), 1),
        "hardware_version": "ESP32-WROOM-32",
        "mqtt_connected": True
    }

print(f"Bắt đầu giả lập thiết bị: {DEVICE_TOKEN}")
print(f"Gửi dữ liệu tới: {SERVER_URL}")
print("Nhấn Ctrl+C để dừng.\n")

try:
    while True:
        payload = generate_payload()
        try:
            response = requests.post(SERVER_URL, json=payload, timeout=5)
            if response.status_code == 200:
                print(f"[OK] Đã gửi: {payload['Temperature']}°C, {payload['Humidity']}% | Response: {response.text}")
            else:
                print(f"[LỖI] HTTP Status {response.status_code}: {response.text}")
        except requests.exceptions.RequestException as e:
            print(f"[LỖI KẾT NỐI] {e}")
        
        # Chờ 3 giây trước khi gửi tiếp
        time.sleep(0.5)
except KeyboardInterrupt:
    print("\nĐã dừng giả lập.")
    sys.exit(0)
