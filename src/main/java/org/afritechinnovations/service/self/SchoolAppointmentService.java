package org.afritechinnovations.service.self;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.self.SchoolAppointmentDto.*;
import org.afritechinnovations.dto.self.ParentPortalDto.AppointmentDecision;
import org.afritechinnovations.dto.communication.FamilyContactDto.Recipient;
import org.afritechinnovations.model.common.StaffModule;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.service.communication.ConversationMessagingService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SchoolAppointmentService {
    private final JdbcTemplate jdbc;
    private final AccessGuard guard;
    private final ConversationMessagingService messaging;
    private static final String SELECT = """
        SELECT a.*, s.name AS school_name, concat(o.first_name,' ',o.last_name) AS organizer_name,
          concat(r.first_name,' ',r.last_name) AS recipient_name
        FROM school_appointments a JOIN schools s ON s.id=a.school_id
        JOIN users o ON o.id=a.organizer_user_id JOIN users r ON r.id=a.recipient_user_id
        """;

    public List<Recipient> recipients(Long schoolId) {
        guard.requireSchoolModule(schoolId, StaffModule.STUDENTS);
        return messaging.recipients(guard.currentUserId(), schoolId, null).stream()
            .filter(r -> "PARENT".equals(r.role()) || "ENSEIGNANT".equals(r.role())).toList();
    }
    public List<Appointment> schoolAppointments(Long schoolId) {
        guard.requireSchoolModule(schoolId, StaffModule.STUDENTS);
        return jdbc.query(SELECT + " WHERE a.school_id=? AND a.organizer_user_id=? ORDER BY a.proposed_at DESC,a.id DESC",
            this::appointment, schoolId, guard.currentUserId());
    }
    public List<Appointment> received() {
        return jdbc.query(SELECT + " WHERE a.recipient_user_id=? ORDER BY a.proposed_at DESC,a.id DESC",
            this::appointment, guard.currentUserId());
    }
    @Transactional
    public Appointment create(Long schoolId, Request request) {
        if (recipients(schoolId).stream().noneMatch(r -> r.userId().equals(request.recipientUserId())))
            throw new AccessDeniedException("Ce destinataire n'est pas accessible dans cet établissement.");
        if (!request.proposedAt().isAfter(LocalDateTime.now())) throw new IllegalArgumentException("Choisissez une date future.");
        Long id = jdbc.queryForObject("INSERT INTO school_appointments(school_id,organizer_user_id,recipient_user_id,proposed_at,reason) VALUES (?,?,?,?,?) RETURNING id",
            Long.class, schoolId, guard.currentUserId(), request.recipientUserId(), request.proposedAt(), request.reason().trim());
        return jdbc.queryForObject(SELECT + " WHERE a.id=?", this::appointment, id);
    }
    @Transactional
    public void decide(Long id, AppointmentDecision request) {
        int updated = jdbc.update("UPDATE school_appointments SET status=?,response=? WHERE id=? AND recipient_user_id=? AND status='PENDING' AND (?='REJECTED' OR proposed_at>?)",
            request.status().name(), request.response().trim(), id, guard.currentUserId(), request.status().name(), LocalDateTime.now());
        if (updated == 0) throw new IllegalArgumentException("Cette demande n'est plus disponible ou ne vous est pas destinée.");
    }
    @Transactional
    public void cancel(Long schoolId, Long id) {
        guard.requireSchoolModule(schoolId, StaffModule.STUDENTS);
        int updated = jdbc.update("UPDATE school_appointments SET status='CANCELLED' WHERE id=? AND school_id=? AND organizer_user_id=? AND status IN ('PENDING','ACCEPTED')",
            id, schoolId, guard.currentUserId());
        if (updated == 0) throw new IllegalArgumentException("Ce rendez-vous ne peut plus être annulé.");
    }
    private Appointment appointment(ResultSet rs, int n) throws SQLException {
        return new Appointment(rs.getLong("id"), rs.getLong("school_id"), rs.getString("school_name"),
            rs.getString("organizer_name"), rs.getString("recipient_name"), rs.getObject("proposed_at", LocalDateTime.class),
            rs.getString("reason"), rs.getString("status"), rs.getString("response"), rs.getLong("organizer_user_id") == guard.currentUserId().longValue());
    }
}
