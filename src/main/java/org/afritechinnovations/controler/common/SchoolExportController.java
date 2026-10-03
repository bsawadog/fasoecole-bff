package org.afritechinnovations.controler.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.service.export.SchoolExportService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/owner/export")
@RequiredArgsConstructor
public class SchoolExportController {
    private final SchoolExportService service;
    @GetMapping("/schools")
    public List<SchoolExportService.SchoolChoice> schools() { return service.choices(); }

    @GetMapping("/schools/{schoolId}/excel")
    public ResponseEntity<StreamingResponseBody> excel(@PathVariable Long schoolId) throws IOException {
        return download(service.create(schoolId,false));
    }
    @GetMapping("/schools/{schoolId}/archive")
    public ResponseEntity<StreamingResponseBody> archive(@PathVariable Long schoolId) throws IOException {
        return download(service.create(schoolId,true));
    }
    private ResponseEntity<StreamingResponseBody> download(SchoolExportService.ExportFile file) throws IOException {
        StreamingResponseBody body=output->{
            try { Files.copy(file.path(),output);output.flush(); }
            finally { Files.deleteIfExists(file.path()); }
        };
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(Files.size(file.path())).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(file.filename(),StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options","nosniff").body(body);
    }
}
