package org.afritechinnovations.dto.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.afritechinnovations.dto.people.TeacherRateRequest;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class EmployeeDirectoryDto {
    private EmployeeDirectoryDto() { }
    public record TeacherProfile(Long id,String employeeNumber,String specialty,LocalDate hireDate,
            String rateType,BigDecimal rate,LocalDate rateEffectiveFrom) { }
    public record Row(OwnerStaffDto.StaffRow staff,TeacherProfile teacher,boolean administrativeEmployment,
            boolean owner,boolean identityEditable,boolean emailEditable,boolean accountActive) { }
    public record Update(@NotNull @Valid OwnerStaffDto.StaffRequest profile,
            @Size(max=50) String employeeNumber,@Size(max=150) String specialty,LocalDate hireDate,
            boolean administrativeEmployment,@Valid TeacherRateRequest teacherRate) { }
}
