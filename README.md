# Web IoT Project

Dự án này là một ứng dụng Web IoT sử dụng Java Spring Boot, MySQL làm cơ sở dữ liệu và Mosquitto làm MQTT Broker để giao tiếp với các thiết bị IoT. Dự án được cấu hình bằng Docker để dễ dàng triển khai và chạy trên mọi môi trường.

Dưới đây là hướng dẫn chi tiết từng bước để cài đặt Docker và chạy dự án này trên máy tính của bạn.

---

## 🛠 Hướng dẫn Cài đặt Môi trường (Cài đặt Docker)

Để chạy dự án, máy tính của bạn cần cài đặt **Docker** và **Docker Compose**. 

### 1. Dành cho Windows
1. Truy cập trang web chính thức của Docker: [Tải Docker Desktop cho Windows](https://docs.docker.com/desktop/install/windows-install/)
2. Nhấn vào nút **Docker Desktop for Windows** để tải file cài đặt `.exe`.
3. Chạy file cài đặt vừa tải về. Đảm bảo rằng bạn đã tích chọn cài đặt **WSL 2** (Windows Subsystem for Linux) nếu được hỏi, vì Docker Desktop yêu cầu WSL 2 để hoạt động tốt nhất trên Windows.
4. Sau khi cài đặt hoàn tất, hãy khởi động lại máy tính (nếu hệ thống yêu cầu).
5. Mở ứng dụng **Docker Desktop** vừa cài đặt và làm theo hướng dẫn để chấp nhận các điều khoản. Chờ cho đến khi biểu tượng Docker ở góc dưới bên phải màn hình chuyển sang màu xanh (Engine running).

### 2. Dành cho macOS
1. Truy cập trang web: [Tải Docker Desktop cho Mac](https://docs.docker.com/desktop/install/mac-install/)
2. Chọn phiên bản phù hợp với chip máy Mac của bạn (Intel hoặc Apple Silicon/M-series).
3. Tải file `.dmg` về, mở file và kéo thả Docker vào thư mục Applications.
4. Mở ứng dụng Docker từ Launchpad, cấp quyền truy cập và chờ cho Docker Engine khởi động thành công.

### 3. Dành cho Ubuntu / Debian (Linux)
Mở Terminal và chạy lần lượt các lệnh sau:
```bash
# Cập nhật danh sách gói
sudo apt-get update

# Cài đặt các gói cần thiết
sudo apt-get install ca-certificates curl gnupg

# Thêm khóa GPG chính thức của Docker
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

# Thiết lập repository
echo \
  "deb [arch="$(dpkg --print-architecture)" signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  "$(. /etc/os-release && echo "$VERSION_CODENAME")" stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Cài đặt Docker Engine và Docker Compose
sudo apt-get update
sudo apt-get install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Phân quyền cho user hiện tại để dùng Docker không cần 'sudo'
sudo usermod -aG docker $USER
newgrp docker
```

---

## 🚀 Hướng dẫn Chạy Dự án

Sau khi đã chắc chắn Docker đang hoạt động, bạn làm theo các bước sau để chạy dự án.

### Bước 1: Clone (Tải về) mã nguồn dự án
Nếu bạn đã có mã nguồn trong máy, hãy bỏ qua bước này. Nếu chưa, hãy mở Terminal/Command Prompt và chạy:
```bash
git clone <đường-dẫn-url-của-repository>
cd web_iot
```

### Bước 2: Chạy dự án với Docker Compose
Đảm bảo bạn đang đứng ở thư mục gốc của dự án (nơi có chứa file `docker-compose.yml`).

Mở Terminal hoặc Command Prompt, chạy lệnh sau:
```bash
docker compose up -d --build
```
*Giải thích lệnh:*
- `up`: Khởi tạo và chạy các container.
- `-d` (detach): Chạy các container ngầm (background), giúp bạn có thể tiếp tục sử dụng terminal.
- `--build`: Build lại Docker image cho ứng dụng Java Spring Boot để đảm bảo code mới nhất được cập nhật.

Quá trình này có thể mất vài phút ở lần chạy đầu tiên vì Docker cần tải về các image cho MySQL, Mosquitto và JDK.

### Bước 3: Kiểm tra trạng thái các dịch vụ
Để kiểm tra xem các dịch vụ đã chạy thành công chưa, bạn dùng lệnh:
```bash
docker compose ps
```
Bạn sẽ thấy 3 container đang ở trạng thái **Up**:
- `iot-java-service` (Web App)
- `mysql-iot-web` (Database)
- `mosquitto-iot-web` (MQTT Broker)

---

## 🌐 Truy cập Ứng dụng

Sau khi mọi thứ đã được khởi chạy thành công, bạn có thể truy cập các dịch vụ qua trình duyệt hoặc các công cụ test mạng:

1. **Giao diện Web Ứng dụng (Java Spring Boot)**:
   Mở trình duyệt và truy cập: 
   👉 **http://localhost:8081**

2. **Cơ sở dữ liệu MySQL**:
   - Host: `localhost`
   - Port: `3307`
   - Username: `duc_user`
   - Password: `duc_password_123`
   - Database: `iot_db`

3. **MQTT Broker (Mosquitto)**:
   Các thiết bị IoT (ví dụ: ESP32, Arduino) hoặc phần mềm MQTT Client (như MQTT Explorer) có thể kết nối thông qua:
   - Host/Broker: `localhost` (hoặc địa chỉ IP của máy đang chạy Docker)
   - Port: `1883`

---

## 🛑 Cách Dừng và Tắt Dự án

Khi không muốn chạy dự án nữa, bạn mở Terminal/Command Prompt tại thư mục dự án (chứa `docker-compose.yml`) và chạy:

```bash
docker compose down
```
Lệnh này sẽ dừng và xóa các container. Tuy nhiên, dữ liệu của database (MySQL) sẽ **không bị mất** vì đã được lưu (mount) ra thư mục `mysql-data` trong mã nguồn của bạn.

Nếu bạn muốn **xóa toàn bộ kể cả dữ liệu trong database**, hãy chạy lệnh:
```bash
docker compose down -v
```
*(Lưu ý: Hành động này sẽ xóa vĩnh viễn toàn bộ dữ liệu bạn đã lưu trong MySQL của dự án này).*
