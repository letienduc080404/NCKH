package com.duc.iot.iot_web_app.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "sensor_readings", indexes = {
    @Index(name = "idx_sensor_recorded", columnList = "sensor_id, recorded_at")
})
@Data
public class SensorReading {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "sensor_id", nullable = false)
    private Sensor sensor;

    private Double rawValue;
    private Double filteredValue;

    @Enumerated(EnumType.STRING)
    private AlertLevel alertLevel;

    private LocalDateTime recordedAt;

    public enum AlertLevel {
        NORMAL, WARNING, DANGER
    }

    @PrePersist
    protected void onCreate() {
        if (this.recordedAt == null) {
            this.recordedAt = LocalDateTime.now();
        }
        if (this.alertLevel == null) {
            this.alertLevel = AlertLevel.NORMAL;
        }
    }
}
