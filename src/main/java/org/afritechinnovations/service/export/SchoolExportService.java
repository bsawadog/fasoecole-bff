package org.afritechinnovations.service.export;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.zip.*;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.repository.common.SchoolRepository;
import org.afritechinnovations.security.AccessGuard;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SchoolExportService {
    private final SchoolRepository schools;
    private final JdbcTemplate jdbc;
    private final AccessGuard guard;
    public record SchoolChoice(Long id,String name,String status) { }
    public record ExportFile(Path path,String filename,String contentType) { }

    @Transactional(readOnly=true)
    public List<SchoolChoice> choices() {
        return (guard.isSuperAdmin()?schools.findAll():schools.findByOwnerId(guard.currentUserId())).stream()
                .map(s->new SchoolChoice(s.getId(),s.getName(),s.getStatus().name())).toList();
    }

    /** One repeatable-read snapshot: totals and details cannot come from different moments. */
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ExportFile create(Long schoolId,boolean attachments) throws IOException {
        School school=schools.findById(schoolId).orElseThrow(()->new IllegalArgumentException("Établissement introuvable"));
        if(!guard.isSuperAdmin() && !school.getOwner().getId().equals(guard.currentUserId()))
            throw new AccessDeniedException("L’export complet est réservé au propriétaire de cet établissement");
        // Export remains available to the owner after suspension, independently of delegated modules.
        LocalDateTime created=LocalDateTime.now();
        String base="donnees_etablissement_"+created.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        Path workbook=Files.createTempFile("fasoecole-export-",".xlsx");
        Path archive=null;
        workbook.toFile().deleteOnExit();
        try {
            try(ExcelArchiveWriter excel=new ExcelArchiveWriter(workbook)) {
                guide(excel,school,created,attachments);
                for(var data:SchoolExportCatalog.SHEETS) writeSheet(excel,data,schoolId);
                excel.finish();
            }
            if(!attachments) return new ExportFile(workbook,base+".xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            archive=Files.createTempFile("fasoecole-export-",".zip");
            archive.toFile().deleteOnExit();
            try(var zip=new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(archive)),StandardCharsets.UTF_8)) {
                zip.putNextEntry(new ZipEntry(base+".xlsx"));Files.copy(workbook,zip);zip.closeEntry();
                zip.putNextEntry(new ZipEntry("LIRE_MOI.txt"));
                zip.write(("Export de "+school.getName()+"\nDate : "+created+"\n\nOuvrir le classeur Excel pour consulter les données de toutes les années.\n"
                        +"Les chemins des documents figurent dans Documents portail et Pièces jointes messages.\n"
                        +"Les liens de Références documents sont externes : leur contenu n’est pas inclus dans l’archive.\n"
                        +"Les conversations privées sans participation de l’établissement ou de son propriétaire ne sont pas exportées.\n"
                        +"Les factures originales constituent les dettes ; les impayés reportés ne sont pas de nouvelles factures.\n")
                        .getBytes(StandardCharsets.UTF_8));zip.closeEntry();
                writeFiles(zip,"SELECT "+SchoolExportCatalog.PORTAL_PATH+" path,f.data FROM parent_portal_files f JOIN parent_portal_posts p ON p.id=f.post_id WHERE p.school_id=? ORDER BY f.id",schoolId);
                writeFiles(zip,"SELECT "+SchoolExportCatalog.MESSAGE_PATH+" path,b.data FROM conversation_attachments f JOIN conversation_attachment_content b ON b.id=f.id JOIN school_conversation_messages m ON m.id=f.message_id JOIN school_conversations c ON c.id=m.conversation_id WHERE c.school_id=? AND "+SchoolExportCatalog.VISIBLE_CONVERSATION+" ORDER BY f.id",schoolId);
            }
            Files.deleteIfExists(workbook);
            return new ExportFile(archive,base+".zip","application/zip");
        } catch(IOException|RuntimeException e) {
            Files.deleteIfExists(workbook);
            if(archive!=null) Files.deleteIfExists(archive);
            throw e;
        }
    }
    private void writeSheet(ExcelArchiveWriter excel,SchoolExportCatalog.DataSheet sheet,Long schoolId) {
        jdbc.query(connection->{
            PreparedStatement statement=connection.prepareStatement(sheet.sql());
            statement.setLong(1,schoolId);statement.setFetchSize(500);return statement;
        },(ResultSetExtractor<Void>) rs->{
            try {
                ResultSetMetaData metadata=rs.getMetaData();int count=metadata.getColumnCount();String[] columns=new String[count];
                for(int i=0;i<count;i++) columns[i]=metadata.getColumnLabel(i+1);
                excel.sheet(sheet.name(),columns);
                while(rs.next()) {
                    Object[] values=new Object[count];for(int i=0;i<count;i++) values[i]=rs.getObject(i+1);
                    excel.row(values);
                }
            }catch(IOException e){throw new UncheckedIOException(e);}
            return null;
        });
    }
    private void writeFiles(ZipOutputStream zip,String sql,Long schoolId) {
        jdbc.query(connection->{
            PreparedStatement statement=connection.prepareStatement(sql);statement.setLong(1,schoolId);statement.setFetchSize(1);return statement;
        },(ResultSetExtractor<Void>) rs->{
            while(rs.next()) try {
                zip.putNextEntry(new ZipEntry(rs.getString("path")));
                try(InputStream data=rs.getBinaryStream("data")) { if(data==null) throw new IOException("Un fichier joint n’a plus de contenu");data.transferTo(zip); }
                zip.closeEntry();
            }catch(IOException e){throw new UncheckedIOException(e);}
            return null;
        });
    }
    private void guide(ExcelArchiveWriter excel,School school,LocalDateTime date,boolean attachments) throws IOException {
        excel.sheet("À lire","Rubrique","Information");
        excel.row("Établissement",school.getName());excel.row("ID établissement",school.getId());excel.row("Export effectué",date);
        excel.row("Périmètre","Toutes les années, y compris les années clôturées. Aucun filtre de l’écran ne réduit l’export.");
        excel.row("Lecture","Utilisez les onglets en bas du classeur. La feuille Sommaire indique le nombre de lignes de chaque feuille. La première ligne peut être filtrée.");
        excel.row("Identités","Les matricules, numéros d’employé et téléphones restent du texte ; leurs zéros initiaux sont conservés. Les noms accompagnent les identifiants de liaison.");
        excel.row("Cohérence","Les feuilles proviennent d’un même instantané de la base. Les paiements ultérieurs ne modifieront pas ce fichier.");
        excel.row("Finances","Montants tels qu’enregistrés, sans conversion de devise. Les factures annulées ont un reste à payer de zéro. Les impayés reportés référencent les factures originales : ne pas additionner deux fois ces dettes.");
        excel.row("Notes","Les notes et bulletins enregistrés sont conservés. Les feuilles Évaluations, Affectations enseignants et Matières fournissent les barèmes, poids et coefficients.");
        excel.row("Textes longs","Si un texte dépasse la limite d’une cellule Excel, son contenu intégral est réparti dans Textes longs. Reconstituer les parties dans leur ordre.");
        excel.row("Fichiers joints",attachments?"Inclus dans les dossiers documents du ZIP. Les chemins sont indiqués dans les feuilles du classeur.":"Le classeur contient leur inventaire. Choisissez l’archive ZIP pour récupérer aussi les fichiers joints.");
        excel.row("Liens externes","Références documents conserve les liens externes, sans télécharger leur contenu. Ils peuvent dépendre d’un autre service.");
        excel.row("Messagerie","Conversations auxquelles l’établissement ou son propriétaire participe. Les échanges privés sans cette participation sont exclus.");
        excel.row("Comptes","Aucun mot de passe, jeton de connexion ou secret d’authentification n’est inclus.");
        excel.row("Dates et heures","Dates et heures enregistrées par le serveur ; fuseau de génération : "+ZoneId.systemDefault());
        excel.row("Données disponibles","Seules les données encore conservées dans la base peuvent être récupérées. L’export n’est pas un outil de restauration de compte ou de réimport automatique.");
        excel.sheet("Légende des codes","Code","Signification");
        String[][] codes={
            {"ACTIVE","Actif"},{"SUSPENDED","Désactivé"},{"ARCHIVED","Archivé"},{"DRAFT","En création"},{"PENDING_APPROVAL","En attente de validation"},
            {"COMPLETED","Inscription clôturée"},{"TRANSFERRED","Transféré"},{"GRADUATED","Diplômé / cycle terminé"},{"DROPPED","Inscription abandonnée"},
            {"PROMOTED","Passage"},{"REPEATED","Redoublement"},{"LEFT","Départ"},{"PRESENT","Présent"},{"ABSENT","Absent"},{"LATE","Retard"},{"EXCUSED","Absence justifiée"},
            {"PENDING","En attente"},{"ACKNOWLEDGED","Signalement enregistré"},{"REJECTED","Rejeté"},{"ACCEPTED","Accepté"},{"APPROVED","Approuvé"},
            {"CANCELLED","Annulé"},{"PAID","Payé"},{"OVERDUE","Échéance dépassée"},{"OPEN","Période ouverte"},{"LOCKED","Période verrouillée"},{"PUBLISHED","Période publiée"},
            {"HOURLY","Taux horaire"},{"MONTHLY","Mensuel"},{"ONE_TIME","Une fois"},{"TERM","Par période"},{"YEARLY","Annuel"},
            {"CASH","Espèces / caisse"},{"BANK","Banque"},{"BANK_TRANSFER","Virement bancaire"},{"MOBILE_MONEY","Paiement mobile"},{"CARD","Carte"},
            {"TEACHER","Enseignant"},{"STUDENT","Élève / étudiant"},{"PARENT","Parent"},{"SCHOOL_ADMIN","Propriétaire"},{"STAFF","Personnel"},
            {"ANNOUNCEMENT","Annonce"},{"HOMEWORK","Devoir"},{"DOCUMENT","Document"},{"CREATE","Création"},{"UPDATE","Modification"},{"DELETE","Suppression"}
        };
        for(String[] code:codes) excel.row((Object[])code);
    }
}
