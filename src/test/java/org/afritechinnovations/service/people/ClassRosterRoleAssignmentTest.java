package org.afritechinnovations.service.people;

import org.afritechinnovations.dto.people.CreateRosterParentRequest;
import org.afritechinnovations.dto.people.UpdateParentProfileRequest;
import org.afritechinnovations.model.academic.SchoolClass;
import org.afritechinnovations.model.common.*;
import org.afritechinnovations.model.people.*;
import org.afritechinnovations.repository.academic.ClassSubjectTeacherRepository;
import org.afritechinnovations.repository.academic.SchoolClassRepository;
import org.afritechinnovations.repository.common.*;
import org.afritechinnovations.repository.people.*;
import org.afritechinnovations.security.SchoolPermissions;
import org.afritechinnovations.service.auth.EmailVerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClassRosterRoleAssignmentTest {
    @Mock SchoolPermissions permissions;
    @Mock SchoolClassRepository classes;
    @Mock StudentRepository students;
    @Mock StudentEnrollmentRepository enrollments;
    @Mock ParentRepository parents;
    @Mock ParentStudentRepository children;
    @Mock UserRepository users;
    @Mock RoleRepository roles;
    @Mock SchoolUserRepository memberships;
    @Mock ClassSubjectTeacherRepository assignments;
    @Mock EmailVerificationService invitations;
    @InjectMocks ClassRosterService service;

    User owner = User.builder().id(10L).firstName("Boubacar").lastName("Sawadogo")
            .email("admin@fasoecole.com").passwordHash("unchanged").emailVerified(true).ownerAccount(true).build();
    School school = School.builder().id(1L).owner(owner).build();
    SchoolClass schoolClass = SchoolClass.builder().id(11L).school(school).build();
    Student student = Student.builder().id(20L).school(school).user(User.builder().id(21L).build()).build();
    Parent existingParent;
    List<ParentStudent> links = new ArrayList<>();

    @BeforeEach
    void setup() {
        existingParent = Parent.builder().id(30L).user(User.builder().id(31L).email("parent@test.bf").build()).build();
        when(classes.findById(11L)).thenReturn(Optional.of(schoolClass));
        when(students.findById(20L)).thenReturn(Optional.of(student));
        when(users.findByIdForUpdate(10L)).thenReturn(Optional.of(owner));
        when(enrollments.findByStudentId(20L)).thenReturn(List.of(StudentEnrollment.builder()
                .student(student).schoolClass(schoolClass).status(EnrollmentStatus.ACTIVE).build()));
        when(roles.findByName("PARENT")).thenReturn(Optional.of(Role.builder().name("PARENT").build()));
        when(users.findByEmailIgnoreCase("admin@fasoecole.com")).thenReturn(Optional.of(owner));
        when(parents.save(any())).thenAnswer(call -> { Parent parent = call.getArgument(0); parent.setId(40L); return parent; });
        when(children.findByStudentIdWithParentUser(20L)).thenAnswer(call -> new ArrayList<>(links));
        when(children.save(any())).thenAnswer(call -> { ParentStudent link = call.getArgument(0); links.add(link); return link; });
        doAnswer(call -> { links.remove(call.getArgument(0)); return null; }).when(children).delete(any());
    }

    CreateRosterParentRequest parentRequest(String email) {
        var request = new CreateRosterParentRequest();
        request.setFirstName("Ne pas remplacer"); request.setLastName("Identité");
        request.setEmail(email); request.setPhone("70000000"); request.setRelationship("Père");
        return request;
    }

    @Test
    void ownerCanBeAddedAsParentWithoutChangingIdentityOrExistingRoles() {
        var result = service.addParent(11L, 20L, parentRequest(" ADMIN@FASOECOLE.COM "), 10L, false);
        assertEquals(10L, result.parents().getFirst().userId());
        assertEquals("Boubacar", owner.getFirstName()); assertNull(owner.getPhone());
        assertEquals("unchanged", owner.getPasswordHash()); assertTrue(owner.isOwnerAccount());
        verify(users, never()).save(any());
        verify(memberships).save(argThat(link -> link.getUser() == owner && "PARENT".equals(link.getRole().getName())));
        verify(invitations).sendAccessAssigned(owner, school, "Parent");
        verify(memberships, never()).delete(any());
    }

    @Test
    void addingTheSameParentAgainDoesNotDuplicateTheLinkOrEmail() {
        Parent parent = Parent.builder().id(40L).user(owner).build();
        when(parents.findByUserId(10L)).thenReturn(Optional.of(parent));
        service.addParent(11L, 20L, parentRequest(owner.getEmail()), 10L, false);
        service.addParent(11L, 20L, parentRequest(owner.getEmail()), 10L, false);
        assertEquals(1, links.size());
        verify(invitations, times(1)).sendAccessAssigned(owner, school, "Parent");
    }

    @Test
    void replacingParentWithExistingOwnerOnlyChangesTheSelectedChildLink() {
        ParentStudent previous = ParentStudent.builder().parent(existingParent).student(student).relationship("Père").build();
        Student sibling = Student.builder().id(22L).school(school).build();
        ParentStudent otherChild = ParentStudent.builder().parent(existingParent).student(sibling).build();
        links.add(previous);
        when(parents.findById(30L)).thenReturn(Optional.of(existingParent));
        when(children.findByParentId(30L)).thenReturn(List.of(previous, otherChild));
        when(users.findById(31L)).thenReturn(Optional.of(existingParent.getUser()));
        var request = new UpdateParentProfileRequest();
        request.setStudentId(20L); request.setEmail(owner.getEmail()); request.setFirstName("Autre"); request.setLastName("Nom");
        var result = service.updateParentProfile(11L, 30L, request, 10L, false);
        assertEquals(10L, result.parents().getFirst().userId());
        assertEquals("Père", result.parents().getFirst().relationship());
        verify(children).delete(previous); verify(children, never()).delete(otherChild);
        verify(users, never()).save(any()); assertEquals("parent@test.bf", existingParent.getUser().getEmail());
    }

    @Test
    void refusesAssignmentForAnotherOwnersClassOrInactiveAccount() {
        assertThrows(AccessDeniedException.class, () -> service.addParent(11L, 20L, parentRequest(owner.getEmail()), 99L, false));
        owner.setActive(false);
        assertThrows(IllegalArgumentException.class, () -> service.addParent(11L, 20L, parentRequest(owner.getEmail()), 10L, false));
        verify(children, never()).save(any());
    }

    @Test
    void cannotLinkAStudentAsTheirOwnParent() {
        when(users.findByEmailIgnoreCase("self@test.bf")).thenReturn(Optional.of(student.getUser()));
        assertThrows(IllegalArgumentException.class, () -> service.addParent(11L, 20L, parentRequest("self@test.bf"), 10L, false));
        verify(children, never()).save(any());
    }

    @Test
    void deletingStudentDossierKeepsTheAccountAndOtherMemberships() {
        SchoolUser studentRole = SchoolUser.builder().school(school).role(Role.builder().name("STUDENT").build()).build();
        SchoolUser parentRole = SchoolUser.builder().school(school).role(Role.builder().name("PARENT").build()).build();
        when(memberships.findByUserId(21L)).thenReturn(List.of(studentRole, parentRole));
        when(students.findAllByUserId(21L)).thenReturn(List.of(student));
        service.removeStudentFromClass(11L, 20L, 10L, false);
        verify(students).delete(student); verify(memberships).delete(studentRole);
        verify(memberships, never()).delete(parentRole); verify(users, never()).deleteById(anyLong());
    }

    private org.afritechinnovations.dto.people.UpdateStudentProfileRequest profile(String email) {
        var request = new org.afritechinnovations.dto.people.UpdateStudentProfileRequest();
        request.setFirstName("Local"); request.setLastName("Student");
        request.setEmail(email); request.setRegistrationNumber("MAT-20");
        return request;
    }

    @Test
    void editingOneSchoolDoesNotChangeTheOtherSchoolOrLogin() {
        User pupil = User.builder().id(21L).firstName("Ali").lastName("Kabore")
                .email("login@test.bf").emailVerified(true).build();
        student.setUser(pupil); student.initializeSchoolIdentity();
        Student other = Student.builder().id(22L).user(pupil)
                .school(School.builder().id(2L).owner(owner).build()).build();
        other.initializeSchoolIdentity();
        service.updateStudentProfile(11L, 20L, profile("login@test.bf"), 10L, false);
        assertEquals("login@test.bf", student.getEmail());
        assertEquals("Local", student.getFirstName());
        assertEquals("Ali", other.getFirstName());
        assertEquals("login@test.bf", other.getEmail());
        assertEquals("login@test.bf", pupil.getEmail());
        assertTrue(pupil.getEmailVerified());
        assertEquals(0, pupil.getSessionVersion());
        verify(users, never()).save(any()); verify(invitations, never()).sendInvitation(any());
    }

    @Test
    void editingAnOwnerStudentDossierDoesNotChangeTheirAccount() {
        student.setUser(owner);
        service.updateStudentProfile(11L, 20L, profile(owner.getEmail()), 10L, false);
        assertEquals("Local", student.getFirstName());
        assertEquals("Boubacar", owner.getFirstName());
        assertEquals("admin@fasoecole.com", owner.getEmail());
        verify(users, never()).save(any()); verify(invitations, never()).sendInvitation(any());
    }

    @Test
    void ownerCanCorrectEmailBeforeActivation() {
        var account = student.getUser(); account.setPasswordSet(false);
        account.setEmail("typo@test.bf");
        when(users.findByIdForUpdate(21L)).thenReturn(Optional.of(account));
        when(invitations.correctUnactivatedEmail(21L, "correct@test.bf")).thenAnswer(call -> {
            account.setEmail("correct@test.bf"); return true;
        });
        var row = service.updateStudentProfile(11L, 20L, profile("correct@test.bf"), 10L, false);
        assertEquals("correct@test.bf", row.email());
        verify(invitations).correctUnactivatedEmail(21L, "correct@test.bf");
    }

    @Test
    void ownerCanCorrectAnUnactivatedAccountSharedBetweenTheirSchools() {
        var account = student.getUser(); account.setPasswordSet(false); account.setEmail("typo@test.bf");
        School secondSchool = School.builder().id(2L).owner(owner).build();
        Student second = Student.builder().id(22L).school(secondSchool).user(account)
                .firstName("Other local name").lastName("Test").registrationNumber("OTHER-22").build();
        when(users.findByIdForUpdate(21L)).thenReturn(Optional.of(account));
        var role = Role.builder().name("STUDENT").build();
        when(memberships.findByUserId(21L)).thenReturn(List.of(
                SchoolUser.builder().school(school).role(role).build(),
                SchoolUser.builder().school(secondSchool).role(role).build()));
        when(students.findAllByUserId(21L)).thenReturn(List.of(student, second));
        when(invitations.correctUnactivatedEmail(21L, "correct@test.bf")).thenAnswer(call -> {
            account.setEmail("correct@test.bf"); return true;
        });
        var row = service.updateStudentProfile(11L, 20L, profile("correct@test.bf"), 10L, false);
        assertEquals("correct@test.bf", row.email());
        assertEquals("correct@test.bf", second.getEmail());
        assertEquals("Other local name", second.getFirstName());
        assertEquals("OTHER-22", second.getRegistrationNumber());
        verify(students, never()).save(second);
        verify(invitations).correctUnactivatedEmail(21L, "correct@test.bf");
    }

    @Test
    void ownerCannotCorrectEmailForAnUnactivatedAccountInAnotherOwnersSchool() {
        student.getUser().setPasswordSet(false);
        when(users.findByIdForUpdate(21L)).thenReturn(Optional.of(student.getUser()));
        when(students.findAllByUserId(21L)).thenReturn(List.of(student, Student.builder().id(22L)
                .school(School.builder().id(2L).owner(User.builder().id(99L).build()).build()).build()));
        assertThrows(IllegalArgumentException.class,
                () -> service.updateStudentProfile(11L, 20L, profile("correct@test.bf"), 10L, false));
        verify(invitations, never()).correctUnactivatedEmail(anyLong(), anyString());
    }

    @Test
    void anotherSchoolMembershipAlsoPreventsOwnerEmailCorrection() {
        student.getUser().setPasswordSet(false);
        when(users.findByIdForUpdate(21L)).thenReturn(Optional.of(student.getUser()));
        when(memberships.findByUserId(21L)).thenReturn(List.of(SchoolUser.builder()
                .school(School.builder().id(2L).owner(User.builder().id(99L).build()).build())
                .role(Role.builder().name("STUDENT").build()).build()));
        assertThrows(IllegalArgumentException.class,
                () -> service.updateStudentProfile(11L, 20L, profile("correct@test.bf"), 10L, false));
        verify(invitations, never()).correctUnactivatedEmail(anyLong(), anyString());
    }

    @Test
    void additionalParentRoleStillPreventsUnactivatedStudentEmailCorrection() {
        student.getUser().setPasswordSet(false);
        when(users.findByIdForUpdate(21L)).thenReturn(Optional.of(student.getUser()));
        when(memberships.findByUserId(21L)).thenReturn(List.of(SchoolUser.builder().school(school)
                .role(Role.builder().name("PARENT").build()).build()));
        assertThrows(IllegalArgumentException.class,
                () -> service.updateStudentProfile(11L, 20L, profile("correct@test.bf"), 10L, false));
        verify(invitations, never()).correctUnactivatedEmail(anyLong(), anyString());
    }

    @Test
    void staffWithStudentAccessCannotCorrectAnAccountEmail() {
        when(permissions.staffAllows(1L, 99L, org.afritechinnovations.model.common.StaffModule.STUDENTS)).thenReturn(true);
        assertThrows(AccessDeniedException.class,
                () -> service.updateStudentProfile(11L, 20L, profile("correct@test.bf"), 99L, false));
        verify(invitations, never()).correctUnactivatedEmail(anyLong(), anyString());
    }

    @Test
    void schoolCannotChangeOrClearThePersonalEmail() {
        student.setUser(owner); student.initializeSchoolIdentity();
        assertThrows(IllegalArgumentException.class,
                () -> service.updateStudentProfile(11L, 20L, profile(null), 10L, false));
        assertThrows(IllegalArgumentException.class,
                () -> service.updateStudentProfile(11L, 20L, profile("other@test.bf"), 10L, false));
        assertEquals(owner.getEmail(), student.getEmail());
        assertEquals("admin@fasoecole.com", owner.getEmail());
    }

    @Test
    void cannotEditAStudentFromAnotherSchool() {
        student.setSchool(School.builder().id(2L).owner(owner).build());
        assertThrows(AccessDeniedException.class,
                () -> service.updateStudentProfile(11L, 20L, profile("local@test.bf"), 10L, false));
        verify(students, never()).save(any());
    }

    @Test
    void explicitActivationAssignsTheSinglePersonalEmail() {
        student.getUser().setPasswordSet(false);
        student.setFirstName("Local"); student.setLastName("Student"); student.setEmail("contact@test.bf");
        when(users.findByIdForUpdate(21L)).thenReturn(Optional.of(student.getUser()));
        when(invitations.sendInvitation(student.getUser())).thenReturn(true);
        var result = service.inviteStudentAccount(11L, 20L, " LOGIN@test.bf ", 10L, false);
        assertTrue(result.emailSent());
        assertEquals("login@test.bf", result.student().accountEmail());
        assertEquals("login@test.bf", result.student().email());
        verify(users).saveAndFlush(student.getUser());
    }

    @Test
    void activationNeverLinksAnExistingAccountByEmail() {
        student.getUser().setPasswordSet(false);
        when(users.findByIdForUpdate(21L)).thenReturn(Optional.of(student.getUser()));
        when(users.findByEmailIgnoreCase(owner.getEmail())).thenReturn(Optional.of(owner));
        assertThrows(IllegalArgumentException.class,
                () -> service.inviteStudentAccount(11L, 20L, owner.getEmail(), 10L, false));
        assertNull(student.getUser().getEmail());
        assertEquals(21L, student.getUser().getId());
        verify(users, never()).saveAndFlush(any()); verifyNoInteractions(invitations);
    }

    @Test
    void activationCannotRepurposeALegacySharedAccount() {
        student.getUser().setPasswordSet(false);
        when(users.findByIdForUpdate(21L)).thenReturn(Optional.of(student.getUser()));
        when(students.findAllByUserId(21L)).thenReturn(List.of(student,
                Student.builder().id(22L).user(student.getUser()).build()));
        assertThrows(IllegalArgumentException.class,
                () -> service.inviteStudentAccount(11L, 20L, "login@test.bf", 10L, false));
        verify(users, never()).saveAndFlush(any()); verifyNoInteractions(invitations);
    }
}
