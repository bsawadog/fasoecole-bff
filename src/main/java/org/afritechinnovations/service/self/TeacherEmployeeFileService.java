package org.afritechinnovations.service.self;

import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.people.TeacherWorkDto;
import org.afritechinnovations.dto.communication.FamilyContactDto;
import org.afritechinnovations.model.common.SchoolStatus;
import org.afritechinnovations.model.people.Teacher;
import org.afritechinnovations.repository.people.TeacherRepository;
import org.afritechinnovations.service.people.TeacherWorkService;
import org.afritechinnovations.service.communication.ConversationMessagingService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class TeacherEmployeeFileService {
    private final TeacherRepository teachers;
    private final TeacherWorkService work;
    private final ConversationMessagingService messages;
    public record Choice(Long teacherId,Long schoolId,String schoolName,String employeeNumber) {}
    public record File(Long teacherId,Long schoolId,String schoolName,LocalDate hireDate,String month,
                       TeacherWorkDto.TeacherDetail detail,boolean canRequest,String administrationName) {}
    public record ActionRequest(@NotBlank @Size(max=120) String subject,@NotBlank @Size(max=3500) String content) {}
    public List<Choice> list(Long user) {
        return teachers.findByUserId(user).stream().map(t->new Choice(t.getId(),t.getSchool().getId(),t.getSchool().getName(),t.getEmployeeNumber()))
                .sorted(Comparator.comparing(Choice::schoolName,String.CASE_INSENSITIVE_ORDER)).toList();
    }
    public File read(Long user,Long teacherId,String month) {
        Teacher teacher=own(user,teacherId);
        String period=month==null || month.isBlank()?YearMonth.now().toString():month;
        var detail=work.getOwnTeacherDetail(teacherId,period,user);
        var owner=teacher.getSchool().getOwner();
        return new File(teacherId,teacher.getSchool().getId(),teacher.getSchool().getName(),teacher.getHireDate(),period,detail,
                canRequest(teacher,user),owner==null?"Administration":((owner.getFirstName()==null?"":owner.getFirstName())+" "+(owner.getLastName()==null?"":owner.getLastName())).trim());
    }
    @Transactional
    public FamilyContactDto.ConversationSummary request(Long user,Long teacherId,ActionRequest request) {
        Teacher teacher=own(user,teacherId);
        if(!canRequest(teacher,user))throw new IllegalArgumentException("Aucun responsable administratif distinct n’est disponible pour recevoir cette demande.");
        String content="Demande concernant ma fiche employé\nÉtablissement : "+teacher.getSchool().getName()+"\nMatricule : "+(teacher.getEmployeeNumber()==null?"Non renseigné":teacher.getEmployeeNumber())+"\n\n"+request.content().trim();
        return messages.sendTeacherMessage(user,new FamilyContactDto.TeacherMessageRequest(teacher.getSchool().getId(),null,
                "[Fiche employé] "+request.subject().trim(),content,List.of(teacher.getSchool().getOwner().getId())),List.of()).getFirst();
    }
    private Teacher own(Long user,Long teacherId) {
        return teachers.findById(teacherId).filter(t->t.getUser().getId().equals(user))
                .orElseThrow(()->new AccessDeniedException("Vous ne pouvez consulter que votre propre fiche employé."));
    }
    private boolean canRequest(Teacher teacher,Long user) {
        var owner=teacher.getSchool().getOwner();
        return teacher.getSchool().getStatus()==SchoolStatus.ACTIVE && owner!=null && !owner.getId().equals(user)
                && Boolean.TRUE.equals(owner.getActive()) && Boolean.TRUE.equals(owner.getApproved());
    }
}
