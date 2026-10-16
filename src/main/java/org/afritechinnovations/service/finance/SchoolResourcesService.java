package org.afritechinnovations.service.finance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.finance.SchoolResourcesDto.*;
import org.afritechinnovations.model.common.SchoolStatus;
import org.afritechinnovations.model.common.StaffModule;
import org.afritechinnovations.repository.common.SchoolRepository;
import org.afritechinnovations.security.SchoolPermissions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional
public class SchoolResourcesService {
    private final JdbcTemplate jdbc;
    private final SchoolRepository schools;
    private final SchoolPermissions permissions;
    private final ObjectMapper mapper;

    private void authorize(Long schoolId, Long actor, boolean admin, StaffModule module, boolean write) {
        var school = schools.findById(schoolId).orElseThrow(() -> new IllegalArgumentException("Établissement introuvable."));
        if (!admin && (school.getOwner() == null || !school.getOwner().getId().equals(actor))
                && !permissions.staffAllows(schoolId, actor, module))
            throw new AccessDeniedException("Vous n’avez pas accès à ce module de cet établissement.");
        if (write && school.getStatus() != SchoolStatus.ACTIVE)
            throw new IllegalArgumentException("Activez l’établissement avant de gérer son patrimoine ou ses stocks.");
    }

    @Transactional(readOnly=true)
    public AssetsOverview assets(Long schoolId, Long actor, boolean admin) {
        authorize(schoolId, actor, admin, StaffModule.ASSETS, false);
        return new AssetsOverview(jdbc.query("SELECT * FROM school_assets WHERE school_id=? ORDER BY name,id", this::assetRow, schoolId));
    }

    public Asset saveAsset(Long schoolId, Long id, AssetInput r, Long actor, boolean admin) {
        authorize(schoolId, actor, admin, StaffModule.ASSETS, true);
        if (id == null) {
            id = jdbc.queryForObject("""
                INSERT INTO school_assets(school_id,code,name,category,location,custodian,ownership,condition,status,
                  quantity,unit_value,acquired_on,maintenance_on,notes) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id
                """, Long.class, schoolId, code(r.code()), r.name().trim(), r.category().name(), r.location().trim(),
                    clean(r.custodian()), r.ownership().name(), r.condition().name(), r.status().name(), r.quantity(),
                    r.unitValue(), r.acquiredOn(), r.maintenanceOn(), clean(r.notes()));
            assetEvent(schoolId, id, "CREATE", "Création de la fiche", actor);
        } else {
            if (r.version() == null || clean(r.changeReason()) == null)
                throw new IllegalArgumentException("Précisez le motif de la modification.");
            int changed = jdbc.update("""
                UPDATE school_assets SET code=?,name=?,category=?,location=?,custodian=?,ownership=?,condition=?,status=?,
                  quantity=?,unit_value=?,acquired_on=?,maintenance_on=?,notes=?,version=version+1,updated_at=now()
                WHERE school_id=? AND id=? AND version=?
                """, code(r.code()), r.name().trim(), r.category().name(), r.location().trim(), clean(r.custodian()),
                    r.ownership().name(), r.condition().name(), r.status().name(), r.quantity(), r.unitValue(),
                    r.acquiredOn(), r.maintenanceOn(), clean(r.notes()), schoolId, id, r.version());
            if (changed != 1) throw new IllegalArgumentException("Cette fiche a changé ou n’est plus accessible. Actualisez avant de réessayer.");
            assetEvent(schoolId, id, "UPDATE", r.changeReason().trim(), actor);
        }
        return requireAsset(schoolId, id);
    }

