package org.afritechinnovations.service.auth;

import org.afritechinnovations.dto.auth.RegisterUserRequest;
import org.afritechinnovations.model.common.*;
import org.afritechinnovations.repository.common.*;
import org.afritechinnovations.repository.people.ParentRepository;
import org.afritechinnovations.security.*;
import org.afritechinnovations.service.common.*;
import org.afritechinnovations.service.people.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Composition réelle des services ; seules la persistance et la livraison SMTP sont simulées. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountRegistrationFlowTest {
    @Mock UserRepository users;
    @Mock SchoolRepository schools;
    @Mock RoleRepository roles;
    @Mock SchoolUserRepository memberships;
    @Mock EmailVerificationTokenRepository proofTokens;
    @Mock PasswordResetTokenRepository resetTokens;
    @Mock EmailService mail;
    @Mock ParentAutoAccessService parentAccess;
    @Mock ParentRepository parents;
    @Mock TeacherProfileService teachers;
    @Mock ClassRosterService roster;
    @Mock AccountOnboardingService onboarding;
    @Mock org.afritechinnovations.repository.people.StudentRepository students;
    @Mock org.afritechinnovations.repository.people.ParentStudentRepository childLinks;
    ParentChildAdmissionService childAdmission;
    @Mock org.afritechinnovations.repository.people.TeacherRepository teacherDossiers;
    SchoolIdentityAdmissionService identityAdmission;
    final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    final JwtService jwt = new JwtService(Base64.getEncoder().encodeToString(new byte[32]), 60_000);
    UserService accounts;
    EmailVerificationService verification;
    PasswordResetService resets;
    User stored;
    EmailVerificationToken proof;
    PasswordResetToken reset;
    String proofRaw, resetRaw;
    final List<SchoolUser> links = new ArrayList<>();

    @BeforeEach void setup() {
        AuthAttemptLimiter mailAttempts = new AuthAttemptLimiter();
        verification = new EmailVerificationService(users, proofTokens, encoder, mail, parentAccess, mailAttempts);
        resets = new PasswordResetService(users, resetTokens, encoder, mail, parentAccess, mailAttempts);
        childAdmission = new ParentChildAdmissionService(students, parents, childLinks);
        identityAdmission = new SchoolIdentityAdmissionService(students, teacherDossiers, memberships);
        accounts = new UserService(users, encoder, schools, roles, memberships, verification, onboarding, childAdmission, identityAdmission);
        ReflectionTestUtils.setField(verification, "frontendBaseUrl", "http://app");
        ReflectionTestUtils.setField(resets, "frontendBaseUrl", "http://app");
        ReflectionTestUtils.setField(resets, "tokenExpirationMinutes", 30L);
        School school = School.builder().id(2L).status(SchoolStatus.ACTIVE).owner(User.builder().id(10L).build()).build();
        when(schools.findById(2L)).thenReturn(Optional.of(school));
        when(students.findBySchoolIdAndRegistrationNumber(2L, "MAT-01")).thenReturn(Optional.of(
                org.afritechinnovations.model.people.Student.builder().id(11L).school(school)
                .registrationNumber("MAT-01").user(User.builder().firstName("Sali").lastName("Diallo").build()).build()));
        when(students.findBySchoolIdAndRegistrationNumberForUpdate(2L, "001")).thenAnswer(i -> Optional.of(
                org.afritechinnovations.model.people.Student.builder().id(22L).school(school).user(stored).registrationNumber("001").build()));
        when(teacherDossiers.findBySchoolIdAndEmployeeNumberForUpdate(2L, "EMP-01")).thenAnswer(i -> Optional.of(
                org.afritechinnovations.model.people.Teacher.builder().id(33L).school(school).user(stored).employeeNumber("EMP-01").build()));
        when(parents.save(any())).thenAnswer(i -> { org.afritechinnovations.model.people.Parent p = i.getArgument(0); p.setId(8L); return p; });
        when(users.findByEmailIgnoreCase(anyString())).thenAnswer(i -> Optional.ofNullable(stored));
        when(users.findById(7L)).thenAnswer(i -> Optional.ofNullable(stored));
        when(users.save(any())).thenAnswer(i -> { stored = i.getArgument(0); stored.setId(7L); return stored; });
        when(roles.findByName(anyString())).thenAnswer(i -> Optional.of(Role.builder().name(i.getArgument(0)).build()));
        when(memberships.findByUserId(7L)).thenAnswer(i -> List.copyOf(links));
        when(memberships.save(any())).thenAnswer(i -> { SchoolUser link = i.getArgument(0); links.add(link); return link; });
        when(proofTokens.save(any())).thenAnswer(i -> proof = i.getArgument(0));
        when(proofTokens.findByTokenHash(anyString())).thenAnswer(i -> proof != null && proof.getTokenHash().equals(i.getArgument(0)) ? Optional.of(proof) : Optional.empty());
        when(proofTokens.findByTokenHashForUpdate(anyString())).thenAnswer(i -> proof != null && proof.getTokenHash().equals(i.getArgument(0)) ? Optional.of(proof) : Optional.empty());
        doAnswer(i -> { proof = null; return null; }).when(proofTokens).delete(any());
        doAnswer(i -> { String body = i.getArgument(2); proofRaw = body.split("verify=")[1].split("\\s")[0]; return null; })
                .when(mail).sendText(anyString(), anyString(), anyString());
        when(resetTokens.save(any())).thenAnswer(i -> reset = i.getArgument(0));
        when(resetTokens.findByTokenHashForUpdate(anyString())).thenAnswer(i -> reset != null && reset.getTokenHash().equals(i.getArgument(0)) ? Optional.of(reset) : Optional.empty());
        doAnswer(i -> { reset = null; return null; }).when(resetTokens).delete(any());
        doAnswer(i -> { String url = i.getArgument(1); resetRaw = url.split("token=")[1]; return null; })
                .when(mail).sendPasswordReset(anyString(), anyString(), anyLong());
    }

    @ParameterizedTest @EnumSource(value = RoleName.class, names = {"PARENT", "TEACHER", "STUDENT"})
    void registrationVerificationApprovalUseAndPasswordResetRespectEveryGate(RoleName role) {
        RegisterUserRequest request = new RegisterUserRequest();
        request.setFirstName("Awa"); request.setLastName("Diallo"); request.setEmail("awa@test.bf");
        request.setPassword("MonMotDePasse"); request.setRequestedRole(role); request.setSchoolId(2L);
        request.setSchoolIdentifier(role == RoleName.STUDENT ? "001" : role == RoleName.TEACHER ? "EMP-01" : null);
        if (role == RoleName.PARENT) request.setChildRegistrationNumbers(List.of("MAT-01"));
        accounts.receiveRegistration(request);
        assertTrue(encoder.matches("MonMotDePasse", stored.getPasswordHash()));
        assertFalse(allowed()); assertTrue(links.isEmpty());
        verify(childLinks, never()).save(any());
        assertThrows(IllegalArgumentException.class, () -> accounts.approvePendingUser(7L, 2L, role, 10L, false, 11L, null));
        verification.confirm(proofRaw, null);
        assertTrue(stored.getEmailVerified()); assertFalse(stored.getApproved()); assertFalse(allowed());
        assertThrows(IllegalArgumentException.class, () -> verification.confirm(proofRaw, null));
        accounts.approvePendingUser(7L, 2L, role, 10L, false, 11L, null);
        if (role == RoleName.PARENT) verify(childLinks).save(argThat(link -> link.getStudent().getId().equals(11L) && link.getParent().getUser() == stored));
        assertTrue(allowed()); assertEquals(List.of(role.name()), links.stream().map(l -> l.getRole().getName()).toList());
        String oldSession = jwt.generateToken(7L, stored.getEmail(), List.of(role.name()), stored.getSessionVersion());
        resets.requestReset(stored.getEmail()); resets.resetPassword(resetRaw, "NouveauMotDePasse");
        assertTrue(encoder.matches("NouveauMotDePasse", stored.getPasswordHash()));
        assertFalse(encoder.matches("MonMotDePasse", stored.getPasswordHash()));
        assertFalse(jwt.isTokenValid(oldSession, principal()));
        assertThrows(IllegalArgumentException.class, () -> resets.resetPassword(resetRaw, "AutreMotDePasse"));
        String newSession = jwt.generateToken(7L, stored.getEmail(), List.of(role.name()), stored.getSessionVersion());
        assertTrue(jwt.isTokenValid(newSession, principal())); assertTrue(allowed());
    }
    UserPrincipal principal() { return new UserPrincipal(stored, links.stream().map(l -> l.getRole().getName()).toList()); }
    boolean allowed() { return AccountAccessPolicy.allows(principal(), "GET", "/api/conversations"); }
}
