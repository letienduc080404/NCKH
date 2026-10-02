package com.duc.iot.iot_web_app.model;

import jakarta.persistence.*;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonBackReference;
import java.time.LocalDateTime;

@Entity
@Table(name = "sensors")
@Data
public class Sensor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sensor_code")
    private String sensorCode;

    @Column(name = "sensor_name")
    private String sensorName;

    @Enumerated(EnumType.STRING)
    @Column(name = "sensor_type", nullable = false)
    private SensorType sensorType;

    private String unit;
    private Double minValue;
    private Double maxValue;
    private Double warningThreshold;
    private Double dangerThreshold;
    private Boolean isEnabled;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "device_id")
    @JsonBackReference
    private Device device;

    public enum SensorType {
        TEMPERATURE, HUMIDITY, GAS, SMOKE, FIRE, WATER, LIGHT, MOTION, CUSTOM
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.isEnabled == null) {
            this.isEnabled = true;
        }
        if (this.sensorType == null) {
            this.sensorType = SensorType.CUSTOM;
        }
    }
}