package com.heronixedu.hub.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents an active classroom session where a teacher can control student computers.
 */
@Entity
@Table(name = "classroom_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassroomSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique session code for students to join.
     */
    @Column(name = "session_code", unique = true, nullable = false, length = 10)
    private String sessionCode;

    /**
     * Display name for the session (e.g., "Math 101 - Period 3").
     */
    @Column(name = "session_name", nullable = false, length = 200)
    private String sessionName;

    /**
     * Teacher who created this session.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private User teacher;

    /**
     * Current state of the session.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private SessionStatus status = SessionStatus.ACTIVE;

    /**
     * Whether all student screens are currently frozen.
     */
    @Column(name = "screens_frozen")
    @Builder.Default
    private Boolean screensFrozen = false;

    /**
     * Custom message to display on frozen screens.
     */
    @Column(name = "freeze_message", length = 500)
    @Builder.Default
    private String freezeMessage = "Please pay attention to the teacher.";

    /**
     * Whether students can use any applications or only allowed ones.
     */
    @Column(name = "restrict_apps")
    @Builder.Default
    private Boolean restrictApps = false;

    /**
     * Comma-separated list of allowed application codes when restrictApps is true.
     */
    @Column(name = "allowed_apps", length = 2000)
    private String allowedApps;

    /**
     * Whether to block internet access for students.
     */
    @Column(name = "block_internet")
    @Builder.Default
    private Boolean blockInternet = false;

    /**
     * Whether to disable copy/paste functionality.
     */
    @Column(name = "disable_clipboard")
    @Builder.Default
    private Boolean disableClipboard = false;

    /**
     * Maximum number of students that can join.
     */
    @Column(name = "max_students")
    @Builder.Default
    private Integer maxStudents = 40;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    /**
     * Students currently in this session.
     */
    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<ClassroomStudent> students = new HashSet<>();

    public enum SessionStatus {
        ACTIVE,
        PAUSED,
        ENDED
    }

    /**
     * Check if an app is allowed in this session.
     */
    public boolean isAppAllowed(String appCode) {
        if (!Boolean.TRUE.equals(restrictApps) || allowedApps == null || allowedApps.isEmpty()) {
            return true;
        }
        return allowedApps.toLowerCase().contains(appCode.toLowerCase());
    }

    /**
     * End the session.
     */
    public void endSession() {
        this.status = SessionStatus.ENDED;
        this.endedAt = LocalDateTime.now();
        this.screensFrozen = false;
    }
}
