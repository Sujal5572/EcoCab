CREATE TABLE IF NOT EXISTS shedlock (
    name VARCHAR(64) PRIMARY KEY,
    lock_until TIMESTAMP,
    locked_at TIMESTAMP,
    locked_by VARCHAR(255)
);