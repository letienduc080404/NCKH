# ==========================================
# GIAI ĐOẠN 1: BUILD CODE (Chạy bằng Maven)
# ==========================================
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build
WORKDIR /app

# Copy thư mục mã nguồn vào Docker
COPY java-app/iot-web-app/pom.xml iot-web-app/
COPY java-app/iot-web-app/src iot-web-app/src/

# Chạy lệnh build của Maven để tạo ra file .jar (Bỏ qua chạy Test để build nhanh hơn)
RUN cd iot-web-app && mvn clean package -DskipTests

# ==========================================
# GIAI ĐOẠN 2: CHẠY APP (Chỉ lấy file .jar)
# ==========================================
FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app

# Chỉ copy file .jar đã được build thành công từ Giai đoạn 1 sang đây (Giúp Docker image cực nhẹ)
COPY --from=build /app/iot-web-app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
