package com.duc.iot.iot_web_app.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "ota_history")
@Data
public class OtaHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @ManyToOne
    @JoinColumn(name = "firmware_id", nullable = false)
    private FirmwareVersion firmware;

    @Enumerated(EnumType.STRING)
    private OtaStatus status;

    private String errorMessage;

    @Column(name = "started_at", updatable = false)
    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    public enum OtaStatus {
        PENDING, DOWNLOADING, VERIFYING, SUCCESS, FAILED
    }

    @PrePersist
    protected void onCreate() {
        this.startedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = OtaStatus.PENDING;
        }
    }
}
