DROP TABLE IF EXISTS prescription_item;
DROP TABLE IF EXISTS prescription;
DROP TABLE IF EXISTS drug_stock;
DROP TABLE IF EXISTS drug;

CREATE TABLE drug (
    id        BIGINT      NOT NULL AUTO_INCREMENT,
    name      VARCHAR(64) NOT NULL,
    unit      VARCHAR(32) NOT NULL,
    category  VARCHAR(16),
    PRIMARY KEY (id)
);

CREATE TABLE drug_stock (
    id        BIGINT        NOT NULL AUTO_INCREMENT,
    drug_id   BIGINT        NOT NULL,
    quantity  DECIMAL(10,3) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE prescription (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    clinic_id  BIGINT      NOT NULL,
    patient    VARCHAR(64) NOT NULL,
    issued_at  DATE        NOT NULL,
    status     VARCHAR(16) NOT NULL DEFAULT 'draft',
    PRIMARY KEY (id)
);

CREATE TABLE prescription_item (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    prescription_id BIGINT        NOT NULL,
    drug_id         BIGINT        NOT NULL,
    weight          DECIMAL(8,3)  NOT NULL,
    unit_price      DECIMAL(10,2) NOT NULL,
    PRIMARY KEY (id)
);
