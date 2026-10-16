ALTER TABLE school_staff_modules DROP CONSTRAINT IF EXISTS school_staff_modules_module_check;
ALTER TABLE school_staff_modules ADD CONSTRAINT school_staff_modules_module_check CHECK (module IN
    ('DASHBOARD','MANAGEMENT','STUDENTS','TEACHERS','FINANCE','EXPENSES','GRADES','ENROLLMENT',
     'CALENDAR','DISCIPLINE','LIBRARY','HOMEWORK','REQUESTS','PARENT_PORTAL','MESSAGES','CASH_BOOK','FINANCIAL_STATEMENTS'));

-- Preserve previously delegated access when splitting the two historical bundles.
INSERT INTO school_staff_modules(staff_id, module)
SELECT old.staff_id, split.module
FROM school_staff_modules old
CROSS JOIN (VALUES ('CALENDAR'),('DISCIPLINE'),('LIBRARY'),('HOMEWORK'),('REQUESTS'),('PARENT_PORTAL'),('MESSAGES')) split(module)
WHERE old.module = 'STUDENTS'
ON CONFLICT (staff_id, module) DO NOTHING;

INSERT INTO school_staff_modules(staff_id, module)
SELECT old.staff_id, split.module
FROM school_staff_modules old
CROSS JOIN (VALUES ('CASH_BOOK'),('FINANCIAL_STATEMENTS')) split(module)
WHERE old.module = 'EXPENSES'
ON CONFLICT (staff_id, module) DO NOTHING;
