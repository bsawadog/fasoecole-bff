ALTER TABLE school_staff_modules DROP CONSTRAINT IF EXISTS school_staff_modules_module_check;
ALTER TABLE school_staff_modules ADD CONSTRAINT school_staff_modules_module_check CHECK (module IN
 ('DASHBOARD','MANAGEMENT','STUDENTS','TEACHERS','FINANCE','EXPENSES','GRADES','ENROLLMENT',
  'CALENDAR','DISCIPLINE','LIBRARY','HOMEWORK','REQUESTS','PARENT_PORTAL','MESSAGES','CASH_BOOK','FINANCIAL_STATEMENTS','ASSETS','INVENTORY'));

CREATE TABLE school_assets (
 id BIGSERIAL PRIMARY KEY,
 school_id BIGINT NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
 code VARCHAR(40) NOT NULL,
 name VARCHAR(150) NOT NULL,
 category VARCHAR(30) NOT NULL CHECK(category IN ('LAND','BUILDING','ROOM','DESK_BENCH','TABLE','CHAIR','CABINET','COMPUTER','EQUIPMENT','OTHER')),
 location VARCHAR(150) NOT NULL,
 custodian VARCHAR(150),
 ownership VARCHAR(20) NOT NULL CHECK(ownership IN ('OWNED','RENTED','BORROWED')),
 condition VARCHAR(20) NOT NULL CHECK(condition IN ('GOOD','FAIR','DAMAGED','UNUSABLE')),
 status VARCHAR(20) NOT NULL CHECK(status IN ('IN_USE','STORED','MAINTENANCE','RETIRED')),
 quantity INTEGER NOT NULL CHECK(quantity BETWEEN 1 AND 1000000),
 unit_value NUMERIC(14,2) CHECK(unit_value >= 0),
 acquired_on DATE,
 maintenance_on DATE,
 notes VARCHAR(2000),
 version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP NOT NULL DEFAULT now(),
 updated_at TIMESTAMP NOT NULL DEFAULT now(),
 UNIQUE(school_id,code), UNIQUE(school_id,id)
);
CREATE TABLE school_asset_events (
 id BIGSERIAL PRIMARY KEY,
 school_id BIGINT NOT NULL,
 asset_id BIGINT NOT NULL,
 event_type VARCHAR(20) NOT NULL CHECK(event_type IN ('CREATE','UPDATE')),
 reason VARCHAR(500) NOT NULL,
 snapshot JSONB NOT NULL,
 created_by BIGINT NOT NULL REFERENCES users(id),
 created_at TIMESTAMP NOT NULL DEFAULT now(),
 FOREIGN KEY(school_id,asset_id) REFERENCES school_assets(school_id,id) ON DELETE CASCADE
);
CREATE INDEX idx_school_asset_events ON school_asset_events(school_id,asset_id,id DESC);

CREATE TABLE school_stock_items (
 id BIGSERIAL PRIMARY KEY,
 school_id BIGINT NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
 code VARCHAR(40) NOT NULL,
 name VARCHAR(150) NOT NULL,
 category VARCHAR(30) NOT NULL CHECK(category IN ('CLEANING','TEACHING','OFFICE','SMALL_EQUIPMENT','OTHER')),
 unit VARCHAR(30) NOT NULL,
 location VARCHAR(150) NOT NULL,
 minimum_quantity NUMERIC(14,3) NOT NULL DEFAULT 0 CHECK(minimum_quantity>=0),
 quantity NUMERIC(14,3) NOT NULL DEFAULT 0 CHECK(quantity>=0),
 unit_cost NUMERIC(14,2) CHECK(unit_cost>=0),
 active BOOLEAN NOT NULL DEFAULT TRUE,
 notes VARCHAR(2000),
 version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP NOT NULL DEFAULT now(),
 updated_at TIMESTAMP NOT NULL DEFAULT now(),
 UNIQUE(school_id,code), UNIQUE(school_id,id)
);
CREATE TABLE school_stock_movements (
 id BIGSERIAL PRIMARY KEY,
 school_id BIGINT NOT NULL,
 item_id BIGINT NOT NULL,
 request_id UUID NOT NULL,
 kind VARCHAR(10) NOT NULL CHECK(kind IN ('IN','OUT','COUNT')),
 movement_date DATE NOT NULL,
 requested_quantity NUMERIC(14,3) NOT NULL CHECK(requested_quantity>=0),
 quantity_change NUMERIC(14,3) NOT NULL,
 balance_after NUMERIC(14,3) NOT NULL CHECK(balance_after>=0),
 reason VARCHAR(500) NOT NULL,
 recipient VARCHAR(150),
 reference VARCHAR(100),
 created_by BIGINT NOT NULL REFERENCES users(id),
 created_at TIMESTAMP NOT NULL DEFAULT now(),
 FOREIGN KEY(school_id,item_id) REFERENCES school_stock_items(school_id,id) ON DELETE CASCADE,
 UNIQUE(school_id,request_id),
 CHECK(kind='COUNT' OR requested_quantity>0),
 CHECK((kind='IN' AND quantity_change=requested_quantity) OR (kind='OUT' AND quantity_change=-requested_quantity) OR kind='COUNT')
);
CREATE INDEX idx_school_stock_movements ON school_stock_movements(school_id,item_id,id DESC);
