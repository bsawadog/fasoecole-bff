package org.afritechinnovations.service.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.DeferredResult;
import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Les révisions deviennent visibles uniquement après validation de la transaction PostgreSQL. */
@Service
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class SchoolDataSyncService {
    private final JdbcTemplate jdbc;
    public record Revision(long revision, LocalDate date) {}
    private record Pending(long since, DeferredResult<Revision> result) {}
    private final ConcurrentHashMap<Long,CopyOnWriteArrayList<Pending>> waiting = new ConcurrentHashMap<>();

    public DeferredResult<Revision> watch(Long schoolId, Long since) {
        Revision current = revision(schoolId);
        DeferredResult<Revision> result = new DeferredResult<>(25_000L, current);
        if (since == null || since != current.revision()) { result.setResult(current); return result; }
        var pending = new Pending(since,result);
        waiting.compute(schoolId,(id,list) -> {
            if (list == null) list = new CopyOnWriteArrayList<>();
            list.add(pending); return list;
        });
        result.onCompletion(() -> waiting.computeIfPresent(schoolId,(id,list) -> { list.remove(pending); return list.isEmpty() ? null : list; }));
        result.onTimeout(() -> result.setResult(revision(schoolId)));
        return result;
    }

    @Scheduled(fixedDelay=1000)
    public void notifyChanges() {
        waiting.forEach((schoolId,list) -> {
            try {
                Revision current = revision(schoolId);
                for (Pending pending : list) if (pending.since()!=current.revision()) pending.result().setResult(current);
            } catch (RuntimeException exception) {
                log.warn("Impossible d'actualiser les données de l'établissement {}",schoolId,exception);
            }
        });
    }
    private Revision revision(Long schoolId) {
        Long value = jdbc.queryForObject("SELECT COALESCE((SELECT revision FROM school_data_revisions WHERE school_id=?),0)",Long.class,schoolId);
        return new Revision(value == null ? 0 : value,LocalDate.now());
    }
}
