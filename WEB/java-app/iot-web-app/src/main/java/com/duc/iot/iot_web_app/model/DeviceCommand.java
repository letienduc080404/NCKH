package com.duc.iot.iot_web_app.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "device_commands")
@Data
public class DeviceCommand {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Column(name = "command_type", nullable = false)
    private String commandType;

    @Column(columnDefinition = "JSON")
    private String payload;

    @Enumerated(EnumType.STRING)
    private CommandStatus status;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum CommandStatus {
        PENDING, SENT, ACKNOWLEDGED, FAILED
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = CommandStatus.PENDING;
        }
    }
}
