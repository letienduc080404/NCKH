package com.duc.iot.iot_web_app.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "devices")
@Data
@SQLDelete(sql = "UPDATE devices SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Device {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_uid", unique = true, nullable = false)
    private String deviceUid;

    @Column(name = "device_name", nullable = false)
    private String deviceName;

    @Column(name = "type_id", nullable = false)
    private Integer typeId;

    @ManyToOne
    @JoinColumn(name = "group_id")
    private DeviceGroup group;

    @Column(name = "hardware_version")
    private String hardwareVersion;

    @Column(name = "firmware_version")
    private String firmwareVersion;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "free_heap")
    private Integer freeHeap;

    @Column(name = "wifi_rssi")
    private Integer wifiRssi;

    private Long uptime;

    @Column(name = "reboot_count")
    private Integer rebootCount;

    @Column(name = "last_reboot_reason")
    private String lastRebootReason;

    @Column(name = "mqtt_connected")
    private Boolean mqttConnected;

    @Column(name = "mac_address")
    private String macAddress;

    private String location;

    private String category; // Trong nhà, Ngoài vườn, Nhà máy
    
    @Enumerated(EnumType.STRING)
    private Status status;

    private LocalDateTime lastSeen;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "device", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Sensor> sensors;

    public enum Status {
        ONLINE, OFFLINE, WARNING, ERROR
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = Status.OFFLINE;
        }
        if (this.deviceUid == null) {
            this.deviceUid = java.util.UUID.randomUUID().toString();
        }
        if (this.typeId == null) {
            this.typeId = 1; // Default type
        }
    }
}
