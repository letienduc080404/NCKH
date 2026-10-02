package com.duc.iot.iot_web_app.model;

import jakarta.persistence.*;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Lưu từng dải ngưỡng (zone) cho một sensor.
 * Ví dụ: Nhiệt độ → "Quá lạnh" (0-25), "Bình thường" (25-35), "Quá nóng" (35-100)
 */
@Entity
@Table(name = "sensor_thresholds")
@Data
public class SensorThreshold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sensor_id", nullable = false)
    private Sensor sensor;

    /** Tên dải ngưỡng do người dùng đặt, VD: "Quá lạnh", "Bình thường", "Quá nóng" */
    @Column(name = "label", nullable = false)
    private String label;

    /** Giá trị tối thiểu của dải (bao gồm) */
    @Column(name = "min_value", nullable = false)
    private Double minValue;

    /** Giá trị tối đa của dải (không bao gồm, trừ dải cuối) */
    @Column(name = "max_value", nullable = false)
    private Double maxValue;

    /** Màu HEX hiển thị trên UI, VD: "#3b82f6", "#10b981", "#ef4444" */
    @Column(name = "color")
    private String color;

    /** Thứ tự hiển thị */
    @Column(name = "display_order")
    private Integer displayOrder;
}
