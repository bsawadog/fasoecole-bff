ALTER TABLE absence_reports
    ADD COLUMN attendance_type VARCHAR(10) NOT NULL DEFAULT 'ABSENT';

ALTER TABLE absence_reports
    ADD CONSTRAINT chk_absence_reports_attendance_type
        CHECK (attendance_type IN ('ABSENT', 'LATE'));
