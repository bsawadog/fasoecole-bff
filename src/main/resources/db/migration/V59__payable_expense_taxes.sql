ALTER TABLE school_payables ADD COLUMN expense_taxes TEXT;
ALTER TABLE fixed_school_charges ADD COLUMN expense_taxes TEXT;
ALTER TABLE expenses ADD COLUMN expense_taxes TEXT;
