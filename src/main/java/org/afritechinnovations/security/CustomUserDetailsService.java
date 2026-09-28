package org.afritechinnovations.security;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.model.common.SchoolUser;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.repository.common.SchoolUserRepository;
import org.afritechinnovations.repository.common.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final SchoolUserRepository schoolUserRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur introuvable: " + email));

        List<String> roles = schoolUserRepository.findByUserId(user.getId())
                .stream()
                .map(SchoolUser::getRole)
                .map(role -> role.getName())
                .distinct()
                .toList();

        return new UserPrincipal(user, roles);
    }
}
