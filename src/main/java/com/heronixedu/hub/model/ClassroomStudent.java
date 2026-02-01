package com.heronixedu.hub.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents a student connected to a classroom session.
 * Tracks student status and allows individual control.
 */
@Entity
@Table(name = "classroom_students")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassroomStudent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The classroom session this student belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ClassroomSession session;

    /**
     * The student user.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    /**
     * Computer/device identifier (hostname or MAC address).
     */
    @Column(name = "device_id", length = 100)
    private String deviceId;

    /**
     * Computer name for display.
     */
    @Column(name = "computer_name", length = 100)
    private String computerName;

    /**
     * IP address of the student's computer.
     */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /**
     * Current connection status.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    @Builder.Default
    private StudentStatus status = StudentStatus.CONNECTED;

    /**
     * Whether this specific student's screen is frozen (individual override).
     */
    @Column(name = "screen_frozen")
    @Builder.Default
    private Boolean screenFrozen = false;

    /**
     * Currently active application on student's computer.
     */
    @Column(name = "current_app", length = 200)
    private String currentApp;

    /**
     * Screenshot thumbnail (base64 encoded, optional feature).
     */
    @Column(name = "screen_thumbnail", columnDefinition = "TEXT")
    private String screenThumbnail;

    /**
     * Last heartbeat from student's Hub instance.
     */
    @Column(name = "last_heartbeat")
    private LocalDateTime lastHeartbeat;

    @Column(name = "joined_at")
    @Builder.Default
    private LocalDateTime joinedAt = LocalDateTime.now();

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    /**
     * Whether attention was requested by teacher (visual alert).
     */
    @Column(name = "attention_requested")
    @Builder.Default
    private Boolean attentionRequested = false;

    /**
     * Private message from teacher to this student.
     */
    @Column(name = "private_message", length = 500)
    private String privateMessage;

    public enum StudentStatus {
        CONNECTED,      // Active and connected
        DISCONNECTED,   // Lost connection
        IDLE,           // Connected but inactive
        AWAY,           // Student marked as away
        BLOCKED         // Blocked from session
    }

    /**
     * Check if student is effectively frozen (either individually or via session).
     */
    public boolean isEffectivelyFrozen() {
        if (Boolean.TRUE.equals(screenFrozen)) {
            return true;
        }
        return session != null && Boolean.TRUE.equals(session.getScreensFrozen());
    }

    /**
     * Check if student connection is stale (no heartbeat in 30 seconds).
     */
    public boolean isConnectionStale() {
        if (lastHeartbeat == null) {
            return true;
        }
        return lastHeartbeat.plusSeconds(30).isBefore(LocalDateTime.now());
    }

    /**
     * Update heartbeat timestamp.
     */
    public void heartbeat() {
        this.lastHeartbeat = LocalDateTime.now();
        if (this.status == StudentStatus.DISCONNECTED) {
            this.status = StudentStatus.CONNECTED;
        }
    }

    /**
     * Mark student as disconnected.
     */
    public void disconnect() {
        this.status = StudentStatus.DISCONNECTED;
        this.leftAt = LocalDateTime.now();
    }
}
