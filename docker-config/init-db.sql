-- 🏗️ DETERMINISTIC INITIALIZATION SCRIPT

-- 1. Create all required databases
CREATE DATABASE IF NOT EXISTS connectsphere_auth;
CREATE DATABASE IF NOT EXISTS comment_db;
CREATE DATABASE IF NOT EXISTS follow_db;
CREATE DATABASE IF NOT EXISTS like_db;
CREATE DATABASE IF NOT EXISTS media_db;
CREATE DATABASE IF NOT EXISTS notification_db;
CREATE DATABASE IF NOT EXISTS post_db;
CREATE DATABASE IF NOT EXISTS search_db;

-- 2. Create the application user explicitly with the NATIVE password plugin.
-- This removes the need for RSA key exchange and 'allowPublicKeyRetrieval'.
CREATE USER IF NOT EXISTS 'mukul'@'%' IDENTIFIED WITH mysql_native_password BY 'password';

-- 3. Grant full privileges to the user
GRANT ALL PRIVILEGES ON *.* TO 'mukul'@'%';
FLUSH PRIVILEGES;
