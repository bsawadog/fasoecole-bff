ALTER TABLE expenses ADD COLUMN invoice_details TEXT;
ALTER TABLE school_tax_obligations ADD COLUMN expense_id BIGINT REFERENCES expenses(id) ON DELETE SET NULL;
ALTER TABLE school_tax_obligations DROP CONSTRAINT school_tax_obligations_origin_check;
ALTER TABLE school_tax_obligations ADD CONSTRAINT school_tax_obligations_origin_check CHECK(origin IN ('MANUAL','PAYROLL','EXPENSE'));
CREATE UNIQUE INDEX idx_tax_expense_code ON school_tax_obligations(expense_id,code) WHERE origin='EXPENSE';
