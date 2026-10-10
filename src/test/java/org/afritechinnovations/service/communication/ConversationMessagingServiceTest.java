package org.afritechinnovations.service.communication;

import org.afritechinnovations.dto.communication.FamilyContactDto;
import org.afritechinnovations.model.academic.*;
import org.afritechinnovations.model.common.*;
import org.afritechinnovations.model.communication.*;
import org.afritechinnovations.model.people.*;
import org.afritechinnovations.repository.academic.ClassSubjectTeacherRepository;
import org.afritechinnovations.repository.common.*;
import org.afritechinnovations.repository.communication.*;
import org.afritechinnovations.repository.people.*;
import org.afritechinnovations.security.AccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationMessagingServiceTest {
    @Mock AccessGuard guard;
    @Mock SchoolRepository schoolRepository;
    @Mock UserRepository userRepository;
    @Mock SchoolConversationRepository conversationRepository;
    @Mock SchoolConversationMessageRepository messageRepository;
    @Mock ConversationParticipantRepository participantRepository;
    @Mock ParentRepository parentRepository;
    @Mock ParentStudentRepository parentStudentRepository;
    @Mock TeacherRepository teacherRepository;
    @Mock ClassSubjectTeacherRepository classTeacherRepository;
    @Mock StudentEnrollmentRepository enrollmentRepository;
    @Mock StudentRepository studentRepository;
    @Mock ConversationAttachmentRepository attachmentRepository;
    @Mock ConversationAttachmentContentRepository attachmentContentRepository;
    @InjectMocks ConversationMessagingService service;

    User sender = user(10L), owner = user(20L), child = user(30L), parent = user(40L);
    School school = School.builder().id(1L).name("School").owner(owner).build();
    AcademicYear year = AcademicYear.builder().id(2L).isCurrent(true).build();
    SchoolClass cls = SchoolClass.builder().id(3L).school(school).academicYear(year).build();
    Teacher teacher = Teacher.builder().id(4L).school(school).user(sender).build();
    Student student = Student.builder().id(5L).school(school).user(child).build();
    ClassSubjectTeacher assignment = ClassSubjectTeacher.builder().schoolClass(cls).teacher(teacher).build();

    static User user(Long id) { return User.builder().id(id).firstName("User").lastName(id.toString()).build(); }

    @BeforeEach void setup() {
        lenient().when(schoolRepository.findById(1L)).thenReturn(Optional.of(school));
        lenient().when(teacherRepository.findByUserId(10L)).thenReturn(List.of(teacher));
    }

    void roster() {
        when(classTeacherRepository.findByTeacherId(4L)).thenReturn(List.of(assignment));
        when(enrollmentRepository.findActiveStudentsWithUserByClassId(3L, EnrollmentStatus.ACTIVE))
                .thenReturn(List.of(StudentEnrollment.builder().student(student).academicYear(year).build()));
        Parent p = Parent.builder().id(6L).user(parent).build();
        when(parentStudentRepository.findByStudentIdWithParentUser(5L)).thenReturn(List.of(
                ParentStudent.builder().parent(p).student(student).build(),
                ParentStudent.builder().parent(p).student(student).build()));
    }

    @Test void listsStudentsParentsAndOwnerWithoutDuplicateParents() {
        roster();
        var recipients = service.teacherRecipients(10L, 1L, 3L);
        assertEquals(Set.of(20L, 30L, 40L), recipients.stream().map(FamilyContactDto.Recipient::userId)
                .collect(java.util.stream.Collectors.toSet()));
        assertEquals(3, recipients.size());
    }

    @Test void excludesDisabledAndUnapprovedAccounts() {
        roster(); child.setActive(false); parent.setApproved(false);
        assertEquals(List.of(20L), service.teacherRecipients(10L, 1L, 3L).stream()
                .map(FamilyContactDto.Recipient::userId).toList());
    }

    @Test void refusesUnassignedClass() {
        when(classTeacherRepository.findByTeacherId(4L)).thenReturn(List.of(assignment));
        assertThrows(AccessDeniedException.class, () -> service.teacherRecipients(10L, 1L, 99L));
    }

    @Test void refusesInactiveOrPastAssignments() {
        when(classTeacherRepository.findByTeacherId(4L)).thenReturn(List.of(assignment));
        assignment.setActive(false);
        assertThrows(AccessDeniedException.class, () -> service.teacherRecipients(10L, 1L, 3L));
        assignment.setActive(true); year.setIsCurrent(false);
        assertThrows(AccessDeniedException.class, () -> service.teacherRecipients(10L, 1L, 3L));
    }

    @Test void refusesRecipientsOutsideClassBeforeWritingAnything() {
        roster();
        assertThrows(AccessDeniedException.class, () -> service.sendTeacherMessage(10L,
                new FamilyContactDto.TeacherMessageRequest(1L, 3L, "Subject", "Message", List.of(30L, 999L)), List.of()));
        verifyNoInteractions(conversationRepository, messageRepository, participantRepository);
    }

    @Test void refusesTeacherFromAnotherSchool() {
        teacher.setSchool(School.builder().id(99L).build());
        assertThrows(AccessDeniedException.class, () -> service.teacherRecipients(10L, 1L, 3L));
        verifyNoInteractions(enrollmentRepository, parentStudentRepository);
    }

    @Test void rejectsEmptyMessageBeforeWritingAnything() {
        roster();
        assertThrows(IllegalArgumentException.class, () -> service.sendTeacherMessage(10L,
                new FamilyContactDto.TeacherMessageRequest(1L, 3L, "Subject", " ", List.of(30L)), List.of()));
        verifyNoInteractions(conversationRepository, messageRepository, participantRepository);
    }

    @Test void createsPrivateThreadsAndDeduplicatesRecipientsIncludingOwner() {
        roster();
        when(userRepository.findById(10L)).thenReturn(Optional.of(sender));
        when(userRepository.getReferenceById(30L)).thenReturn(child);
        when(userRepository.getReferenceById(20L)).thenReturn(owner);
        when(conversationRepository.save(any())).thenAnswer(i -> {
            SchoolConversation c = i.getArgument(0); c.setId(System.nanoTime()); return c;
        });
        when(participantRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(messageRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        var result = service.sendTeacherMessage(10L, new FamilyContactDto.TeacherMessageRequest(
                1L, 3L, "Subject", "Message", List.of(30L, 20L, 30L)), List.of());
        assertEquals(2, result.size());
        ArgumentCaptor<SchoolConversation> captor = ArgumentCaptor.forClass(SchoolConversation.class);
        verify(conversationRepository, times(2)).save(captor.capture());
        for (var c : captor.getAllValues()) {
            assertEquals(2, c.getParticipants().size());
            assertTrue(c.getParticipants().stream().allMatch(p -> p.getSchool() == null));
            assertEquals(1, c.getParticipants().stream().filter(p -> p.getUser().getId().equals(10L)).count());
        }
    }

    @Test void ownerInboxIncludesPersonalThreadsAndHidesOtherPrivateThreads() {
        var mine = SchoolConversation.builder().id(50L).school(school).build();
        mine.getParticipants().add(ConversationParticipant.builder().user(owner).build());
        var others = SchoolConversation.builder().id(51L).school(school).build();
        others.getParticipants().add(ConversationParticipant.builder().user(parent).build());
        when(conversationRepository.findBySchool(1L)).thenReturn(List.of(mine, others));
        assertEquals(List.of(50L), service.conversations(20L, 1L).stream()
                .map(FamilyContactDto.ConversationSummary::id).toList());
    }
}
