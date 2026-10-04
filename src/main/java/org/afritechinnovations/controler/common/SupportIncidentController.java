package org.afritechinnovations.controler.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.security.AccessGuard;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.*;

@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
@Transactional
public class SupportIncidentController {
 private final JdbcTemplate jdbc;
 private final AccessGuard guard;
 @ExceptionHandler(ResponseStatusException.class)
 public ResponseEntity<Map<String,Object>> invalid(ResponseStatusException exception) {
  return ResponseEntity.status(exception.getStatusCode()).body(Map.of("status",exception.getStatusCode().value(),"message",Objects.requireNonNullElse(exception.getReason(),"Requête impossible")));
 }
 public record Request(@NotNull Long schoolId, @NotBlank @Size(max=160) String title,
   @NotBlank @Size(max=200) String pageTitle, @NotBlank @Size(max=8000) String description) {}
 public record Action(@NotBlank String action, @NotNull @Size(max=8000) String note) {}
 private static final String SELECT = "SELECT i.id,i.school_id,i.reporter_id,i.title,i.page_title,i.description,i.status,i.assigned_to,i.created_at,i.updated_at,(i.image IS NOT NULL) AS has_image,s.name AS school_name FROM support_incidents i JOIN schools s ON s.id=i.school_id ";
 @GetMapping("/incidents/attention-count")
 public Long attentionCount() {
  if(guard.isSuperAdmin()) return jdbc.queryForObject("SELECT count(*) FROM support_incidents WHERE status='OPEN'",Long.class);
  return jdbc.queryForObject("SELECT count(*) FROM support_incidents i JOIN schools s ON s.id=i.school_id WHERE s.owner_id=? AND i.status='RETEST_REQUESTED'",Long.class,guard.currentUserId());
 }
 private void owner(long school) {
  var ids=jdbc.queryForList("SELECT owner_id FROM schools WHERE id=?",Long.class,school);
  if(ids.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Établissement introuvable");
  if(!guard.isSuperAdmin() && !ids.getFirst().equals(guard.currentUserId())) throw new AccessDeniedException("Établissement inaccessible");
 }
 private Map<String,Object> incident(long id, boolean lock) {
  var rows=jdbc.queryForList(SELECT+"WHERE i.id=?"+(lock?" FOR UPDATE OF i":""),id);
  if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Incident introuvable");
  owner(((Number)rows.getFirst().get("school_id")).longValue()); return rows.getFirst();
 }
 @GetMapping("/incidents")
 public List<Map<String,Object>> list(@RequestParam(defaultValue="0") @Min(0) int page,
   @RequestParam(defaultValue="") @Size(max=160) String search) {
  String scope=guard.isSuperAdmin()?"":" AND s.owner_id="+guard.currentUserId();
  return jdbc.queryForList(SELECT+"WHERE (i.title ILIKE ? OR s.name ILIKE ?)"+scope+" ORDER BY i.updated_at DESC,i.id DESC LIMIT 30 OFFSET ?","%"+search+"%","%"+search+"%",Math.max(0,page)*30L);
 }
 @GetMapping("/incidents/{id}")
 public Map<String,Object> detail(@PathVariable long id) {
  var result=new HashMap<>(incident(id,false));
  result.put("events",jdbc.queryForList("SELECT e.action,e.note,e.created_at,u.first_name,u.last_name FROM support_incident_events e JOIN users u ON u.id=e.actor_id WHERE e.incident_id=? ORDER BY e.id",id)); return result;
 }
 @PostMapping(value="/incidents",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
 public Map<String,Object> create(@Valid @RequestPart("request") Request request,@RequestPart(value="image",required=false) MultipartFile image) throws Exception {
  owner(request.schoolId()); byte[] bytes=null; String type=null;
  if(image!=null&&!image.isEmpty()) {
   if(image.getSize()>2*1024*1024) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Image limitée à 2 Mo");
   bytes=image.getBytes();
   try(var stream=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
    var readers=ImageIO.getImageReaders(stream);
    if(!readers.hasNext()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Image PNG ou JPEG requise");
    var reader=readers.next();
    try { reader.setInput(stream); String format=reader.getFormatName().toLowerCase(Locale.ROOT);
     if(!Set.of("png","jpeg","jpg").contains(format)||reader.getWidth(0)>8000||reader.getHeight(0)>8000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Image PNG/JPEG, dimensions maximales 8000 pixels");
     type=format.equals("png")?"image/png":"image/jpeg";
    } finally {reader.dispose();}
   }
  }
  Long id=jdbc.queryForObject("INSERT INTO support_incidents(school_id,reporter_id,title,page_title,description,image,image_type) VALUES(?,?,?,?,?,?,?) RETURNING id",Long.class,request.schoolId(),guard.currentUserId(),request.title().strip(),request.pageTitle().strip(),request.description().strip(),bytes,type);
  event(id,"OPEN","Incident déclaré"); notifyAdmins(id,"Nouvel incident : "+request.title()); return detail(id);
 }
 @PostMapping("/incidents/{id}/actions")
 public Map<String,Object> action(@PathVariable long id,@Valid @RequestBody Action action) {
  var row=incident(id,true); String state=(String)row.get("status"); String next;
  switch(action.action()) {
   case "CLAIM" -> {guard.requireSuperAdmin(); require(state.equals("OPEN")); next="IN_PROGRESS"; jdbc.update("UPDATE support_incidents SET assigned_to=? WHERE id=?",guard.currentUserId(),id);}
   case "RETEST" -> {guard.requireSuperAdmin(); require(state.equals("IN_PROGRESS")&&!action.note().isBlank()); next="RETEST_REQUESTED";}
   case "CLOSE" -> {require(!state.equals("CLOSED")); next="CLOSED";}
   case "REOPEN" -> {require(Set.of("CLOSED","RETEST_REQUESTED").contains(state)&&!action.note().isBlank()); next="OPEN";}
   case "COMMENT" -> {require(!state.equals("CLOSED")&&!action.note().isBlank()); next=state;}
   default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Action inconnue");
  }
  jdbc.update("UPDATE support_incidents SET status=?,updated_at=now() WHERE id=?",next,id); event(id,action.action(),action.note().strip());
  if(guard.isSuperAdmin()) notifyUser(((Number)row.get("reporter_id")).longValue(),id,"Incident « "+row.get("title")+" » : "+label(next));
  else notifyAdmins(id,"Incident « "+row.get("title")+" » : "+label(next));
  return detail(id);
 }
 private void require(boolean condition) {if(!condition) throw new ResponseStatusException(HttpStatus.CONFLICT,"État de l'incident modifié ou commentaire obligatoire. Actualisez la page.");}
 private String label(String state) {return switch(state){case "OPEN"->"ouvert";case "IN_PROGRESS"->"pris en charge";case "RETEST_REQUESTED"->"à retester";default->"fermé";};}
 private void event(long id,String action,String note) {
  jdbc.update("INSERT INTO support_incident_events(incident_id,actor_id,action,note) VALUES(?,?,?,?)",id,guard.currentUserId(),action,note);
  jdbc.update("INSERT INTO audit_logs(user_id,action,entity,entity_id) VALUES(?,?,?,?)",guard.currentUserId(),"SUPPORT_"+action,"SUPPORT_INCIDENT",id);
 }
 private void notifyUser(long user,long id,String content) {jdbc.update("INSERT INTO notifications(user_id,title,content,is_read,created_at) VALUES(?,?,?,false,now())",user,"Suivi de l'incident #"+id,content);}
 private void notifyAdmins(long id,String content) {jdbc.queryForList("SELECT p.user_id FROM user_platform_roles p JOIN users u ON u.id=p.user_id WHERE p.role='SUPER_ADMIN' AND u.active=true",Long.class).forEach(user->notifyUser(user,id,content));}
 @GetMapping("/incidents/{id}/image")
 public ResponseEntity<byte[]> image(@PathVariable long id) {
  incident(id,false); var data=jdbc.queryForMap("SELECT image,image_type FROM support_incidents WHERE id=?",id);
  if(data.get("image")==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  return ResponseEntity.ok().header("Cache-Control","private, no-store").header("X-Content-Type-Options","nosniff").contentType(MediaType.parseMediaType((String)data.get("image_type"))).body((byte[])data.get("image"));
 }
 @PostMapping("/school-access/{id}")
 public void access(@PathVariable long id) {
  guard.requireSuperAdmin(); owner(id);
  jdbc.update("INSERT INTO audit_logs(user_id,action,entity,entity_id) VALUES(?,?,?,?)",guard.currentUserId(),"PLATFORM_SCHOOL_ACCESS","SCHOOL",id);
 }
}
