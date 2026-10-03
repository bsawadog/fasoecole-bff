-- Keep V34 unchanged after application; extend invoice archive protection.
CREATE OR REPLACE FUNCTION protect_closed_invoice() RETURNS TRIGGER LANGUAGE plpgsql AS $$
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
  IF TG_OP='UPDATE' AND NEW.academic_year_id IS DISTINCT FROM OLD.academic_year_id THEN
    SELECT closed_at INTO closed FROM academic_years WHERE id=NEW.academic_year_id FOR SHARE;
    IF closed IS NOT NULL THEN
      RAISE EXCEPTION 'Cette année scolaire est clôturée.' USING ERRCODE='23514';
    END IF;
  END IF;
  IF TG_OP='DELETE' THEN RETURN OLD; END IF; RETURN NEW;
END $$;
