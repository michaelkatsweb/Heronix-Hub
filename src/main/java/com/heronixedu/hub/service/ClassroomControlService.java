package com.heronixedu.hub.service;

import com.heronixedu.hub.model.ClassroomSession;
import com.heronixedu.hub.model.ClassroomStudent;
import com.heronixedu.hub.model.User;
import com.heronixedu.hub.model.enums.AuditAction;
import com.heronixedu.hub.repository.ClassroomSessionRepository;
import com.heronixedu.hub.repository.ClassroomStudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing classroom sessions and student computer control.
 * Allows teachers to freeze screens, restrict apps, and monitor student activity.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClassroomControlService {

    private final ClassroomSessionRepository sessionRepository;
    private final ClassroomStudentRepository studentRepository;
    private final AuditLogService auditLogService;

    private static final String SESSION_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int SESSION_CODE_LENGTH = 6;
    private static final SecureRandom random = new SecureRandom();

    // ============== TEACHER FUNCTIONS ==============

    /**
     * Create a new classroom session.
     */
    @Transactional
    public ClassroomSession createSession(String sessionName, User teacher) {
        // Generate unique session code
        String sessionCode = generateUniqueSessionCode();

        ClassroomSession session = ClassroomSession.builder()
                .sessionCode(sessionCode)
                .sessionName(sessionName)
                .teacher(teacher)
                .status(ClassroomSession.SessionStatus.ACTIVE)
                .screensFrozen(false)
                .freezeMessage("Please pay attention to the teacher.")
                .restrictApps(false)
                .blockInternet(false)
                .disableClipboard(false)
                .maxStudents(40)
                .build();

        ClassroomSession saved = sessionRepository.save(session);

        auditLogService.log(
                AuditAction.CONFIG_CHANGE,
                teacher.getUsername(),
                "Created classroom session: " + sessionName + " (Code: " + sessionCode + ")"
        );

        log.info("Teacher {} created classroom session {} with code {}",
                teacher.getUsername(), sessionName, sessionCode);

        return saved;
    }

    /**
     * Generate a unique session code.
     */
    private String generateUniqueSessionCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(SESSION_CODE_LENGTH);
            for (int i = 0; i < SESSION_CODE_LENGTH; i++) {
                sb.append(SESSION_CODE_CHARS.charAt(random.nextInt(SESSION_CODE_CHARS.length())));
            }
            code = sb.toString();
        } while (sessionRepository.existsBySessionCode(code));

        return code;
    }

    /**
     * Freeze all student screens in a session.
     */
    @Transactional
    public ClassroomSession freezeAllScreens(Long sessionId, String message, User teacher) {
        ClassroomSession session = getSessionForTeacher(sessionId, teacher);

        session.setScreensFrozen(true);
        if (message != null && !message.isEmpty()) {
            session.setFreezeMessage(message);
        }

        ClassroomSession saved = sessionRepository.save(session);

        auditLogService.log(
                AuditAction.CONFIG_CHANGE,
                teacher.getUsername(),
                "Froze all screens in session: " + session.getSessionName()
        );

        log.info("Teacher {} froze all screens in session {}", teacher.getUsername(), session.getSessionCode());
        return saved;
    }

    /**
     * Unfreeze all student screens in a session.
     */
    @Transactional
    public ClassroomSession unfreezeAllScreens(Long sessionId, User teacher) {
        ClassroomSession session = getSessionForTeacher(sessionId, teacher);

        session.setScreensFrozen(false);

        // Also unfreeze individually frozen students
        List<ClassroomStudent> students = studentRepository.findBySession(session);
        for (ClassroomStudent student : students) {
            student.setScreenFrozen(false);
        }
        studentRepository.saveAll(students);

        ClassroomSession saved = sessionRepository.save(session);

        auditLogService.log(
                AuditAction.CONFIG_CHANGE,
                teacher.getUsername(),
                "Unfroze all screens in session: " + session.getSessionName()
        );

        log.info("Teacher {} unfroze all screens in session {}", teacher.getUsername(), session.getSessionCode());
        return saved;
    }

    /**
     * Freeze a specific student's screen.
     */
    @Transactional
    public ClassroomStudent freezeStudent(Long sessionId, Long studentId, User teacher) {
        ClassroomSession session = getSessionForTeacher(sessionId, teacher);
        ClassroomStudent student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found in session"));

        if (!student.getSession().getId().equals(session.getId())) {
            throw new IllegalArgumentException("Student is not in this session");
        }

        student.setScreenFrozen(true);
        ClassroomStudent saved = studentRepository.save(student);

        log.info("Teacher {} froze screen for student {} in session {}",
                teacher.getUsername(), student.getStudent().getUsername(), session.getSessionCode());

        return saved;
    }

    /**
     * Unfreeze a specific student's screen.
     */
    @Transactional
    public ClassroomStudent unfreezeStudent(Long sessionId, Long studentId, User teacher) {
        ClassroomSession session = getSessionForTeacher(sessionId, teacher);
        ClassroomStudent student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));

        if (!student.getSession().getId().equals(session.getId())) {
            throw new IllegalArgumentException("Student is not in this session");
        }

        student.setScreenFrozen(false);
        ClassroomStudent saved = studentRepository.save(student);

        log.info("Teacher {} unfroze screen for student {} in session {}",
                teacher.getUsername(), student.getStudent().getUsername(), session.getSessionCode());

        return saved;
    }

    /**
     * Request attention from a specific student (shows visual alert).
     */
    @Transactional
    public ClassroomStudent requestAttention(Long sessionId, Long studentId, User teacher) {
        ClassroomSession session = getSessionForTeacher(sessionId, teacher);
        ClassroomStudent student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));

        student.setAttentionRequested(true);
        return studentRepository.save(student);
    }

    /**
     * Send a private message to a student.
     */
    @Transactional
    public ClassroomStudent sendPrivateMessage(Long sessionId, Long studentId, String message, User teacher) {
        ClassroomSession session = getSessionForTeacher(sessionId, teacher);
        ClassroomStudent student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));

        student.setPrivateMessage(message);
        return studentRepository.save(student);
    }

    /**
     * Set app restrictions for the session.
     */
    @Transactional
    public ClassroomSession setAppRestrictions(Long sessionId, boolean restrictApps,
                                                String allowedApps, User teacher) {
        ClassroomSession session = getSessionForTeacher(sessionId, teacher);

        session.setRestrictApps(restrictApps);
        session.setAllowedApps(allowedApps);

        ClassroomSession saved = sessionRepository.save(session);

        auditLogService.log(
                AuditAction.CONFIG_CHANGE,
                teacher.getUsername(),
                "Updated app restrictions for session: " + session.getSessionName() +
                        " (restricted: " + restrictApps + ")"
        );

        return saved;
    }

    /**
     * Block student from session.
     */
    @Transactional
    public ClassroomStudent blockStudent(Long sessionId, Long studentId, User teacher) {
        ClassroomSession session = getSessionForTeacher(sessionId, teacher);
        ClassroomStudent student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));

        student.setStatus(ClassroomStudent.StudentStatus.BLOCKED);
        student.disconnect();

        return studentRepository.save(student);
    }

    /**
     * End a classroom session.
     */
    @Transactional
    public ClassroomSession endSession(Long sessionId, User teacher) {
        ClassroomSession session = getSessionForTeacher(sessionId, teacher);

        session.endSession();

        // Disconnect all students
        List<ClassroomStudent> students = studentRepository.findBySession(session);
        for (ClassroomStudent student : students) {
            student.disconnect();
        }
        studentRepository.saveAll(students);

        ClassroomSession saved = sessionRepository.save(session);

        auditLogService.log(
                AuditAction.CONFIG_CHANGE,
                teacher.getUsername(),
                "Ended classroom session: " + session.getSessionName()
        );

        log.info("Teacher {} ended session {}", teacher.getUsername(), session.getSessionCode());
        return saved;
    }

    /**
     * Get all students in a session.
     */
    public List<ClassroomStudent> getSessionStudents(Long sessionId, User teacher) {
        ClassroomSession session = getSessionForTeacher(sessionId, teacher);
        return studentRepository.findBySessionOrderByStudentName(session);
    }

    /**
     * Get active sessions for a teacher.
     */
    public List<ClassroomSession> getActiveSessionsForTeacher(User teacher) {
        return sessionRepository.findActiveSessionsByTeacher(teacher);
    }

    // ============== STUDENT FUNCTIONS ==============

    /**
     * Join a classroom session using the session code.
     */
    @Transactional
    public ClassroomStudent joinSession(String sessionCode, User student) {
        ClassroomSession session = sessionRepository.findBySessionCode(sessionCode.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Invalid session code"));

        if (session.getStatus() != ClassroomSession.SessionStatus.ACTIVE) {
            throw new IllegalStateException("Session is not active");
        }

        // Check if already in session
        Optional<ClassroomStudent> existing = studentRepository.findBySessionAndStudent(session, student);
        if (existing.isPresent()) {
            ClassroomStudent cs = existing.get();
            cs.heartbeat();
            return studentRepository.save(cs);
        }

        // Check max students
        long currentCount = studentRepository.countConnectedStudents(session);
        if (currentCount >= session.getMaxStudents()) {
            throw new IllegalStateException("Session is full");
        }

        // Get device info
        String computerName = getComputerName();
        String ipAddress = getLocalIpAddress();

        ClassroomStudent classroomStudent = ClassroomStudent.builder()
                .session(session)
                .student(student)
                .computerName(computerName)
                .ipAddress(ipAddress)
                .deviceId(computerName + "-" + student.getUsername())
                .status(ClassroomStudent.StudentStatus.CONNECTED)
                .lastHeartbeat(LocalDateTime.now())
                .build();

        ClassroomStudent saved = studentRepository.save(classroomStudent);

        log.info("Student {} joined session {} from {}",
                student.getUsername(), sessionCode, computerName);

        return saved;
    }

    /**
     * Leave the current session.
     */
    @Transactional
    public void leaveSession(User student) {
        Optional<ClassroomStudent> cs = studentRepository.findActiveSessionForStudent(student);
        if (cs.isPresent()) {
            ClassroomStudent classroomStudent = cs.get();
            classroomStudent.disconnect();
            studentRepository.save(classroomStudent);

            log.info("Student {} left session {}", student.getUsername(),
                    classroomStudent.getSession().getSessionCode());
        }
    }

    /**
     * Send heartbeat to keep connection alive.
     */
    @Transactional
    public ClassroomStudent heartbeat(User student, String currentApp) {
        ClassroomStudent cs = studentRepository.findActiveSessionForStudent(student)
                .orElseThrow(() -> new IllegalStateException("Not in an active session"));

        cs.heartbeat();
        if (currentApp != null) {
            cs.setCurrentApp(currentApp);
        }

        return studentRepository.save(cs);
    }

    /**
     * Get current session status for a student.
     */
    public Optional<StudentSessionStatus> getStudentSessionStatus(User student) {
        return studentRepository.findActiveSessionForStudent(student)
                .map(cs -> new StudentSessionStatus(
                        cs.getSession().getSessionCode(),
                        cs.getSession().getSessionName(),
                        cs.getSession().getTeacher().getFullName(),
                        cs.isEffectivelyFrozen(),
                        cs.getSession().getFreezeMessage(),
                        cs.getSession().getRestrictApps(),
                        cs.getSession().getAllowedApps(),
                        cs.getAttentionRequested(),
                        cs.getPrivateMessage()
                ));
    }

    /**
     * Clear attention request after student acknowledges.
     */
    @Transactional
    public void clearAttentionRequest(User student) {
        studentRepository.findActiveSessionForStudent(student)
                .ifPresent(cs -> {
                    cs.setAttentionRequested(false);
                    studentRepository.save(cs);
                });
    }

    /**
     * Clear private message after student reads it.
     */
    @Transactional
    public void clearPrivateMessage(User student) {
        studentRepository.findActiveSessionForStudent(student)
                .ifPresent(cs -> {
                    cs.setPrivateMessage(null);
                    studentRepository.save(cs);
                });
    }

    // ============== UTILITY FUNCTIONS ==============

    /**
     * Clean up stale connections periodically.
     */
    @Scheduled(fixedRate = 15000) // Every 15 seconds
    @Transactional
    public void cleanupStaleConnections() {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(30);

        List<ClassroomSession> activeSessions = sessionRepository.findAllActiveSessions();
        for (ClassroomSession session : activeSessions) {
            int disconnected = studentRepository.markStaleConnectionsAsDisconnected(session, threshold);
            if (disconnected > 0) {
                log.debug("Marked {} stale connections as disconnected in session {}",
                        disconnected, session.getSessionCode());
            }
        }
    }

    /**
     * Get session for teacher with permission check.
     */
    private ClassroomSession getSessionForTeacher(Long sessionId, User teacher) {
        ClassroomSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        if (!session.getTeacher().getId().equals(teacher.getId())) {
            throw new SecurityException("You do not have permission to manage this session");
        }

        return session;
    }

    private String getComputerName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return System.getProperty("user.name") + "-PC";
        }
    }

    private String getLocalIpAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Record class for student session status.
     */
    public record StudentSessionStatus(
            String sessionCode,
            String sessionName,
            String teacherName,
            boolean screenFrozen,
            String freezeMessage,
            boolean appsRestricted,
            String allowedApps,
            boolean attentionRequested,
            String privateMessage
    ) {}
}
