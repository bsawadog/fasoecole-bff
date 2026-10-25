package org.afritechinnovations.service.academic;
import org.afritechinnovations.model.academic.Subject;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.repository.academic.SubjectRepository;
import org.afritechinnovations.repository.common.SchoolRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class SubjectCatalogTest {
 @Test void generalCatalogHasEveryRequestedCodeAndCategory(){
  var all=SubjectCatalog.pack("SECONDARY").subjects();assertEquals(15,all.size());
  assertEquals(Set.of("FRA","MAT","ANG","ALL","ESP","HGE","PHI","SVT","PCH","ECM","EPS","INF","SES","APL","MUS"),all.stream().map(SubjectCatalog.Entry::code).collect(java.util.stream.Collectors.toSet()));
  assertTrue(all.stream().allMatch(e->!e.category().isBlank()));
 }
 @Test void technicalCodesHaveOneMeaningAndSharedRdmIsIdentical(){
  Map<String,SubjectCatalog.Entry> codes=new HashMap<>();
  for(var pack:SubjectCatalog.PACKS)for(var entry:pack.subjects())if(codes.putIfAbsent(entry.code(),entry)!=null)assertEquals(codes.get(entry.code()),entry);
  assertEquals("Secrétariat",codes.get("SEC").name());assertEquals("Sécurité de chantier",codes.get("SCT").name());assertEquals(64,codes.size());
 }
 @Test void applicationCompletesExistingSubjectsWithoutOverwritingCoefficientsAndIsRepeatable(){
  var schools=mock(SchoolRepository.class);var subjects=mock(SubjectRepository.class);var school=School.builder().id(1L).build();
  when(schools.lockById(1L)).thenReturn(Optional.of(school));
  var existing=new ArrayList<Subject>();existing.add(Subject.builder().id(1L).school(school).name("Francais").coefficient(new BigDecimal("3")).build());
  when(subjects.findBySchoolId(1L)).thenAnswer(a->new ArrayList<>(existing));
  when(subjects.save(any(Subject.class))).thenAnswer(a->{Subject value=a.getArgument(0);if(!existing.contains(value))existing.add(value);return value;});
  var service=new SubjectCatalogService(schools,subjects);var first=service.apply(1L,"SECONDARY");
  assertEquals(14,first.created());assertEquals(1,first.completed());assertTrue(first.conflicts().isEmpty());
  assertEquals("FRA",existing.getFirst().getCode());assertEquals(new BigDecimal("3"),existing.getFirst().getCoefficient());
  var second=service.apply(1L,"SECONDARY");assertEquals(0,second.created());assertEquals(0,second.completed());assertEquals(15,existing.size());
 }
 @Test void conflictingCodeIsReportedAndNeverReassigned(){
  var schools=mock(SchoolRepository.class);var subjects=mock(SubjectRepository.class);var school=School.builder().id(1L).build();when(schools.lockById(1L)).thenReturn(Optional.of(school));
  var existing=Subject.builder().school(school).name("Matière personnalisée").code("FRA").category("Personnalisée").build();when(subjects.findBySchoolId(1L)).thenReturn(List.of(existing));when(subjects.save(any(Subject.class))).thenAnswer(a->a.getArgument(0));
  var result=new SubjectCatalogService(schools,subjects).apply(1L,"SECONDARY");assertEquals(1,result.conflicts().size());assertEquals(14,result.created());assertEquals("Matière personnalisée",existing.getName());
 }
 @Test void applyingTwoTechnicalCatalogsDoesNotDuplicateStrengthOfMaterials(){
  var schools=mock(SchoolRepository.class);var subjects=mock(SubjectRepository.class);when(schools.lockById(1L)).thenReturn(Optional.of(School.builder().id(1L).build()));
  var existing=new ArrayList<Subject>();when(subjects.findBySchoolId(1L)).thenAnswer(a->new ArrayList<>(existing));when(subjects.save(any(Subject.class))).thenAnswer(a->{Subject value=a.getArgument(0);if(!existing.contains(value))existing.add(value);return value;});
  var service=new SubjectCatalogService(schools,subjects);service.apply(1L,"MECHANICAL");var second=service.apply(1L,"BUILDING");assertEquals(8,second.created());assertEquals(17,existing.size());assertEquals(1,existing.stream().filter(s->"RDM".equals(s.getCode())).count());
 }
 @Test void rejectsUnknownCatalogBeforeAnyDatabaseMutation(){var schools=mock(SchoolRepository.class);var subjects=mock(SubjectRepository.class);assertThrows(IllegalArgumentException.class,()->new SubjectCatalogService(schools,subjects).apply(1L,"UNKNOWN"));verifyNoInteractions(schools,subjects);}
}
