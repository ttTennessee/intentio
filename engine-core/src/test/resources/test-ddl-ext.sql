DROP TABLE IF EXISTS comment;
DROP TABLE IF EXISTS post;
DROP TABLE IF EXISTS audit;
DROP TABLE IF EXISTS token;
DROP TABLE IF EXISTS account;

CREATE TABLE account (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    username   VARCHAR(64)  NOT NULL,
    secret     VARCHAR(128),
    age        BIGINT,
    status     BIGINT       NOT NULL DEFAULT 1,
    joined_on  DATE,
    last_seen  TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE TABLE post (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    account_id BIGINT       NOT NULL,
    title      VARCHAR(128),
    PRIMARY KEY (id)
);

CREATE TABLE comment (
    id      BIGINT       NOT NULL AUTO_INCREMENT,
    post_id BIGINT       NOT NULL,
    body    VARCHAR(255),
    PRIMARY KEY (id)
);

CREATE TABLE audit (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    account_id BIGINT,
    action     VARCHAR(64),
    PRIMARY KEY (id)
);

CREATE TABLE token (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    account_id BIGINT      NOT NULL,
    tok_value  VARCHAR(64),
    PRIMARY KEY (id)
);
