package org.afritechinnovations.service.common;

import org.afritechinnovations.dto.common.CreateSchoolAccessRequest;
import org.afritechinnovations.model.common.Role;
import org.afritechinnovations.model.common.RoleName;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.SchoolAccessRequest;
import org.afritechinnovations.model.common.SchoolAccessStatus;
import org.afritechinnovations.model.common.SchoolStatus;
import org.afritechinnovations.model.common.SchoolUser;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.repository.common.RoleRepository;
import org.afritechinnovations.repository.common.SchoolAccessRequestRepository;
import org.afritechinnovations.repository.common.SchoolRepository;
import org.afritechinnovations.repository.common.SchoolUserRepository;
import org.afritechinnovations.repository.common.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchoolAccessRequestServiceTest {
    @Mock SchoolAccessRequestRepository requests;
    @Mock UserRepository users;
    @Mock SchoolRepository schools;
    @Mock SchoolUserRepository schoolUsers;
    @Mock RoleRepository roles;
    @Mock org.afritechinnovations.repository.people.ParentRepository parents;
    @Mock org.afritechinnovations.repository.people.ParentStudentRepository parentStudents;
    @Mock org.afritechinnovations.service.people.TeacherProfileService teacherProfiles;
    @Mock org.afritechinnovations.service.people.ClassRosterService classRoster;
    @Mock ParentChildAdmissionService childAdmission;
    @Mock SchoolIdentityAdmissionService identityAdmission;
    @InjectMocks SchoolAccessRequestService service;

    private final User owner = User.builder().id(10L).build();
    private final User teacher = User.builder().id(7L).firstName("Awa").lastName("Diallo")
            .email("awa@ecole.bf").approved(true).active(true).emailVerified(true).build();
    private final School home = School.builder().id(1L).name("École A").owner(owner).status(SchoolStatus.ACTIVE).build();
    private final School other = School.builder().id(2L).name("École B").owner(owner).status(SchoolStatus.ACTIVE).build();
    private final Role teacherRole = Role.builder().name("TEACHER").build();

    private CreateSchoolAccessRequest request(Long schoolId, RoleName role) {
        CreateSchoolAccessRequest request = new CreateSchoolAccessRequest();
        request.setSchoolId(schoolId);
        request.setRequestedRole(role);
        request.setSchoolIdentifier(role == RoleName.STUDENT ? "001" : role == RoleName.TEACHER ? "EMP-01" : null);
        return request;
    }

    @Test
    void teacherCanRequestAnotherSchool() {
        when(users.findById(7L)).thenReturn(Optional.of(teacher));
        when(schoolUsers.findByUserId(7L)).thenReturn(List.of(
                SchoolUser.builder().user(teacher).school(home).role(teacherRole).build()));
        when(schools.findById(2L)).thenReturn(Optional.of(other));
        when(requests.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var created = service.create(7L, request(2L, RoleName.TEACHER));

        assertEquals(2L, created.getSchoolId());
        assertEquals(SchoolAccessStatus.PENDING, created.getStatus());
    }

    @Test
    void parentRequestsStoreMatriculesWithoutExposingDirectoryMatchesOrGrantingAccess() {
        when(users.findById(7L)).thenReturn(Optional.of(teacher));
        when(schoolUsers.findByUserId(7L)).thenReturn(List.of(SchoolUser.builder().user(teacher).school(home).role(Role.builder().name("PARENT").build()).build()));
        var request = request(2L, RoleName.PARENT);
        assertThrows(IllegalArgumentException.class, () -> service.create(7L, request));
        request.setChildRegistrationNumbers(List.of(" 001 ", "002", "001"));
        when(schools.findById(2L)).thenReturn(Optional.of(other));
        when(requests.save(any())).thenAnswer(i -> i.getArgument(0));
        var dto = service.create(7L, request);
        assertEquals(List.of("001", "002"), dto.getChildRegistrationNumbers());
        assertEquals(List.of(), dto.getChildReview());
        assertEquals(SchoolAccessStatus.PENDING, dto.getStatus());
        verifyNoInteractions(childAdmission);
        verify(schoolUsers, never()).save(any());
    }

    @Test
    void approvalLinksClaimedChildrenOnlyAfterSchoolOwnerValidationAndReviewIsPrivate() {
        SchoolAccessRequest pending = SchoolAccessRequest.builder().id(3L).user(teacher).school(other)
            .requestedRole(RoleName.PARENT).childRegistrationNumbers(new java.util.ArrayList<>(List.of("001"))).build();
        when(requests.findById(3L)).thenReturn(Optional.of(pending));
        assertThrows(AccessDeniedException.class, () -> service.approve(3L, 99L, false));
        verifyNoInteractions(childAdmission);
        when(roles.findByName("PARENT")).thenReturn(Optional.of(Role.builder().name("PARENT").build()));
        when(requests.save(any())).thenAnswer(i -> i.getArgument(0));
        assertEquals(SchoolAccessStatus.APPROVED, service.approve(3L, 10L, false).getStatus());
        verify(childAdmission).attachApproved(teacher, 2L, List.of("001"));
    }

    @Test
    void rejectsSchoolAlreadyAccessibleWithSameRole() {
        when(users.findById(7L)).thenReturn(Optional.of(teacher));
        when(schoolUsers.findByUserId(7L)).thenReturn(List.of(
                SchoolUser.builder().user(teacher).school(home).role(teacherRole).build()));
        when(schools.findById(1L)).thenReturn(Optional.of(home));

        assertThrows(IllegalArgumentException.class, () -> service.create(7L, request(1L, RoleName.TEACHER)));
        verify(requests, never()).save(any());
    }

    @Test
    void onlyAuthorizedSchoolReviewResolvesUnapprovedChildNames() {
        SchoolAccessRequest pending = SchoolAccessRequest.builder().id(3L).user(teacher).school(other)
                .requestedRole(RoleName.PARENT).childRegistrationNumbers(new java.util.ArrayList<>(List.of("001"))).build();
        when(requests.findByUserIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(pending));
        assertEquals(List.of(), service.findMine(7L).getFirst().getChildReview());
        verifyNoInteractions(childAdmission);
        when(schools.findByOwnerId(10L)).thenReturn(List.of(other));
        when(requests.findByStatusInAndSchoolIdInOrderByCreatedAtAsc(any(), eq(List.of(2L)))).thenReturn(List.of(pending));
        when(childAdmission.review(2L, List.of("001"))).thenReturn(List.of("001 — Sali Diallo"));
        assertEquals(List.of("001 — Sali Diallo"), service.findPendingFor(10L, false).getFirst().getChildReview());
    }

    @Test
    void ownersCannotUseThisFlow() {
        when(users.findById(10L)).thenReturn(Optional.of(User.builder().id(10L).approved(true).emailVerified(true).build()));
        when(schoolUsers.findByUserId(10L)).thenReturn(List.of(SchoolUser.builder()
                .school(home).role(Role.builder().name("SCHOOL_ADMIN").build()).build()));

        assertThrows(AccessDeniedException.class, () -> service.create(10L, request(2L, RoleName.TEACHER)));
    }

    @Test
    void ownerApprovalGrantsSchoolMembership() {
        SchoolAccessRequest pending = SchoolAccessRequest.builder().id(3L).user(teacher).school(other)
                .requestedRole(RoleName.TEACHER).status(SchoolAccessStatus.PENDING).build();
        when(requests.findById(3L)).thenReturn(Optional.of(pending));
        when(schoolUsers.findByUserId(7L)).thenReturn(List.of());
        when(roles.findByName("TEACHER")).thenReturn(Optional.of(teacherRole));
        when(users.getReferenceById(10L)).thenReturn(owner);
        when(requests.save(pending)).thenReturn(pending);

        var result = service.approve(3L, 10L, false);

        ArgumentCaptor<SchoolUser> link = ArgumentCaptor.forClass(SchoolUser.class);
        verify(schoolUsers).save(link.capture());
        assertSame(other, link.getValue().getSchool());
        assertSame(teacherRole, link.getValue().getRole());
        verify(identityAdmission).attachApproved(teacher, other, RoleName.TEACHER, pending.getSchoolIdentifier());
        assertEquals(SchoolAccessStatus.APPROVED, result.getStatus());
    }

    @Test
    void anotherOwnerCannotDecide() {
        SchoolAccessRequest pending = SchoolAccessRequest.builder().id(3L).user(teacher).school(other)
                .requestedRole(RoleName.TEACHER).status(SchoolAccessStatus.PENDING).build();
        when(requests.findById(3L)).thenReturn(Optional.of(pending));

        assertThrows(AccessDeniedException.class, () -> service.reject(3L, 99L, false));
        verify(requests, never()).save(any());
    }

    @Test
    void ownerRevokesAutomaticParentAccessAndRemovesTheSchoolLink() {
        Role parentRole = Role.builder().name("PARENT").build();
        SchoolUser parentLink = SchoolUser.builder().id(40L).user(teacher).school(other).role(parentRole).build();
        SchoolUser homeLink = SchoolUser.builder().id(41L).user(teacher).school(home).role(parentRole).build();
        SchoolAccessRequest auto = SchoolAccessRequest.builder().id(5L).user(teacher).school(other)
                .requestedRole(RoleName.PARENT).status(SchoolAccessStatus.AUTO_APPROVED).build();
        when(requests.findById(5L)).thenReturn(Optional.of(auto));
        when(schoolUsers.findByUserId(7L)).thenReturn(List.of(parentLink, homeLink));
        when(requests.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(parents.findByUserId(7L)).thenReturn(Optional.empty());

        var result = service.revokeAutomatic(5L, 10L, false);

        assertEquals(SchoolAccessStatus.REVOKED, result.getStatus());
        verify(schoolUsers).delete(parentLink);
        verify(schoolUsers, never()).delete(homeLink);
        assertThrows(IllegalArgumentException.class, () -> service.confirmAutomatic(5L, 10L, false));
    }

    @Test
    void ownerConfirmsAutomaticAccessButCannotRevokeAManualRequest() {
        SchoolAccessRequest auto = SchoolAccessRequest.builder().id(6L).user(teacher).school(other)
                .requestedRole(RoleName.PARENT).status(SchoolAccessStatus.AUTO_APPROVED).build();
        SchoolAccessRequest manual = SchoolAccessRequest.builder().id(8L).user(teacher).school(other)
                .requestedRole(RoleName.TEACHER).status(SchoolAccessStatus.PENDING).build();
        when(requests.findById(6L)).thenReturn(Optional.of(auto));
        when(requests.findById(8L)).thenReturn(Optional.of(manual));
        when(requests.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(parents.findByUserId(7L)).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> service.revokeAutomatic(6L, 99L, false));
        assertEquals(SchoolAccessStatus.APPROVED, service.confirmAutomatic(6L, 10L, false).getStatus());
        assertThrows(IllegalArgumentException.class, () -> service.revokeAutomatic(8L, 10L, false));
    }

    @Test
    void unverifiedUserCannotRequestOrReceiveAnotherSchoolAccess() {
        teacher.setEmailVerified(false);
        when(users.findById(7L)).thenReturn(Optional.of(teacher));
        assertThrows(AccessDeniedException.class, () -> service.create(7L, request(2L, RoleName.TEACHER)));
        SchoolAccessRequest pending = SchoolAccessRequest.builder().id(3L).user(teacher).school(other)
                .requestedRole(RoleName.TEACHER).status(SchoolAccessStatus.PENDING).build();
        when(requests.findById(3L)).thenReturn(Optional.of(pending));
        assertThrows(IllegalArgumentException.class, () -> service.approve(3L, 10L, false));
        verify(schoolUsers, never()).save(any());
    }

    @Test
    void persistedPrivilegedRequestIsRejectedAndStudentApprovalProvisionsTheClass() {
        SchoolAccessRequest pending = SchoolAccessRequest.builder().id(3L).user(teacher).school(other)
                .requestedRole(RoleName.SUPER_ADMIN).status(SchoolAccessStatus.PENDING).build();
        when(requests.findById(3L)).thenReturn(Optional.of(pending));
        assertThrows(IllegalArgumentException.class, () -> service.approve(3L, 10L, false));
        verify(schoolUsers, never()).save(any());
        pending.setRequestedRole(RoleName.STUDENT);
        when(roles.findByName("STUDENT")).thenReturn(Optional.of(Role.builder().name("STUDENT").build()));
        when(requests.save(pending)).thenReturn(pending);
        assertEquals(SchoolAccessStatus.APPROVED, service.approve(3L, 10L, false, 11L, "M1").getStatus());
        verify(identityAdmission).attachApproved(teacher, other, RoleName.STUDENT, pending.getSchoolIdentifier());
    }
}
