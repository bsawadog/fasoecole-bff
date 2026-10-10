CREATE TABLE school_cash_books (
    school_id BIGINT PRIMARY KEY REFERENCES schools(id),
    opened_on DATE NOT NULL,
    opening_balance NUMERIC(14,2) NOT NULL CHECK (opening_balance >= 0),
    created_by BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    closed_on DATE,
    closed_by BIGINT REFERENCES users(id),
    CHECK (closed_on IS NULL OR closed_on >= opened_on)
);

CREATE TABLE cash_book_entries (
    id BIGSERIAL PRIMARY KEY,
    school_id BIGINT NOT NULL REFERENCES school_cash_books(school_id),
    request_id UUID NOT NULL UNIQUE,
    entry_date DATE NOT NULL,
    direction VARCHAR(3) NOT NULL CHECK (direction IN ('IN','OUT')),
    amount NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    label VARCHAR(150) NOT NULL,
    reference VARCHAR(100),
    created_by BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_cash_book_entries_school_date ON cash_book_entries(school_id,entry_date,id);

CREATE TABLE cash_book_month_closures (
    school_id BIGINT NOT NULL REFERENCES school_cash_books(school_id),
    month DATE NOT NULL CHECK (EXTRACT(DAY FROM month) = 1),
    opening_balance NUMERIC(14,2) NOT NULL,
    receipts NUMERIC(14,2) NOT NULL,
    payments NUMERIC(14,2) NOT NULL,
    closing_balance NUMERIC(14,2) NOT NULL,
    counted_balance NUMERIC(14,2) NOT NULL CHECK (counted_balance >= 0),
    note VARCHAR(500),
    closed_by BIGINT NOT NULL REFERENCES users(id),
    closed_at TIMESTAMP NOT NULL DEFAULT now(),
    entries JSONB NOT NULL DEFAULT '[]'::jsonb,
    PRIMARY KEY(school_id,month),
    CHECK (closing_balance = opening_balance + receipts - payments),
    CHECK (counted_balance = closing_balance)
);

-- Each real cash movement appears once. Payables backed by expenses are already
-- included through expenses; only teacher payments need the payable method join.
CREATE VIEW school_cash_movements AS
SELECT p.id, y.school_id, p.payment_date entry_date, 'IN'::VARCHAR direction,
       p.amount, ('Frais scolaires · ' || COALESCE(u.first_name,'') || ' ' || COALESCE(u.last_name,''))::TEXT label,
       p.reference, 'SCHOOL_PAYMENT'::VARCHAR source
FROM payments p JOIN invoices i ON i.id=p.invoice_id
JOIN academic_years y ON y.id=i.academic_year_id
JOIN students s ON s.id=i.student_id JOIN users u ON u.id=s.user_id
WHERE p.method='CASH'
UNION ALL
SELECT e.id,e.school_id,e.expense_date,'OUT',e.amount,e.label,e.reference,'EXPENSE'
FROM expenses e WHERE e.payment_method='CASH'
UNION ALL
SELECT t.id,x.school_id,t.payment_date,'OUT',t.amount,
       ('Salaire · ' || COALESCE(u.first_name,'') || ' ' || COALESCE(u.last_name,'')),t.reference,'TEACHER_PAYMENT'
FROM teacher_payments t JOIN school_payable_payments x ON x.teacher_payment_id=t.id
JOIN teachers teacher ON teacher.id=t.teacher_id JOIN users u ON u.id=teacher.user_id
WHERE x.method='CASH'
UNION ALL
SELECT id,school_id,entry_date,direction,amount,label,reference,'MANUAL'
FROM cash_book_entries;

CREATE INDEX idx_payments_cash_date ON payments(payment_date,invoice_id) WHERE method='CASH';
CREATE INDEX idx_expenses_cash_school_date ON expenses(school_id,expense_date) WHERE payment_method='CASH';

-- Lock the same book row as month closure so a source edit and a closure cannot
-- race. Protect both the OLD and NEW dates, including changes from/to CASH.
CREATE FUNCTION protect_closed_cash_book() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE data JSONB; school_key BIGINT; operation_date DATE; book school_cash_books%ROWTYPE;
BEGIN
  FOR data IN SELECT value FROM jsonb_array_elements(
    CASE WHEN TG_OP='INSERT' THEN jsonb_build_array(to_jsonb(NEW))
         WHEN TG_OP='DELETE' THEN jsonb_build_array(to_jsonb(OLD))
         ELSE jsonb_build_array(to_jsonb(OLD),to_jsonb(NEW)) END)
  LOOP
    school_key := NULL; operation_date := NULL;
    IF TG_TABLE_NAME='cash_book_entries' THEN
      school_key := (data->>'school_id')::BIGINT;
      operation_date := (data->>'entry_date')::DATE;
    ELSIF TG_TABLE_NAME='payments' AND data->>'method'='CASH' THEN
      SELECT y.school_id INTO school_key FROM invoices i JOIN academic_years y ON y.id=i.academic_year_id
        WHERE i.id=(data->>'invoice_id')::BIGINT;
      operation_date := (data->>'payment_date')::DATE;
    ELSIF TG_TABLE_NAME='expenses' AND data->>'payment_method'='CASH' THEN
      school_key := (data->>'school_id')::BIGINT;
      operation_date := (data->>'expense_date')::DATE;
    ELSIF TG_TABLE_NAME='school_payable_payments' AND data->>'method'='CASH'
          AND data->>'teacher_payment_id' IS NOT NULL THEN
      school_key := (data->>'school_id')::BIGINT;
      SELECT payment_date INTO operation_date FROM teacher_payments WHERE id=(data->>'teacher_payment_id')::BIGINT;
    ELSIF TG_TABLE_NAME='teacher_payments' THEN
      SELECT school_id INTO school_key FROM school_payable_payments
        WHERE teacher_payment_id=(data->>'id')::BIGINT AND method='CASH';
      operation_date := (data->>'payment_date')::DATE;
    END IF;
    IF school_key IS NOT NULL THEN
      SELECT * INTO book FROM school_cash_books WHERE school_id=school_key FOR UPDATE;
      IF FOUND AND operation_date >= book.opened_on THEN
        IF (book.closed_on IS NOT NULL AND operation_date <= book.closed_on)
           OR EXISTS(SELECT 1 FROM cash_book_month_closures c WHERE c.school_id=school_key
                     AND c.month=date_trunc('month',operation_date)::DATE) THEN
          RAISE EXCEPTION 'Cette période de caisse est clôturée et consultable uniquement.' USING ERRCODE='23514';
        END IF;
        IF TG_TABLE_NAME='cash_book_entries' AND book.closed_on IS NOT NULL THEN
          RAISE EXCEPTION 'Cette caisse est clôturée et consultable uniquement.' USING ERRCODE='23514';
        END IF;
      END IF;
    END IF;
  END LOOP;
  IF TG_OP='DELETE' THEN RETURN OLD; ELSE RETURN NEW; END IF;
END $$;

CREATE TRIGGER cash_book_manual_guard BEFORE INSERT OR UPDATE OR DELETE ON cash_book_entries
FOR EACH ROW EXECUTE FUNCTION protect_closed_cash_book();
CREATE TRIGGER cash_book_payment_guard BEFORE INSERT OR UPDATE OR DELETE ON payments
FOR EACH ROW EXECUTE FUNCTION protect_closed_cash_book();
CREATE TRIGGER cash_book_expense_guard BEFORE INSERT OR UPDATE OR DELETE ON expenses
FOR EACH ROW EXECUTE FUNCTION protect_closed_cash_book();
CREATE TRIGGER cash_book_teacher_guard BEFORE INSERT OR UPDATE OR DELETE ON teacher_payments
FOR EACH ROW EXECUTE FUNCTION protect_closed_cash_book();
CREATE TRIGGER cash_book_payable_guard BEFORE INSERT OR UPDATE OR DELETE ON school_payable_payments
FOR EACH ROW EXECUTE FUNCTION protect_closed_cash_book();
