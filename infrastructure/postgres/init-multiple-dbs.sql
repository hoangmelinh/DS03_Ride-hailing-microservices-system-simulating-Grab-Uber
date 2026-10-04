-- ==============================================================================
-- GoRide Distributed System - PostgreSQL Multiple Database Initialization
-- Mount this file to /docker-entrypoint-initdb.d/init-multiple-dbs.sql
-- or run directly via: psql -U goride_admin -f init-multiple-dbs.sql
-- ==============================================================================

-- 1. User & Authentication Database
CREATE DATABASE goride_user;
GRANT ALL PRIVILEGES ON DATABASE goride_user TO CURRENT_USER;

-- 2. Driver & Reservation Database
CREATE DATABASE goride_driver;
GRANT ALL PRIVILEGES ON DATABASE goride_driver TO CURRENT_USER;

-- 3. Trip Lifecycle Database
CREATE DATABASE goride_trip;
GRANT ALL PRIVILEGES ON DATABASE goride_trip TO CURRENT_USER;

-- 4. Matching & Saga Database
CREATE DATABASE goride_matching;
GRANT ALL PRIVILEGES ON DATABASE goride_matching TO CURRENT_USER;

-- 5. Payment Database
CREATE DATABASE goride_payment;
GRANT ALL PRIVILEGES ON DATABASE goride_payment TO CURRENT_USER;

-- 6. Notification Database
CREATE DATABASE goride_notification;
GRANT ALL PRIVILEGES ON DATABASE goride_notification TO CURRENT_USER;
