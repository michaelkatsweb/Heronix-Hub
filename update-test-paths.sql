-- Update product paths to point to Hub JAR for testing SSO
-- This allows you to test SSO by launching multiple Hub instances

UPDATE products
SET executable_path = 'H:\Heronix\Heronix-Hub\target\heronix-hub-1.0.0.jar',
    is_installed = TRUE
WHERE product_code = 'SIS';

UPDATE products
SET executable_path = 'H:\Heronix\Heronix-Hub\target\heronix-hub-1.0.0.jar',
    is_installed = TRUE
WHERE product_code = 'SCHEDULER';

UPDATE products
SET executable_path = 'H:\Heronix\Heronix-Hub\target\heronix-hub-1.0.0.jar',
    is_installed = TRUE
WHERE product_code = 'TIME';

UPDATE products
SET executable_path = 'H:\Heronix\Heronix-Hub\target\heronix-hub-1.0.0.jar',
    is_installed = TRUE
WHERE product_code = 'POS';

-- Verify the update
SELECT product_code, product_name, executable_path, is_installed
FROM products;
