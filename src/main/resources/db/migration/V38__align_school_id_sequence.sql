-- Explicit imports may have inserted school IDs without advancing the sequence.
-- Keep existing schools and only move the generator forward, never backwards.
LOCK TABLE schools IN SHARE ROW EXCLUSIVE MODE;
DO $$
DECLARE
    school_sequence REGCLASS := pg_get_serial_sequence('schools', 'id')::REGCLASS;
    maximum_id BIGINT;
    sequence_value BIGINT;
    sequence_called BOOLEAN;
BEGIN
    SELECT MAX(id) INTO maximum_id FROM schools;
    IF school_sequence IS NOT NULL AND maximum_id IS NOT NULL THEN
        EXECUTE format('SELECT last_value, is_called FROM %s', school_sequence)
            INTO sequence_value, sequence_called;
        IF sequence_value < maximum_id OR (sequence_value = maximum_id AND NOT sequence_called) THEN
            PERFORM setval(school_sequence, maximum_id, TRUE);
        END IF;
    END IF;
END $$;
