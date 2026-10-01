package org.afritechinnovations.controler.common;

import org.afritechinnovations.dto.common.UpdateProfileRequest;
import org.afritechinnovations.dto.common.UserDto;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.security.UserPrincipal;
import org.afritechinnovations.service.common.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProfileControllerTest {
    @Mock UserService users;
    @Mock AccessGuard guard;
    @InjectMocks UserController controller;

    @Test
    void updatesOnlyAuthenticatedAccount() {
        UserPrincipal principal = new UserPrincipal(User.builder().id(7L).active(true).approved(true).build(),
                List.of("TEACHER"));
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        UpdateProfileRequest request = new UpdateProfileRequest();
        UserDto updated = UserDto.builder().id(7L).firstName("Awa").build();
        when(users.updateProfile(7L, request)).thenReturn(updated);

        assertSame(updated, controller.updateCurrentUser(request, authentication));
        verify(users).updateProfile(7L, request);
    }

    @Test
    void rejectsUnauthenticatedUpdate() {
        assertThrows(AccessDeniedException.class,
                () -> controller.updateCurrentUser(new UpdateProfileRequest(), null));
        verifyNoInteractions(users);
    }
}
