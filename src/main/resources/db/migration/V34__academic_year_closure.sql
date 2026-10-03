ALTER TABLE academic_years ADD COLUMN closed_at TIMESTAMP;
ALTER TABLE academic_years ADD COLUMN closed_by BIGINT REFERENCES users(id);
ALTER TABLE academic_years ADD COLUMN next_year_id BIGINT REFERENCES academic_years(id);
ALTER TABLE academic_years ADD CONSTRAINT closed_year_not_current CHECK (closed_at IS NULL OR NOT is_current);

-- The original invoice remains the sole debt: never generate a second charge when rolling over.
CREATE TABLE academic_year_receivables (
    academic_year_id BIGINT NOT NULL REFERENCES academic_years(id),
    invoice_id BIGINT NOT NULL REFERENCES invoices(id),
    amount_at_closure NUMERIC(12,2) NOT NULL CHECK(amount_at_closure > 0),
    PRIMARY KEY(academic_year_id, invoice_id)
);
CREATE TABLE academic_year_balances (
    academic_year_id BIGINT NOT NULL REFERENCES academic_years(id),
    account VARCHAR(30) NOT NULL CHECK(account IN ('CASH','BANK')),
    opening_balance NUMERIC(12,2) NOT NULL,
    PRIMARY KEY(academic_year_id, account)
);

CREATE TRIGGER academic_year_revision AFTER INSERT OR UPDATE OR DELETE ON academic_years
FOR EACH ROW EXECUTE FUNCTION bump_school_data_revision();

CREATE FUNCTION bump_academic_data_revision() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE data JSONB; school_key BIGINT; BEGIN
  FOR data IN SELECT value FROM jsonb_array_elements(
    CASE WHEN TG_OP='INSERT' THEN jsonb_build_array(to_jsonb(NEW))
         WHEN TG_OP='DELETE' THEN jsonb_build_array(to_jsonb(OLD))
         ELSE jsonb_build_array(to_jsonb(OLD),to_jsonb(NEW)) END)
  LOOP
    school_key := (data->>'school_id')::BIGINT;
    IF school_key IS NULL AND (data->>'class_id') IS NOT NULL THEN
      SELECT school_id INTO school_key FROM classes WHERE id=(data->>'class_id')::BIGINT;
    ELSIF school_key IS NULL AND (data->>'class_subject_teacher_id') IS NOT NULL THEN
      SELECT c.school_id INTO school_key FROM class_subject_teacher a JOIN classes c ON c.id=a.class_id
      WHERE a.id=(data->>'class_subject_teacher_id')::BIGINT;
    ELSIF school_key IS NULL AND (data->>'academic_year_id') IS NOT NULL THEN
      SELECT school_id INTO school_key FROM academic_years WHERE id=(data->>'academic_year_id')::BIGINT;
    END IF;
    IF school_key IS NOT NULL THEN
      INSERT INTO school_data_revisions(school_id,revision) VALUES(school_key,1)
      ON CONFLICT(school_id) DO UPDATE SET revision=school_data_revisions.revision+1;
    END IF;
  END LOOP;
  RETURN NULL;
END $$;

CREATE FUNCTION protect_closed_financial_date() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE data JSONB; school_key BIGINT; operation_date DATE; year_key BIGINT; closed TIMESTAMP;
BEGIN
  FOR data IN SELECT value FROM jsonb_array_elements(
    CASE WHEN TG_OP='INSERT' THEN jsonb_build_array(to_jsonb(NEW))
         WHEN TG_OP='DELETE' THEN jsonb_build_array(to_jsonb(OLD))
         ELSE jsonb_build_array(to_jsonb(OLD),to_jsonb(NEW)) END)
  LOOP
    IF TG_TABLE_NAME='payments' THEN
      SELECT s.school_id INTO school_key FROM invoices i JOIN students s ON s.id=i.student_id WHERE i.id=(data->>'invoice_id')::BIGINT;
      operation_date := (data->>'payment_date')::DATE;
    ELSIF TG_TABLE_NAME='teacher_payments' THEN
      SELECT school_id INTO school_key FROM teachers WHERE id=(data->>'teacher_id')::BIGINT;
      operation_date := (data->>'payment_date')::DATE;
    ELSE
      school_key := (data->>'school_id')::BIGINT;
      operation_date := (data->>'expense_date')::DATE;
    END IF;
    SELECT id,closed_at INTO year_key,closed FROM academic_years
      WHERE school_id=school_key AND operation_date BETWEEN start_date AND end_date FOR SHARE;
    IF closed IS NOT NULL THEN
      RAISE EXCEPTION 'Cette année scolaire est clôturée. Enregistrez le paiement à la date réelle dans une année ouverte.' USING ERRCODE='23514';
    END IF;
  END LOOP;
  IF TG_OP='DELETE' THEN RETURN OLD; END IF; RETURN NEW;
END $$;
CREATE TRIGGER payment_archive_guard BEFORE INSERT OR UPDATE OR DELETE ON payments FOR EACH ROW EXECUTE FUNCTION protect_closed_financial_date();
CREATE TRIGGER expense_archive_guard BEFORE INSERT OR UPDATE OR DELETE ON expenses FOR EACH ROW EXECUTE FUNCTION protect_closed_financial_date();
CREATE TRIGGER payroll_archive_guard BEFORE INSERT OR UPDATE OR DELETE ON teacher_payments FOR EACH ROW EXECUTE FUNCTION protect_closed_financial_date();

