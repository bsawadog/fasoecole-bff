package org.afritechinnovations.controler.common;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.service.common.SchoolDataSyncService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

@RestController
@RequestMapping("/api/schools")
@RequiredArgsConstructor
public class SchoolDataSyncController {
    private final AccessGuard guard;
    private final SchoolDataSyncService service;
    @GetMapping("/{schoolId}/changes")
    public DeferredResult<SchoolDataSyncService.Revision> changes(@PathVariable Long schoolId,@RequestParam(required=false) Long since) {
        guard.requireSchoolMember(schoolId);
        return service.watch(schoolId,since);
    }
}
