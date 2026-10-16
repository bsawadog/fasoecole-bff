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

    @Test
    void sharedAccountIdentityCannotBeOverwrittenFromAStudentDossier() {
        student.setUser(owner);
        when(users.findById(owner.getId())).thenReturn(Optional.of(owner));
        var request = new org.afritechinnovations.dto.people.UpdateStudentProfileRequest();
        request.setFirstName("Different"); request.setLastName("Name"); request.setEmail(owner.getEmail());
        assertThrows(IllegalArgumentException.class, () -> service.updateStudentProfile(11L, 20L, request, 10L, false));
        assertEquals("Boubacar", owner.getFirstName()); verify(users, never()).save(any());
    }
}
