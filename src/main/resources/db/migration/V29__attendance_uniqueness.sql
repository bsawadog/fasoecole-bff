-- Conserver une copie des anciennes lignes en double avant de les consolider.
CREATE TABLE attendance_duplicate_archive AS
SELECT a.*, CURRENT_TIMESTAMP AS archived_at FROM attendances a
WHERE EXISTS (SELECT 1 FROM attendances newer WHERE newer.student_id=a.student_id
    AND newer.class_id=a.class_id AND newer.attendance_date=a.attendance_date AND newer.id>a.id);
DELETE FROM attendances a WHERE EXISTS (SELECT 1 FROM attendance_duplicate_archive archived WHERE archived.id=a.id);
ALTER TABLE attendances ADD CONSTRAINT uq_attendance_student_class_date UNIQUE(student_id,class_id,attendance_date);
