package org.afritechinnovations.service.common;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.model.people.EnrollmentStatus;
import org.afritechinnovations.repository.common.SchoolUserRepository;
import org.afritechinnovations.repository.people.ParentRepository;
import org.afritechinnovations.repository.people.ParentStudentRepository;
import org.afritechinnovations.repository.people.StudentRepository;
import org.afritechinnovations.repository.people.StudentEnrollmentRepository;
import org.afritechinnovations.repository.people.TeacherRepository;
import org.afritechinnovations.repository.academic.ClassSubjectTeacherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountOnboardingService {
    private final SchoolUserRepository memberships;
    private final ParentRepository parents;
    private final ParentStudentRepository children;
    private final StudentRepository students;
    private final StudentEnrollmentRepository enrollments;
    private final TeacherRepository teachers;
    private final ClassSubjectTeacherRepository assignments;

    @Transactional(readOnly = true)
    public List<String> steps(User user) {
        List<String> steps = new ArrayList<>();
        if (!Boolean.TRUE.equals(user.getActive())) steps.add("ACCOUNT_INACTIVE");
        if (!Boolean.TRUE.equals(user.getPasswordSet())) steps.add("PASSWORD_REQUIRED");
        if (user.isMustChangePassword()) steps.add("PASSWORD_CHANGE_REQUIRED");
        if (!Boolean.TRUE.equals(user.getEmailVerified())) steps.add("EMAIL_VERIFICATION_REQUIRED");
        if (!Boolean.TRUE.equals(user.getApproved())) steps.add("APPROVAL_REQUIRED");
        var roles = memberships.findByUserId(user.getId()).stream().map(m -> m.getRole().getName()).toList();
        if (roles.contains("PARENT") && parents.findByUserId(user.getId())
                .map(p -> children.findByParentId(p.getId()).isEmpty()).orElse(true)) steps.add("CHILD_LINK_REQUIRED");
        if (roles.contains("STUDENT") && students.findAllByUserId(user.getId()).stream()
                .noneMatch(s -> enrollments.findByStudentId(s.getId()).stream().anyMatch(e -> e.getStatus() == EnrollmentStatus.ACTIVE))) {
            steps.add("CLASS_ASSIGNMENT_REQUIRED");
        }
        if (roles.contains("TEACHER") && teachers.findByUserId(user.getId()).stream()
                .noneMatch(t -> assignments.findByTeacherId(t.getId()).stream().anyMatch(a -> a.isActive()))) {
            steps.add("TEACHING_ASSIGNMENT_REQUIRED");
        }
        if (steps.isEmpty()) steps.add("READY");
        return List.copyOf(steps);
    }
}
