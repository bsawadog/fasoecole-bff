package org.afritechinnovations.service.academic;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.model.academic.Subject;
import org.afritechinnovations.repository.academic.SubjectRepository;
import org.afritechinnovations.repository.common.SchoolRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;

@Service @RequiredArgsConstructor
public class SubjectCatalogService {
 private final SchoolRepository schools;private final SubjectRepository subjects;
 public record Result(int created,int completed,List<String> conflicts){}
 @Transactional public Result apply(Long schoolId,String packId){
  var pack=SubjectCatalog.pack(packId);
  var school=schools.lockById(schoolId).orElseThrow(()->new IllegalArgumentException("Établissement introuvable."));
  var existing=new ArrayList<>(subjects.findBySchoolId(schoolId));int created=0,completed=0;var conflicts=new ArrayList<String>();
  for(var item:pack.subjects()){
   var byName=existing.stream().filter(s->SubjectCatalog.normalize(s.getName()).equals(SubjectCatalog.normalize(item.name()))).toList();
   var byCode=existing.stream().filter(s->SubjectCatalog.normalize(s.getCode()).equals(SubjectCatalog.normalize(item.code()))).toList();
   if(byName.size()>1||byCode.size()>1||byCode.size()==1&&(byName.isEmpty()||byName.getFirst()!=byCode.getFirst())){conflicts.add(item.code()+" : "+item.name()+" (code ou nom déjà utilisé)");continue;}
   if(!byName.isEmpty()){
    var subject=byName.getFirst();boolean changed=false;
    if(subject.getCode()==null||subject.getCode().isBlank()){subject.setCode(item.code());changed=true;}
    if(subject.getCategory()==null||subject.getCategory().isBlank()){subject.setCategory(item.category());changed=true;}
    if(changed){subjects.save(subject);completed++;}
   }else{
    var subject=subjects.save(Subject.builder().school(school).name(item.name()).code(item.code()).category(item.category()).build());existing.add(subject);created++;
   }
  }
  return new Result(created,completed,conflicts);
 }
}
