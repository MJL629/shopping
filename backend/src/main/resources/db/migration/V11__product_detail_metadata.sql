-- AI-assisted product detail schema; historical order snapshots remain unchanged.
ALTER TABLE product ADD COLUMN gallery_json TEXT;
ALTER TABLE product ADD COLUMN specifications_json TEXT;
ALTER TABLE product ADD COLUMN source_price DECIMAL(10,2);
ALTER TABLE product ADD COLUMN source_currency VARCHAR(3);
ALTER TABLE product ADD COLUMN source_observed_at VARCHAR(40);
ALTER TABLE product ADD COLUMN merchant_name VARCHAR(100);
