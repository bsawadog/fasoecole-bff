package org.afritechinnovations.config;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.repository.academic.AcademicYearRepository;
import org.afritechinnovations.security.AccessGuard;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@RequiredArgsConstructor
public class AcademicContextConfig implements WebMvcConfigurer {
    private final AcademicYearRepository years;
    private final AccessGuard guard;
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) {
                if (!"GET".equals(request.getMethod()) || request.getHeader("X-Academic-Year")==null) return true;
                Long yearId, schoolId;
                try { yearId=Long.valueOf(request.getHeader("X-Academic-Year")); schoolId=Long.valueOf(request.getHeader("X-Academic-School")); }
                catch(RuntimeException e) { throw new IllegalArgumentException("Contexte d'année invalide."); }
                guard.requireSchoolMember(schoolId);
                var year = years.findById(yearId).orElseThrow(() -> new IllegalArgumentException("Année introuvable."));
                if(!year.getSchool().getId().equals(schoolId)) throw new IllegalArgumentException("Cette année n'appartient pas à l'établissement.");
                request.setAttribute("selectedAcademicYear",year);
                return true;
            }
        }).addPathPatterns("/api/**").excludePathPatterns("/api/auth/**","/api/schools/registration-options","/api/owner/export/**");
    }
}
