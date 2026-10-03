package org.afritechinnovations.service.common;

import org.afritechinnovations.model.common.*;
import org.afritechinnovations.model.people.*;
import org.afritechinnovations.repository.people.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class ParentChildAdmissionServiceTest {
    @Mock StudentRepository students;
    @Mock ParentRepository parents;
    @Mock ParentStudentRepository links;
    @InjectMocks ParentChildAdmissionService service;
    User user = User.builder().id(7L).build();
    Parent parent = Parent.builder().id(8L).user(user).build();
    Student child(long id, String number) {
        return Student.builder().id(id).registrationNumber(number).school(School.builder().id(2L).build())
                .user(User.builder().firstName("Sali").lastName("Diallo").build()).build();
    }

    @Test void normalizationPreservesLeadingZeroesDeduplicatesAndRejectsInvalidClaims() {
        assertEquals(List.of("001", "MAT-02"), ParentChildAdmissionService.normalize(List.of(" 001 ", "MAT-02", "001"), true));
        assertThrows(IllegalArgumentException.class, () -> ParentChildAdmissionService.normalize(null, true));
        assertThrows(IllegalArgumentException.class, () -> ParentChildAdmissionService.normalize(List.of(" "), true));
        assertThrows(IllegalArgumentException.class, () -> ParentChildAdmissionService.normalize(List.of("a".repeat(51)), true));
        assertThrows(IllegalArgumentException.class, () -> ParentChildAdmissionService.normalize(Collections.nCopies(21, "001"), true));
    }

    @Test void approvalCreatesParentAndAllChildrenLinks() {
        when(students.findBySchoolIdAndRegistrationNumber(2L, "001")).thenReturn(Optional.of(child(11L, "001")));
        when(students.findBySchoolIdAndRegistrationNumber(2L, "002")).thenReturn(Optional.of(child(12L, "002")));
        when(parents.save(any())).thenReturn(parent);
        service.attachApproved(user, 2L, List.of("001", "002"));
        verify(parents).save(argThat(p -> p.getUser() == user));
        verify(links, times(2)).save(any());
    }

    @Test void alreadyAssociatedChildrenAreNotDuplicatedAndOtherParentLinksArePreserved() {
        Student existing = child(11L, "001");
        when(parents.findByUserId(7L)).thenReturn(Optional.of(parent));
        when(students.findBySchoolIdAndRegistrationNumber(2L, "001")).thenReturn(Optional.of(existing));
        when(links.findByParentId(8L)).thenReturn(List.of(ParentStudent.builder().parent(parent).student(existing).build()));
        service.attachApproved(user, 2L, List.of("001", "001"));
        verify(parents, never()).save(any()); verify(links, never()).save(any()); verify(links, never()).delete(any());
    }

    @Test void unknownOrOtherSchoolMatriculeRejectsEntireAdmissionBeforeAnyWrite() {
        when(students.findBySchoolIdAndRegistrationNumber(2L, "001")).thenReturn(Optional.of(child(11L, "001")));
        assertThrows(IllegalArgumentException.class, () -> service.attachApproved(user, 2L, List.of("001", "OTHER-SCHOOL")));
        verify(students).findBySchoolIdAndRegistrationNumber(2L, "OTHER-SCHOOL");
        verify(parents, never()).save(any()); verify(links, never()).save(any());
    }

    @Test void legacyEmptyRequestRequiresExistingSchoolChildLink() {
        assertThrows(IllegalArgumentException.class, () -> service.attachApproved(user, 2L, List.of()));
        when(parents.findByUserId(7L)).thenReturn(Optional.of(parent));
        when(links.findByParentId(8L)).thenReturn(List.of(ParentStudent.builder().parent(parent).student(child(11L, "001")).build()));
        assertDoesNotThrow(() -> service.attachApproved(user, 2L, List.of()));
        verify(links, never()).save(any());
    }

    @Test void schoolReviewIdentifiesMatchesAndMissingChildrenWithoutWritingLinks() {
        when(students.findBySchoolIdAndRegistrationNumber(2L, "001")).thenReturn(Optional.of(child(11L, "001")));
        assertEquals(List.of("001 — Sali Diallo", "002 — introuvable dans cet établissement"), service.review(2L, List.of("001", "002")));
        verifyNoInteractions(parents, links);
    }
}
