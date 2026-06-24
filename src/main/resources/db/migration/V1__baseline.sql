-- Baseline migration. Feature migrations are added by the harness per PRD/FRS.
CREATE TABLE IF NOT EXISTS app_info (
    id   BIGINT       NOT NULL AUTO_INCREMENT,
    name VARCHAR(64)  NOT NULL,
    PRIMARY KEY (id)
);
