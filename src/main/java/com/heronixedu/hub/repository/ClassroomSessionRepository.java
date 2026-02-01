package com.heronixedu.hub.repository;

import com.heronixedu.hub.model.ClassroomSession;
import com.heronixedu.hub.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClassroomSessionRepository extends JpaRepository<ClassroomSession, Long> {

    Optional<ClassroomSession> findBySessionCode(String sessionCode);

    List<ClassroomSession> findByTeacherAndStatus(User teacher, ClassroomSession.SessionStatus status);

    List<ClassroomSession> findByTeacherOrderByCreatedAtDesc(User teacher);

    @Query("SELECT s FROM ClassroomSession s WHERE s.status = 'ACTIVE' AND s.teacher = :teacher")
    List<ClassroomSession> findActiveSessionsByTeacher(User teacher);

    @Query("SELECT s FROM ClassroomSession s WHERE s.status = 'ACTIVE'")
    List<ClassroomSession> findAllActiveSessions();

    boolean existsBySessionCode(String sessionCode);

    @Query("SELECT COUNT(s) FROM ClassroomSession s WHERE s.status = 'ACTIVE'")
    long countActiveSessions();
}
