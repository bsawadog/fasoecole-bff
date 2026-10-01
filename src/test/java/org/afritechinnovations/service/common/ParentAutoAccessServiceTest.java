package org.afritechinnovations.service.common;

import org.afritechinnovations.model.common.Role;
import org.afritechinnovations.model.common.RoleName;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.SchoolAccessRequest;
import org.afritechinnovations.model.common.SchoolAccessStatus;
import org.afritechinnovations.model.common.SchoolStatus;
import org.afritechinnovations.model.common.SchoolUser;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.model.people.Parent;
import org.afritechinnovations.model.people.ParentStudent;
import org.afritechinnovations.model.people.Student;
import org.afritechinnovations.repository.common.RoleRepository;
import org.afritechinnovations.repository.common.SchoolAccessRequestRepository;
import org.afritechinnovations.repository.common.SchoolRepository;
import org.afritechinnovations.repository.common.SchoolUserRepository;
import org.afritechinnovations.repository.common.UserRepository;
import org.afritechinnovations.repository.people.ParentRepository;
import org.afritechinnovations.repository.people.ParentStudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ParentAutoAccessServiceTest {
    @Mock UserRepository users;
    @Mock ParentRepository parents;
    @Mock ParentStudentRepository parentStudents;
    @Mock SchoolUserRepository schoolUsers;
    @Mock SchoolAccessRequestRepository requests;
    @Mock SchoolRepository schools;
    @Mock RoleRepository roles;
    @InjectMocks ParentAutoAccessService service;

    private final School schoolA = School.builder().id(1L).name("École A").status(SchoolStatus.ACTIVE).build();
    private final School schoolB = School.builder().id(2L).name("École B").status(SchoolStatus.ACTIVE).build();
    private final Role parentRole = Role.builder().name("PARENT").build();
    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id(7L).firstName("Awa").lastName("Kaboré").email("awa@ecole.bf")
                .active(true).approved(true).emailVerified(true).build();
        Parent parent = Parent.builder().id(70L).user(user).build();
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(parents.findByUserId(7L)).thenReturn(Optional.of(parent));
        when(parentStudents.findByParentId(70L)).thenReturn(List.of(
                ParentStudent.builder().parent(parent).student(Student.builder().id(1L).school(schoolA).build()).build(),
                ParentStudent.builder().parent(parent).student(Student.builder().id(2L).school(schoolB).build()).build()));
        when(roles.findByName("PARENT")).thenReturn(Optional.of(parentRole));
        when(requests.findByUserIdAndSchoolIdAndRequestedRole(anyLong(), anyLong(), any())).thenReturn(List.of());
        when(schoolUsers.findByUserId(7L)).thenReturn(List.of(
                SchoolUser.builder().user(user).school(schoolA).role(parentRole).build()));
    }

    @Test
    void loginGrantsMissingSchoolsAndTracksThemForTheOwner() {
        int granted = service.grantFromChildren(7L, false);

        assertEquals(1, granted);
        ArgumentCaptor<SchoolUser> link = ArgumentCaptor.forClass(SchoolUser.class);
        verify(schoolUsers).save(link.capture());
        assertSame(schoolB, link.getValue().getSchool());
        ArgumentCaptor<SchoolAccessRequest> record = ArgumentCaptor.forClass(SchoolAccessRequest.class);
        verify(requests).save(record.capture());
        assertSame(schoolB, record.getValue().getSchool());
        assertEquals(SchoolAccessStatus.AUTO_APPROVED, record.getValue().getStatus());
        assertEquals(RoleName.PARENT, record.getValue().getRequestedRole());
    }

    @Test
    void neverRegrantsAnAccessRevokedByTheOwner() {
        when(requests.findByUserIdAndSchoolIdAndRequestedRole(7L, 2L, RoleName.PARENT)).thenReturn(List.of(
                SchoolAccessRequest.builder().status(SchoolAccessStatus.REVOKED).build()));

        assertEquals(0, service.grantFromChildren(7L, false));
        verify(schoolUsers, never()).save(any());
        verify(requests, never()).save(any());
    }

    @Test
    void activationApprovesPendingAccountAndKeepsItsOriginalRequest() {
        user.setApproved(false);
        user.setRequestedSchoolId(3L);
        user.setRequestedRole(RoleName.TEACHER);
        School schoolC = School.builder().id(3L).status(SchoolStatus.ACTIVE).build();
        when(schools.findById(3L)).thenReturn(Optional.of(schoolC));

        assertEquals(2, service.grantFromChildren(7L, true));

        assertTrue(user.getApproved());
        ArgumentCaptor<SchoolAccessRequest> records = ArgumentCaptor.forClass(SchoolAccessRequest.class);
        verify(requests, times(3)).save(records.capture());
        SchoolAccessRequest teacherRequest = records.getAllValues().get(2);
        assertSame(schoolC, teacherRequest.getSchool());
        assertEquals(RoleName.TEACHER, teacherRequest.getRequestedRole());
        assertEquals(SchoolAccessStatus.PENDING, teacherRequest.getStatus());
    }

    @Test
    void neverGrantsAccessWithoutAVerifiedEmail() {
        user.setEmailVerified(false);

        assertEquals(0, service.grantFromChildren(7L, true));
        verifyNoInteractions(schoolUsers, requests);
    }

    @Test
    void ignoresUsersWithoutChildren() {
        when(parents.findByUserId(7L)).thenReturn(Optional.empty());

        assertEquals(0, service.grantFromChildren(7L, true));
        verifyNoInteractions(schoolUsers, requests);
    }
}
