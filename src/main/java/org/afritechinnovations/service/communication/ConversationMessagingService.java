package org.afritechinnovations.service.communication;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.communication.FamilyContactDto;
import org.afritechinnovations.model.common.StaffModule;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.model.communication.ConversationParticipant;
import org.afritechinnovations.model.communication.SchoolConversation;
import org.afritechinnovations.model.communication.SchoolConversationMessage;
import org.afritechinnovations.model.people.Parent;
import org.afritechinnovations.model.people.Student;
import org.afritechinnovations.model.people.Teacher;
import org.afritechinnovations.repository.academic.ClassSubjectTeacherRepository;
import org.afritechinnovations.repository.common.SchoolRepository;
import org.afritechinnovations.repository.common.UserRepository;
import org.afritechinnovations.repository.communication.ConversationParticipantRepository;
import org.afritechinnovations.repository.communication.SchoolConversationMessageRepository;
import org.afritechinnovations.repository.communication.SchoolConversationRepository;
import org.afritechinnovations.repository.people.ParentRepository;
import org.afritechinnovations.repository.people.ParentStudentRepository;
import org.afritechinnovations.repository.people.StudentRepository;
import org.afritechinnovations.repository.people.TeacherRepository;
import org.afritechinnovations.repository.people.StudentEnrollmentRepository;
import org.afritechinnovations.security.AccessGuard;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class ConversationMessagingService {
    private final AccessGuard guard;
    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;
    private final SchoolConversationRepository conversationRepository;
    private final SchoolConversationMessageRepository messageRepository;
    private final ConversationParticipantRepository participantRepository;
    private final ParentRepository parentRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final TeacherRepository teacherRepository;
    private final ClassSubjectTeacherRepository classTeacherRepository;
    private final StudentEnrollmentRepository enrollmentRepository;
    private final StudentRepository studentRepository;

    @Transactional(readOnly = true)
    public List<FamilyContactDto.ConversationSummary> conversations(Long userId) {
        return participantRepository.findAllForUser(userId).stream()
                .map(p -> FamilyContactMapper.summaryForUser(p.getConversation(), userId)).toList();
    }

    @Transactional(readOnly = true)
    public List<FamilyContactDto.ConversationSummary> conversations(Long userId, Long schoolId) {
        if (schoolId == null) return conversations(userId);
        guard.requireSchoolModule(schoolId, StaffModule.STUDENTS);
        return conversationRepository.findBySchool(schoolId).stream()
                .filter(c -> c.getParticipants().stream().anyMatch(p -> p.getSchool() != null))
                .map(c -> FamilyContactMapper.summary(c, true)).toList();
    }

    @Transactional(readOnly = true)
    public List<FamilyContactDto.Recipient> recipients(Long userId, Long schoolId, Long studentId) {
        School school = school(schoolId);
        requireSchoolAccess(userId, schoolId, studentId);
        LinkedHashMap<Long, FamilyContactDto.Recipient> recipients = new LinkedHashMap<>();
        boolean parentActor = parentRepository.findByUserId(userId).isPresent();
        boolean teacherActor = !teacherRepository.findByUserId(userId).isEmpty();
        boolean schoolActor = !parentActor && !teacherActor;
        if (parentActor || schoolActor) {
            for (Teacher teacher : teacherRepository.findBySchoolId(schoolId)) {
                if (studentId == null || teachesStudent(teacher, studentId)) {
                    addRecipient(recipients, teacher.getUser(), "ENSEIGNANT");
                }
            }
        }
        if (teacherActor || schoolActor) {
            if (studentId != null) {
                parentStudentRepository.findByStudentIdWithParentUser(studentId).forEach(ps ->
                        addRecipient(recipients, ps.getParent().getUser(), "PARENT"));
            } else {
                parentRepository.searchInSchools(List.of(schoolId), "%", PageRequest.of(0, 10000)).forEach(parent ->
                        addRecipient(recipients, parent.getUser(), "PARENT"));
            }
        }
        recipients.remove(userId);
        return List.copyOf(recipients.values());
    }

    public FamilyContactDto.ConversationThread start(Long userId, FamilyContactDto.NewConversationRequest request) {
        School school = school(request.schoolId());
        requireSchoolAccess(userId, request.schoolId(), request.studentId());
        List<Long> recipientIds = request.recipientUserIds() == null ? List.of() : request.recipientUserIds().stream()
                .filter(Objects::nonNull).distinct().filter(id -> !id.equals(userId)).toList();
        if (!request.recipientSchool() && recipientIds.isEmpty()) {
            throw new IllegalArgumentException("Sélectionnez au moins un destinataire");
        }
        for (Long recipientId : recipientIds) validateRecipient(recipientId, request.schoolId(), request.studentId());
        Student student = null;
        if (request.studentId() != null) {
            student = studentRepository.findById(request.studentId()).orElse(null);
            if (student == null || !student.getSchool().getId().equals(request.schoolId())) {
                throw new IllegalArgumentException("L'élève n'appartient pas à cet établissement");
            }
        }
        User sender = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable"));
        LocalDateTime now = LocalDateTime.now();
        boolean schoolSender = isSchoolAccount(userId, request.schoolId());
        if (!schoolSender && recipientIds.isEmpty() && !request.recipientSchool()) {
            throw new IllegalArgumentException("Sélectionnez au moins un destinataire");
        }
        User parentRecipient = recipientIds.stream().map(id -> parentRepository.findByUserId(id).orElse(null))
                .filter(Objects::nonNull).map(Parent::getUser).findFirst().orElse(isParent(sender) ? sender : null);
        SchoolConversation conversation = conversationRepository.save(SchoolConversation.builder()
                .school(school).parentUser(parentRecipient).student(student).subject(request.subject().trim())
                .createdAt(now).lastMessageAt(now).unreadBySchool(!schoolSender).unreadByParent(schoolSender).build());
        if (schoolSender) {
            participantRepository.save(ConversationParticipant.builder().conversation(conversation).school(school).lastReadAt(now).build());
        } else {
            participantRepository.save(ConversationParticipant.builder().conversation(conversation).user(sender).lastReadAt(now).build());
        }
        if (request.recipientSchool() && !schoolSender) {
            participantRepository.save(ConversationParticipant.builder().conversation(conversation).school(school).build());
        }
        for (Long recipientId : recipientIds) {
            participantRepository.save(ConversationParticipant.builder().conversation(conversation)
                    .user(userRepository.getReferenceById(recipientId)).build());
        }
        messageRepository.save(SchoolConversationMessage.builder().conversation(conversation).sender(sender)
                .fromSchool(schoolSender).content(request.content().trim()).sentAt(now).build());
        return thread(conversation, userId);
    }

    public FamilyContactDto.ConversationThread read(Long userId, Long conversationId) {
        SchoolConversation conversation = requireConversation(conversationId);
        ConversationParticipant participant = participantFor(userId, conversation);
        participant.setLastReadAt(LocalDateTime.now());
        if (participant.getSchool() != null) conversation.setUnreadBySchool(false);
        else if (parentRepository.findByUserId(userId).isPresent()) conversation.setUnreadByParent(false);
        return thread(conversation, userId);
    }

    public FamilyContactDto.ConversationThread reply(Long userId, Long conversationId, FamilyContactDto.ReplyRequest request) {
        SchoolConversation conversation = requireConversation(conversationId);
        ConversationParticipant sender = participantFor(userId, conversation);
        User user = userRepository.getReferenceById(userId);
        boolean fromSchool = sender.getSchool() != null;
        LocalDateTime now = LocalDateTime.now();
        messageRepository.save(SchoolConversationMessage.builder().conversation(conversation).sender(user)
                .fromSchool(fromSchool).content(request.content().trim()).sentAt(now).build());
        conversation.setLastMessageAt(now);
        sender.setLastReadAt(now);
        conversation.setUnreadBySchool(!fromSchool);
        conversation.setUnreadByParent(fromSchool);
        return thread(conversation, userId);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return messageRepository.countUnreadByUser(userId);
    }

    private FamilyContactDto.ConversationThread thread(SchoolConversation conversation, Long userId) {
        boolean schoolView = participantRepository.findByConversationIdAndUserId(conversation.getId(), userId).isEmpty()
                && participantRepository.findByConversationIdAndSchoolId(conversation.getId(), conversation.getSchool().getId()).isPresent();
        List<SchoolConversationMessage> messages = messageRepository.findThread(conversation.getId());
        return schoolView ? FamilyContactMapper.thread(conversation, messages, true)
                : FamilyContactMapper.threadForUser(conversation, messages, userId);
    }

    private SchoolConversation requireConversation(Long id) {
        return conversationRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Conversation introuvable"));
    }

    private ConversationParticipant participantFor(Long userId, SchoolConversation conversation) {
        return participantRepository.findByConversationIdAndUserId(conversation.getId(), userId)
                .or(() -> participantRepository.findByConversationIdAndSchoolId(conversation.getId(), conversation.getSchool().getId())
                        .filter(p -> {
                            try { guard.requireSchoolModule(conversation.getSchool().getId(), StaffModule.STUDENTS); return true; }
                            catch (AccessDeniedException denied) { return false; }
                        }))
                .orElseThrow(() -> new AccessDeniedException("Cette conversation ne vous concerne pas"));
    }

    private void requireSchoolAccess(Long userId, Long schoolId, Long studentId) {
        if (parentRepository.findByUserId(userId).map(parent -> parentStudentRepository.findByParentId(parent.getId()).stream()
                .anyMatch(ps -> ps.getStudent().getSchool().getId().equals(schoolId)
                        && (studentId == null || ps.getStudent().getId().equals(studentId)))).orElse(false)) return;
        if (teacherRepository.findByUserId(userId).stream().anyMatch(t -> t.getSchool().getId().equals(schoolId)
                && (studentId == null || teachesStudent(t, studentId)))) return;
        guard.requireSchoolModule(schoolId, StaffModule.STUDENTS);
    }

    private void validateRecipient(Long userId, Long schoolId, Long studentId) {
        boolean teacher = teacherRepository.findByUserId(userId).stream().anyMatch(t -> t.getSchool().getId().equals(schoolId)
                && (studentId == null || teachesStudent(t, studentId)));
        boolean parent = parentRepository.findByUserId(userId).map(p -> parentStudentRepository.findByParentId(p.getId()).stream()
                .anyMatch(ps -> ps.getStudent().getSchool().getId().equals(schoolId)
                        && (studentId == null || ps.getStudent().getId().equals(studentId)))).orElse(false);
        if (!teacher && !parent) throw new AccessDeniedException("Un destinataire n'est pas rattaché à cet établissement");
    }

    private boolean teachesStudent(Teacher teacher, Long studentId) {
        return classTeacherRepository.findByTeacherId(teacher.getId()).stream().filter(ct -> ct.isActive())
                .anyMatch(ct -> ct.getSchoolClass().getSchool().getId().equals(teacher.getSchool().getId())
                        && enrollmentRepository.findByStudentId(studentId).stream()
                        .anyMatch(enrollment -> enrollment.getSchoolClass().getId().equals(ct.getSchoolClass().getId())));
    }

    private boolean isParent(User user) { return parentRepository.findByUserId(user.getId()).isPresent(); }
    private boolean isTeacher(User user) { return !teacherRepository.findByUserId(user.getId()).isEmpty(); }
    private boolean isSchoolAccount(Long userId, Long schoolId) {
        return !isParent(userRepository.getReferenceById(userId))
                && teacherRepository.findByUserId(userId).stream().noneMatch(t -> t.getSchool().getId().equals(schoolId));
    }
    private School school(Long id) { return schoolRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Établissement introuvable")); }
    private void addRecipient(Map<Long, FamilyContactDto.Recipient> map, User user, String role) {
        if (user != null) map.putIfAbsent(user.getId(), new FamilyContactDto.Recipient(user.getId(),
                (user.getFirstName() + " " + user.getLastName()).trim(), role));
    }
}
