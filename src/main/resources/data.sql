-- Default admin user (username: admin, password: admin123)
-- Password hash generated with BCrypt strength 10
MERGE INTO users (username, password_hash, full_name, role, is_active)
KEY (username)
VALUES ('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Administrator', 'ADMIN', TRUE);

-- Register Heronix products
MERGE INTO products (product_code, product_name, executable_path, is_installed)
KEY (product_code)
VALUES ('SIS', 'Heronix-SIS', './heronix-sis.jar', FALSE);

MERGE INTO products (product_code, product_name, executable_path, is_installed)
KEY (product_code)
VALUES ('SCHEDULER', 'Heronix-Scheduler', './heronix-scheduler.jar', FALSE);

MERGE INTO products (product_code, product_name, executable_path, is_installed)
KEY (product_code)
VALUES ('TIME', 'Heronix-Time', './heronix-time.jar', FALSE);

MERGE INTO products (product_code, product_name, executable_path, is_installed)
KEY (product_code)
VALUES ('POS', 'Heronix-POS', './heronix-pos.jar', FALSE);
