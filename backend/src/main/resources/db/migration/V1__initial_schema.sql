CREATE TYPE request_category AS ENUM ('PACKAGE', 'BUY', 'PICKUP');
CREATE TYPE request_status AS ENUM ('OPEN', 'ACCEPTED', 'PICKED_UP', 'DELIVERING', 'DELIVERED', 'CANCELLED');

-- Matches the original Python schema so existing databases can baseline at V1.
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(320) NOT NULL UNIQUE,
    phone VARCHAR(32),
    rating NUMERIC(3, 2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_users_rating CHECK (rating >= 0 AND rating <= 5)
);

CREATE TABLE delivery_requests (
    id SERIAL PRIMARY KEY,
    customer_id INTEGER NOT NULL REFERENCES users(id),
    helper_id INTEGER REFERENCES users(id),
    category request_category NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    pickup_address VARCHAR(500) NOT NULL,
    delivery_address VARCHAR(500) NOT NULL,
    shopping_budget NUMERIC(10, 2),
    helper_reward NUMERIC(10, 2) NOT NULL,
    deadline TIMESTAMPTZ NOT NULL,
    status request_status NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_requests_helper_reward CHECK (helper_reward >= 0),
    CONSTRAINT ck_requests_shopping_budget CHECK (shopping_budget >= 0)
);
CREATE INDEX ix_delivery_requests_customer_id ON delivery_requests(customer_id);
CREATE INDEX ix_delivery_requests_helper_id ON delivery_requests(helper_id);
CREATE INDEX ix_delivery_requests_status ON delivery_requests(status);
