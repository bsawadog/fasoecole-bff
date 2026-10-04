-- B39: consolidated PostgreSQL schema through V39.
-- Only new, empty databases use this baseline migration.
-- Existing Flyway databases continue to use V1..V39 and later migrations.
-- No business data, accounts, credentials or Flyway history are included.
-- Platform administrator provisioning is a separate environment-specific step.

--
-- PostgreSQL database dump
--


-- Dumped from database version 16.15 (Debian 16.15-1.pgdg13+2)
-- Dumped by pg_dump version 16.15 (Debian 16.15-1.pgdg13+2)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET search_path TO public;
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: bump_academic_data_revision(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.bump_academic_data_revision() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE data JSONB; school_key BIGINT; BEGIN
  FOR data IN SELECT value FROM jsonb_array_elements(
    CASE WHEN TG_OP='INSERT' THEN jsonb_build_array(to_jsonb(NEW))
         WHEN TG_OP='DELETE' THEN jsonb_build_array(to_jsonb(OLD))
         ELSE jsonb_build_array(to_jsonb(OLD),to_jsonb(NEW)) END)
  LOOP
    school_key := (data->>'school_id')::BIGINT;
    IF school_key IS NULL AND (data->>'class_id') IS NOT NULL THEN
      SELECT school_id INTO school_key FROM classes WHERE id=(data->>'class_id')::BIGINT;
    ELSIF school_key IS NULL AND (data->>'class_subject_teacher_id') IS NOT NULL THEN
      SELECT c.school_id INTO school_key FROM class_subject_teacher a JOIN classes c ON c.id=a.class_id
      WHERE a.id=(data->>'class_subject_teacher_id')::BIGINT;
    ELSIF school_key IS NULL AND (data->>'academic_year_id') IS NOT NULL THEN
      SELECT school_id INTO school_key FROM academic_years WHERE id=(data->>'academic_year_id')::BIGINT;
    END IF;
    IF school_key IS NOT NULL THEN
      INSERT INTO school_data_revisions(school_id,revision) VALUES(school_key,1)
      ON CONFLICT(school_id) DO UPDATE SET revision=school_data_revisions.revision+1;
    END IF;
  END LOOP;
  RETURN NULL;
END $$;


--
-- Name: bump_school_data_revision(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.bump_school_data_revision() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE row_data JSONB; school_key BIGINT;
BEGIN
    FOR row_data IN SELECT value FROM jsonb_array_elements(
        CASE WHEN TG_OP='INSERT' THEN jsonb_build_array(to_jsonb(NEW))
             WHEN TG_OP='DELETE' THEN jsonb_build_array(to_jsonb(OLD))
             ELSE jsonb_build_array(to_jsonb(OLD),to_jsonb(NEW)) END)
    LOOP
        school_key := NULL;
        IF TG_TABLE_NAME='attendances' THEN
            SELECT school_id INTO school_key FROM classes WHERE id=(row_data->>'class_id')::BIGINT;
        ELSIF TG_TABLE_NAME='invoices' THEN
            SELECT school_id INTO school_key FROM students WHERE id=(row_data->>'student_id')::BIGINT;
        ELSIF TG_TABLE_NAME='payments' THEN
            SELECT s.school_id INTO school_key FROM invoices i JOIN students s ON s.id=i.student_id WHERE i.id=(row_data->>'invoice_id')::BIGINT;
        ELSE
            school_key := (row_data->>'school_id')::BIGINT;
        END IF;
        IF school_key IS NOT NULL AND EXISTS(SELECT 1 FROM schools WHERE id=school_key) THEN
            INSERT INTO school_data_revisions(school_id,revision) VALUES(school_key,1)
            ON CONFLICT(school_id) DO UPDATE SET revision=school_data_revisions.revision+1;
        END IF;
    END LOOP;
    RETURN NULL;
END $$;


--
-- Name: bump_school_lifecycle_revision(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.bump_school_lifecycle_revision() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.status IS DISTINCT FROM OLD.status THEN
        INSERT INTO school_data_revisions(school_id,revision) VALUES(NEW.id,1)
        ON CONFLICT(school_id) DO UPDATE SET revision=school_data_revisions.revision+1;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: protect_closed_academic_year(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.protect_closed_academic_year() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE data JSONB; year_key BIGINT; closed TIMESTAMP;
BEGIN
  FOR data IN SELECT value FROM jsonb_array_elements(
    CASE WHEN TG_OP='INSERT' THEN jsonb_build_array(to_jsonb(NEW))
         WHEN TG_OP='DELETE' THEN jsonb_build_array(to_jsonb(OLD))
         ELSE jsonb_build_array(to_jsonb(OLD),to_jsonb(NEW)) END)
  LOOP
    year_key := NULL;
    IF (data->>'academic_year_id') IS NOT NULL THEN
      year_key := (data->>'academic_year_id')::BIGINT;
    ELSIF (data->>'class_id') IS NOT NULL THEN
      SELECT academic_year_id INTO year_key FROM classes WHERE id=(data->>'class_id')::BIGINT;
    ELSIF (data->>'class_subject_teacher_id') IS NOT NULL THEN
      SELECT c.academic_year_id INTO year_key FROM class_subject_teacher a JOIN classes c ON c.id=a.class_id
      WHERE a.id=(data->>'class_subject_teacher_id')::BIGINT;
    ELSIF (data->>'period_id') IS NOT NULL THEN
      SELECT academic_year_id INTO year_key FROM grade_periods WHERE id=(data->>'period_id')::BIGINT;
    ELSIF (data->>'evaluation_id') IS NOT NULL THEN
      SELECT p.academic_year_id INTO year_key FROM evaluations e JOIN grade_periods p ON p.id=e.period_id
      WHERE e.id=(data->>'evaluation_id')::BIGINT;
    ELSIF (data->>'slot_id') IS NOT NULL THEN
      SELECT c.academic_year_id INTO year_key FROM teacher_schedule_slots s JOIN classes c ON c.id=s.class_id
      WHERE s.id=(data->>'slot_id')::BIGINT;
    END IF;
    SELECT closed_at INTO closed FROM academic_years WHERE id=year_key FOR SHARE;
    IF closed IS NOT NULL THEN
      RAISE EXCEPTION 'Cette année scolaire est clôturée et consultable uniquement.' USING ERRCODE='23514';
    END IF;
  END LOOP;
  IF TG_OP='DELETE' THEN RETURN OLD; END IF;
  RETURN NEW;
END $$;


--
-- Name: protect_closed_financial_date(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.protect_closed_financial_date() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE data JSONB; school_key BIGINT; operation_date DATE; year_key BIGINT; closed TIMESTAMP;
BEGIN
  FOR data IN SELECT value FROM jsonb_array_elements(
    CASE WHEN TG_OP='INSERT' THEN jsonb_build_array(to_jsonb(NEW))
         WHEN TG_OP='DELETE' THEN jsonb_build_array(to_jsonb(OLD))
         ELSE jsonb_build_array(to_jsonb(OLD),to_jsonb(NEW)) END)
  LOOP
    IF TG_TABLE_NAME='payments' THEN
      SELECT s.school_id INTO school_key FROM invoices i JOIN students s ON s.id=i.student_id WHERE i.id=(data->>'invoice_id')::BIGINT;
      operation_date := (data->>'payment_date')::DATE;
    ELSIF TG_TABLE_NAME='teacher_payments' THEN
      SELECT school_id INTO school_key FROM teachers WHERE id=(data->>'teacher_id')::BIGINT;
      operation_date := (data->>'payment_date')::DATE;
    ELSE
      school_key := (data->>'school_id')::BIGINT;
      operation_date := (data->>'expense_date')::DATE;
    END IF;
    SELECT id,closed_at INTO year_key,closed FROM academic_years
      WHERE school_id=school_key AND operation_date BETWEEN start_date AND end_date FOR SHARE;
    IF closed IS NOT NULL THEN
      RAISE EXCEPTION 'Cette année scolaire est clôturée. Enregistrez le paiement à la date réelle dans une année ouverte.' USING ERRCODE='23514';
    END IF;
  END LOOP;
  IF TG_OP='DELETE' THEN RETURN OLD; END IF; RETURN NEW;
END $$;


--
-- Name: protect_closed_invoice(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.protect_closed_invoice() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE closed TIMESTAMP; BEGIN
  IF TG_OP='INSERT' THEN
    SELECT closed_at INTO closed FROM academic_years WHERE id=NEW.academic_year_id FOR SHARE;
  ELSE
    SELECT closed_at INTO closed FROM academic_years WHERE id=OLD.academic_year_id FOR SHARE;
  END IF;
  IF closed IS NOT NULL THEN
    IF TG_OP<>'UPDATE' THEN RAISE EXCEPTION 'Cette année scolaire est clôturée.' USING ERRCODE='23514'; END IF;
    IF (to_jsonb(OLD)-'status') IS DISTINCT FROM (to_jsonb(NEW)-'status') OR NEW.status='CANCELLED' THEN
      RAISE EXCEPTION 'Cette année scolaire est clôturée.' USING ERRCODE='23514';
    END IF;
  END IF;
  IF TG_OP='UPDATE' AND NEW.academic_year_id IS DISTINCT FROM OLD.academic_year_id THEN
    SELECT closed_at INTO closed FROM academic_years WHERE id=NEW.academic_year_id FOR SHARE;
    IF closed IS NOT NULL THEN
      RAISE EXCEPTION 'Cette année scolaire est clôturée.' USING ERRCODE='23514';
    END IF;
  END IF;
  IF TG_OP='DELETE' THEN RETURN OLD; END IF; RETURN NEW;
END $$;


--
-- Name: protect_closed_year_metadata(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.protect_closed_year_metadata() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  IF OLD.closed_at IS NOT NULL THEN
    RAISE EXCEPTION 'Cette année scolaire est clôturée.' USING ERRCODE='23514';
  END IF;
  IF TG_OP='DELETE' THEN RETURN OLD; END IF;
  RETURN NEW;
END $$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: absence_reports; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.absence_reports (
    id bigint NOT NULL,
    student_id bigint NOT NULL,
    school_id bigint NOT NULL,
    reported_by bigint,
    start_date date NOT NULL,
    end_date date NOT NULL,
    reason character varying(500) NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    school_comment character varying(500),
    handled_by bigint,
    handled_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    attendance_type character varying(10) DEFAULT 'ABSENT'::character varying NOT NULL,
    CONSTRAINT chk_absence_reports_attendance_type CHECK (((attendance_type)::text = ANY ((ARRAY['ABSENT'::character varying, 'LATE'::character varying])::text[]))),
    CONSTRAINT chk_absence_reports_dates CHECK ((end_date >= start_date)),
    CONSTRAINT chk_absence_reports_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'ACKNOWLEDGED'::character varying, 'REJECTED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: absence_reports_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.absence_reports_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: absence_reports_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.absence_reports_id_seq OWNED BY public.absence_reports.id;


--
-- Name: academic_year_balances; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.academic_year_balances (
    academic_year_id bigint NOT NULL,
    account character varying(30) NOT NULL,
    opening_balance numeric(12,2) NOT NULL,
    CONSTRAINT academic_year_balances_account_check CHECK (((account)::text = ANY ((ARRAY['CASH'::character varying, 'BANK'::character varying])::text[])))
);


--
-- Name: academic_year_receivables; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.academic_year_receivables (
    academic_year_id bigint NOT NULL,
    invoice_id bigint NOT NULL,
    amount_at_closure numeric(12,2) NOT NULL,
    CONSTRAINT academic_year_receivables_amount_at_closure_check CHECK ((amount_at_closure > (0)::numeric))
);


--
-- Name: academic_years; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.academic_years (
    id bigint NOT NULL,
    school_id bigint NOT NULL,
    label character varying(20) NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    is_current boolean DEFAULT false NOT NULL,
    closed_at timestamp without time zone,
    closed_by bigint,
    next_year_id bigint,
    CONSTRAINT closed_year_not_current CHECK (((closed_at IS NULL) OR (NOT is_current)))
);


--
-- Name: academic_years_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.academic_years_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: academic_years_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.academic_years_id_seq OWNED BY public.academic_years.id;


--
-- Name: attendance_duplicate_archive; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.attendance_duplicate_archive (
    id bigint,
    student_id bigint,
    class_id bigint,
    attendance_date date,
    status character varying(20),
    justification character varying(500),
    archived_at timestamp with time zone
);


--
-- Name: attendances; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.attendances (
    id bigint NOT NULL,
    student_id bigint NOT NULL,
    class_id bigint NOT NULL,
    attendance_date date NOT NULL,
    status character varying(20) NOT NULL,
    justification character varying(500),
    CONSTRAINT attendances_status_check CHECK (((status)::text = ANY ((ARRAY['PRESENT'::character varying, 'ABSENT'::character varying, 'LATE'::character varying, 'EXCUSED'::character varying])::text[])))
);


--
-- Name: attendances_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.attendances_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: attendances_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.attendances_id_seq OWNED BY public.attendances.id;


--
-- Name: audit_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_logs (
    id bigint NOT NULL,
    user_id bigint,
    action character varying(100) NOT NULL,
    entity character varying(100) NOT NULL,
    entity_id bigint,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: audit_logs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.audit_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: audit_logs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.audit_logs_id_seq OWNED BY public.audit_logs.id;


--
-- Name: class_subject_teacher; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.class_subject_teacher (
    id bigint NOT NULL,
    class_id bigint NOT NULL,
    subject_id bigint NOT NULL,
    teacher_id bigint NOT NULL,
    coefficient numeric(4,2),
    active boolean DEFAULT true NOT NULL,
    deactivated_at timestamp without time zone
);


--
-- Name: class_subject_teacher_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.class_subject_teacher_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: class_subject_teacher_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.class_subject_teacher_id_seq OWNED BY public.class_subject_teacher.id;


--
-- Name: classes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.classes (
    id bigint NOT NULL,
    school_id bigint NOT NULL,
    academic_year_id bigint NOT NULL,
    level_id bigint NOT NULL,
    name character varying(100) NOT NULL,
    capacity integer
);


--
-- Name: classes_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.classes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: classes_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.classes_id_seq OWNED BY public.classes.id;


--
-- Name: conversation_attachment_content; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.conversation_attachment_content (
    id bigint NOT NULL,
    data bytea NOT NULL
);


--
-- Name: conversation_attachments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.conversation_attachments (
    id bigint NOT NULL,
    message_id bigint NOT NULL,
    filename character varying(200) NOT NULL,
    size_bytes bigint NOT NULL,
    CONSTRAINT conversation_attachments_size_bytes_check CHECK (((size_bytes > 0) AND (size_bytes <= 10485760)))
);


--
-- Name: conversation_attachments_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.conversation_attachments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: conversation_attachments_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.conversation_attachments_id_seq OWNED BY public.conversation_attachments.id;


--
-- Name: conversation_participants; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.conversation_participants (
    id bigint NOT NULL,
    conversation_id bigint NOT NULL,
    user_id bigint,
    school_id bigint,
    last_read_at timestamp without time zone,
    CONSTRAINT chk_conversation_participant_target CHECK (((((user_id IS NOT NULL))::integer + ((school_id IS NOT NULL))::integer) = 1))
);


--
-- Name: conversation_participants_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.conversation_participants_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: conversation_participants_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.conversation_participants_id_seq OWNED BY public.conversation_participants.id;


--
-- Name: documents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.documents (
    id bigint NOT NULL,
    school_id bigint NOT NULL,
    class_id bigint,
    uploaded_by bigint NOT NULL,
    title character varying(200) NOT NULL,
    file_url character varying(500) NOT NULL,
    type character varying(50),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: documents_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.documents_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: documents_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.documents_id_seq OWNED BY public.documents.id;


--
-- Name: email_verification_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.email_verification_tokens (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    token_hash character varying(64) NOT NULL,
    purpose character varying(20) NOT NULL,
    requested_school_id bigint,
    requested_role character varying(30),
    expires_at timestamp without time zone NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    recipient_email character varying(150),
    delivery_status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    school_identifier character varying(50),
    CONSTRAINT email_verification_tokens_purpose_check CHECK (((purpose)::text = ANY ((ARRAY['VERIFY'::character varying, 'ACTIVATE'::character varying])::text[])))
);


--
-- Name: email_verification_tokens_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.email_verification_tokens_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: email_verification_tokens_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.email_verification_tokens_id_seq OWNED BY public.email_verification_tokens.id;


--
-- Name: evaluations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.evaluations (
    id bigint NOT NULL,
    class_subject_teacher_id bigint NOT NULL,
    period_id bigint NOT NULL,
    title character varying(120) NOT NULL,
    type character varying(30) NOT NULL,
    eval_date date NOT NULL,
    max_value numeric(5,2) DEFAULT 20 NOT NULL,
    weight numeric(4,2) DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT evaluations_max_value_check CHECK ((max_value > (0)::numeric)),
    CONSTRAINT evaluations_weight_check CHECK ((weight > (0)::numeric))
);


--
-- Name: evaluations_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.evaluations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: evaluations_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.evaluations_id_seq OWNED BY public.evaluations.id;


--
-- Name: expense_budgets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.expense_budgets (
    id bigint NOT NULL,
    academic_year_id bigint NOT NULL,
    category_id bigint NOT NULL,
    amount numeric(14,2) NOT NULL,
    CONSTRAINT expense_budgets_amount_check CHECK ((amount >= (0)::numeric))
);


--
-- Name: expense_budgets_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.expense_budgets_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: expense_budgets_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.expense_budgets_id_seq OWNED BY public.expense_budgets.id;


--
-- Name: expense_categories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.expense_categories (
    id bigint NOT NULL,
    school_id bigint NOT NULL,
    name character varying(100) NOT NULL,
    description character varying(255),
    system_code character varying(30),
    active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: expense_categories_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.expense_categories_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: expense_categories_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.expense_categories_id_seq OWNED BY public.expense_categories.id;


--
-- Name: expenses; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.expenses (
    id bigint NOT NULL,
    school_id bigint NOT NULL,
    category_id bigint NOT NULL,
    expense_date date NOT NULL,
    amount numeric(12,2) NOT NULL,
    label character varying(150) NOT NULL,
    supplier character varying(150),
    payment_method character varying(30) NOT NULL,
    reference character varying(100),
    notes character varying(500),
    created_by bigint,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone,
    CONSTRAINT expenses_amount_check CHECK ((amount > (0)::numeric))
);


--
-- Name: expenses_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.expenses_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: expenses_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.expenses_id_seq OWNED BY public.expenses.id;


--
-- Name: fee_types; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.fee_types (
    id bigint NOT NULL,
    school_id bigint NOT NULL,
    name character varying(150) NOT NULL,
    amount numeric(12,2) NOT NULL,
    frequency character varying(30) DEFAULT 'ONE_TIME'::character varying NOT NULL,
    level_id bigint,
    description character varying(255),
    active boolean DEFAULT true NOT NULL,
    CONSTRAINT fee_types_frequency_check CHECK (((frequency)::text = ANY ((ARRAY['ONE_TIME'::character varying, 'MONTHLY'::character varying, 'TERM'::character varying, 'YEARLY'::character varying])::text[])))
);


--
-- Name: fee_types_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.fee_types_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: fee_types_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.fee_types_id_seq OWNED BY public.fee_types.id;


--
-- Name: grade_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.grade_history (
    id bigint NOT NULL,
    evaluation_id bigint,
    evaluation_title character varying(160) NOT NULL,
    class_id bigint,
    period_id bigint,
    student_id bigint,
    action character varying(20) NOT NULL,
    old_value numeric(5,2),
    new_value numeric(5,2),
    reason character varying(255),
    changed_by bigint,
    changed_by_name character varying(160),
    changed_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT grade_history_action_check CHECK (((action)::text = ANY ((ARRAY['CREATE'::character varying, 'UPDATE'::character varying, 'DELETE'::character varying])::text[])))
);


--
-- Name: grade_history_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.grade_history_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: grade_history_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.grade_history_id_seq OWNED BY public.grade_history.id;


--
-- Name: grade_periods; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.grade_periods (
    id bigint NOT NULL,
    school_id bigint NOT NULL,
    academic_year_id bigint NOT NULL,
    code character varying(20) NOT NULL,
    name character varying(80) NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    pass_mark numeric(5,2) DEFAULT 10 NOT NULL,
    status character varying(20) DEFAULT 'OPEN'::character varying NOT NULL,
    published_at timestamp without time zone,
    CONSTRAINT grade_periods_check CHECK ((end_date >= start_date)),
    CONSTRAINT grade_periods_status_check CHECK (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'LOCKED'::character varying, 'PUBLISHED'::character varying])::text[])))
);


--
-- Name: grade_periods_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.grade_periods_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: grade_periods_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.grade_periods_id_seq OWNED BY public.grade_periods.id;


--
-- Name: grades; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.grades (
    id bigint NOT NULL,
    student_id bigint NOT NULL,
    class_subject_teacher_id bigint NOT NULL,
    term character varying(20) NOT NULL,
    type character varying(30) NOT NULL,
    value numeric(5,2) NOT NULL,
    max_value numeric(5,2) DEFAULT 20 NOT NULL,
    grade_date date DEFAULT CURRENT_DATE NOT NULL,
    evaluation_id bigint,
    appreciation character varying(500)
);


--
-- Name: grades_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.grades_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: grades_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.grades_id_seq OWNED BY public.grades.id;


--
-- Name: invoices; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.invoices (
    id bigint NOT NULL,
    student_id bigint NOT NULL,
    fee_type_id bigint NOT NULL,
    academic_year_id bigint NOT NULL,
    amount_due numeric(12,2) NOT NULL,
    due_date date NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    discount_amount numeric(12,2) DEFAULT 0 NOT NULL,
    discount_reason character varying(255),
    CONSTRAINT invoices_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PAID'::character varying, 'OVERDUE'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: invoices_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.invoices_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: invoices_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.invoices_id_seq OWNED BY public.invoices.id;


--
-- Name: levels; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.levels (
    id bigint NOT NULL,
    school_id bigint NOT NULL,
    name character varying(100) NOT NULL,
    cycle character varying(30) NOT NULL,
    order_index integer DEFAULT 0 NOT NULL
);


--
-- Name: levels_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.levels_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: levels_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.levels_id_seq OWNED BY public.levels.id;


--
-- Name: messages; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.messages (
    id bigint NOT NULL,
    sender_id bigint NOT NULL,
    receiver_id bigint NOT NULL,
    subject character varying(200),
    content text NOT NULL,
    sent_at timestamp without time zone DEFAULT now() NOT NULL,
    read_at timestamp without time zone
);


--
-- Name: messages_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.messages_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: messages_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.messages_id_seq OWNED BY public.messages.id;


--
-- Name: notifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notifications (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    title character varying(200) NOT NULL,
    content text,
    is_read boolean DEFAULT false NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: notifications_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.notifications_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notifications_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.notifications_id_seq OWNED BY public.notifications.id;


--
-- Name: parent_appointments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.parent_appointments (
    id bigint NOT NULL,
    school_id bigint NOT NULL,
    student_id bigint NOT NULL,
    parent_user_id bigint NOT NULL,
    teacher_user_id bigint,
    proposed_at timestamp without time zone NOT NULL,
    reason character varying(1000) NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    response character varying(1000),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT parent_appointments_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'ACCEPTED'::character varying, 'REJECTED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: parent_appointments_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.parent_appointments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: parent_appointments_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.parent_appointments_id_seq OWNED BY public.parent_appointments.id;


--
-- Name: parent_portal_files; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.parent_portal_files (
    id bigint NOT NULL,
    post_id bigint NOT NULL,
    filename character varying(200) NOT NULL,
    size_bytes bigint NOT NULL,
    data bytea NOT NULL
);


--
-- Name: parent_portal_files_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.parent_portal_files_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: parent_portal_files_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.parent_portal_files_id_seq OWNED BY public.parent_portal_files.id;


--
-- Name: parent_portal_posts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.parent_portal_posts (
    id bigint NOT NULL,
    school_id bigint NOT NULL,
    class_id bigint,
    student_id bigint,
    author_id bigint NOT NULL,
    kind character varying(20) NOT NULL,
    title character varying(160) NOT NULL,
    content character varying(4000) NOT NULL,
    due_date date,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT parent_portal_posts_kind_check CHECK (((kind)::text = ANY ((ARRAY['ANNOUNCEMENT'::character varying, 'HOMEWORK'::character varying, 'DOCUMENT'::character varying])::text[])))
);


--
-- Name: parent_portal_posts_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.parent_portal_posts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: parent_portal_posts_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.parent_portal_posts_id_seq OWNED BY public.parent_portal_posts.id;


--
-- Name: parent_student; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.parent_student (
    id bigint NOT NULL,
    parent_id bigint NOT NULL,
    student_id bigint NOT NULL,
    relationship character varying(50)
);


--
-- Name: parent_student_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.parent_student_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: parent_student_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.parent_student_id_seq OWNED BY public.parent_student.id;


--
-- Name: parents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.parents (
    id bigint NOT NULL,
    user_id bigint NOT NULL
);


--
-- Name: parents_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.parents_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: parents_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.parents_id_seq OWNED BY public.parents.id;


--
-- Name: password_reset_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.password_reset_tokens (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    token_hash character varying(64) NOT NULL,
    expires_at timestamp without time zone NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    recipient_email character varying(150)
);


--
-- Name: password_reset_tokens_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.password_reset_tokens_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: password_reset_tokens_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.password_reset_tokens_id_seq OWNED BY public.password_reset_tokens.id;


--
-- Name: payments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payments (
    id bigint NOT NULL,
    invoice_id bigint NOT NULL,
    amount numeric(12,2) NOT NULL,
    payment_date date DEFAULT CURRENT_DATE NOT NULL,
    method character varying(30) NOT NULL,
    reference character varying(100),
    CONSTRAINT payments_method_check CHECK (((method)::text = ANY ((ARRAY['CASH'::character varying, 'MOBILE_MONEY'::character varying, 'BANK_TRANSFER'::character varying, 'CARD'::character varying])::text[])))
);


--
-- Name: payments_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.payments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: payments_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.payments_id_seq OWNED BY public.payments.id;


--
-- Name: report_cards; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.report_cards (
    id bigint NOT NULL,
    student_id bigint NOT NULL,
    academic_year_id bigint NOT NULL,
    term character varying(20) NOT NULL,
    average numeric(5,2),
    rank integer,
    comment character varying(255),
    validated boolean DEFAULT false NOT NULL,
    class_id bigint,
    period_id bigint,
    class_size integer,
    mention character varying(40),
    generated_at timestamp without time zone
);


--
-- Name: report_cards_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.report_cards_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: report_cards_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.report_cards_id_seq OWNED BY public.report_cards.id;


--
-- Name: roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.roles (
    id bigint NOT NULL,
    name character varying(50) NOT NULL
);


--
-- Name: roles_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.roles_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: roles_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.roles_id_seq OWNED BY public.roles.id;


--
-- Name: school_access_requested_children; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.school_access_requested_children (
    request_id bigint NOT NULL,
    child_index integer NOT NULL,
    registration_number character varying(50) NOT NULL
);


--
-- Name: school_access_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.school_access_requests (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    school_id bigint NOT NULL,
    requested_role character varying(30) NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    decided_by bigint,
    decided_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    school_identifier character varying(50),
    CONSTRAINT school_access_requests_requested_role_check CHECK (((requested_role)::text = ANY ((ARRAY['TEACHER'::character varying, 'PARENT'::character varying, 'STUDENT'::character varying])::text[]))),
    CONSTRAINT school_access_requests_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'AUTO_APPROVED'::character varying, 'REVOKED'::character varying])::text[])))
);


--
-- Name: school_access_requests_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.school_access_requests_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: school_access_requests_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.school_access_requests_id_seq OWNED BY public.school_access_requests.id;


--
-- Name: school_conversation_messages; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.school_conversation_messages (
    id bigint NOT NULL,
    conversation_id bigint NOT NULL,
    sender_id bigint,
    from_school boolean NOT NULL,
    content text NOT NULL,
    sent_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: school_conversation_messages_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.school_conversation_messages_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: school_conversation_messages_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.school_conversation_messages_id_seq OWNED BY public.school_conversation_messages.id;


--
-- Name: school_conversations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.school_conversations (
    id bigint NOT NULL,
    school_id bigint NOT NULL,
    parent_user_id bigint,
    student_id bigint,
    subject character varying(160) NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    last_message_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    unread_by_school boolean DEFAULT true NOT NULL,
    unread_by_parent boolean DEFAULT false NOT NULL
);


--
-- Name: school_conversations_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.school_conversations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: school_conversations_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.school_conversations_id_seq OWNED BY public.school_conversations.id;


--
-- Name: school_data_revisions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.school_data_revisions (
    school_id bigint NOT NULL,
    revision bigint DEFAULT 0 NOT NULL
);


--
-- Name: school_staff; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.school_staff (
    id bigint NOT NULL,
    school_id bigint NOT NULL,
    user_id bigint NOT NULL,
    job_title character varying(80) NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_by bigint,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone
);


--
-- Name: school_staff_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.school_staff_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: school_staff_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.school_staff_id_seq OWNED BY public.school_staff.id;


--
-- Name: school_staff_modules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.school_staff_modules (
    staff_id bigint NOT NULL,
    module character varying(30) NOT NULL,
    CONSTRAINT school_staff_modules_module_check CHECK (((module)::text = ANY ((ARRAY['DASHBOARD'::character varying, 'MANAGEMENT'::character varying, 'STUDENTS'::character varying, 'TEACHERS'::character varying, 'FINANCE'::character varying, 'EXPENSES'::character varying, 'GRADES'::character varying, 'ENROLLMENT'::character varying])::text[])))
);


--
-- Name: school_status_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.school_status_events (
    id bigint NOT NULL,
    school_id bigint NOT NULL,
    actor_id bigint NOT NULL,
    previous_status character varying(20) NOT NULL,
    new_status character varying(20) NOT NULL,
    changed_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: school_status_events_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.school_status_events_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: school_status_events_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.school_status_events_id_seq OWNED BY public.school_status_events.id;


--
-- Name: school_users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.school_users (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    school_id bigint NOT NULL,
    role_id bigint NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: school_users_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.school_users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: school_users_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.school_users_id_seq OWNED BY public.school_users.id;


--
-- Name: schools; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.schools (
    id bigint NOT NULL,
    name character varying(200) NOT NULL,
    type character varying(30) NOT NULL,
    address character varying(255),
    phone character varying(30),
    email character varying(150),
    owner_id bigint NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    activated_at timestamp without time zone,
    deactivated_at timestamp without time zone,
    submitted_at timestamp without time zone,
    expected_student_count integer,
    expected_class_count integer,
    expected_teacher_count integer,
    CONSTRAINT schools_expected_counts_check CHECK ((((expected_student_count IS NULL) OR (expected_student_count >= 0)) AND ((expected_class_count IS NULL) OR (expected_class_count >= 1)) AND ((expected_teacher_count IS NULL) OR (expected_teacher_count >= 0)))),
    CONSTRAINT schools_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PENDING_APPROVAL'::character varying, 'ACTIVE'::character varying, 'SUSPENDED'::character varying, 'ARCHIVED'::character varying])::text[]))),
    CONSTRAINT schools_type_check CHECK (((type)::text = ANY ((ARRAY['PRESCOLAIRE'::character varying, 'PRIMAIRE'::character varying, 'SECONDAIRE'::character varying, 'MIXTE'::character varying, 'UNIVERSITE'::character varying, 'FORMATION'::character varying])::text[])))
);


--
-- Name: schools_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.schools_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: schools_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.schools_id_seq OWNED BY public.schools.id;


--
-- Name: student_enrollments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_enrollments (
    id bigint NOT NULL,
    student_id bigint NOT NULL,
    class_id bigint NOT NULL,
    academic_year_id bigint NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    enrollment_date date DEFAULT CURRENT_DATE NOT NULL,
    decision character varying(20),
    decision_average numeric(5,2),
    decided_at timestamp without time zone,
    CONSTRAINT student_enrollments_decision_check CHECK (((decision)::text = ANY ((ARRAY['PROMOTED'::character varying, 'REPEATED'::character varying, 'GRADUATED'::character varying, 'LEFT'::character varying])::text[]))),
    CONSTRAINT student_enrollments_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'COMPLETED'::character varying, 'TRANSFERRED'::character varying, 'GRADUATED'::character varying, 'DROPPED'::character varying])::text[])))
);


--
-- Name: student_enrollments_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.student_enrollments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: student_enrollments_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.student_enrollments_id_seq OWNED BY public.student_enrollments.id;


--
-- Name: students; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.students (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    school_id bigint NOT NULL,
    registration_number character varying(50) NOT NULL,
    birth_date date,
    gender character varying(10)
);


--
-- Name: students_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.students_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: students_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.students_id_seq OWNED BY public.students.id;


--
-- Name: subjects; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.subjects (
    id bigint NOT NULL,
    school_id bigint NOT NULL,
    name character varying(150) NOT NULL,
    code character varying(30),
    coefficient numeric(4,2) DEFAULT 1 NOT NULL,
    CONSTRAINT chk_subjects_coefficient CHECK (((coefficient >= 0.25) AND (coefficient <= (20)::numeric)))
);


--
-- Name: subjects_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.subjects_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: subjects_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.subjects_id_seq OWNED BY public.subjects.id;


--
-- Name: subscriptions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.subscriptions (
    id bigint NOT NULL,
    school_id bigint NOT NULL,
    plan character varying(50) NOT NULL,
    start_date date NOT NULL,
    end_date date,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    CONSTRAINT subscriptions_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'EXPIRED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: subscriptions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.subscriptions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: subscriptions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.subscriptions_id_seq OWNED BY public.subscriptions.id;


--
-- Name: teacher_extra_hours; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.teacher_extra_hours (
    id bigint NOT NULL,
    teacher_id bigint NOT NULL,
    class_id bigint NOT NULL,
    work_date date NOT NULL,
    hours numeric(5,2) NOT NULL,
    description character varying(255),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT teacher_extra_hours_hours_check CHECK (((hours > (0)::numeric) AND (hours <= (24)::numeric)))
);


--
-- Name: teacher_extra_hours_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.teacher_extra_hours_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: teacher_extra_hours_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.teacher_extra_hours_id_seq OWNED BY public.teacher_extra_hours.id;


--
-- Name: teacher_payments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.teacher_payments (
    id bigint NOT NULL,
    teacher_id bigint NOT NULL,
    pay_month date NOT NULL,
    payment_date date NOT NULL,
    amount numeric(12,2) NOT NULL,
    reference character varying(100),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT teacher_payments_amount_check CHECK ((amount > (0)::numeric)),
    CONSTRAINT teacher_payments_pay_month_check CHECK ((EXTRACT(day FROM pay_month) = (1)::numeric))
);


--
-- Name: teacher_payments_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.teacher_payments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: teacher_payments_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.teacher_payments_id_seq OWNED BY public.teacher_payments.id;


--
-- Name: teacher_rates; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.teacher_rates (
    id bigint NOT NULL,
    teacher_id bigint NOT NULL,
    rate_type character varying(20) NOT NULL,
    amount numeric(12,2) NOT NULL,
    effective_from date NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT teacher_rates_amount_check CHECK ((amount > (0)::numeric)),
    CONSTRAINT teacher_rates_effective_from_check CHECK ((EXTRACT(day FROM effective_from) = (1)::numeric)),
    CONSTRAINT teacher_rates_rate_type_check CHECK (((rate_type)::text = ANY ((ARRAY['HOURLY'::character varying, 'MONTHLY'::character varying])::text[])))
);


--
-- Name: teacher_rates_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.teacher_rates_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: teacher_rates_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.teacher_rates_id_seq OWNED BY public.teacher_rates.id;


--
-- Name: teacher_schedule_slots; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.teacher_schedule_slots (
    id bigint NOT NULL,
    teacher_id bigint NOT NULL,
    class_id bigint NOT NULL,
    day_of_week integer NOT NULL,
    start_time time without time zone NOT NULL,
    end_time time without time zone NOT NULL,
    effective_from date NOT NULL,
    effective_to date,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_teacher_slot_dates CHECK (((effective_to IS NULL) OR (effective_to >= effective_from))),
    CONSTRAINT ck_teacher_slot_times CHECK ((end_time > start_time)),
    CONSTRAINT teacher_schedule_slots_day_of_week_check CHECK (((day_of_week >= 1) AND (day_of_week <= 7)))
);


--
-- Name: teacher_schedule_slots_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.teacher_schedule_slots_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: teacher_schedule_slots_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.teacher_schedule_slots_id_seq OWNED BY public.teacher_schedule_slots.id;


--
-- Name: teacher_session_records; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.teacher_session_records (
    id bigint NOT NULL,
    slot_id bigint NOT NULL,
    session_date date NOT NULL,
    status character varying(10) NOT NULL,
    recorded_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT teacher_session_records_status_check CHECK (((status)::text = ANY ((ARRAY['PRESENT'::character varying, 'ABSENT'::character varying])::text[])))
);


--
-- Name: teacher_session_records_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.teacher_session_records_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: teacher_session_records_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.teacher_session_records_id_seq OWNED BY public.teacher_session_records.id;


--
-- Name: teachers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.teachers (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    school_id bigint NOT NULL,
    specialty character varying(150),
    hire_date date,
    employee_number character varying(50) NOT NULL
);


--
-- Name: teachers_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.teachers_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: teachers_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.teachers_id_seq OWNED BY public.teachers.id;


--
-- Name: user_platform_roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_platform_roles (
    user_id bigint NOT NULL,
    role character varying(30) NOT NULL,
    CONSTRAINT user_platform_roles_role_check CHECK (((role)::text = 'SUPER_ADMIN'::text))
);


--
-- Name: user_requested_children; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_requested_children (
    user_id bigint NOT NULL,
    child_index integer NOT NULL,
    registration_number character varying(50) NOT NULL
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id bigint NOT NULL,
    first_name character varying(100) NOT NULL,
    last_name character varying(100) NOT NULL,
    email character varying(150),
    password_hash character varying(255) NOT NULL,
    phone character varying(30),
    active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    approved boolean DEFAULT true NOT NULL,
    requested_school_id bigint,
    requested_role character varying(30),
    password_set boolean DEFAULT true NOT NULL,
    email_verified boolean DEFAULT false NOT NULL,
    session_version bigint DEFAULT 0 NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    school_identifier character varying(50),
    must_change_password boolean DEFAULT false NOT NULL,
    owner_account boolean DEFAULT false NOT NULL,
    CONSTRAINT chk_users_requested_role CHECK (((requested_role IS NULL) OR ((requested_role)::text = ANY ((ARRAY['TEACHER'::character varying, 'PARENT'::character varying, 'STUDENT'::character varying])::text[]))))
);


--
-- Name: users_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: users_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.users_id_seq OWNED BY public.users.id;


--
-- Name: verification_requested_children; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.verification_requested_children (
    token_id bigint NOT NULL,
    child_index integer NOT NULL,
    registration_number character varying(50) NOT NULL
);


--
-- Name: absence_reports id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.absence_reports ALTER COLUMN id SET DEFAULT nextval('public.absence_reports_id_seq'::regclass);


--
-- Name: academic_years id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_years ALTER COLUMN id SET DEFAULT nextval('public.academic_years_id_seq'::regclass);


--
-- Name: attendances id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendances ALTER COLUMN id SET DEFAULT nextval('public.attendances_id_seq'::regclass);


--
-- Name: audit_logs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs ALTER COLUMN id SET DEFAULT nextval('public.audit_logs_id_seq'::regclass);


--
-- Name: class_subject_teacher id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.class_subject_teacher ALTER COLUMN id SET DEFAULT nextval('public.class_subject_teacher_id_seq'::regclass);


--
-- Name: classes id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.classes ALTER COLUMN id SET DEFAULT nextval('public.classes_id_seq'::regclass);


--
-- Name: conversation_attachments id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.conversation_attachments ALTER COLUMN id SET DEFAULT nextval('public.conversation_attachments_id_seq'::regclass);


--
-- Name: conversation_participants id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.conversation_participants ALTER COLUMN id SET DEFAULT nextval('public.conversation_participants_id_seq'::regclass);


--
-- Name: documents id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documents ALTER COLUMN id SET DEFAULT nextval('public.documents_id_seq'::regclass);


--
-- Name: email_verification_tokens id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_verification_tokens ALTER COLUMN id SET DEFAULT nextval('public.email_verification_tokens_id_seq'::regclass);


--
-- Name: evaluations id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evaluations ALTER COLUMN id SET DEFAULT nextval('public.evaluations_id_seq'::regclass);


--
-- Name: expense_budgets id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expense_budgets ALTER COLUMN id SET DEFAULT nextval('public.expense_budgets_id_seq'::regclass);


--
-- Name: expense_categories id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expense_categories ALTER COLUMN id SET DEFAULT nextval('public.expense_categories_id_seq'::regclass);


--
-- Name: expenses id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expenses ALTER COLUMN id SET DEFAULT nextval('public.expenses_id_seq'::regclass);


--
-- Name: fee_types id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fee_types ALTER COLUMN id SET DEFAULT nextval('public.fee_types_id_seq'::regclass);


--
-- Name: grade_history id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grade_history ALTER COLUMN id SET DEFAULT nextval('public.grade_history_id_seq'::regclass);


--
-- Name: grade_periods id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grade_periods ALTER COLUMN id SET DEFAULT nextval('public.grade_periods_id_seq'::regclass);


--
-- Name: grades id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grades ALTER COLUMN id SET DEFAULT nextval('public.grades_id_seq'::regclass);


--
-- Name: invoices id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices ALTER COLUMN id SET DEFAULT nextval('public.invoices_id_seq'::regclass);


--
-- Name: levels id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.levels ALTER COLUMN id SET DEFAULT nextval('public.levels_id_seq'::regclass);


--
-- Name: messages id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.messages ALTER COLUMN id SET DEFAULT nextval('public.messages_id_seq'::regclass);


--
-- Name: notifications id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications ALTER COLUMN id SET DEFAULT nextval('public.notifications_id_seq'::regclass);


--
-- Name: parent_appointments id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.parent_appointments ALTER COLUMN id SET DEFAULT nextval('public.parent_appointments_id_seq'::regclass);


--
-- Name: parent_portal_files id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.parent_portal_files ALTER COLUMN id SET DEFAULT nextval('public.parent_portal_files_id_seq'::regclass);


--
-- Name: parent_portal_posts id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.parent_portal_posts ALTER COLUMN id SET DEFAULT nextval('public.parent_portal_posts_id_seq'::regclass);


--
-- Name: parent_student id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.parent_student ALTER COLUMN id SET DEFAULT nextval('public.parent_student_id_seq'::regclass);


--
-- Name: parents id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.parents ALTER COLUMN id SET DEFAULT nextval('public.parents_id_seq'::regclass);


--
-- Name: password_reset_tokens id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_tokens ALTER COLUMN id SET DEFAULT nextval('public.password_reset_tokens_id_seq'::regclass);


--
-- Name: payments id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payments ALTER COLUMN id SET DEFAULT nextval('public.payments_id_seq'::regclass);


--
-- Name: report_cards id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.report_cards ALTER COLUMN id SET DEFAULT nextval('public.report_cards_id_seq'::regclass);


--
-- Name: roles id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles ALTER COLUMN id SET DEFAULT nextval('public.roles_id_seq'::regclass);


--
-- Name: school_access_requests id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_access_requests ALTER COLUMN id SET DEFAULT nextval('public.school_access_requests_id_seq'::regclass);


--
-- Name: school_conversation_messages id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_conversation_messages ALTER COLUMN id SET DEFAULT nextval('public.school_conversation_messages_id_seq'::regclass);


--
-- Name: school_conversations id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_conversations ALTER COLUMN id SET DEFAULT nextval('public.school_conversations_id_seq'::regclass);


--
-- Name: school_staff id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_staff ALTER COLUMN id SET DEFAULT nextval('public.school_staff_id_seq'::regclass);


--
-- Name: school_status_events id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_status_events ALTER COLUMN id SET DEFAULT nextval('public.school_status_events_id_seq'::regclass);


--
-- Name: school_users id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_users ALTER COLUMN id SET DEFAULT nextval('public.school_users_id_seq'::regclass);


--
-- Name: schools id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.schools ALTER COLUMN id SET DEFAULT nextval('public.schools_id_seq'::regclass);


--
-- Name: student_enrollments id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_enrollments ALTER COLUMN id SET DEFAULT nextval('public.student_enrollments_id_seq'::regclass);


--
-- Name: students id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.students ALTER COLUMN id SET DEFAULT nextval('public.students_id_seq'::regclass);


--
-- Name: subjects id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subjects ALTER COLUMN id SET DEFAULT nextval('public.subjects_id_seq'::regclass);


--
-- Name: subscriptions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscriptions ALTER COLUMN id SET DEFAULT nextval('public.subscriptions_id_seq'::regclass);


--
-- Name: teacher_extra_hours id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teacher_extra_hours ALTER COLUMN id SET DEFAULT nextval('public.teacher_extra_hours_id_seq'::regclass);


--
-- Name: teacher_payments id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teacher_payments ALTER COLUMN id SET DEFAULT nextval('public.teacher_payments_id_seq'::regclass);


--
-- Name: teacher_rates id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teacher_rates ALTER COLUMN id SET DEFAULT nextval('public.teacher_rates_id_seq'::regclass);


--
-- Name: teacher_schedule_slots id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teacher_schedule_slots ALTER COLUMN id SET DEFAULT nextval('public.teacher_schedule_slots_id_seq'::regclass);


--
-- Name: teacher_session_records id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teacher_session_records ALTER COLUMN id SET DEFAULT nextval('public.teacher_session_records_id_seq'::regclass);


--
-- Name: teachers id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teachers ALTER COLUMN id SET DEFAULT nextval('public.teachers_id_seq'::regclass);


--
-- Name: users id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users ALTER COLUMN id SET DEFAULT nextval('public.users_id_seq'::regclass);


--
-- Name: absence_reports absence_reports_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.absence_reports
    ADD CONSTRAINT absence_reports_pkey PRIMARY KEY (id);


--
-- Name: academic_year_balances academic_year_balances_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_year_balances
    ADD CONSTRAINT academic_year_balances_pkey PRIMARY KEY (academic_year_id, account);


--
-- Name: academic_year_receivables academic_year_receivables_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_year_receivables
    ADD CONSTRAINT academic_year_receivables_pkey PRIMARY KEY (academic_year_id, invoice_id);


--
-- Name: academic_years academic_years_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_years
    ADD CONSTRAINT academic_years_pkey PRIMARY KEY (id);


--
-- Name: attendances attendances_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendances
    ADD CONSTRAINT attendances_pkey PRIMARY KEY (id);


--
-- Name: audit_logs audit_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT audit_logs_pkey PRIMARY KEY (id);


--
-- Name: class_subject_teacher class_subject_teacher_class_id_subject_id_teacher_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.class_subject_teacher
    ADD CONSTRAINT class_subject_teacher_class_id_subject_id_teacher_id_key UNIQUE (class_id, subject_id, teacher_id);


--
-- Name: class_subject_teacher class_subject_teacher_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.class_subject_teacher
    ADD CONSTRAINT class_subject_teacher_pkey PRIMARY KEY (id);


--
-- Name: classes classes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.classes
    ADD CONSTRAINT classes_pkey PRIMARY KEY (id);


--
-- Name: conversation_attachment_content conversation_attachment_content_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.conversation_attachment_content
    ADD CONSTRAINT conversation_attachment_content_pkey PRIMARY KEY (id);


--
-- Name: conversation_attachments conversation_attachments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.conversation_attachments
    ADD CONSTRAINT conversation_attachments_pkey PRIMARY KEY (id);


--
-- Name: conversation_participants conversation_participants_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.conversation_participants
    ADD CONSTRAINT conversation_participants_pkey PRIMARY KEY (id);


--
-- Name: documents documents_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documents
    ADD CONSTRAINT documents_pkey PRIMARY KEY (id);


--
-- Name: email_verification_tokens email_verification_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_verification_tokens
    ADD CONSTRAINT email_verification_tokens_pkey PRIMARY KEY (id);


--
-- Name: email_verification_tokens email_verification_tokens_token_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_verification_tokens
    ADD CONSTRAINT email_verification_tokens_token_hash_key UNIQUE (token_hash);


--
-- Name: email_verification_tokens email_verification_tokens_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_verification_tokens
    ADD CONSTRAINT email_verification_tokens_user_id_key UNIQUE (user_id);


--
-- Name: evaluations evaluations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evaluations
    ADD CONSTRAINT evaluations_pkey PRIMARY KEY (id);


--
-- Name: expense_budgets expense_budgets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expense_budgets
    ADD CONSTRAINT expense_budgets_pkey PRIMARY KEY (id);


--
-- Name: expense_categories expense_categories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expense_categories
    ADD CONSTRAINT expense_categories_pkey PRIMARY KEY (id);


--
-- Name: expenses expenses_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expenses
    ADD CONSTRAINT expenses_pkey PRIMARY KEY (id);


--
-- Name: fee_types fee_types_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fee_types
    ADD CONSTRAINT fee_types_pkey PRIMARY KEY (id);


--
-- Name: grade_history grade_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grade_history
    ADD CONSTRAINT grade_history_pkey PRIMARY KEY (id);


--
-- Name: grade_periods grade_periods_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grade_periods
    ADD CONSTRAINT grade_periods_pkey PRIMARY KEY (id);


--
-- Name: grade_periods grade_periods_school_id_academic_year_id_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grade_periods
    ADD CONSTRAINT grade_periods_school_id_academic_year_id_code_key UNIQUE (school_id, academic_year_id, code);


--
-- Name: grades grades_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grades
    ADD CONSTRAINT grades_pkey PRIMARY KEY (id);


--
-- Name: invoices invoices_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT invoices_pkey PRIMARY KEY (id);


--
-- Name: levels levels_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.levels
    ADD CONSTRAINT levels_pkey PRIMARY KEY (id);


--
-- Name: messages messages_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.messages
    ADD CONSTRAINT messages_pkey PRIMARY KEY (id);


--
-- Name: notifications notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);


--
-- Name: parent_appointments parent_appointments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.parent_appointments
    ADD CONSTRAINT parent_appointments_pkey PRIMARY KEY (id);


--
-- Name: parent_portal_files parent_portal_files_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.parent_portal_files
    ADD CONSTRAINT parent_portal_files_pkey PRIMARY KEY (id);


--
-- Name: parent_portal_posts parent_portal_posts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.parent_portal_posts
    ADD CONSTRAINT parent_portal_posts_pkey PRIMARY KEY (id);


--
-- Name: parent_student parent_student_parent_id_student_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.parent_student
    ADD CONSTRAINT parent_student_parent_id_student_id_key UNIQUE (parent_id, student_id);


--
-- Name: parent_student parent_student_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.parent_student
    ADD CONSTRAINT parent_student_pkey PRIMARY KEY (id);


--
-- Name: parents parents_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.parents
    ADD CONSTRAINT parents_pkey PRIMARY KEY (id);


--
-- Name: password_reset_tokens password_reset_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT password_reset_tokens_pkey PRIMARY KEY (id);


--
-- Name: password_reset_tokens password_reset_tokens_token_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT password_reset_tokens_token_hash_key UNIQUE (token_hash);


--
-- Name: password_reset_tokens password_reset_tokens_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT password_reset_tokens_user_id_key UNIQUE (user_id);


--
-- Name: payments payments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT payments_pkey PRIMARY KEY (id);


--
-- Name: report_cards report_cards_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.report_cards
    ADD CONSTRAINT report_cards_pkey PRIMARY KEY (id);


--
-- Name: roles roles_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_name_key UNIQUE (name);


--
-- Name: roles roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (id);


--
-- Name: school_access_requested_children school_access_requested_children_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_access_requested_children
    ADD CONSTRAINT school_access_requested_children_pkey PRIMARY KEY (request_id, child_index);


--
-- Name: school_access_requests school_access_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_access_requests
    ADD CONSTRAINT school_access_requests_pkey PRIMARY KEY (id);


--
-- Name: school_conversation_messages school_conversation_messages_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_conversation_messages
    ADD CONSTRAINT school_conversation_messages_pkey PRIMARY KEY (id);


--
-- Name: school_conversations school_conversations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_conversations
    ADD CONSTRAINT school_conversations_pkey PRIMARY KEY (id);


--
-- Name: school_data_revisions school_data_revisions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_data_revisions
    ADD CONSTRAINT school_data_revisions_pkey PRIMARY KEY (school_id);


--
-- Name: school_staff_modules school_staff_modules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_staff_modules
    ADD CONSTRAINT school_staff_modules_pkey PRIMARY KEY (staff_id, module);


--
-- Name: school_staff school_staff_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_staff
    ADD CONSTRAINT school_staff_pkey PRIMARY KEY (id);


--
-- Name: school_status_events school_status_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_status_events
    ADD CONSTRAINT school_status_events_pkey PRIMARY KEY (id);


--
-- Name: school_users school_users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_users
    ADD CONSTRAINT school_users_pkey PRIMARY KEY (id);


--
-- Name: school_users school_users_user_id_school_id_role_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_users
    ADD CONSTRAINT school_users_user_id_school_id_role_id_key UNIQUE (user_id, school_id, role_id);


--
-- Name: schools schools_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.schools
    ADD CONSTRAINT schools_pkey PRIMARY KEY (id);


--
-- Name: student_enrollments student_enrollments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_enrollments
    ADD CONSTRAINT student_enrollments_pkey PRIMARY KEY (id);


--
-- Name: students students_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.students
    ADD CONSTRAINT students_pkey PRIMARY KEY (id);


--
-- Name: students students_school_id_registration_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.students
    ADD CONSTRAINT students_school_id_registration_number_key UNIQUE (school_id, registration_number);


--
-- Name: subjects subjects_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subjects
    ADD CONSTRAINT subjects_pkey PRIMARY KEY (id);


--
-- Name: subscriptions subscriptions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscriptions
    ADD CONSTRAINT subscriptions_pkey PRIMARY KEY (id);


--
-- Name: teacher_extra_hours teacher_extra_hours_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teacher_extra_hours
    ADD CONSTRAINT teacher_extra_hours_pkey PRIMARY KEY (id);


--
-- Name: teacher_payments teacher_payments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teacher_payments
    ADD CONSTRAINT teacher_payments_pkey PRIMARY KEY (id);


--
-- Name: teacher_rates teacher_rates_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teacher_rates
    ADD CONSTRAINT teacher_rates_pkey PRIMARY KEY (id);


--
-- Name: teacher_schedule_slots teacher_schedule_slots_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teacher_schedule_slots
    ADD CONSTRAINT teacher_schedule_slots_pkey PRIMARY KEY (id);


--
-- Name: teacher_session_records teacher_session_records_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teacher_session_records
    ADD CONSTRAINT teacher_session_records_pkey PRIMARY KEY (id);


--
-- Name: teachers teachers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teachers
    ADD CONSTRAINT teachers_pkey PRIMARY KEY (id);


--
-- Name: expense_budgets uk_expense_budgets_year_category; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expense_budgets
    ADD CONSTRAINT uk_expense_budgets_year_category UNIQUE (academic_year_id, category_id);


--
-- Name: school_staff uk_school_staff_school_user; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_staff
    ADD CONSTRAINT uk_school_staff_school_user UNIQUE (school_id, user_id);


--
-- Name: teacher_rates uk_teacher_rates_teacher_month; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teacher_rates
    ADD CONSTRAINT uk_teacher_rates_teacher_month UNIQUE (teacher_id, effective_from);


--
-- Name: teachers uk_teacher_school_employee_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teachers
    ADD CONSTRAINT uk_teacher_school_employee_number UNIQUE (school_id, employee_number);


--
-- Name: teacher_session_records uk_teacher_session_slot_date; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teacher_session_records
    ADD CONSTRAINT uk_teacher_session_slot_date UNIQUE (slot_id, session_date);


--
-- Name: attendances uq_attendance_student_class_date; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendances
    ADD CONSTRAINT uq_attendance_student_class_date UNIQUE (student_id, class_id, attendance_date);


--
-- Name: user_platform_roles user_platform_roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_platform_roles
    ADD CONSTRAINT user_platform_roles_pkey PRIMARY KEY (user_id, role);


--
-- Name: user_requested_children user_requested_children_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_requested_children
    ADD CONSTRAINT user_requested_children_pkey PRIMARY KEY (user_id, child_index);


--
-- Name: users users_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: verification_requested_children verification_requested_children_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.verification_requested_children
    ADD CONSTRAINT verification_requested_children_pkey PRIMARY KEY (token_id, child_index);


--
-- Name: idx_absence_reports_school; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_absence_reports_school ON public.absence_reports USING btree (school_id, status, created_at DESC);


--
-- Name: idx_absence_reports_student; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_absence_reports_student ON public.absence_reports USING btree (student_id, start_date);


--
-- Name: idx_conversation_attachments_message; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_conversation_attachments_message ON public.conversation_attachments USING btree (message_id);


--
-- Name: idx_conversation_participants_school; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_conversation_participants_school ON public.conversation_participants USING btree (school_id, conversation_id);


--
-- Name: idx_conversation_participants_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_conversation_participants_user ON public.conversation_participants USING btree (user_id, conversation_id);


--
-- Name: idx_evaluations_period; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_evaluations_period ON public.evaluations USING btree (period_id);


--
-- Name: idx_expenses_category; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_expenses_category ON public.expenses USING btree (category_id);


--
-- Name: idx_expenses_school_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_expenses_school_date ON public.expenses USING btree (school_id, expense_date);


--
-- Name: idx_grade_history_class_period; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_grade_history_class_period ON public.grade_history USING btree (class_id, period_id);


--
-- Name: idx_invoices_student; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_invoices_student ON public.invoices USING btree (student_id);


--
-- Name: idx_parent_appointments_parent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_parent_appointments_parent ON public.parent_appointments USING btree (parent_user_id, student_id);


--
-- Name: idx_parent_appointments_school; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_parent_appointments_school ON public.parent_appointments USING btree (school_id, proposed_at);


--
-- Name: idx_parent_portal_files_post; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_parent_portal_files_post ON public.parent_portal_files USING btree (post_id);


--
-- Name: idx_parent_portal_posts_school; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_parent_portal_posts_school ON public.parent_portal_posts USING btree (school_id, created_at DESC);


--
-- Name: idx_payments_invoice; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payments_invoice ON public.payments USING btree (invoice_id);


--
-- Name: idx_school_access_requests_school_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_school_access_requests_school_status ON public.school_access_requests USING btree (school_id, status);


--
-- Name: idx_school_access_requests_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_school_access_requests_user ON public.school_access_requests USING btree (user_id);


--
-- Name: idx_school_conversation_messages; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_school_conversation_messages ON public.school_conversation_messages USING btree (conversation_id, sent_at);


--
-- Name: idx_school_conversations_parent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_school_conversations_parent ON public.school_conversations USING btree (parent_user_id, last_message_at DESC);


--
-- Name: idx_school_conversations_school; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_school_conversations_school ON public.school_conversations USING btree (school_id, last_message_at DESC);


--
-- Name: idx_school_staff_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_school_staff_user ON public.school_staff USING btree (user_id);


--
-- Name: idx_student_enrollments_student; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_student_enrollments_student ON public.student_enrollments USING btree (student_id);


--
-- Name: idx_student_enrollments_year; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_student_enrollments_year ON public.student_enrollments USING btree (academic_year_id);


--
-- Name: idx_teacher_extra_hours_teacher_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_teacher_extra_hours_teacher_date ON public.teacher_extra_hours USING btree (teacher_id, work_date);


--
-- Name: idx_teacher_payments_teacher_month; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_teacher_payments_teacher_month ON public.teacher_payments USING btree (teacher_id, pay_month);


--
-- Name: idx_teacher_schedule_slots_teacher; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_teacher_schedule_slots_teacher ON public.teacher_schedule_slots USING btree (teacher_id);


--
-- Name: idx_users_pending_approval; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_pending_approval ON public.users USING btree (approved, created_at) WHERE (approved = false);


--
-- Name: idx_users_pending_school; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_pending_school ON public.users USING btree (requested_school_id, created_at) WHERE (approved = false);


--
-- Name: school_status_events_school_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX school_status_events_school_idx ON public.school_status_events USING btree (school_id, changed_at);


--
-- Name: uk_expense_categories_school_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_expense_categories_school_code ON public.expense_categories USING btree (school_id, system_code) WHERE (system_code IS NOT NULL);


--
-- Name: uk_expense_categories_school_name; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_expense_categories_school_name ON public.expense_categories USING btree (school_id, lower((name)::text));


--
-- Name: uk_school_access_requests_pending; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_school_access_requests_pending ON public.school_access_requests USING btree (user_id, school_id, requested_role) WHERE ((status)::text = 'PENDING'::text);


--
-- Name: uq_conversation_school_participant; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_conversation_school_participant ON public.conversation_participants USING btree (conversation_id, school_id) WHERE (school_id IS NOT NULL);


--
-- Name: uq_conversation_user_participant; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_conversation_user_participant ON public.conversation_participants USING btree (conversation_id, user_id) WHERE (user_id IS NOT NULL);


--
-- Name: uq_grades_evaluation_student; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_grades_evaluation_student ON public.grades USING btree (evaluation_id, student_id) WHERE (evaluation_id IS NOT NULL);


--
-- Name: uq_report_cards_period_student; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_report_cards_period_student ON public.report_cards USING btree (period_id, student_id) WHERE (period_id IS NOT NULL);


--
-- Name: absence_reports absence_report_revision; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER absence_report_revision AFTER INSERT OR DELETE OR UPDATE ON public.absence_reports FOR EACH ROW EXECUTE FUNCTION public.bump_school_data_revision();


--
-- Name: academic_year_balances academic_revision; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER academic_revision AFTER INSERT OR DELETE OR UPDATE ON public.academic_year_balances FOR EACH ROW EXECUTE FUNCTION public.bump_academic_data_revision();


--
-- Name: academic_year_receivables academic_revision; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER academic_revision AFTER INSERT OR DELETE OR UPDATE ON public.academic_year_receivables FOR EACH ROW EXECUTE FUNCTION public.bump_academic_data_revision();


--
-- Name: class_subject_teacher academic_revision; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER academic_revision AFTER INSERT OR DELETE OR UPDATE ON public.class_subject_teacher FOR EACH ROW EXECUTE FUNCTION public.bump_academic_data_revision();


--
-- Name: expense_budgets academic_revision; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER academic_revision AFTER INSERT OR DELETE OR UPDATE ON public.expense_budgets FOR EACH ROW EXECUTE FUNCTION public.bump_academic_data_revision();


--
-- Name: expenses academic_revision; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER academic_revision AFTER INSERT OR DELETE OR UPDATE ON public.expenses FOR EACH ROW EXECUTE FUNCTION public.bump_academic_data_revision();


--
-- Name: grade_periods academic_revision; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER academic_revision AFTER INSERT OR DELETE OR UPDATE ON public.grade_periods FOR EACH ROW EXECUTE FUNCTION public.bump_academic_data_revision();


--
-- Name: grades academic_revision; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER academic_revision AFTER INSERT OR DELETE OR UPDATE ON public.grades FOR EACH ROW EXECUTE FUNCTION public.bump_academic_data_revision();


--
-- Name: report_cards academic_revision; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER academic_revision AFTER INSERT OR DELETE OR UPDATE ON public.report_cards FOR EACH ROW EXECUTE FUNCTION public.bump_academic_data_revision();


--
-- Name: school_staff academic_revision; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER academic_revision AFTER INSERT OR DELETE OR UPDATE ON public.school_staff FOR EACH ROW EXECUTE FUNCTION public.bump_academic_data_revision();


--
-- Name: student_enrollments academic_revision; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER academic_revision AFTER INSERT OR DELETE OR UPDATE ON public.student_enrollments FOR EACH ROW EXECUTE FUNCTION public.bump_academic_data_revision();


--
-- Name: academic_years academic_year_revision; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER academic_year_revision AFTER INSERT OR DELETE OR UPDATE ON public.academic_years FOR EACH ROW EXECUTE FUNCTION public.bump_school_data_revision();


--
-- Name: parent_appointments appointment_revision; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER appointment_revision AFTER INSERT OR DELETE OR UPDATE ON public.parent_appointments FOR EACH ROW EXECUTE FUNCTION public.bump_school_data_revision();


--
-- Name: attendances archive_guard; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER archive_guard BEFORE INSERT OR DELETE OR UPDATE ON public.attendances FOR EACH ROW EXECUTE FUNCTION public.protect_closed_academic_year();


--
-- Name: class_subject_teacher archive_guard; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER archive_guard BEFORE INSERT OR DELETE OR UPDATE ON public.class_subject_teacher FOR EACH ROW EXECUTE FUNCTION public.protect_closed_academic_year();


--
-- Name: classes archive_guard; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER archive_guard BEFORE INSERT OR DELETE OR UPDATE ON public.classes FOR EACH ROW EXECUTE FUNCTION public.protect_closed_academic_year();


--
-- Name: evaluations archive_guard; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER archive_guard BEFORE INSERT OR DELETE OR UPDATE ON public.evaluations FOR EACH ROW EXECUTE FUNCTION public.protect_closed_academic_year();


--
-- Name: expense_budgets archive_guard; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER archive_guard BEFORE INSERT OR DELETE OR UPDATE ON public.expense_budgets FOR EACH ROW EXECUTE FUNCTION public.protect_closed_academic_year();


--
-- Name: grade_history archive_guard; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER archive_guard BEFORE INSERT OR DELETE OR UPDATE ON public.grade_history FOR EACH ROW EXECUTE FUNCTION public.protect_closed_academic_year();


--
-- Name: grade_periods archive_guard; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER archive_guard BEFORE INSERT OR DELETE OR UPDATE ON public.grade_periods FOR EACH ROW EXECUTE FUNCTION public.protect_closed_academic_year();


--
-- Name: grades archive_guard; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER archive_guard BEFORE INSERT OR DELETE OR UPDATE ON public.grades FOR EACH ROW EXECUTE FUNCTION public.protect_closed_academic_year();


--
-- Name: report_cards archive_guard; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER archive_guard BEFORE INSERT OR DELETE OR UPDATE ON public.report_cards FOR EACH ROW EXECUTE FUNCTION public.protect_closed_academic_year();


--
-- Name: student_enrollments archive_guard; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER archive_guard BEFORE INSERT OR DELETE OR UPDATE ON public.student_enrollments FOR EACH ROW EXECUTE FUNCTION public.protect_closed_academic_year();


--
-- Name: teacher_extra_hours archive_guard; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER archive_guard BEFORE INSERT OR DELETE OR UPDATE ON public.teacher_extra_hours FOR EACH ROW EXECUTE FUNCTION public.protect_closed_academic_year();


--
-- Name: teacher_schedule_slots archive_guard; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER archive_guard BEFORE INSERT OR DELETE OR UPDATE ON public.teacher_schedule_slots FOR EACH ROW EXECUTE FUNCTION public.protect_closed_academic_year();


--
-- Name: teacher_session_records archive_guard; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER archive_guard BEFORE INSERT OR DELETE OR UPDATE ON public.teacher_session_records FOR EACH ROW EXECUTE FUNCTION public.protect_closed_academic_year();


--
-- Name: attendances attendance_revision; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER attendance_revision AFTER INSERT OR DELETE OR UPDATE ON public.attendances FOR EACH ROW EXECUTE FUNCTION public.bump_school_data_revision();


--
-- Name: classes class_revision; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER class_revision AFTER INSERT OR DELETE OR UPDATE ON public.classes FOR EACH ROW EXECUTE FUNCTION public.bump_school_data_revision();


--
-- Name: expenses expense_archive_guard; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER expense_archive_guard BEFORE INSERT OR DELETE OR UPDATE ON public.expenses FOR EACH ROW EXECUTE FUNCTION public.protect_closed_financial_date();


--
-- Name: invoices invoice_archive_guard; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER invoice_archive_guard BEFORE INSERT OR DELETE OR UPDATE ON public.invoices FOR EACH ROW EXECUTE FUNCTION public.protect_closed_invoice();


--
-- Name: invoices invoice_revision; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER invoice_revision AFTER INSERT OR DELETE OR UPDATE ON public.invoices FOR EACH ROW EXECUTE FUNCTION public.bump_school_data_revision();


--
-- Name: payments payment_archive_guard; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER payment_archive_guard BEFORE INSERT OR DELETE OR UPDATE ON public.payments FOR EACH ROW EXECUTE FUNCTION public.protect_closed_financial_date();


--
-- Name: payments payment_revision; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER payment_revision AFTER INSERT OR DELETE OR UPDATE ON public.payments FOR EACH ROW EXECUTE FUNCTION public.bump_school_data_revision();


--
-- Name: teacher_payments payroll_archive_guard; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER payroll_archive_guard BEFORE INSERT OR DELETE OR UPDATE ON public.teacher_payments FOR EACH ROW EXECUTE FUNCTION public.protect_closed_financial_date();


--
-- Name: parent_portal_posts portal_post_revision; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER portal_post_revision AFTER INSERT OR DELETE OR UPDATE ON public.parent_portal_posts FOR EACH ROW EXECUTE FUNCTION public.bump_school_data_revision();


--
-- Name: schools school_lifecycle_revision; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER school_lifecycle_revision AFTER UPDATE OF status ON public.schools FOR EACH ROW EXECUTE FUNCTION public.bump_school_lifecycle_revision();


--
-- Name: school_users school_user_revision; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER school_user_revision AFTER INSERT OR DELETE OR UPDATE ON public.school_users FOR EACH ROW EXECUTE FUNCTION public.bump_school_data_revision();


--
-- Name: students student_revision; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER student_revision AFTER INSERT OR DELETE OR UPDATE ON public.students FOR EACH ROW EXECUTE FUNCTION public.bump_school_data_revision();


--
-- Name: teachers teacher_revision; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER teacher_revision AFTER INSERT OR DELETE OR UPDATE ON public.teachers FOR EACH ROW EXECUTE FUNCTION public.bump_school_data_revision();


--
-- Name: academic_years year_archive_guard; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER year_archive_guard BEFORE DELETE OR UPDATE ON public.academic_years FOR EACH ROW EXECUTE FUNCTION public.protect_closed_year_metadata();


--
-- Name: absence_reports absence_reports_handled_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.absence_reports
    ADD CONSTRAINT absence_reports_handled_by_fkey FOREIGN KEY (handled_by) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: absence_reports absence_reports_reported_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.absence_reports
    ADD CONSTRAINT absence_reports_reported_by_fkey FOREIGN KEY (reported_by) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: absence_reports absence_reports_school_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.absence_reports
    ADD CONSTRAINT absence_reports_school_id_fkey FOREIGN KEY (school_id) REFERENCES public.schools(id) ON DELETE CASCADE;


--
-- Name: absence_reports absence_reports_student_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.absence_reports
    ADD CONSTRAINT absence_reports_student_id_fkey FOREIGN KEY (student_id) REFERENCES public.students(id) ON DELETE CASCADE;


--
-- Name: academic_year_balances academic_year_balances_academic_year_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_year_balances
    ADD CONSTRAINT academic_year_balances_academic_year_id_fkey FOREIGN KEY (academic_year_id) REFERENCES public.academic_years(id);


--
-- Name: academic_year_receivables academic_year_receivables_academic_year_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_year_receivables
    ADD CONSTRAINT academic_year_receivables_academic_year_id_fkey FOREIGN KEY (academic_year_id) REFERENCES public.academic_years(id);


--
-- Name: academic_year_receivables academic_year_receivables_invoice_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_year_receivables
    ADD CONSTRAINT academic_year_receivables_invoice_id_fkey FOREIGN KEY (invoice_id) REFERENCES public.invoices(id);


--
-- Name: academic_years academic_years_closed_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_years
    ADD CONSTRAINT academic_years_closed_by_fkey FOREIGN KEY (closed_by) REFERENCES public.users(id);


--
-- Name: academic_years academic_years_next_year_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_years
    ADD CONSTRAINT academic_years_next_year_id_fkey FOREIGN KEY (next_year_id) REFERENCES public.academic_years(id);


--
-- Name: academic_years academic_years_school_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_years
    ADD CONSTRAINT academic_years_school_id_fkey FOREIGN KEY (school_id) REFERENCES public.schools(id) ON DELETE CASCADE;


--
-- Name: attendances attendances_class_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendances
    ADD CONSTRAINT attendances_class_id_fkey FOREIGN KEY (class_id) REFERENCES public.classes(id) ON DELETE CASCADE;


--
-- Name: attendances attendances_student_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendances
    ADD CONSTRAINT attendances_student_id_fkey FOREIGN KEY (student_id) REFERENCES public.students(id) ON DELETE CASCADE;


--
-- Name: audit_logs audit_logs_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT audit_logs_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: class_subject_teacher class_subject_teacher_class_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.class_subject_teacher
    ADD CONSTRAINT class_subject_teacher_class_id_fkey FOREIGN KEY (class_id) REFERENCES public.classes(id) ON DELETE CASCADE;


--
-- Name: class_subject_teacher class_subject_teacher_subject_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.class_subject_teacher
    ADD CONSTRAINT class_subject_teacher_subject_id_fkey FOREIGN KEY (subject_id) REFERENCES public.subjects(id) ON DELETE CASCADE;


--
-- Name: class_subject_teacher class_subject_teacher_teacher_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.class_subject_teacher
    ADD CONSTRAINT class_subject_teacher_teacher_id_fkey FOREIGN KEY (teacher_id) REFERENCES public.teachers(id) ON DELETE CASCADE;


--
-- Name: classes classes_academic_year_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.classes
    ADD CONSTRAINT classes_academic_year_id_fkey FOREIGN KEY (academic_year_id) REFERENCES public.academic_years(id) ON DELETE CASCADE;


--
-- Name: classes classes_level_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.classes
    ADD CONSTRAINT classes_level_id_fkey FOREIGN KEY (level_id) REFERENCES public.levels(id);


--
-- Name: classes classes_school_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.classes
    ADD CONSTRAINT classes_school_id_fkey FOREIGN KEY (school_id) REFERENCES public.schools(id) ON DELETE CASCADE;


--
-- Name: conversation_attachment_content conversation_attachment_content_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.conversation_attachment_content
    ADD CONSTRAINT conversation_attachment_content_id_fkey FOREIGN KEY (id) REFERENCES public.conversation_attachments(id) ON DELETE CASCADE;


--
-- Name: conversation_attachments conversation_attachments_message_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.conversation_attachments
    ADD CONSTRAINT conversation_attachments_message_id_fkey FOREIGN KEY (message_id) REFERENCES public.school_conversation_messages(id) ON DELETE CASCADE;


--
-- Name: conversation_participants conversation_participants_conversation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.conversation_participants
    ADD CONSTRAINT conversation_participants_conversation_id_fkey FOREIGN KEY (conversation_id) REFERENCES public.school_conversations(id) ON DELETE CASCADE;


--
-- Name: conversation_participants conversation_participants_school_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.conversation_participants
    ADD CONSTRAINT conversation_participants_school_id_fkey FOREIGN KEY (school_id) REFERENCES public.schools(id) ON DELETE CASCADE;


--
-- Name: conversation_participants conversation_participants_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.conversation_participants
    ADD CONSTRAINT conversation_participants_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: documents documents_class_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documents
    ADD CONSTRAINT documents_class_id_fkey FOREIGN KEY (class_id) REFERENCES public.classes(id) ON DELETE SET NULL;


--
-- Name: documents documents_school_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documents
    ADD CONSTRAINT documents_school_id_fkey FOREIGN KEY (school_id) REFERENCES public.schools(id) ON DELETE CASCADE;


--
-- Name: documents documents_uploaded_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documents
    ADD CONSTRAINT documents_uploaded_by_fkey FOREIGN KEY (uploaded_by) REFERENCES public.users(id);


--
-- Name: email_verification_tokens email_verification_tokens_requested_school_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_verification_tokens
    ADD CONSTRAINT email_verification_tokens_requested_school_id_fkey FOREIGN KEY (requested_school_id) REFERENCES public.schools(id) ON DELETE SET NULL;


--
-- Name: email_verification_tokens email_verification_tokens_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_verification_tokens
    ADD CONSTRAINT email_verification_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: evaluations evaluations_class_subject_teacher_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evaluations
    ADD CONSTRAINT evaluations_class_subject_teacher_id_fkey FOREIGN KEY (class_subject_teacher_id) REFERENCES public.class_subject_teacher(id) ON DELETE CASCADE;


--
-- Name: evaluations evaluations_period_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evaluations
    ADD CONSTRAINT evaluations_period_id_fkey FOREIGN KEY (period_id) REFERENCES public.grade_periods(id) ON DELETE CASCADE;


--
-- Name: expense_budgets expense_budgets_academic_year_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expense_budgets
    ADD CONSTRAINT expense_budgets_academic_year_id_fkey FOREIGN KEY (academic_year_id) REFERENCES public.academic_years(id) ON DELETE CASCADE;


--
-- Name: expense_budgets expense_budgets_category_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expense_budgets
    ADD CONSTRAINT expense_budgets_category_id_fkey FOREIGN KEY (category_id) REFERENCES public.expense_categories(id) ON DELETE CASCADE;


--
-- Name: expense_categories expense_categories_school_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expense_categories
    ADD CONSTRAINT expense_categories_school_id_fkey FOREIGN KEY (school_id) REFERENCES public.schools(id) ON DELETE CASCADE;


--
-- Name: expenses expenses_category_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expenses
    ADD CONSTRAINT expenses_category_id_fkey FOREIGN KEY (category_id) REFERENCES public.expense_categories(id);


--
-- Name: expenses expenses_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expenses
    ADD CONSTRAINT expenses_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: expenses expenses_school_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expenses
    ADD CONSTRAINT expenses_school_id_fkey FOREIGN KEY (school_id) REFERENCES public.schools(id) ON DELETE CASCADE;


--
-- Name: fee_types fee_types_level_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fee_types
    ADD CONSTRAINT fee_types_level_id_fkey FOREIGN KEY (level_id) REFERENCES public.levels(id) ON DELETE SET NULL;


--
-- Name: fee_types fee_types_school_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fee_types
    ADD CONSTRAINT fee_types_school_id_fkey FOREIGN KEY (school_id) REFERENCES public.schools(id) ON DELETE CASCADE;


--
-- Name: grade_history grade_history_changed_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grade_history
    ADD CONSTRAINT grade_history_changed_by_fkey FOREIGN KEY (changed_by) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: grade_history grade_history_class_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grade_history
    ADD CONSTRAINT grade_history_class_id_fkey FOREIGN KEY (class_id) REFERENCES public.classes(id) ON DELETE CASCADE;


--
-- Name: grade_history grade_history_evaluation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grade_history
    ADD CONSTRAINT grade_history_evaluation_id_fkey FOREIGN KEY (evaluation_id) REFERENCES public.evaluations(id) ON DELETE SET NULL;


--
-- Name: grade_history grade_history_period_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grade_history
    ADD CONSTRAINT grade_history_period_id_fkey FOREIGN KEY (period_id) REFERENCES public.grade_periods(id) ON DELETE CASCADE;


--
-- Name: grade_history grade_history_student_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grade_history
    ADD CONSTRAINT grade_history_student_id_fkey FOREIGN KEY (student_id) REFERENCES public.students(id) ON DELETE CASCADE;


--
-- Name: grade_periods grade_periods_academic_year_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grade_periods
    ADD CONSTRAINT grade_periods_academic_year_id_fkey FOREIGN KEY (academic_year_id) REFERENCES public.academic_years(id) ON DELETE CASCADE;


--
-- Name: grade_periods grade_periods_school_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grade_periods
    ADD CONSTRAINT grade_periods_school_id_fkey FOREIGN KEY (school_id) REFERENCES public.schools(id) ON DELETE CASCADE;


--
-- Name: grades grades_class_subject_teacher_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grades
    ADD CONSTRAINT grades_class_subject_teacher_id_fkey FOREIGN KEY (class_subject_teacher_id) REFERENCES public.class_subject_teacher(id) ON DELETE CASCADE;


--
-- Name: grades grades_evaluation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grades
    ADD CONSTRAINT grades_evaluation_id_fkey FOREIGN KEY (evaluation_id) REFERENCES public.evaluations(id) ON DELETE CASCADE;


--
-- Name: grades grades_student_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grades
    ADD CONSTRAINT grades_student_id_fkey FOREIGN KEY (student_id) REFERENCES public.students(id) ON DELETE CASCADE;


--
-- Name: invoices invoices_academic_year_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT invoices_academic_year_id_fkey FOREIGN KEY (academic_year_id) REFERENCES public.academic_years(id) ON DELETE CASCADE;


--
-- Name: invoices invoices_fee_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT invoices_fee_type_id_fkey FOREIGN KEY (fee_type_id) REFERENCES public.fee_types(id);


--
-- Name: invoices invoices_student_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT invoices_student_id_fkey FOREIGN KEY (student_id) REFERENCES public.students(id) ON DELETE CASCADE;


--
-- Name: levels levels_school_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.levels
    ADD CONSTRAINT levels_school_id_fkey FOREIGN KEY (school_id) REFERENCES public.schools(id) ON DELETE CASCADE;


--
-- Name: messages messages_receiver_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.messages
    ADD CONSTRAINT messages_receiver_id_fkey FOREIGN KEY (receiver_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: messages messages_sender_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.messages
    ADD CONSTRAINT messages_sender_id_fkey FOREIGN KEY (sender_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: notifications notifications_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: parent_appointments parent_appointments_parent_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.parent_appointments
    ADD CONSTRAINT parent_appointments_parent_user_id_fkey FOREIGN KEY (parent_user_id) REFERENCES public.users(id);


--
-- Name: parent_appointments parent_appointments_school_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.parent_appointments
    ADD CONSTRAINT parent_appointments_school_id_fkey FOREIGN KEY (school_id) REFERENCES public.schools(id);


--
-- Name: parent_appointments parent_appointments_student_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.parent_appointments
    ADD CONSTRAINT parent_appointments_student_id_fkey FOREIGN KEY (student_id) REFERENCES public.students(id);


--
-- Name: parent_appointments parent_appointments_teacher_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.parent_appointments
    ADD CONSTRAINT parent_appointments_teacher_user_id_fkey FOREIGN KEY (teacher_user_id) REFERENCES public.users(id);


--
-- Name: parent_portal_files parent_portal_files_post_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.parent_portal_files
    ADD CONSTRAINT parent_portal_files_post_id_fkey FOREIGN KEY (post_id) REFERENCES public.parent_portal_posts(id) ON DELETE CASCADE;


--
-- Name: parent_portal_posts parent_portal_posts_author_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.parent_portal_posts
    ADD CONSTRAINT parent_portal_posts_author_id_fkey FOREIGN KEY (author_id) REFERENCES public.users(id);


--
-- Name: parent_portal_posts parent_portal_posts_class_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.parent_portal_posts
    ADD CONSTRAINT parent_portal_posts_class_id_fkey FOREIGN KEY (class_id) REFERENCES public.classes(id);


--
-- Name: parent_portal_posts parent_portal_posts_school_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.parent_portal_posts
    ADD CONSTRAINT parent_portal_posts_school_id_fkey FOREIGN KEY (school_id) REFERENCES public.schools(id);


--
-- Name: parent_portal_posts parent_portal_posts_student_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.parent_portal_posts
    ADD CONSTRAINT parent_portal_posts_student_id_fkey FOREIGN KEY (student_id) REFERENCES public.students(id);


--
-- Name: parent_student parent_student_parent_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.parent_student
    ADD CONSTRAINT parent_student_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES public.parents(id) ON DELETE CASCADE;


--
-- Name: parent_student parent_student_student_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.parent_student
    ADD CONSTRAINT parent_student_student_id_fkey FOREIGN KEY (student_id) REFERENCES public.students(id) ON DELETE CASCADE;


--
-- Name: parents parents_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.parents
    ADD CONSTRAINT parents_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: password_reset_tokens password_reset_tokens_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT password_reset_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: payments payments_invoice_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT payments_invoice_id_fkey FOREIGN KEY (invoice_id) REFERENCES public.invoices(id) ON DELETE CASCADE;


--
-- Name: report_cards report_cards_academic_year_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.report_cards
    ADD CONSTRAINT report_cards_academic_year_id_fkey FOREIGN KEY (academic_year_id) REFERENCES public.academic_years(id) ON DELETE CASCADE;


--
-- Name: report_cards report_cards_class_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.report_cards
    ADD CONSTRAINT report_cards_class_id_fkey FOREIGN KEY (class_id) REFERENCES public.classes(id) ON DELETE CASCADE;


--
-- Name: report_cards report_cards_period_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.report_cards
    ADD CONSTRAINT report_cards_period_id_fkey FOREIGN KEY (period_id) REFERENCES public.grade_periods(id) ON DELETE CASCADE;


--
-- Name: report_cards report_cards_student_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.report_cards
    ADD CONSTRAINT report_cards_student_id_fkey FOREIGN KEY (student_id) REFERENCES public.students(id) ON DELETE CASCADE;


--
-- Name: school_access_requested_children school_access_requested_children_request_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_access_requested_children
    ADD CONSTRAINT school_access_requested_children_request_id_fkey FOREIGN KEY (request_id) REFERENCES public.school_access_requests(id) ON DELETE CASCADE;


--
-- Name: school_access_requests school_access_requests_decided_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_access_requests
    ADD CONSTRAINT school_access_requests_decided_by_fkey FOREIGN KEY (decided_by) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: school_access_requests school_access_requests_school_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_access_requests
    ADD CONSTRAINT school_access_requests_school_id_fkey FOREIGN KEY (school_id) REFERENCES public.schools(id) ON DELETE CASCADE;


--
-- Name: school_access_requests school_access_requests_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_access_requests
    ADD CONSTRAINT school_access_requests_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: school_conversation_messages school_conversation_messages_conversation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_conversation_messages
    ADD CONSTRAINT school_conversation_messages_conversation_id_fkey FOREIGN KEY (conversation_id) REFERENCES public.school_conversations(id) ON DELETE CASCADE;


--
-- Name: school_conversation_messages school_conversation_messages_sender_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_conversation_messages
    ADD CONSTRAINT school_conversation_messages_sender_id_fkey FOREIGN KEY (sender_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: school_conversations school_conversations_parent_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_conversations
    ADD CONSTRAINT school_conversations_parent_user_id_fkey FOREIGN KEY (parent_user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: school_conversations school_conversations_school_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_conversations
    ADD CONSTRAINT school_conversations_school_id_fkey FOREIGN KEY (school_id) REFERENCES public.schools(id) ON DELETE CASCADE;


--
-- Name: school_conversations school_conversations_student_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_conversations
    ADD CONSTRAINT school_conversations_student_id_fkey FOREIGN KEY (student_id) REFERENCES public.students(id) ON DELETE SET NULL;


--
-- Name: school_data_revisions school_data_revisions_school_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_data_revisions
    ADD CONSTRAINT school_data_revisions_school_id_fkey FOREIGN KEY (school_id) REFERENCES public.schools(id) ON DELETE CASCADE;


--
-- Name: school_staff school_staff_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_staff
    ADD CONSTRAINT school_staff_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: school_staff_modules school_staff_modules_staff_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_staff_modules
    ADD CONSTRAINT school_staff_modules_staff_id_fkey FOREIGN KEY (staff_id) REFERENCES public.school_staff(id) ON DELETE CASCADE;


--
-- Name: school_staff school_staff_school_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_staff
    ADD CONSTRAINT school_staff_school_id_fkey FOREIGN KEY (school_id) REFERENCES public.schools(id) ON DELETE CASCADE;


--
-- Name: school_staff school_staff_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_staff
    ADD CONSTRAINT school_staff_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: school_status_events school_status_events_actor_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_status_events
    ADD CONSTRAINT school_status_events_actor_id_fkey FOREIGN KEY (actor_id) REFERENCES public.users(id);


--
-- Name: school_status_events school_status_events_school_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_status_events
    ADD CONSTRAINT school_status_events_school_id_fkey FOREIGN KEY (school_id) REFERENCES public.schools(id);


--
-- Name: school_users school_users_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_users
    ADD CONSTRAINT school_users_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.roles(id);


--
-- Name: school_users school_users_school_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_users
    ADD CONSTRAINT school_users_school_id_fkey FOREIGN KEY (school_id) REFERENCES public.schools(id) ON DELETE CASCADE;


--
-- Name: school_users school_users_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_users
    ADD CONSTRAINT school_users_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: schools schools_owner_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.schools
    ADD CONSTRAINT schools_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES public.users(id);


--
-- Name: student_enrollments student_enrollments_academic_year_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_enrollments
    ADD CONSTRAINT student_enrollments_academic_year_id_fkey FOREIGN KEY (academic_year_id) REFERENCES public.academic_years(id) ON DELETE CASCADE;


--
-- Name: student_enrollments student_enrollments_class_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_enrollments
    ADD CONSTRAINT student_enrollments_class_id_fkey FOREIGN KEY (class_id) REFERENCES public.classes(id) ON DELETE CASCADE;


--
-- Name: student_enrollments student_enrollments_student_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_enrollments
    ADD CONSTRAINT student_enrollments_student_id_fkey FOREIGN KEY (student_id) REFERENCES public.students(id) ON DELETE CASCADE;


--
-- Name: students students_school_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.students
    ADD CONSTRAINT students_school_id_fkey FOREIGN KEY (school_id) REFERENCES public.schools(id) ON DELETE CASCADE;


--
-- Name: students students_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.students
    ADD CONSTRAINT students_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: subjects subjects_school_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subjects
    ADD CONSTRAINT subjects_school_id_fkey FOREIGN KEY (school_id) REFERENCES public.schools(id) ON DELETE CASCADE;


--
-- Name: subscriptions subscriptions_school_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscriptions
    ADD CONSTRAINT subscriptions_school_id_fkey FOREIGN KEY (school_id) REFERENCES public.schools(id) ON DELETE CASCADE;


--
-- Name: teacher_extra_hours teacher_extra_hours_class_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teacher_extra_hours
    ADD CONSTRAINT teacher_extra_hours_class_id_fkey FOREIGN KEY (class_id) REFERENCES public.classes(id) ON DELETE CASCADE;


--
-- Name: teacher_extra_hours teacher_extra_hours_teacher_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teacher_extra_hours
    ADD CONSTRAINT teacher_extra_hours_teacher_id_fkey FOREIGN KEY (teacher_id) REFERENCES public.teachers(id) ON DELETE CASCADE;


--
-- Name: teacher_payments teacher_payments_teacher_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teacher_payments
    ADD CONSTRAINT teacher_payments_teacher_id_fkey FOREIGN KEY (teacher_id) REFERENCES public.teachers(id) ON DELETE CASCADE;


--
-- Name: teacher_rates teacher_rates_teacher_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teacher_rates
    ADD CONSTRAINT teacher_rates_teacher_id_fkey FOREIGN KEY (teacher_id) REFERENCES public.teachers(id) ON DELETE CASCADE;


--
-- Name: teacher_schedule_slots teacher_schedule_slots_class_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teacher_schedule_slots
    ADD CONSTRAINT teacher_schedule_slots_class_id_fkey FOREIGN KEY (class_id) REFERENCES public.classes(id) ON DELETE CASCADE;


--
-- Name: teacher_schedule_slots teacher_schedule_slots_teacher_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teacher_schedule_slots
    ADD CONSTRAINT teacher_schedule_slots_teacher_id_fkey FOREIGN KEY (teacher_id) REFERENCES public.teachers(id) ON DELETE CASCADE;


--
-- Name: teacher_session_records teacher_session_records_slot_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teacher_session_records
    ADD CONSTRAINT teacher_session_records_slot_id_fkey FOREIGN KEY (slot_id) REFERENCES public.teacher_schedule_slots(id) ON DELETE CASCADE;


--
-- Name: teachers teachers_school_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teachers
    ADD CONSTRAINT teachers_school_id_fkey FOREIGN KEY (school_id) REFERENCES public.schools(id) ON DELETE CASCADE;


--
-- Name: teachers teachers_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teachers
    ADD CONSTRAINT teachers_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: user_platform_roles user_platform_roles_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_platform_roles
    ADD CONSTRAINT user_platform_roles_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: user_requested_children user_requested_children_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_requested_children
    ADD CONSTRAINT user_requested_children_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: users users_requested_school_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_requested_school_id_fkey FOREIGN KEY (requested_school_id) REFERENCES public.schools(id);


--
-- Name: verification_requested_children verification_requested_children_token_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.verification_requested_children
    ADD CONSTRAINT verification_requested_children_token_id_fkey FOREIGN KEY (token_id) REFERENCES public.email_verification_tokens(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--



-- Required reference data. Preserve the original role ordering.
INSERT INTO public.roles (name) VALUES
    ('SUPER_ADMIN'),
    ('SCHOOL_ADMIN'),
    ('TEACHER'),
    ('PARENT'),
    ('STUDENT'),
    ('STAFF');
