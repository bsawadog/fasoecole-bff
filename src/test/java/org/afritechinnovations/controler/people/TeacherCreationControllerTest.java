package org.afritechinnovations.controler.people;

import org.afritechinnovations.dto.people.CreateClassTeacherRequest;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.security.UserPrincipal;
import org.afritechinnovations.service.people.TeacherWorkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.*;

/** Actual HTTP deserialization and Bean Validation; persistence and business service are mocked. */
class TeacherCreationControllerTest {
    TeacherWorkService service;
    MockMvc mvc;
    UsernamePasswordAuthenticationToken owner;

    @BeforeEach void setup() {
        service = mock(TeacherWorkService.class);
        mvc = MockMvcBuilders.standaloneSetup(new TeacherWorkController(service)).build();
        var principal = new UserPrincipal(User.builder().id(10L).emailVerified(true).build(), List.of("SCHOOL_ADMIN"));
        owner = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test void newTeacherCanBeCreatedWithoutAnyPasswordField() throws Exception {
        mvc.perform(post("/api/teacher-work/classes/3/teachers").principal(owner).contentType("application/json")
                .content("""
                  {"firstName":"Awa","lastName":"Diallo","email":"awa@test.bf","subjectId":8,"employeeNumber":"EMP-001"}
                  """)).andExpect(status().isCreated());
        var request = org.mockito.ArgumentCaptor.forClass(CreateClassTeacherRequest.class);
        verify(service).createClassTeacher(eq(3L), request.capture(), eq(10L), eq(false));
        assertNull(request.getValue().getPassword());
        assertEquals("EMP-001", request.getValue().getEmployeeNumber());
    }

    @Test void emailAndSubjectRemainMandatoryEvenThoughPasswordIsNotRequired() throws Exception {
        mvc.perform(post("/api/teacher-work/classes/3/teachers").principal(owner).contentType("application/json")
                .content("""
                  {"firstName":"Awa","lastName":"Diallo","email":""}
                  """)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
