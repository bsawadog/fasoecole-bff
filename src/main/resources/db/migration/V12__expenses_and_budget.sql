-- Dépenses et budget de l'établissement (espace propriétaire).

CREATE TABLE expense_categories (
    id BIGSERIAL PRIMARY KEY,
    school_id BIGINT NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    -- Catégorie gérée par l'application (ex. PAYROLL alimentée par la paie des enseignants).
    system_code VARCHAR(30),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_expense_categories_school_name ON expense_categories(school_id, lower(name));
CREATE UNIQUE INDEX uk_expense_categories_school_code ON expense_categories(school_id, system_code)
    WHERE system_code IS NOT NULL;

CREATE TABLE expenses (
    id BIGSERIAL PRIMARY KEY,
    school_id BIGINT NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    category_id BIGINT NOT NULL REFERENCES expense_categories(id),
    expense_date DATE NOT NULL,
    amount NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    label VARCHAR(150) NOT NULL,
    supplier VARCHAR(150),
    payment_method VARCHAR(30) NOT NULL,
    reference VARCHAR(100),
    notes VARCHAR(500),
    created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP
);
CREATE INDEX idx_expenses_school_date ON expenses(school_id, expense_date);
CREATE INDEX idx_expenses_category ON expenses(category_id);

CREATE TABLE expense_budgets (
    id BIGSERIAL PRIMARY KEY,
    academic_year_id BIGINT NOT NULL REFERENCES academic_years(id) ON DELETE CASCADE,
    category_id BIGINT NOT NULL REFERENCES expense_categories(id) ON DELETE CASCADE,
    amount NUMERIC(14,2) NOT NULL CHECK (amount >= 0),
    CONSTRAINT uk_expense_budgets_year_category UNIQUE (academic_year_id, category_id)
);
