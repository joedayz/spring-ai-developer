CREATE TABLE IF NOT EXISTS orders (
    id          BIGINT PRIMARY KEY,
    customer_id VARCHAR(20) NOT NULL,
    item        VARCHAR(100) NOT NULL,
    total       DECIMAL(10,2) NOT NULL,
    status      VARCHAR(20) NOT NULL,
    tracking    VARCHAR(30)
);