    private Asset requireAsset(Long schoolId, Long id) {
        return jdbc.query("SELECT * FROM school_assets WHERE school_id=? AND id=?", this::assetRow, schoolId, id)
                .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("Bien introuvable dans cet établissement."));
    }

    private void assetEvent(Long schoolId, Long id, String type, String reason, Long actor) {
        try {
            jdbc.update("INSERT INTO school_asset_events(school_id,asset_id,event_type,reason,snapshot,created_by) VALUES(?,?,?,?,?::jsonb,?)",
                    schoolId, id, type, reason, mapper.writeValueAsString(requireAsset(schoolId, id)), actor);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Impossible de conserver l’historique du bien.", e);
        }
    }

    @Transactional(readOnly=true)
    public List<AssetEvent> assetHistory(Long schoolId, Long id, Long actor, boolean admin) {
        authorize(schoolId, actor, admin, StaffModule.ASSETS, false);
        requireAsset(schoolId, id);
        return jdbc.query("""
            SELECT e.*,concat(u.first_name,' ',u.last_name) actor FROM school_asset_events e
            JOIN users u ON u.id=e.created_by WHERE e.school_id=? AND e.asset_id=? ORDER BY e.id DESC LIMIT 200
            """, (rs,n) -> new AssetEvent(rs.getLong("id"), rs.getString("event_type"), rs.getString("reason"),
                rs.getString("snapshot"), rs.getString("actor"), rs.getTimestamp("created_at").toLocalDateTime()), schoolId, id);
    }

    @Transactional(readOnly=true)
    public StockOverview stock(Long schoolId, Long actor, boolean admin) {
        authorize(schoolId, actor, admin, StaffModule.INVENTORY, false);
        return new StockOverview(jdbc.query("SELECT * FROM school_stock_items WHERE school_id=? ORDER BY name,id", this::stockRow, schoolId));
    }

    public StockItem saveStockItem(Long schoolId, Long id, StockInput r, Long actor, boolean admin) {
        authorize(schoolId, actor, admin, StaffModule.INVENTORY, true);
        if (id == null) {
            id = jdbc.queryForObject("""
                INSERT INTO school_stock_items(school_id,code,name,category,unit,location,minimum_quantity,unit_cost,active,notes)
                VALUES(?,?,?,?,?,?,?,?,?,?) RETURNING id
                """, Long.class, schoolId, code(r.code()), r.name().trim(), r.category().name(), r.unit().trim(),
                    r.location().trim(), r.minimumQuantity(), r.unitCost(), r.active(), clean(r.notes()));
        } else {
            StockItem current = requireStockItem(schoolId, id, true);
            if (r.version() == null || r.version() != current.version())
                throw new IllegalArgumentException("Cette fiche a changé. Actualisez avant de réessayer.");
            if (!r.active() && current.quantity().signum() != 0)
                throw new IllegalArgumentException("Le stock doit être à zéro avant d’archiver cet article. Enregistrez d’abord sa sortie ou son inventaire.");
            if (!r.unit().trim().equals(current.unit()) && jdbc.queryForObject(
                    "SELECT count(*) FROM school_stock_movements WHERE school_id=? AND item_id=?", Long.class, schoolId, id) > 0)
                throw new IllegalArgumentException("L’unité ne peut plus changer après un mouvement. Créez un nouvel article pour une autre unité.");
            jdbc.update("""
                UPDATE school_stock_items SET code=?,name=?,category=?,unit=?,location=?,minimum_quantity=?,unit_cost=?,
                  active=?,notes=?,version=version+1,updated_at=now() WHERE school_id=? AND id=?
                """, code(r.code()), r.name().trim(), r.category().name(), r.unit().trim(), r.location().trim(),
                    r.minimumQuantity(), r.unitCost(), r.active(), clean(r.notes()), schoolId, id);
        }
        return requireStockItem(schoolId, id, false);
    }

    private StockItem requireStockItem(Long schoolId, Long id, boolean lock) {
        return jdbc.query("SELECT * FROM school_stock_items WHERE school_id=? AND id=?" + (lock ? " FOR UPDATE" : ""),
                        this::stockRow, schoolId, id).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Article introuvable dans cet établissement."));
    }

    public StockItem move(Long schoolId, Long id, MovementInput r, Long actor, boolean admin) {
        authorize(schoolId, actor, admin, StaffModule.INVENTORY, true);
        StockItem item = requireStockItem(schoolId, id, true);
        BigDecimal quantity = quantity(r.quantity());
        var previous = jdbc.queryForList("SELECT * FROM school_stock_movements WHERE school_id=? AND request_id=?", schoolId, r.requestId());
        if (!previous.isEmpty()) {
            var row = previous.getFirst();
            if (((Number)row.get("item_id")).longValue() != id || !r.kind().name().equals(row.get("kind"))
                    || quantity.compareTo((BigDecimal)row.get("requested_quantity")) != 0
                    || !r.date().equals(((java.sql.Date)row.get("movement_date")).toLocalDate())
                    || !r.reason().trim().equals(row.get("reason")) || !Objects.equals(clean(r.recipient()), row.get("recipient"))
                    || !Objects.equals(clean(r.reference()), row.get("reference")))
                throw new IllegalArgumentException("Cette référence correspond déjà à un autre mouvement. Actualisez le formulaire.");
            return item;
        }
        if (!item.active()) throw new IllegalArgumentException("Réactivez cet article avant d’enregistrer un mouvement.");
        if (r.date().isAfter(LocalDate.now())) throw new IllegalArgumentException("La date ne peut pas être dans le futur.");
        if (r.kind() != MovementKind.COUNT && quantity.signum() == 0)
            throw new IllegalArgumentException("La quantité du mouvement doit être supérieure à zéro.");
        if (r.kind() == MovementKind.OUT && clean(r.recipient()) == null)
            throw new IllegalArgumentException("Indiquez le destinataire ou service de la sortie.");
        BigDecimal delta = switch (r.kind()) {
            case IN -> quantity;
            case OUT -> quantity.negate();
            case COUNT -> quantity.subtract(item.quantity());
        };
        BigDecimal balance = quantity(item.quantity().add(delta));
        jdbc.update("""
            INSERT INTO school_stock_movements(school_id,item_id,request_id,kind,movement_date,requested_quantity,
              quantity_change,balance_after,reason,recipient,reference,created_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
            """, schoolId, id, r.requestId(), r.kind().name(), r.date(), quantity, delta, balance, r.reason().trim(),
                clean(r.recipient()), clean(r.reference()), actor);
        jdbc.update("UPDATE school_stock_items SET quantity=?,version=version+1,updated_at=now() WHERE school_id=? AND id=?", balance, schoolId, id);
        return requireStockItem(schoolId, id, false);
    }

    @Transactional(readOnly=true)
    public List<StockMovement> movements(Long schoolId, Long id, Long actor, boolean admin) {
        authorize(schoolId, actor, admin, StaffModule.INVENTORY, false);
        requireStockItem(schoolId, id, false);
        return jdbc.query("""
            SELECT m.*,concat(u.first_name,' ',u.last_name) actor FROM school_stock_movements m JOIN users u ON u.id=m.created_by
            WHERE m.school_id=? AND m.item_id=? ORDER BY m.id DESC LIMIT 200
            """, (rs,n) -> new StockMovement(rs.getLong("id"), MovementKind.valueOf(rs.getString("kind")),
                rs.getDate("movement_date").toLocalDate(), rs.getBigDecimal("requested_quantity"),
                rs.getBigDecimal("quantity_change"), rs.getBigDecimal("balance_after"), rs.getString("reason"),
                rs.getString("recipient"), rs.getString("reference"), rs.getString("actor"),
                rs.getTimestamp("created_at").toLocalDateTime()), schoolId, id);
    }

    static BigDecimal quantity(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.compareTo(new BigDecimal("99999999999.999")) > 0)
            throw new IllegalArgumentException("Stock insuffisant ou quantité hors limites.");
        try { return value.setScale(3, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException e) { throw new IllegalArgumentException("La quantité accepte au maximum trois décimales."); }
    }
    private static String code(String value) { return value.trim().toUpperCase(Locale.ROOT); }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private Asset assetRow(ResultSet r, int n) throws SQLException {
        return new Asset(r.getLong("id"), r.getString("code"), r.getString("name"), AssetCategory.valueOf(r.getString("category")),
                r.getString("location"), r.getString("custodian"), Ownership.valueOf(r.getString("ownership")),
                AssetCondition.valueOf(r.getString("condition")), AssetStatus.valueOf(r.getString("status")), r.getInt("quantity"),
                r.getBigDecimal("unit_value"), r.getObject("acquired_on", LocalDate.class), r.getObject("maintenance_on", LocalDate.class),
                r.getString("notes"), r.getLong("version"));
    }
    private StockItem stockRow(ResultSet r, int n) throws SQLException {
        return new StockItem(r.getLong("id"), r.getString("code"), r.getString("name"), StockCategory.valueOf(r.getString("category")),
                r.getString("unit"), r.getString("location"), r.getBigDecimal("minimum_quantity"), r.getBigDecimal("quantity"),
                r.getBigDecimal("unit_cost"), r.getBoolean("active"), r.getString("notes"), r.getLong("version"));
    }
}
