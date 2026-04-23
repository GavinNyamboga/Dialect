CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    full_name TEXT NOT NULL,
    email TEXT NOT NULL UNIQUE,
    country TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS products (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    category TEXT NOT NULL,
    price NUMERIC(10, 2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE IF NOT EXISTS orders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    status TEXT NOT NULL,
    total NUMERIC(10, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    product_id BIGINT NOT NULL REFERENCES products(id),
    quantity INT NOT NULL,
    unit_price NUMERIC(10, 2) NOT NULL
);

INSERT INTO users (id, full_name, email, country, created_at) VALUES
(1, 'Amina Yusuf', 'amina@example.com', 'Kenya', '2026-01-10 09:15:00'),
(2, 'Daniel Kimani', 'daniel@example.com', 'Kenya', '2026-01-12 14:30:00'),
(3, 'Grace Okafor', 'grace@example.com', 'Nigeria', '2026-02-03 11:00:00'),
(4, 'Liam Carter', 'liam@example.com', 'United States', '2026-02-20 18:45:00'),
(5, 'Sofia Mendes', 'sofia@example.com', 'Brazil', '2026-01-18 08:00:00'),
(6, 'Noah Smith', 'noah@example.com', 'United Kingdom', '2026-01-22 13:25:00'),
(7, 'Fatima Ali', 'fatima@example.com', 'Egypt', '2026-02-01 10:10:00'),
(8, 'Mei Chen', 'mei@example.com', 'China', '2026-02-06 16:40:00'),
(9, 'Arjun Patel', 'arjun@example.com', 'India', '2026-02-11 12:05:00'),
(10, 'Emma Johnson', 'emma@example.com', 'Canada', '2026-02-14 09:55:00'),
(11, 'Carlos Rivera', 'carlos@example.com', 'Mexico', '2026-02-18 17:35:00'),
(12, 'Nadia Hassan', 'nadia@example.com', 'Morocco', '2026-02-22 11:45:00'),
(13, 'Oliver Brown', 'oliver@example.com', 'Australia', '2026-02-25 15:20:00'),
(14, 'Thandi Ndlovu', 'thandi@example.com', 'South Africa', '2026-03-01 07:50:00'),
(15, 'Hannah Becker', 'hannah@example.com', 'Germany', '2026-03-04 14:15:00'),
(16, 'Yuki Tanaka', 'yuki@example.com', 'Japan', '2026-03-07 18:30:00'),
(17, 'Marta Kowalski', 'marta@example.com', 'Poland', '2026-03-10 09:05:00'),
(18, 'Lucas Martin', 'lucas@example.com', 'France', '2026-03-13 13:10:00'),
(19, 'Isabella Rossi', 'isabella@example.com', 'Italy', '2026-03-16 16:25:00'),
(20, 'Sam Wilson', 'sam@example.com', 'United States', '2026-03-19 10:45:00'),
(21, 'Priya Sharma', 'priya@example.com', 'India', '2026-03-22 08:35:00'),
(22, 'Kwame Mensah', 'kwame@example.com', 'Ghana', '2026-03-25 12:55:00'),
(23, 'Ana Silva', 'ana@example.com', 'Portugal', '2026-03-28 19:05:00'),
(24, 'Omar Haddad', 'omar@example.com', 'Jordan', '2026-03-31 11:30:00')
ON CONFLICT (id) DO NOTHING;

INSERT INTO products (id, name, category, price, active) VALUES
(1, 'Wireless Mouse', 'Electronics', 25.00, true),
(2, 'Mechanical Keyboard', 'Electronics', 85.00, true),
(3, 'Notebook', 'Stationery', 5.50, true),
(4, 'Desk Lamp', 'Home Office', 42.00, true),
(5, 'Old USB Cable', 'Electronics', 3.00, false),
(6, 'Laptop Stand', 'Home Office', 38.00, true),
(7, 'Noise Cancelling Headphones', 'Electronics', 149.00, true),
(8, 'Gel Pen Pack', 'Stationery', 7.25, true),
(9, 'Planner', 'Stationery', 18.00, true),
(10, 'Ergonomic Chair', 'Home Office', 260.00, true),
(11, 'Webcam', 'Electronics', 72.00, true),
(12, 'Monitor Arm', 'Home Office', 95.00, true),
(13, 'Sticky Notes', 'Stationery', 4.75, true),
(14, 'Portable SSD', 'Electronics', 119.00, true),
(15, 'Water Bottle', 'Lifestyle', 22.00, true),
(16, 'Desk Mat', 'Home Office', 31.50, true),
(17, 'Bluetooth Speaker', 'Electronics', 64.00, true),
(18, 'Archive Box', 'Stationery', 12.00, true),
(19, 'Standing Desk', 'Home Office', 420.00, true),
(20, 'Legacy VGA Adapter', 'Electronics', 8.00, false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO orders (id, user_id, status, total, created_at)
SELECT
    order_number,
    ((order_number * 7) % 24) + 1 AS user_id,
    CASE order_number % 8
        WHEN 0 THEN 'cancelled'
        WHEN 1 THEN 'paid'
        WHEN 2 THEN 'paid'
        WHEN 3 THEN 'shipped'
        WHEN 4 THEN 'paid'
        WHEN 5 THEN 'pending'
        WHEN 6 THEN 'shipped'
        ELSE 'refunded'
    END AS status,
    0.00 AS total,
    TIMESTAMP '2026-01-01 08:00:00'
        + ((order_number * 3) % 110) * INTERVAL '1 day'
        + ((order_number * 37) % 540) * INTERVAL '1 minute' AS created_at
FROM generate_series(1, 120) AS order_number
ON CONFLICT (id) DO NOTHING;

INSERT INTO order_items (id, order_id, product_id, quantity, unit_price)
SELECT
    ((o.id - 1) * 3) + item_number AS id,
    o.id AS order_id,
    p.id AS product_id,
    ((o.id + item_number) % 4) + 1 AS quantity,
    p.price AS unit_price
FROM orders o
JOIN generate_series(1, 3) AS item_number
  ON item_number <= ((o.id % 3) + 1)
JOIN products p
  ON p.id = (((o.id * item_number * 5) % 20) + 1)
WHERE o.id BETWEEN 1 AND 120
ON CONFLICT (id) DO NOTHING;

UPDATE orders o
SET total = totals.total
FROM (
    SELECT
        order_id,
        SUM(quantity * unit_price)::NUMERIC(10, 2) AS total
    FROM order_items
    GROUP BY order_id
) totals
WHERE totals.order_id = o.id;

SELECT setval('users_id_seq', (SELECT MAX(id) FROM users));
SELECT setval('products_id_seq', (SELECT MAX(id) FROM products));
SELECT setval('orders_id_seq', (SELECT MAX(id) FROM orders));
SELECT setval('order_items_id_seq', (SELECT MAX(id) FROM order_items));
