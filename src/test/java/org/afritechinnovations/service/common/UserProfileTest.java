package org.afritechinnovations.service.common;

import org.afritechinnovations.dto.common.UpdateProfileRequest;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.repository.common.RoleRepository;
import org.afritechinnovations.repository.common.SchoolRepository;
import org.afritechinnovations.repository.common.SchoolUserRepository;
import org.afritechinnovations.repository.common.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProfileTest {
    @Mock UserRepository users;
    @Mock PasswordEncoder encoder;
    @Mock SchoolRepository schools;
    @Mock RoleRepository roles;
    @Mock SchoolUserRepository schoolUsers;
    @InjectMocks UserService service;

    @Test
    void updatesOnlyEditableFieldsOfRequestedUser() {
        User user = User.builder().id(7L).firstName("Awa").lastName("Diallo")
                .email("awa@ecole.bf").passwordHash("hash").active(true).approved(true).build();
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(users.save(user)).thenReturn(user);
        when(schoolUsers.findByUserId(7L)).thenReturn(List.of());
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFirstName(" Aminata ");
        request.setLastName(" Diallo ");
        request.setPhone(" 70000000 ");

        var result = service.updateProfile(7L, request);

        assertEquals("Aminata", result.getFirstName());
        assertEquals("Diallo", result.getLastName());
        assertEquals("70000000", result.getPhone());
        assertEquals("awa@ecole.bf", result.getEmail());
        assertEquals("hash", user.getPasswordHash());
        verify(users).save(user);
    }

    @Test
    void changesPasswordOnlyWhenCurrentPasswordMatches() {
        User user = User.builder().id(7L).passwordHash("old-hash").build();
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(encoder.matches("bad", "old-hash")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> service.changePassword(7L, "bad", "NouveauMdp1"));
        verify(users, never()).save(any());

        when(encoder.matches("ancienMdp1", "old-hash")).thenReturn(true);
        when(encoder.matches("NouveauMdp1", "old-hash")).thenReturn(false);
        when(encoder.encode("NouveauMdp1")).thenReturn("new-hash");
        service.changePassword(7L, "ancienMdp1", "NouveauMdp1");

        assertEquals("new-hash", user.getPasswordHash());
        verify(users).save(user);
    }
}
