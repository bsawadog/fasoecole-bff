package org.afritechinnovations.config;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.afritechinnovations.security.AccessGuard;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Applies suspension to scoped requests, including controllers using owner checks directly. */
@Configuration
@RequiredArgsConstructor
public class SchoolAvailabilityConfig implements WebMvcConfigurer {
    private final AccessGuard guard;
    private final JdbcTemplate jdbc;

    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) {
                if (guard.isSuperAdmin()) return true;
                Object attributes=request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
                Map<?,?> variables=attributes instanceof Map<?,?> m ? m : Map.of();
                checkSchool(request.getParameter("schoolId"));
                checkSchool(request.getHeader("X-Academic-School"));
                checkSchool(variables.get("schoolId"));
                String path=request.getServletPath();
                Object id=variables.get("id");
                if (path.matches("/api/schools/\\d+.*")) checkSchool(id);
                checkEntity("classes",variables.get("classId"));
                checkEntity("students",variables.get("studentId"));
                checkEntity("teachers",variables.get("teacherId"));
                checkEntity("academic_years",variables.get("yearId"));
                if (path.startsWith("/api/classes/")) checkEntity("classes",id);
                if (path.startsWith("/api/students/")) checkEntity("students",id);
                if (path.startsWith("/api/academic-years/")) checkEntity("academic_years",id);
                return true;
            }
            private void checkEntity(String table,Object value) {
                if(value==null) return;
                Long id=number(value);
                jdbc.query("SELECT school_id FROM "+table+" WHERE id=?",(r,n)->r.getLong(1),id).forEach(this::checkSchool);
            }
            private void checkSchool(Object value) {
                if(value==null) return;
                Long id=number(value);
                Boolean suspended=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM schools WHERE id=? AND status IN ('SUSPENDED','ARCHIVED'))",Boolean.class,id);
                if(Boolean.TRUE.equals(suspended)) throw new AccessDeniedException("Cet établissement est désactivé. Contactez l’administrateur de la plateforme.");
                Boolean preparing=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM schools WHERE id=? AND status IN ('DRAFT','PENDING_APPROVAL') AND owner_id<>?)",Boolean.class,id,guard.currentUserId());
                if(Boolean.TRUE.equals(preparing)) throw new AccessDeniedException("Cet établissement n’est pas encore activé");
            }
            private Long number(Object value) {
                try { return Long.valueOf(value.toString()); }
                catch(NumberFormatException e) { throw new IllegalArgumentException("Identifiant d’établissement invalide"); }
            }
        }).addPathPatterns("/api/**").excludePathPatterns("/api/auth/**","/api/platform/**","/api/users/**",
                "/api/schools/registration-options","/api/school-access-requests/**","/api/owner/export/**");
    }
}