CREATE FUNCTION protect_closed_invoice() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE closed TIMESTAMP; BEGIN
  IF TG_OP='INSERT' THEN
    SELECT closed_at INTO closed FROM academic_years WHERE id=NEW.academic_year_id FOR SHARE;
  ELSE
    SELECT closed_at INTO closed FROM academic_years WHERE id=OLD.academic_year_id FOR SHARE;
  END IF;
  IF closed IS NOT NULL THEN
    IF TG_OP<>'UPDATE' THEN RAISE EXCEPTION 'Cette année scolaire est clôturée.' USING ERRCODE='23514'; END IF;
    IF (to_jsonb(OLD)-'status') IS DISTINCT FROM (to_jsonb(NEW)-'status') OR NEW.status='CANCELLED' THEN
      RAISE EXCEPTION 'Cette année scolaire est clôturée.' USING ERRCODE='23514';
    END IF;
  END IF;
  IF TG_OP='DELETE' THEN RETURN OLD; END IF; RETURN NEW;
END $$;
CREATE TRIGGER invoice_archive_guard BEFORE INSERT OR UPDATE OR DELETE ON invoices FOR EACH ROW EXECUTE FUNCTION protect_closed_invoice();
DO $$ DECLARE t TEXT; BEGIN
  FOREACH t IN ARRAY ARRAY['student_enrollments','grade_periods','grades','report_cards','class_subject_teacher',
    'expenses','expense_budgets','school_staff','academic_year_receivables','academic_year_balances']
  LOOP
    EXECUTE format('CREATE TRIGGER academic_revision AFTER INSERT OR UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION bump_academic_data_revision()',t);
  END LOOP;
END $$;

-- Check both the old and new year: moving a row must not bypass archive protection.
-- Share locks serialize writes with the owner's year closure transaction.
CREATE FUNCTION protect_closed_academic_year() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE data JSONB; year_key BIGINT; closed TIMESTAMP;
BEGIN
  FOR data IN SELECT value FROM jsonb_array_elements(
    CASE WHEN TG_OP='INSERT' THEN jsonb_build_array(to_jsonb(NEW))
         WHEN TG_OP='DELETE' THEN jsonb_build_array(to_jsonb(OLD))
         ELSE jsonb_build_array(to_jsonb(OLD),to_jsonb(NEW)) END)
  LOOP
    year_key := NULL;
    IF (data->>'academic_year_id') IS NOT NULL THEN
      year_key := (data->>'academic_year_id')::BIGINT;
    ELSIF (data->>'class_id') IS NOT NULL THEN
      SELECT academic_year_id INTO year_key FROM classes WHERE id=(data->>'class_id')::BIGINT;
    ELSIF (data->>'class_subject_teacher_id') IS NOT NULL THEN
      SELECT c.academic_year_id INTO year_key FROM class_subject_teacher a JOIN classes c ON c.id=a.class_id
      WHERE a.id=(data->>'class_subject_teacher_id')::BIGINT;
    ELSIF (data->>'period_id') IS NOT NULL THEN
      SELECT academic_year_id INTO year_key FROM grade_periods WHERE id=(data->>'period_id')::BIGINT;
    ELSIF (data->>'evaluation_id') IS NOT NULL THEN
      SELECT p.academic_year_id INTO year_key FROM evaluations e JOIN grade_periods p ON p.id=e.period_id
      WHERE e.id=(data->>'evaluation_id')::BIGINT;
    ELSIF (data->>'slot_id') IS NOT NULL THEN
      SELECT c.academic_year_id INTO year_key FROM teacher_schedule_slots s JOIN classes c ON c.id=s.class_id
      WHERE s.id=(data->>'slot_id')::BIGINT;
    END IF;
    SELECT closed_at INTO closed FROM academic_years WHERE id=year_key FOR SHARE;
    IF closed IS NOT NULL THEN
      RAISE EXCEPTION 'Cette année scolaire est clôturée et consultable uniquement.' USING ERRCODE='23514';
    END IF;
  END LOOP;
  IF TG_OP='DELETE' THEN RETURN OLD; END IF;
  RETURN NEW;
END $$;

CREATE FUNCTION protect_closed_year_metadata() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
  IF OLD.closed_at IS NOT NULL THEN
    RAISE EXCEPTION 'Cette année scolaire est clôturée.' USING ERRCODE='23514';
  END IF;
  IF TG_OP='DELETE' THEN RETURN OLD; END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER year_archive_guard BEFORE UPDATE OR DELETE ON academic_years
FOR EACH ROW EXECUTE FUNCTION protect_closed_year_metadata();
DO $$ DECLARE t TEXT; BEGIN
  FOREACH t IN ARRAY ARRAY['classes','student_enrollments','grade_periods','evaluations','grades',
    'grade_history','report_cards','attendances','class_subject_teacher','teacher_schedule_slots',
    'teacher_session_records','teacher_extra_hours','expense_budgets']
  LOOP
    EXECUTE format('CREATE TRIGGER archive_guard BEFORE INSERT OR UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION protect_closed_academic_year()',t);
  END LOOP;
END $$;
