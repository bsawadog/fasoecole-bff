package org.afritechinnovations.service.export;

import java.util.List;

/** Explicit business-data allowlist. Every query is scoped to exactly one school. */
final class SchoolExportCatalog {
    private SchoolExportCatalog() { }
    record DataSheet(String name,String sql) { }
    static final String VISIBLE_CONVERSATION="""
        EXISTS (SELECT 1 FROM conversation_participants cp JOIN schools cs ON cs.id=c.school_id
                WHERE cp.conversation_id=c.id AND (cp.school_id=c.school_id OR cp.user_id=cs.owner_id))
        """;
    static final String PORTAL_PATH="'documents/portail/' || f.id || '_' || regexp_replace(f.filename,'[^a-zA-Z0-9._-]','_','g')";
    static final String MESSAGE_PATH="'documents/messages/' || f.id || '_' || regexp_replace(f.filename,'[^a-zA-Z0-9._-]','_','g')";
    static final List<DataSheet> SHEETS=List.of(
        new DataSheet("Calendrier scolaire", """
            SELECT e.title "Titre",e.kind "Type",c.name "Classe",e.starts_on "Début",e.ends_on "Fin",e.description "Informations"
            FROM school_calendar_events e LEFT JOIN classes c ON c.id=e.class_id WHERE e.school_id=? ORDER BY e.starts_on,e.id
            """),
        new DataSheet("Discipline", """
            SELECT s.registration_number "Matricule",concat(u.first_name,' ',u.last_name) "Élève",o.kind "Type",
            o.observed_on "Date",o.description "Observation",o.action "Mesure prise",o.shared_with_family "Partagé",o.resolved "Traité"
            FROM student_observations o JOIN students s ON s.id=o.student_id JOIN users u ON u.id=s.user_id WHERE o.school_id=? ORDER BY o.id
            """),
        new DataSheet("Demandes administratives", """
            SELECT s.registration_number "Matricule",r.kind "Document",r.reason "Motif",r.status "Statut",r.response "Réponse",r.created_at "Demande",r.updated_at "Modification"
            FROM school_document_requests r JOIN students s ON s.id=r.student_id WHERE r.school_id=? ORDER BY r.id
            """),
        new DataSheet("Livres", "SELECT title \"Titre\",author \"Auteur\",reference \"Référence\",copies \"Exemplaires\" FROM library_books WHERE school_id=? ORDER BY id"),
        new DataSheet("Prêts bibliothèque", """
            SELECT b.title "Livre",b.reference "Référence",s.registration_number "Matricule",l.borrowed_on "Emprunt",l.due_on "Retour prévu",l.returned_on "Retour effectué"
            FROM library_loans l JOIN library_books b ON b.id=l.book_id JOIN students s ON s.id=l.student_id WHERE l.school_id=? ORDER BY l.id
            """),
        new DataSheet("Suivi devoirs", """
            SELECT p.title "Devoir",s.registration_number "Matricule",h.status "Statut",h.feedback "Retour",h.updated_at "Modification"
            FROM homework_progress h JOIN parent_portal_posts p ON p.id=h.post_id JOIN students s ON s.id=h.student_id WHERE h.school_id=? ORDER BY h.id
            """),
        new DataSheet("Établissement","""
            SELECT s.id "ID établissement",s.name "Nom",s.type "Type",s.status "Statut",s.address "Adresse",
            s.phone "Téléphone",s.email "Courriel",u.first_name "Prénom propriétaire",u.last_name "Nom propriétaire",
            u.email "Courriel propriétaire",u.phone "Téléphone propriétaire",s.created_at "Création",
            s.activated_at "Dernière activation",s.deactivated_at "Dernière désactivation",s.submitted_at "Soumis pour validation"
            FROM schools s JOIN users u ON u.id=s.owner_id WHERE s.id=?
            """),
        new DataSheet("Années scolaires","""
            SELECT y.id "ID année",y.label "Année",y.start_date "Début",y.end_date "Fin",y.is_current "Année courante",
            y.closed_at "Clôture",CONCAT_WS(' ',u.first_name,u.last_name) "Clôturée par",ny.label "Année suivante"
            FROM academic_years y LEFT JOIN users u ON u.id=y.closed_by
            LEFT JOIN academic_years ny ON ny.id=y.next_year_id AND ny.school_id=y.school_id WHERE y.school_id=? ORDER BY y.start_date,y.id
            """),
        new DataSheet("Niveaux","""
            SELECT id "ID niveau",name "Niveau",cycle "Cycle",order_index "Ordre" FROM levels WHERE school_id=? ORDER BY order_index,id
            """),
        new DataSheet("Classes","""
            SELECT c.id "ID classe",c.name "Classe",y.label "Année",l.name "Niveau",c.capacity "Capacité"
            FROM classes c JOIN academic_years y ON y.id=c.academic_year_id AND y.school_id=c.school_id
            JOIN levels l ON l.id=c.level_id AND l.school_id=c.school_id WHERE c.school_id=? ORDER BY y.start_date,c.name,c.id
            """),
        new DataSheet("Matières","""
            SELECT id "ID matière",name "Matière",code "Code",coefficient "Coefficient par défaut"
            FROM subjects WHERE school_id=? ORDER BY name,id
            """),
        new DataSheet("Élèves et étudiants","""
            SELECT s.id "ID élève",s.registration_number "Matricule",u.first_name "Prénom",u.last_name "Nom",
            s.birth_date "Naissance",s.gender "Genre",u.email "Courriel",u.phone "Téléphone",u.active "Compte actif",
            u.created_at "Création du compte" FROM students s JOIN users u ON u.id=s.user_id WHERE s.school_id=? ORDER BY u.last_name,u.first_name,s.id
            """),
        new DataSheet("Parents","""
            SELECT p.id "ID parent",u.first_name "Prénom",u.last_name "Nom",u.email "Courriel",u.phone "Téléphone",u.active "Compte actif"
            FROM parents p JOIN users u ON u.id=p.user_id JOIN schools scope ON scope.id=?
            WHERE EXISTS(SELECT 1 FROM parent_student ps JOIN students s ON s.id=ps.student_id WHERE ps.parent_id=p.id AND s.school_id=scope.id)
               OR EXISTS(SELECT 1 FROM school_users su JOIN roles r ON r.id=su.role_id WHERE su.user_id=u.id AND su.school_id=scope.id AND r.name='PARENT')
            ORDER BY u.last_name,u.first_name,p.id
            """),
        new DataSheet("Liens parents enfants","""
            SELECT ps.id "ID lien",p.id "ID parent",CONCAT_WS(' ',pu.first_name,pu.last_name) "Parent",pu.email "Courriel parent",
            pu.phone "Téléphone parent",s.id "ID élève",s.registration_number "Matricule",CONCAT_WS(' ',su.first_name,su.last_name) "Élève",ps.relationship "Lien familial"
            FROM parent_student ps JOIN parents p ON p.id=ps.parent_id JOIN users pu ON pu.id=p.user_id
            JOIN students s ON s.id=ps.student_id JOIN users su ON su.id=s.user_id WHERE s.school_id=? ORDER BY s.registration_number,ps.id
            """),
        new DataSheet("Enseignants","""
            SELECT t.id "ID enseignant",t.employee_number "Numéro employé",u.first_name "Prénom",u.last_name "Nom",u.email "Courriel",
            u.phone "Téléphone",t.specialty "Spécialité",t.hire_date "Embauche",u.active "Compte actif"
            FROM teachers t JOIN users u ON u.id=t.user_id WHERE t.school_id=? ORDER BY u.last_name,u.first_name,t.id
            """),
        new DataSheet("Employés","""
            SELECT st.id "ID employé",u.first_name "Prénom",u.last_name "Nom",u.email "Courriel",u.phone "Téléphone",
            st.job_title "Fonction",st.monthly_salary "Salaire mensuel",st.active "Employé actif",u.active "Compte actif",st.created_at "Création",st.updated_at "Modification",
            (SELECT STRING_AGG(m.module,', ' ORDER BY m.module) FROM school_staff_modules m WHERE m.staff_id=st.id) "Modules autorisés"
            FROM school_staff st JOIN users u ON u.id=st.user_id WHERE st.school_id=? ORDER BY u.last_name,u.first_name,st.id
            """),
        new DataSheet("Accès établissement","""
            SELECT su.id "ID accès",u.id "ID compte",u.first_name "Prénom",u.last_name "Nom",u.email "Courriel",u.phone "Téléphone",
            r.name "Rôle dans cet établissement",u.active "Compte actif",u.approved "Compte approuvé",su.created_at "Ajout"
            FROM school_users su JOIN users u ON u.id=su.user_id JOIN roles r ON r.id=su.role_id WHERE su.school_id=? ORDER BY u.last_name,u.first_name,su.id
            """),
        new DataSheet("Inscriptions et passages","""
            SELECT e.id "ID inscription",s.id "ID élève",s.registration_number "Matricule",CONCAT_WS(' ',u.first_name,u.last_name) "Élève",
            y.label "Année",c.name "Classe",l.name "Niveau",e.enrollment_date "Inscription",e.status "Statut",e.decision "Décision",
            e.decision_average "Moyenne de décision",e.decided_at "Décision enregistrée"
            FROM student_enrollments e JOIN students s ON s.id=e.student_id JOIN users u ON u.id=s.user_id
            JOIN academic_years y ON y.id=e.academic_year_id AND y.school_id=s.school_id
            JOIN classes c ON c.id=e.class_id AND c.school_id=s.school_id JOIN levels l ON l.id=c.level_id
            WHERE s.school_id=? ORDER BY y.start_date,c.name,s.registration_number,e.id
            """),
        new DataSheet("Affectations enseignants","""
            SELECT a.id "ID affectation",t.employee_number "Numéro employé",CONCAT_WS(' ',u.first_name,u.last_name) "Enseignant",
            y.label "Année",c.name "Classe",s.name "Matière",COALESCE(a.coefficient,s.coefficient) "Coefficient effectif",a.coefficient "Coefficient spécifique",a.active "Affectation active"
            FROM class_subject_teacher a JOIN classes c ON c.id=a.class_id JOIN academic_years y ON y.id=c.academic_year_id
            JOIN subjects s ON s.id=a.subject_id AND s.school_id=c.school_id JOIN teachers t ON t.id=a.teacher_id AND t.school_id=c.school_id
            JOIN users u ON u.id=t.user_id WHERE c.school_id=? ORDER BY y.start_date,c.name,s.name,a.id
            """),
        new DataSheet("Périodes","""
            SELECT p.id "ID période",y.label "Année",p.code "Code",p.name "Période",p.start_date "Début",p.end_date "Fin",
            p.pass_mark "Seuil de passage",p.status "Statut",p.published_at "Publication"
            FROM grade_periods p JOIN academic_years y ON y.id=p.academic_year_id AND y.school_id=p.school_id WHERE p.school_id=? ORDER BY y.start_date,p.start_date,p.id
            """),
        new DataSheet("Évaluations","""
            SELECT e.id "ID évaluation",y.label "Année",p.name "Période",c.name "Classe",s.name "Matière",
            CONCAT_WS(' ',u.first_name,u.last_name) "Enseignant",e.title "Évaluation",e.type "Type",e.eval_date "Date",e.max_value "Barème",e.weight "Poids"
            FROM evaluations e JOIN grade_periods p ON p.id=e.period_id JOIN class_subject_teacher a ON a.id=e.class_subject_teacher_id
            JOIN classes c ON c.id=a.class_id AND c.school_id=p.school_id JOIN academic_years y ON y.id=c.academic_year_id
            JOIN subjects s ON s.id=a.subject_id AND s.school_id=c.school_id JOIN teachers t ON t.id=a.teacher_id AND t.school_id=c.school_id
            JOIN users u ON u.id=t.user_id WHERE c.school_id=? ORDER BY y.start_date,c.name,e.eval_date,e.id
            """),
        new DataSheet("Notes","""
            SELECT g.id "ID note",s.registration_number "Matricule",CONCAT_WS(' ',u.first_name,u.last_name) "Élève",y.label "Année",
            c.name "Classe",sub.name "Matière",g.term "Période historique",p.name "Période",e.title "Évaluation",g.type "Type",
            g.value "Note",g.max_value "Barème",e.weight "Poids",g.grade_date "Date",g.appreciation "Appréciation"
            FROM grades g JOIN students s ON s.id=g.student_id JOIN users u ON u.id=s.user_id
            JOIN class_subject_teacher a ON a.id=g.class_subject_teacher_id JOIN classes c ON c.id=a.class_id AND c.school_id=s.school_id
            JOIN academic_years y ON y.id=c.academic_year_id JOIN subjects sub ON sub.id=a.subject_id AND sub.school_id=c.school_id
            LEFT JOIN evaluations e ON e.id=g.evaluation_id LEFT JOIN grade_periods p ON p.id=e.period_id AND p.school_id=c.school_id
            WHERE s.school_id=? ORDER BY y.start_date,c.name,s.registration_number,g.grade_date,g.id
            """),
        new DataSheet("Historique des notes","""
            SELECT h.id "ID historique",s.registration_number "Matricule",CONCAT_WS(' ',u.first_name,u.last_name) "Élève",
            y.label "Année",c.name "Classe",p.name "Période",h.evaluation_title "Évaluation",h.action "Action",h.old_value "Ancienne note",
            h.new_value "Nouvelle note",h.reason "Motif",h.changed_by_name "Auteur",h.changed_at "Date"
            FROM grade_history h JOIN students s ON s.id=h.student_id JOIN users u ON u.id=s.user_id
            LEFT JOIN classes c ON c.id=h.class_id AND c.school_id=s.school_id LEFT JOIN academic_years y ON y.id=c.academic_year_id
            LEFT JOIN grade_periods p ON p.id=h.period_id AND p.school_id=s.school_id WHERE s.school_id=? ORDER BY h.changed_at,h.id
            """),
        new DataSheet("Bulletins","""
            SELECT r.id "ID bulletin",s.registration_number "Matricule",CONCAT_WS(' ',u.first_name,u.last_name) "Élève",y.label "Année",
            c.name "Classe",r.term "Période historique",p.name "Période",r.average "Moyenne",r.rank "Rang",r.class_size "Effectif",
            r.mention "Mention",r.comment "Commentaire",r.validated "Validé",r.generated_at "Génération"
            FROM report_cards r JOIN students s ON s.id=r.student_id JOIN users u ON u.id=s.user_id
            JOIN academic_years y ON y.id=r.academic_year_id AND y.school_id=s.school_id
            LEFT JOIN classes c ON c.id=r.class_id AND c.school_id=s.school_id LEFT JOIN grade_periods p ON p.id=r.period_id AND p.school_id=s.school_id
            WHERE s.school_id=? ORDER BY y.start_date,s.registration_number,r.id
            """),
        new DataSheet("Présences","""
            SELECT a.id "ID présence",s.registration_number "Matricule",CONCAT_WS(' ',u.first_name,u.last_name) "Élève",
            y.label "Année",c.name "Classe",a.attendance_date "Date",a.status "Statut",a.justification "Justification"
            FROM attendances a JOIN students s ON s.id=a.student_id JOIN users u ON u.id=s.user_id
            JOIN classes c ON c.id=a.class_id AND c.school_id=s.school_id JOIN academic_years y ON y.id=c.academic_year_id
            WHERE s.school_id=? ORDER BY a.attendance_date,c.name,s.registration_number,a.id
            """),
        new DataSheet("Signalements","""
            SELECT r.id "ID signalement",s.registration_number "Matricule",CONCAT_WS(' ',u.first_name,u.last_name) "Élève",
            r.attendance_type "Type",r.start_date "Début",r.end_date "Fin",r.reason "Motif",r.status "Statut",
            CONCAT_WS(' ',pu.first_name,pu.last_name) "Signalé par",r.created_at "Signalement",r.school_comment "Commentaire établissement",
            CONCAT_WS(' ',hu.first_name,hu.last_name) "Traité par",r.handled_at "Traitement"
            FROM absence_reports r JOIN students s ON s.id=r.student_id AND s.school_id=r.school_id JOIN users u ON u.id=s.user_id
            LEFT JOIN users pu ON pu.id=r.reported_by LEFT JOIN users hu ON hu.id=r.handled_by WHERE r.school_id=? ORDER BY r.start_date,r.id
            """),
        new DataSheet("Emplois du temps","""
            SELECT sl.id "ID créneau",t.employee_number "Numéro employé",CONCAT_WS(' ',u.first_name,u.last_name) "Enseignant",
            y.label "Année",c.name "Classe",CASE sl.day_of_week WHEN 1 THEN 'Lundi' WHEN 2 THEN 'Mardi' WHEN 3 THEN 'Mercredi'
            WHEN 4 THEN 'Jeudi' WHEN 5 THEN 'Vendredi' WHEN 6 THEN 'Samedi' WHEN 7 THEN 'Dimanche' END "Jour",
            sl.start_time "Début cours",sl.end_time "Fin cours",sl.effective_from "Valide depuis",sl.effective_to "Valide jusqu’à"
            FROM teacher_schedule_slots sl JOIN teachers t ON t.id=sl.teacher_id JOIN users u ON u.id=t.user_id
            JOIN classes c ON c.id=sl.class_id AND c.school_id=t.school_id JOIN academic_years y ON y.id=c.academic_year_id
            WHERE t.school_id=? ORDER BY y.start_date,c.name,sl.day_of_week,sl.start_time,sl.id
            """),
        new DataSheet("Séances enseignants","""
            SELECT r.id "ID séance",t.employee_number "Numéro employé",CONCAT_WS(' ',u.first_name,u.last_name) "Enseignant",
            y.label "Année",c.name "Classe",r.session_date "Date",sl.start_time "Début",sl.end_time "Fin",r.status "Statut",r.recorded_at "Enregistrement"
            FROM teacher_session_records r JOIN teacher_schedule_slots sl ON sl.id=r.slot_id JOIN teachers t ON t.id=sl.teacher_id
            JOIN users u ON u.id=t.user_id JOIN classes c ON c.id=sl.class_id AND c.school_id=t.school_id JOIN academic_years y ON y.id=c.academic_year_id
            WHERE t.school_id=? ORDER BY r.session_date,t.employee_number,r.id
            """),
        new DataSheet("Heures supplémentaires","""
            SELECT h.id "ID heures",t.employee_number "Numéro employé",CONCAT_WS(' ',u.first_name,u.last_name) "Enseignant",
            y.label "Année",c.name "Classe",h.work_date "Date",h.hours "Heures",h.description "Description"
            FROM teacher_extra_hours h JOIN teachers t ON t.id=h.teacher_id JOIN users u ON u.id=t.user_id
            JOIN classes c ON c.id=h.class_id AND c.school_id=t.school_id JOIN academic_years y ON y.id=c.academic_year_id
            WHERE t.school_id=? ORDER BY h.work_date,t.employee_number,h.id
            """),
        new DataSheet("Tarifs enseignants","""
            SELECT r.id "ID tarif",t.employee_number "Numéro employé",CONCAT_WS(' ',u.first_name,u.last_name) "Enseignant",
            r.rate_type "Type",r.amount "Montant",r.effective_from "Valide depuis",r.created_at "Création"
            FROM teacher_rates r JOIN teachers t ON t.id=r.teacher_id JOIN users u ON u.id=t.user_id WHERE t.school_id=? ORDER BY t.employee_number,r.effective_from,r.id
            """),
        new DataSheet("Paiements enseignants","""
            SELECT p.id "ID paiement",t.employee_number "Numéro employé",CONCAT_WS(' ',u.first_name,u.last_name) "Enseignant",
            p.pay_month "Mois payé",p.payment_date "Date paiement",p.amount "Montant",p.reference "Référence"
            FROM teacher_payments p JOIN teachers t ON t.id=p.teacher_id JOIN users u ON u.id=t.user_id WHERE t.school_id=? ORDER BY p.payment_date,t.employee_number,p.id
            """),
        new DataSheet("Frais scolaires","""
            SELECT f.id "ID frais",f.name "Frais",l.name "Niveau",f.amount "Montant",f.frequency "Fréquence",f.description "Description",f.active "Actif"
            FROM fee_types f LEFT JOIN levels l ON l.id=f.level_id AND l.school_id=f.school_id WHERE f.school_id=? ORDER BY f.name,f.id
            """),
        new DataSheet("Factures","""
            SELECT i.id "ID facture",s.registration_number "Matricule",CONCAT_WS(' ',u.first_name,u.last_name) "Élève",y.label "Année",
            f.name "Frais",i.amount_due "Montant brut",i.discount_amount "Réduction",i.discount_reason "Motif réduction",
            i.amount_due-i.discount_amount "Montant net",COALESCE(p.paid,0) "Total payé",
            CASE WHEN i.status='CANCELLED' THEN 0 ELSE GREATEST(i.amount_due-i.discount_amount-COALESCE(p.paid,0),0) END "Reste à payer",
            i.due_date "Échéance",i.status "Statut"
            FROM invoices i JOIN students s ON s.id=i.student_id JOIN users u ON u.id=s.user_id
            JOIN academic_years y ON y.id=i.academic_year_id AND y.school_id=s.school_id
            LEFT JOIN fee_types f ON f.id=i.fee_type_id AND f.school_id=s.school_id
            LEFT JOIN LATERAL (SELECT SUM(amount) paid FROM payments WHERE invoice_id=i.id) p ON TRUE
            WHERE s.school_id=? ORDER BY y.start_date,s.registration_number,i.id
            """),
        new DataSheet("Paiements élèves","""
            SELECT p.id "ID paiement",i.id "ID facture",s.registration_number "Matricule",CONCAT_WS(' ',u.first_name,u.last_name) "Élève",
            y.label "Année de la facture",p.payment_date "Date paiement",p.amount "Montant",p.method "Mode",p.reference "Référence"
            FROM payments p JOIN invoices i ON i.id=p.invoice_id JOIN students s ON s.id=i.student_id JOIN users u ON u.id=s.user_id
            JOIN academic_years y ON y.id=i.academic_year_id AND y.school_id=s.school_id WHERE s.school_id=? ORDER BY p.payment_date,p.id
            """),
        new DataSheet("Impayés reportés","""
            SELECT y.label "Année de report",i.id "ID facture originale",s.registration_number "Matricule",CONCAT_WS(' ',u.first_name,u.last_name) "Élève",
            oy.label "Année de la facture",r.amount_at_closure "Solde au report",
            CASE WHEN i.status='CANCELLED' THEN 0 ELSE GREATEST(i.amount_due-i.discount_amount-COALESCE(p.paid,0),0) END "Solde actuel"
            FROM academic_year_receivables r JOIN academic_years y ON y.id=r.academic_year_id JOIN invoices i ON i.id=r.invoice_id
            JOIN students s ON s.id=i.student_id AND s.school_id=y.school_id JOIN users u ON u.id=s.user_id
            JOIN academic_years oy ON oy.id=i.academic_year_id AND oy.school_id=y.school_id
            LEFT JOIN LATERAL (SELECT SUM(amount) paid FROM payments WHERE invoice_id=i.id) p ON TRUE WHERE y.school_id=? ORDER BY y.start_date,i.id
            """),
        new DataSheet("Soldes ouverture","""
            SELECT y.label "Année",b.account "Compte",b.opening_balance "Solde ouverture"
            FROM academic_year_balances b JOIN academic_years y ON y.id=b.academic_year_id WHERE y.school_id=? ORDER BY y.start_date,b.account
            """),
        new DataSheet("Catégories dépenses","""
            SELECT id "ID catégorie",name "Catégorie",description "Description",system_code "Code système",active "Active"
            FROM expense_categories WHERE school_id=? ORDER BY name,id
            """),
        new DataSheet("Dépenses","""
            SELECT e.id "ID dépense",c.name "Catégorie",e.expense_date "Date",e.amount "Montant",e.label "Libellé",e.supplier "Fournisseur",
            e.payment_method "Mode paiement",e.reference "Référence",e.notes "Notes",CONCAT_WS(' ',u.first_name,u.last_name) "Enregistrée par",
            e.created_at "Création",e.updated_at "Modification"
            FROM expenses e JOIN expense_categories c ON c.id=e.category_id AND c.school_id=e.school_id LEFT JOIN users u ON u.id=e.created_by
            WHERE e.school_id=? ORDER BY e.expense_date,e.id
            """),
        new DataSheet("Charges fixes","""
            SELECT f.id "ID charge",c.name "Catégorie",f.label "Libellé",f.supplier "Bénéficiaire",f.amount "Montant mensuel",
            f.due_day "Jour échéance",f.start_month "Premier mois",f.active "Active",f.created_at "Création"
            FROM fixed_school_charges f JOIN expense_categories c ON c.id=f.category_id WHERE f.school_id=? ORDER BY f.label,f.id
            """),
        new DataSheet("Dépenses à payer","""
            SELECT p.id "ID échéance",p.source "Origine",p.period "Mois",p.due_date "Échéance",c.name "Catégorie",
            p.label "Libellé",p.supplier "Bénéficiaire",p.amount "Montant à la préparation",p.cancelled "Annulée",p.notes "Notes",
            p.teacher_id "ID enseignant",p.created_at "Création"
            FROM school_payables p JOIN expense_categories c ON c.id=p.category_id WHERE p.school_id=? ORDER BY p.due_date,p.id
            """),
        new DataSheet("Versements dépenses","""
            SELECT x.id "ID versement",p.label "Dépense",p.id "ID échéance",x.payment_date "Date",x.amount "Montant",
            x.method "Mode",x.reference "Référence",x.expense_id "ID sortie caisse",x.teacher_payment_id "ID paie enseignant",
            CONCAT_WS(' ',u.first_name,u.last_name) "Enregistré par",x.created_at "Enregistrement"
            FROM school_payable_payments x JOIN school_payables p ON p.id=x.payable_id LEFT JOIN users u ON u.id=x.created_by
            WHERE x.school_id=? ORDER BY x.payment_date,x.id
            """),
        new DataSheet("Budgets","""
            SELECT b.id "ID budget",y.label "Année",c.name "Catégorie",b.amount "Montant"
            FROM expense_budgets b JOIN academic_years y ON y.id=b.academic_year_id JOIN expense_categories c ON c.id=b.category_id AND c.school_id=y.school_id
            WHERE y.school_id=? ORDER BY y.start_date,c.name,b.id
            """),
        new DataSheet("Publications et devoirs","""
            SELECT p.id "ID publication",p.kind "Type",p.title "Titre",p.content "Contenu",c.name "Classe",y.label "Année de la classe",
            s.registration_number "Matricule ciblé",CONCAT_WS(' ',u.first_name,u.last_name) "Auteur",p.due_date "Échéance",p.created_at "Publication"
            FROM parent_portal_posts p LEFT JOIN classes c ON c.id=p.class_id AND c.school_id=p.school_id LEFT JOIN academic_years y ON y.id=c.academic_year_id
            LEFT JOIN students s ON s.id=p.student_id AND s.school_id=p.school_id JOIN users u ON u.id=p.author_id WHERE p.school_id=? ORDER BY p.created_at,p.id
            """),
        new DataSheet("Documents portail","SELECT f.id \"ID fichier\",p.id \"ID publication\",p.title \"Publication\",f.filename \"Nom fichier\",f.size_bytes \"Taille octets\","
            +PORTAL_PATH+" \"Chemin dans ZIP\" FROM parent_portal_files f JOIN parent_portal_posts p ON p.id=f.post_id WHERE p.school_id=? ORDER BY p.id,f.id"),
        new DataSheet("Rendez-vous","""
            SELECT a.id "ID rendez-vous",s.registration_number "Matricule",CONCAT_WS(' ',u.first_name,u.last_name) "Élève",
            CONCAT_WS(' ',pu.first_name,pu.last_name) "Parent",CONCAT_WS(' ',tu.first_name,tu.last_name) "Enseignant",
            a.proposed_at "Date proposée",a.reason "Motif",a.status "Statut",a.response "Réponse",a.created_at "Demande"
            FROM parent_appointments a JOIN students s ON s.id=a.student_id AND s.school_id=a.school_id JOIN users u ON u.id=s.user_id
            JOIN users pu ON pu.id=a.parent_user_id LEFT JOIN users tu ON tu.id=a.teacher_user_id WHERE a.school_id=? ORDER BY a.proposed_at,a.id
            """),
        new DataSheet("Conversations","""
            SELECT c.id "ID conversation",c.subject "Sujet",s.registration_number "Matricule",c.created_at "Création",c.last_message_at "Dernier message"
            FROM school_conversations c LEFT JOIN students s ON s.id=c.student_id AND s.school_id=c.school_id WHERE c.school_id=? AND
            """+VISIBLE_CONVERSATION+" ORDER BY c.created_at,c.id"),
        new DataSheet("Destinataires messages","""
            SELECT p.conversation_id "ID conversation",c.subject "Sujet",u.first_name "Prénom",u.last_name "Nom",u.email "Courriel",
            CASE WHEN p.school_id IS NOT NULL THEN 'Établissement' ELSE 'Personne' END "Destinataire",p.last_read_at "Dernière lecture"
            FROM conversation_participants p JOIN school_conversations c ON c.id=p.conversation_id LEFT JOIN users u ON u.id=p.user_id
            WHERE c.school_id=? AND
            """+VISIBLE_CONVERSATION+" ORDER BY c.id,p.id"),
        new DataSheet("Messages","""
            SELECT m.id "ID message",c.id "ID conversation",c.subject "Sujet",CONCAT_WS(' ',u.first_name,u.last_name) "Expéditeur",
            m.from_school "Envoyé par établissement",m.content "Contenu",m.sent_at "Envoi"
            FROM school_conversation_messages m JOIN school_conversations c ON c.id=m.conversation_id LEFT JOIN users u ON u.id=m.sender_id
            WHERE c.school_id=? AND
            """+VISIBLE_CONVERSATION+" ORDER BY c.id,m.sent_at,m.id"),
        new DataSheet("Pièces jointes messages","SELECT f.id \"ID fichier\",m.id \"ID message\",c.id \"ID conversation\",f.filename \"Nom fichier\",f.size_bytes \"Taille octets\","
            +MESSAGE_PATH+" \"Chemin dans ZIP\" FROM conversation_attachments f JOIN school_conversation_messages m ON m.id=f.message_id JOIN school_conversations c ON c.id=m.conversation_id"
            +" WHERE c.school_id=? AND "+VISIBLE_CONVERSATION+" ORDER BY c.id,m.id,f.id"),
        new DataSheet("Références documents","""
            SELECT d.id "ID document",d.title "Titre",d.type "Type",d.file_url "Lien externe",c.name "Classe",y.label "Année de la classe",
            CONCAT_WS(' ',u.first_name,u.last_name) "Ajouté par",d.created_at "Ajout"
            FROM documents d LEFT JOIN classes c ON c.id=d.class_id AND c.school_id=d.school_id LEFT JOIN academic_years y ON y.id=c.academic_year_id
            JOIN users u ON u.id=d.uploaded_by WHERE d.school_id=? ORDER BY d.created_at,d.id
            """),
        new DataSheet("Demandes accès","""
            SELECT a.id "ID demande",u.first_name "Prénom",u.last_name "Nom",u.email "Courriel",u.phone "Téléphone",a.requested_role "Rôle demandé",
            a.school_identifier "Identifiant déclaré",a.status "Statut",a.created_at "Demande",a.decided_at "Décision",
            CONCAT_WS(' ',du.first_name,du.last_name) "Décidée par",
            (SELECT STRING_AGG(ch.registration_number,', ' ORDER BY ch.child_index) FROM school_access_requested_children ch WHERE ch.request_id=a.id) "Matricules enfants déclarés"
            FROM school_access_requests a JOIN users u ON u.id=a.user_id LEFT JOIN users du ON du.id=a.decided_by WHERE a.school_id=? ORDER BY a.created_at,a.id
            """),
        new DataSheet("Demandes inscription","""
            SELECT u.id "ID compte",u.first_name "Prénom",u.last_name "Nom",u.email "Courriel",u.phone "Téléphone",u.requested_role "Rôle demandé",
            u.school_identifier "Identifiant déclaré",u.approved "Approuvé",u.email_verified "Courriel vérifié",u.created_at "Création",
            (SELECT STRING_AGG(ch.registration_number,', ' ORDER BY ch.child_index) FROM user_requested_children ch WHERE ch.user_id=u.id) "Matricules enfants déclarés"
            FROM users u WHERE u.requested_school_id=? ORDER BY u.created_at,u.id
            """),
        new DataSheet("Abonnements","""
            SELECT id "ID abonnement",plan "Offre",start_date "Début",end_date "Fin",status "Statut"
            FROM subscriptions WHERE school_id=? ORDER BY start_date,id
            """),
        new DataSheet("Historique activation","""
            SELECT e.id "ID événement",e.previous_status "Ancien statut",e.new_status "Nouveau statut",e.changed_at "Date",
            CONCAT_WS(' ',u.first_name,u.last_name) "Auteur" FROM school_status_events e JOIN users u ON u.id=e.actor_id WHERE e.school_id=? ORDER BY e.changed_at,e.id
            """)
    );
}
