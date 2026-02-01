package com.heronixedu.hub.repository;

import com.heronixedu.hub.model.ClassroomSession;
import com.heronixedu.hub.model.ClassroomStudent;
import com.heronixedu.hub.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClassroomStudentRepository extends JpaRepository<ClassroomStudent, Long> {

    List<ClassroomStudent> findBySession(ClassroomSession session);

    List<ClassroomStudent> findBySessionAndStatus(ClassroomSession session, ClassroomStudent.StudentStatus status);

    Optional<ClassroomStudent> findBySessionAndStudent(ClassroomSession session, User student);

    Optional<ClassroomStudent> findByStudentAndSessionStatus(User student, ClassroomSession.SessionStatus sessionStatus);

    @Query("SELECT cs FROM ClassroomStudent cs WHERE cs.student = :student AND cs.session.status = 'ACTIVE'")
    Optional<ClassroomStudent> findActiveSessionForStudent(User student);

    @Query("SELECT COUNT(cs) FROM ClassroomStudent cs WHERE cs.session = :session AND cs.status = 'CONNECTED'")
    long countConnectedStudents(ClassroomSession session);

    @Modifying
    @Query("UPDATE ClassroomStudent cs SET cs.status = 'DISCONNECTED' WHERE cs.session = :session AND cs.lastHeartbeat < :threshold")
    int markStaleConnectionsAsDisconnected(ClassroomSession session, LocalDateTime threshold);

    @Query("SELECT cs FROM ClassroomStudent cs WHERE cs.session = :session ORDER BY cs.student.fullName")
    List<ClassroomStudent> findBySessionOrderByStudentName(ClassroomSession session);
}
