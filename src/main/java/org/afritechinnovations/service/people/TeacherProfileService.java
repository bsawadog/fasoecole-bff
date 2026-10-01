package org.afritechinnovations.service.people;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.SchoolUser;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.model.people.Teacher;
import org.afritechinnovations.repository.common.SchoolUserRepository;
import org.afritechinnovations.repository.people.TeacherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

/** Garantit qu'un utilisateur ayant le rôle enseignant dans un établissement y possède une fiche enseignant. */
@Service
@RequiredArgsConstructor
@Transactional
public class TeacherProfileService {

    static final String TEACHER_ROLE = "TEACHER";

    private final TeacherRepository teacherRepository;
    private final SchoolUserRepository schoolUserRepository;

    public Teacher ensureProfile(User user, School school) {
        return teacherRepository.findByUserId(user.getId()).stream()
                .filter(teacher -> teacher.getSchool().getId().equals(school.getId()))
                .findFirst()
                .orElseGet(() -> teacherRepository.save(Teacher.builder().user(user).school(school).build()));
    }

    /** Crée les fiches manquantes de tous les comptes enseignants rattachés à l'établissement. */
    public void ensureSchoolProfiles(Long schoolId) {
        Set<Long> withProfile = new HashSet<>();
        teacherRepository.findBySchoolId(schoolId).forEach(teacher -> withProfile.add(teacher.getUser().getId()));
        for (SchoolUser link : schoolUserRepository.findAllWithUserAndRoleBySchoolId(schoolId)) {
            if (TEACHER_ROLE.equals(link.getRole().getName()) && withProfile.add(link.getUser().getId())) {
                teacherRepository.save(Teacher.builder().user(link.getUser()).school(link.getSchool()).build());
            }
        }
    }
}