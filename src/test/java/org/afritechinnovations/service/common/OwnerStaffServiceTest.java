package org.afritechinnovations.service.common;

import org.afritechinnovations.dto.common.OwnerStaffDto;
import org.afritechinnovations.model.common.Role;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.SchoolStaff;
import org.afritechinnovations.model.common.SchoolType;
import org.afritechinnovations.model.common.SchoolUser;
import org.afritechinnovations.model.common.StaffModule;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.repository.common.RoleRepository;
import org.afritechinnovations.repository.common.SchoolRepository;
import org.afritechinnovations.repository.common.SchoolStaffRepository;
import org.afritechinnovations.repository.common.SchoolUserRepository;
import org.afritechinnovations.repository.common.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mail.MailSendException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OwnerStaffServiceTest {

    private static final Long OWNER_ID = 10L;

    @Mock SchoolRepository schoolRepository;
    @Mock SchoolStaffRepository staffRepository;
    @Mock SchoolUserRepository schoolUserRepository;
    @Mock RoleRepository roleRepository;
    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock EmailService emailService;
    @Mock org.afritechinnovations.service.auth.EmailVerificationService invitations;
    @Mock org.afritechinnovations.service.auth.PasswordResetService resets;

    @InjectMocks OwnerStaffService service;

    private School school;
    private final Role staffRole = Role.builder().id(6L).name("STAFF").build();

    @BeforeEach
    void setUp() {
        school = School.builder().id(1L).name("École ABC").type(SchoolType.SECONDAIRE)
                .owner(User.builder().id(OWNER_ID).build()).build();
        when(schoolRepository.findById(1L)).thenReturn(Optional.of(school));
        when(roleRepository.findByName("STAFF")).thenReturn(Optional.of(staffRole));
        when(passwordEncoder.encode(anyString())).thenAnswer(i -> "hash:" + i.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            if (u.getId() == null) u.setId(40L);
            return u;
        });
        when(staffRepository.save(any(SchoolStaff.class))).thenAnswer(i -> {
            SchoolStaff s = i.getArgument(0);
            if (s.getId() == null) s.setId(7L);
            return s;
        });
    }

    private OwnerStaffDto.StaffRequest request(String email, Set<StaffModule> modules) {
        return new OwnerStaffDto.StaffRequest("Awa", "Ouédraogo", email, "70000000", "Comptable", modules, new java.math.BigDecimal("100000.00"));
    }

    @Test
    void createsAnInvitedAccountWithTheStaffRoleAndNoDisclosedPassword() {
        when(userRepository.findByEmailIgnoreCase("awa@ecole.bf")).thenReturn(Optional.empty());
        when(invitations.sendInvitation(any())).thenReturn(false);

        OwnerStaffDto.StaffCreated created = service.create(1L,
                request(" Awa@Ecole.bf ", EnumSet.of(StaffModule.EXPENSES, StaffModule.FINANCE)), OWNER_ID, false);

        assertNull(created.temporaryPassword());
        ArgumentCaptor<User> account = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(account.capture());
        assertFalse(account.getValue().getPasswordSet());
        assertFalse(account.getValue().getEmailVerified());
        verify(invitations).sendInvitation(account.getValue());
        assertFalse(created.existingAccount());
        assertFalse(created.emailSent());
        assertEquals("awa@ecole.bf", created.staff().email());
        assertEquals(new java.math.BigDecimal("100000.00"), created.staff().monthlySalary());
        assertEquals(List.of("FINANCE", "EXPENSES"), created.staff().modules());
        ArgumentCaptor<SchoolUser> link = ArgumentCaptor.forClass(SchoolUser.class);
        verify(schoolUserRepository).save(link.capture());
        assertEquals("STAFF", link.getValue().getRole().getName());
        assertEquals(school, link.getValue().getSchool());
    }

    @Test
    void linksAnExistingAccountWithoutChangingItsPassword() {
        User teacher = User.builder().id(41L).firstName("Ali").lastName("Sawadogo").email("ali@ecole.bf")
                .passwordHash("secret").build();
        when(userRepository.findByEmailIgnoreCase("ali@ecole.bf")).thenReturn(Optional.of(teacher));
        when(schoolUserRepository.findByUserId(41L)).thenReturn(List.of(SchoolUser.builder()
                .school(school).user(teacher).role(Role.builder().name("TEACHER").build()).build()));

        OwnerStaffDto.StaffCreated created = service.create(1L,
                request("ali@ecole.bf", EnumSet.of(StaffModule.GRADES)), OWNER_ID, false);

        assertNull(created.temporaryPassword());
        assertTrue(created.existingAccount());
        assertEquals("secret", teacher.getPasswordHash());
        assertFalse(created.staff().managedAccount());
        verify(userRepository, never()).save(any());
    }

    @Test
    void onlyTheOwnerManagesStaffAndSharedAccountsCannotBeReset() {
        assertThrows(AccessDeniedException.class, () -> service.list(1L, 99L, false));

        User teacher = User.builder().id(41L).email("ali@ecole.bf").build();
        SchoolStaff staff = SchoolStaff.builder().id(7L).school(school).user(teacher).jobTitle("Censeur")
                .modules(EnumSet.of(StaffModule.GRADES)).build();
        when(staffRepository.findById(7L)).thenReturn(Optional.of(staff));
        when(schoolUserRepository.findByUserId(41L)).thenReturn(List.of(
                SchoolUser.builder().school(school).user(teacher).role(staffRole).build(),
                SchoolUser.builder().school(school).user(teacher).role(Role.builder().name("TEACHER").build()).build()));

        assertThrows(IllegalArgumentException.class, () -> service.resetPassword(7L, OWNER_ID, false));
        assertThrows(AccessDeniedException.class, () -> service.setActive(7L, false, 99L, false));

        OwnerStaffDto.StaffRow suspended = service.setActive(7L, false, OWNER_ID, false);
        assertFalse(suspended.active());
        assertFalse(staff.allows(StaffModule.GRADES));
    }

    @Test
    void accessListsOwnedSchoolsThenActiveDelegations() {
        School other = School.builder().id(2L).name("Lycée B").type(SchoolType.SECONDAIRE).build();
        School suspended = School.builder().id(3L).name("Collège C").build();
        when(schoolRepository.findByOwnerId(OWNER_ID)).thenReturn(List.of(school));
        when(staffRepository.findByUserWithSchool(OWNER_ID)).thenReturn(List.of(
                SchoolStaff.builder().school(other).jobTitle("Directeur")
                        .modules(EnumSet.of(StaffModule.STUDENTS, StaffModule.DASHBOARD)).build(),
                SchoolStaff.builder().school(suspended).jobTitle("Secrétaire").active(false)
                        .modules(EnumSet.of(StaffModule.STUDENTS)).build()));

        List<OwnerStaffDto.SchoolAccess> access = service.accessOf(OWNER_ID);

        assertEquals(2, access.size());
        assertTrue(access.get(0).owner());
        assertEquals(StaffModule.values().length, access.get(0).modules().size());
        assertFalse(access.get(1).owner());
        assertEquals(List.of("DASHBOARD", "STUDENTS"), access.get(1).modules());
    }

    @Test
    void managedStaffResetSendsALinkWithoutChangingThePasswordOrExposingCredentials() {
        User account = User.builder().id(40L).email("awa@ecole.bf").passwordHash("chosen").emailVerified(true).build();
        SchoolStaff staff = SchoolStaff.builder().id(7L).school(school).user(account).jobTitle("Comptable")
                .modules(EnumSet.of(StaffModule.FINANCE)).build();
        when(staffRepository.findById(7L)).thenReturn(Optional.of(staff));
        when(schoolUserRepository.findByUserId(40L)).thenReturn(List.of(SchoolUser.builder()
                .user(account).school(school).role(staffRole).build()));
        when(resets.sendManagedReset(account)).thenReturn(true);
        var result = service.resetPassword(7L, OWNER_ID, false);
        assertTrue(result.emailSent()); assertNull(result.temporaryPassword());
        assertEquals("chosen", account.getPasswordHash());
        verify(resets).sendManagedReset(account); verify(userRepository, never()).save(any());
        account.setPasswordSet(false);
        when(invitations.sendInvitation(account)).thenReturn(true);
        assertTrue(service.resetPassword(7L, OWNER_ID, false).emailSent());
        verify(invitations).sendInvitation(account);
        verify(resets, times(1)).sendManagedReset(account);
    }
}
