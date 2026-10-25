-- Keep existing users and their requests, without assigning a shared/default password.
ALTER TABLE users ADD COLUMN password_hash VARCHAR(255);
CREATE UNIQUE INDEX ux_users_email_case_insensitive ON users (lower(email));
