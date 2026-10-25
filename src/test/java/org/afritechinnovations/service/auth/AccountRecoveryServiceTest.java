package org.afritechinnovations.service.auth;

import org.afritechinnovations.dto.auth.AccountRecoveryDto.*;
import org.afritechinnovations.model.common.*;
import org.afritechinnovations.model.people.Student;
import org.afritechinnovations.repository.common.*;
import org.afritechinnovations.repository.people.*;
import org.afritechinnovations.security.*;
import org.afritechinnovations.service.common.EmailService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.*;
import org.mockito.quality.Strictness;
import org.springframework.mail.MailSendException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.*;
import static org.afritechinnovations.model.common.AccountRecoveryRequest.Status.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) @MockitoSettings(strictness=Strictness.LENIENT)
class AccountRecoveryServiceTest {
    @Mock AccountRecoveryRequestRepository requests;
    @Mock AccountRecoveryEventRepository events;
    @Mock StudentRepository students;
    @Mock UserRepository users;
    @Mock SchoolRepository schools;
    @Mock SchoolUserRepository memberships;
    @Mock ParentRepository parents;
    @Mock TeacherRepository teachers;
    @Mock EmailVerificationTokenRepository verificationTokens;
    @Mock PasswordResetTokenRepository resetTokens;
    @Mock PasswordEncoder encoder;
    @Mock EmailService mail;
    @Mock AuthAttemptLimiter limiter;
    @Mock AccessGuard guard;
    @InjectMocks AccountRecoveryService service;
    User owner, user;
    School school;
    Student student;
    AccountRecoveryRequest request;

