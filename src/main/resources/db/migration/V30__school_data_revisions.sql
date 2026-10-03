CREATE TABLE school_data_revisions (
    school_id BIGINT PRIMARY KEY REFERENCES schools(id) ON DELETE CASCADE,
    revision BIGINT NOT NULL DEFAULT 0
);
CREATE FUNCTION bump_school_data_revision() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE row_data JSONB; school_key BIGINT;
BEGIN
    FOR row_data IN SELECT value FROM jsonb_array_elements(
        CASE WHEN TG_OP='INSERT' THEN jsonb_build_array(to_jsonb(NEW))
             WHEN TG_OP='DELETE' THEN jsonb_build_array(to_jsonb(OLD))
             ELSE jsonb_build_array(to_jsonb(OLD),to_jsonb(NEW)) END)
    LOOP
        school_key := NULL;
        IF TG_TABLE_NAME='attendances' THEN
            SELECT school_id INTO school_key FROM classes WHERE id=(row_data->>'class_id')::BIGINT;
        ELSIF TG_TABLE_NAME='invoices' THEN
            SELECT school_id INTO school_key FROM students WHERE id=(row_data->>'student_id')::BIGINT;
        ELSIF TG_TABLE_NAME='payments' THEN
            SELECT s.school_id INTO school_key FROM invoices i JOIN students s ON s.id=i.student_id WHERE i.id=(row_data->>'invoice_id')::BIGINT;
        ELSE
            school_key := (row_data->>'school_id')::BIGINT;
        END IF;
        IF school_key IS NOT NULL AND EXISTS(SELECT 1 FROM schools WHERE id=school_key) THEN
            INSERT INTO school_data_revisions(school_id,revision) VALUES(school_key,1)
            ON CONFLICT(school_id) DO UPDATE SET revision=school_data_revisions.revision+1;
        END IF;
    END LOOP;
    RETURN NULL;
END $$;
CREATE TRIGGER attendance_revision AFTER INSERT OR UPDATE OR DELETE ON attendances FOR EACH ROW EXECUTE FUNCTION bump_school_data_revision();
CREATE TRIGGER absence_report_revision AFTER INSERT OR UPDATE OR DELETE ON absence_reports FOR EACH ROW EXECUTE FUNCTION bump_school_data_revision();
CREATE TRIGGER invoice_revision AFTER INSERT OR UPDATE OR DELETE ON invoices FOR EACH ROW EXECUTE FUNCTION bump_school_data_revision();
CREATE TRIGGER payment_revision AFTER INSERT OR UPDATE OR DELETE ON payments FOR EACH ROW EXECUTE FUNCTION bump_school_data_revision();
CREATE TRIGGER portal_post_revision AFTER INSERT OR UPDATE OR DELETE ON parent_portal_posts FOR EACH ROW EXECUTE FUNCTION bump_school_data_revision();
CREATE TRIGGER appointment_revision AFTER INSERT OR UPDATE OR DELETE ON parent_appointments FOR EACH ROW EXECUTE FUNCTION bump_school_data_revision();
CREATE TRIGGER student_revision AFTER INSERT OR UPDATE OR DELETE ON students FOR EACH ROW EXECUTE FUNCTION bump_school_data_revision();
CREATE TRIGGER teacher_revision AFTER INSERT OR UPDATE OR DELETE ON teachers FOR EACH ROW EXECUTE FUNCTION bump_school_data_revision();
CREATE TRIGGER class_revision AFTER INSERT OR UPDATE OR DELETE ON classes FOR EACH ROW EXECUTE FUNCTION bump_school_data_revision();
CREATE TRIGGER school_user_revision AFTER INSERT OR UPDATE OR DELETE ON school_users FOR EACH ROW EXECUTE FUNCTION bump_school_data_revision();
