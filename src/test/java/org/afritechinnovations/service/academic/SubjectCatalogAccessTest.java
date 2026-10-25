package org.afritechinnovations.service.academic;
import org.afritechinnovations.controler.academic.SubjectController;
import org.afritechinnovations.model.common.StaffModule;
import org.afritechinnovations.security.AccessGuard;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SubjectCatalogAccessTest {
 @Test void rejectsCatalogMutationBeforeServiceWhenSchoolManagementAccessIsDenied(){
  var guard=mock(AccessGuard.class);var catalog=mock(SubjectCatalogService.class);
  doThrow(new AccessDeniedException("denied")).when(guard).requireSchoolModule(2L,StaffModule.MANAGEMENT);
  var controller=new SubjectController(mock(SubjectService.class),guard,catalog);
  assertThrows(AccessDeniedException.class,()->controller.applyCatalog("SECONDARY",2L));verifyNoInteractions(catalog);
 }
}
