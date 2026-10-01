-- Catalogue des frais : tarif par niveau, description et archivage.
ALTER TABLE fee_types
    ADD COLUMN level_id BIGINT REFERENCES levels(id) ON DELETE SET NULL,
    ADD COLUMN description VARCHAR(255),
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

-- Réductions et exonérations tracées sur chaque frais facturé.
ALTER TABLE invoices
    ADD COLUMN discount_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN discount_reason VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_invoices_student ON invoices(student_id);
CREATE INDEX IF NOT EXISTS idx_payments_invoice ON payments(invoice_id);
