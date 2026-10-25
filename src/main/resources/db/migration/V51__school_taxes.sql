ALTER TABLE school_staff_modules DROP CONSTRAINT IF EXISTS school_staff_modules_module_check;
ALTER TABLE school_staff_modules ADD CONSTRAINT school_staff_modules_module_check CHECK (module IN
 ('DASHBOARD','MANAGEMENT','STUDENTS','TEACHERS','FINANCE','EXPENSES','GRADES','ENROLLMENT',
  'CALENDAR','DISCIPLINE','LIBRARY','HOMEWORK','REQUESTS','PARENT_PORTAL','MESSAGES','CASH_BOOK','FINANCIAL_STATEMENTS','ASSETS','INVENTORY','PAYROLL','TAXES'));
ALTER TABLE school_payables DROP CONSTRAINT school_payables_source_check;
ALTER TABLE school_payables ADD CONSTRAINT school_payables_source_check CHECK (source IN ('MANUAL','FIXED','STAFF_SALARY','TEACHER_SALARY','TAX'));

CREATE TABLE school_tax_obligations (
 payable_id BIGINT PRIMARY KEY REFERENCES school_payables(id),
 school_id BIGINT NOT NULL REFERENCES schools(id),
 code VARCHAR(20) NOT NULL CHECK(code IN ('TVA','IUTS','TPA','PATENTE','INCOME','PROPERTY','OTHER')),
 period_start DATE NOT NULL,
 period_end DATE NOT NULL CHECK(period_end >= period_start),
 origin VARCHAR(20) NOT NULL CHECK(origin IN ('MANUAL','PAYROLL')),
 declaration_reference VARCHAR(100),
 request_id UUID NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 UNIQUE(school_id,request_id)
);
CREATE INDEX idx_school_tax_period ON school_tax_obligations(school_id,period_start,period_end);
CREATE UNIQUE INDEX idx_tax_payroll_month ON school_tax_obligations(school_id,code,period_start) WHERE origin='PAYROLL';
CREATE TABLE school_tax_events (
 id BIGSERIAL PRIMARY KEY,
 school_id BIGINT NOT NULL REFERENCES schools(id),
 payable_id BIGINT NOT NULL REFERENCES school_tax_obligations(payable_id),
 action VARCHAR(20) NOT NULL,
 amount NUMERIC(12,2) NOT NULL,
 due_date DATE NOT NULL,
 snapshot JSONB NOT NULL,
 reason VARCHAR(300) NOT NULL,
 created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
 created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_school_tax_events ON school_tax_events(school_id,payable_id,id DESC);
