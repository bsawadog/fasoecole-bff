package org.afritechinnovations.service.common;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.model.people.Parent;
import org.afritechinnovations.model.people.ParentStudent;
import org.afritechinnovations.model.people.Student;
import org.afritechinnovations.repository.people.ParentRepository;
import org.afritechinnovations.repository.people.ParentStudentRepository;
import org.afritechinnovations.repository.people.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** A matricule is a claim to be reviewed by the school, never a proof of parenthood. */
@Service
@RequiredArgsConstructor
@Transactional
public class ParentChildAdmissionService {
    private final StudentRepository students;
    private final ParentRepository parents;
    private final ParentStudentRepository links;

    public static List<String> normalize(List<String> numbers, boolean required) {
        if (numbers == null) numbers = List.of();
        if (numbers.size() > 20) throw new IllegalArgumentException("Au maximum 20 matricules par demande");
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String number : numbers) {
            if (number == null || number.trim().isEmpty() || number.trim().length() > 50
                    || number.trim().matches(".*[\\p{Cntrl}].*")) {
                throw new IllegalArgumentException("Chaque matricule doit contenir de 1 à 50 caractères");
            }
            result.add(number.trim());
        }
        if (required && result.isEmpty()) throw new IllegalArgumentException("Renseignez au moins un matricule de vos enfants");
        return List.copyOf(result);
    }

    /** Called only after the approver's ownership and account checks. Resolve everything before writing. */
    public void attachApproved(User user, Long schoolId, List<String> numbers) {
        List<String> normalized = normalize(numbers, false);
        Parent existing = parents.findByUserId(user.getId()).orElse(null);
        if (normalized.isEmpty()) {
            boolean alreadyLinked = existing != null && links.findByParentId(existing.getId()).stream()
                    .anyMatch(link -> schoolId.equals(link.getStudent().getSchool().getId()));
            if (alreadyLinked) return; // Existing school-created parent, or legacy request with verified links.
            throw new IllegalArgumentException("Aucun enfant renseigné : demandez au parent de compléter ses matricules");
        }
        List<Student> children = normalized.stream().map(number -> students
                .findBySchoolIdAndRegistrationNumber(schoolId, number)
                .orElseThrow(() -> new IllegalArgumentException("Matricule introuvable dans cet établissement : " + number))).toList();
        Parent parent = existing != null ? existing : parents.save(Parent.builder().user(user).build());
        Set<Long> linkedIds = new HashSet<>();
        links.findByParentId(parent.getId()).forEach(link -> linkedIds.add(link.getStudent().getId()));
        for (Student child : children) {
            if (linkedIds.add(child.getId())) links.save(ParentStudent.builder().parent(parent).student(child).build());
        }
    }

    /** Private school review only: do not expose directory matches on public registration or to applicants. */
    @Transactional(readOnly = true)
    public List<String> review(Long schoolId, List<String> numbers) {
        return normalize(numbers, false).stream().map(number -> students
                .findBySchoolIdAndRegistrationNumber(schoolId, number)
                .map(child -> number + " — " + child.getUser().getFirstName() + " " + child.getUser().getLastName())
                .orElse(number + " — introuvable dans cet établissement")).toList();
    }
}