    @BeforeEach void setup() {
        ReflectionTestUtils.setField(service,"frontendBaseUrl","http://app/");
        owner=User.builder().id(10L).build();
        user=User.builder().id(100L).firstName("Ali").lastName("Diallo").email("old@example.test")
            .passwordHash("old-hash").passwordSet(true).emailVerified(true).approved(true).build();
        school=School.builder().id(1L).name("School A").status(SchoolStatus.ACTIVE).owner(owner).build();
        student=Student.builder().id(20L).school(school).user(user).firstName("Ali").lastName("Diallo").registrationNumber("001").build();
        request=AccountRecoveryRequest.builder().id(50L).school(school).student(student).user(user)
            .firstName("Ali").lastName("Diallo").registrationNumber("001").newEmail("new@example.test")
            .reason("Courriel et mot de passe perdus").status(PENDING).createdAt(LocalDateTime.now()).build();
        when(students.findBySchoolIdAndRegistrationNumber(1L,"001")).thenReturn(Optional.of(student));
        when(students.findById(20L)).thenReturn(Optional.of(student));
        when(students.findAllByUserId(100L)).thenReturn(List.of(student));
        when(users.findByIdForUpdate(100L)).thenReturn(Optional.of(user));
        when(requests.lockById(50L)).thenReturn(Optional.of(request));
        when(requests.save(any())).thenAnswer(call -> { AccountRecoveryRequest row=call.getArgument(0); if(row.getId()==null)row.setId(50L);return row; });
        when(requests.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(limiter.allow(anyString(),anyInt())).thenReturn(true);
        when(guard.currentUserId()).thenReturn(10L);
        when(encoder.encode(anyString())).thenAnswer(call -> "hash:"+call.getArgument(0));
        when(requests.findByTokenHashForUpdate(anyString())).thenAnswer(call ->
            Objects.equals(call.getArgument(0),request.getTokenHash())?Optional.of(request):Optional.empty());
    }
    private Submit submission() { return new Submit(1L,"001","Ali","Diallo","new@example.test","Courriel et mot de passe perdus"); }
    private Decision approval() { return new Decision(true,true,"Identité vérifiée avec le responsable légal déjà enregistré."); }
    private String approveAndToken() {
        service.decide(50L,approval());
        var body=ArgumentCaptor.forClass(String.class);
        verify(mail).sendText(eq("new@example.test"),anyString(),body.capture());
        var matcher=java.util.regex.Pattern.compile("recover=([\\w-]+)").matcher(body.getValue());
        assertTrue(matcher.find());return matcher.group(1);
    }
    @Test void publicRequestDoesNotChangeAccountOrSendLinks() {
        service.submit(submission());
        verify(requests).save(argThat(r -> r.getStatus()==PENDING&&r.getStudent()==student));
        assertEquals("old@example.test",user.getEmail());assertEquals("old-hash",user.getPasswordHash());
        verifyNoInteractions(mail);verify(users,never()).saveAndFlush(any());
    }
    @Test void unmatchedOrDuplicateRequestDoesNotCreateAnotherRecovery() {
        service.submit(new Submit(1L,"001","Someone","Else","new@example.test","Mot de passe oublié"));
        verify(requests,never()).save(any());
        when(requests.findByUserIdAndStatusIn(eq(100L),any())).thenReturn(List.of(request));
        service.submit(submission());verify(requests,never()).save(any());
    }
    @Test void crossOwnerAndAdministrativeAccountsGoToSupport() {
        when(students.findAllByUserId(100L)).thenReturn(List.of(student,
            Student.builder().school(School.builder().id(2L).owner(User.builder().id(99L).build()).build()).build()));
        service.submit(submission());verify(requests).save(argThat(r -> r.getStatus()==SUPPORT_REQUIRED));
        clearInvocations(requests);
        user.setOwnerAccount(true);service.submit(submission());verify(requests).save(argThat(r -> r.getStatus()==SUPPORT_REQUIRED));
    }
    @Test void actualSchoolOwnerIsProtectedEvenWithoutGlobalOwnerFlag() {
        when(schools.findByOwnerId(100L)).thenReturn(List.of(School.builder().id(3L).owner(user).build()));
        service.submit(submission());verify(requests).save(argThat(r -> r.getStatus()==SUPPORT_REQUIRED));
    }
    @Test void approvalRequiresIdentityProofAndActualSchoolOwnership() {
        assertThrows(IllegalArgumentException.class,()->service.decide(50L,new Decision(true,false,"Pas encore vérifié.")));
        when(guard.currentUserId()).thenReturn(99L);
        assertThrows(AccessDeniedException.class,()->service.decide(50L,approval()));
        verifyNoInteractions(mail);assertNull(request.getTokenHash());
    }
    @Test void ownerCannotApproveAnAccountSharedWithAnotherOwner() {
        when(memberships.findByUserId(100L)).thenReturn(List.of(SchoolUser.builder()
            .school(School.builder().id(2L).owner(User.builder().id(99L).build()).build())
            .role(Role.builder().name("STUDENT").build()).build()));
        assertEquals("SUPPORT_REQUIRED",service.decide(50L,approval()).status());
        verifyNoInteractions(mail);assertEquals("old@example.test",user.getEmail());
    }
    @Test void supportCanReviewEscalatedRequestsWithAnAuditRecord() {
        request.setStatus(SUPPORT_REQUIRED);user.setOwnerAccount(true);
        when(guard.isSuperAdmin()).thenReturn(true);when(guard.currentUserId()).thenReturn(99L);
        assertEquals("APPROVED",service.decide(50L,approval()).status());
        assertTrue(request.isSupportDecision());assertEquals(99L,request.getDecidedBy());
        verify(events).save(argThat(e->e.getAction().equals("APPROVED")&&e.getActorId().equals(99L)));
    }
    @Test void approvedLinkDoesNotChangeCurrentCredentialsBeforeConfirmation() {
        String token=approveAndToken();
        assertEquals("old@example.test",user.getEmail());assertEquals("old-hash",user.getPasswordHash());
        assertEquals(0,user.getSessionVersion());assertNotEquals(token,request.getTokenHash());
        verify(mail).sendText(eq("old@example.test"),anyString(),anyString());
        assertEquals("SENT",request.getDeliveryStatus());assertEquals("SENT",request.getOldNotificationStatus());
    }
    @Test void confirmationReplacesCredentialsButPreservesDossiersAndAccess() {
        String token=approveAndToken();
        service.complete(new Complete(token,"new-password"));
        assertEquals("new@example.test",user.getEmail());assertEquals("hash:new-password",user.getPasswordHash());
        assertTrue(user.getEmailVerified());assertTrue(user.getPasswordSet());assertEquals(1,user.getSessionVersion());
        assertEquals(COMPLETED,request.getStatus());assertNull(request.getTokenHash());
        assertEquals("001",student.getRegistrationNumber());assertEquals(100L,student.getUser().getId());
        verify(resetTokens).deleteByUserId(100L);verify(verificationTokens).deleteByUserId(100L);
        verify(students,never()).save(any());verify(memberships,never()).save(any());
        verify(events).save(argThat(e -> e.getAction().equals("COMPLETED")));
        assertThrows(IllegalArgumentException.class,()->service.complete(new Complete(token,"another-password")));
    }
    @Test void expiredLinksAndChangedSessionsCannotRecoverAnAccount() {
        String token=approveAndToken();
        request.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        assertThrows(IllegalArgumentException.class,()->service.complete(new Complete(token,"new-password")));
        request.setExpiresAt(LocalDateTime.now().plusHours(1));user.revokeSessions();
        assertThrows(IllegalArgumentException.class,()->service.complete(new Complete(token,"new-password")));
        assertEquals("old@example.test",user.getEmail());
    }
    @Test void scopeChangesAfterApprovalInvalidateOwnerApprovedLink() {
        String token=approveAndToken();user.setOwnerAccount(true);
        assertThrows(IllegalArgumentException.class,()->service.complete(new Complete(token,"new-password")));
        assertEquals("old@example.test",user.getEmail());
    }
    @Test void destinationEmailCannotTakeOverOrMergeAnotherAccount() {
        when(users.findByEmailIgnoreCase("new@example.test")).thenReturn(Optional.of(User.builder().id(101L).build()));
        assertThrows(IllegalArgumentException.class,()->service.decide(50L,approval()));verifyNoInteractions(mail);
    }
    @Test void destinationUniquenessIsCheckedAgainAtConfirmation() {
        String token=approveAndToken();
        when(users.findByEmailIgnoreCase("new@example.test")).thenReturn(Optional.of(User.builder().id(101L).build()));
        assertThrows(IllegalArgumentException.class,()->service.complete(new Complete(token,"new-password")));
        assertEquals("old@example.test",user.getEmail());
    }
    @Test void smtpFailureCanBeRetriedWithoutChangingExistingCredentials() {
        doThrow(new MailSendException("offline")).when(mail).sendText(eq("new@example.test"),anyString(),anyString());
        assertEquals("FAILED",service.decide(50L,approval()).deliveryStatus());
        String oldHash=request.getTokenHash();
        doNothing().when(mail).sendText(eq("new@example.test"),anyString(),anyString());
        assertEquals("SENT",service.resend(50L).deliveryStatus());assertNotEquals(oldHash,request.getTokenHash());
        assertEquals("old@example.test",user.getEmail());assertEquals(2,request.getSendCount());
    }
    @Test void rejectionNeverSendsRecoveryLinks() {
        assertEquals("REJECTED",service.decide(50L,new Decision(false,false,"Identité non confirmée par le responsable légal.")).status());
        verifyNoInteractions(mail);assertNull(request.getTokenHash());
    }
    @Test void ownerCanCancelAnApprovedRecoveryBeforeItIsConsumed() {
        String token=approveAndToken();
        service.decide(50L,new Decision(false,false,"Annulation : la demande ne vient pas du titulaire."));
        assertNull(request.getTokenHash());assertEquals(REJECTED,request.getStatus());
        assertThrows(IllegalArgumentException.class,()->service.complete(new Complete(token,"new-password")));
        assertEquals("old@example.test",user.getEmail());
        verify(events).save(argThat(e -> e.getAction().equals("CANCELLED")));
    }
    @Test void staleIdentityVerificationCannotBeReusedIndefinitely() {
        approveAndToken();request.setDecidedAt(LocalDateTime.now().minusDays(2));
        assertThrows(IllegalArgumentException.class,()->service.resend(50L));
    }
    @Test void dossierReassignmentInvalidatesPendingRecovery() {
        student.setUser(User.builder().id(101L).build());
        assertThrows(IllegalArgumentException.class,()->service.decide(50L,approval()));verifyNoInteractions(mail);
    }
}
