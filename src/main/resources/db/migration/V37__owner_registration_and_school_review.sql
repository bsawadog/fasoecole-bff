ALTER TABLE users ADD COLUMN owner_account BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE schools ADD COLUMN submitted_at TIMESTAMP;
ALTER TABLE schools DROP CONSTRAINT IF EXISTS schools_status_check;
ALTER TABLE schools ADD CONSTRAINT schools_status_check
    CHECK(status IN ('DRAFT','PENDING_APPROVAL','ACTIVE','SUSPENDED','ARCHIVED'));

CREATE FUNCTION bump_school_lifecycle_revision() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.status IS DISTINCT FROM OLD.status THEN
        INSERT INTO school_data_revisions(school_id,revision) VALUES(NEW.id,1)
        ON CONFLICT(school_id) DO UPDATE SET revision=school_data_revisions.revision+1;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER school_lifecycle_revision AFTER UPDATE OF status ON schools
FOR EACH ROW EXECUTE FUNCTION bump_school_lifecycle_revision();
