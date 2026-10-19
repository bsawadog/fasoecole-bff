CREATE TABLE school_bank_books (
    school_id BIGINT PRIMARY KEY REFERENCES schools(id),
    opened_on DATE NOT NULL,
    opening_balance NUMERIC(14,2) NOT NULL,
    created_by BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE TABLE bank_book_entries (
    id BIGSERIAL PRIMARY KEY,
    school_id BIGINT NOT NULL REFERENCES school_bank_books(school_id),
    request_id UUID NOT NULL UNIQUE,
    entry_date DATE NOT NULL,
    direction VARCHAR(3) NOT NULL CHECK (direction IN ('IN','OUT')),
    amount NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    label VARCHAR(150) NOT NULL,
    reference VARCHAR(100),
    created_by BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_bank_book_entries_school_date ON bank_book_entries(school_id,entry_date,id);

-- Only transfers are treated as bank movements. Card/mobile receipts need an
-- explicit bank deposit, since their settlement is not recorded by the app.
CREATE VIEW school_bank_movements AS
SELECT p.id,y.school_id,p.payment_date entry_date,'IN'::VARCHAR direction,p.amount,
       ('Frais scolaires · ' || COALESCE(u.first_name,'') || ' ' || COALESCE(u.last_name,''))::TEXT label,
       p.reference,'SCHOOL_PAYMENT'::VARCHAR source
FROM payments p JOIN invoices i ON i.id=p.invoice_id
JOIN academic_years y ON y.id=i.academic_year_id
JOIN students s ON s.id=i.student_id JOIN users u ON u.id=s.user_id
WHERE p.method='BANK_TRANSFER'
UNION ALL
SELECT e.id,e.school_id,e.expense_date,'OUT',e.amount,e.label,e.reference,'EXPENSE'
FROM expenses e WHERE e.payment_method='BANK_TRANSFER'
UNION ALL
SELECT t.id,tch.school_id,t.payment_date,'OUT',t.amount,
       ('Salaire · ' || COALESCE(u.first_name,'') || ' ' || COALESCE(u.last_name,'')),t.reference,'TEACHER_PAYMENT'
FROM teacher_payments t JOIN teachers tch ON tch.id=t.teacher_id JOIN users u ON u.id=tch.user_id
WHERE EXISTS (SELECT 1 FROM school_payable_payments x WHERE x.teacher_payment_id=t.id AND x.school_id=tch.school_id AND x.method='BANK_TRANSFER')
UNION ALL
SELECT id,school_id,entry_date,direction,amount,label,reference,'MANUAL' FROM bank_book_entries;

ALTER TABLE school_staff_modules DROP CONSTRAINT IF EXISTS school_staff_modules_module_check;
ALTER TABLE school_staff_modules ADD CONSTRAINT school_staff_modules_module_check CHECK (module IN
 ('DASHBOARD','MANAGEMENT','STUDENTS','TEACHERS','FINANCE','EXPENSES','GRADES','ENROLLMENT',
  'CALENDAR','DISCIPLINE','LIBRARY','HOMEWORK','REQUESTS','PARENT_PORTAL','MESSAGES','CASH_BOOK','BANK_BOOK','FINANCIAL_STATEMENTS','ASSETS','INVENTORY','PAYROLL','TAXES'));
