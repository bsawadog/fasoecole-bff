ALTER TABLE school_staff_modules DROP CONSTRAINT IF EXISTS school_staff_modules_module_check;
ALTER TABLE school_staff_modules ADD CONSTRAINT school_staff_modules_module_check CHECK (module IN
 ('DASHBOARD','MANAGEMENT','STUDENTS','TEACHERS','FINANCE','EXPENSES','GRADES','ENROLLMENT',
  'CALENDAR','DISCIPLINE','LIBRARY','HOMEWORK','REQUESTS','PARENT_PORTAL','MESSAGES','CASH_BOOK','FINANCIAL_STATEMENTS','ASSETS','INVENTORY','PAYROLL'));

CREATE TABLE school_payroll_documents (
 id BIGSERIAL PRIMARY KEY,
 school_id BIGINT NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
 employee_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
 kind VARCHAR(30) NOT NULL CHECK(kind IN ('PAYSLIP','EMPLOYMENT_CERTIFICATE')),
 period DATE,
 request_id UUID NOT NULL,
 snapshot JSONB NOT NULL,
 created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
 created_at TIMESTAMP NOT NULL DEFAULT now(),
 UNIQUE(school_id,request_id),
 CHECK((kind='PAYSLIP' AND period IS NOT NULL) OR (kind='EMPLOYMENT_CERTIFICATE' AND period IS NULL))
);
CREATE INDEX idx_school_payroll_documents_employee ON school_payroll_documents(school_id,employee_user_id,id DESC);
