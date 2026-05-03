-- Add stock tracking to rice products
alter table rice_products add column if not exists stock_kg numeric(12, 2) not null default 0;

-- Track each stock import event
create table if not exists stock_entries (
    id bigserial primary key,
    product_id bigint not null references rice_products(id),
    quantity_kg numeric(12, 2) not null,
    cost_per_kg numeric(12, 2) not null,
    imported_at timestamp not null default now()
);

create index if not exists idx_stock_entries_product_id on stock_entries(product_id);

-- Customer loyalty points by phone number
create table if not exists customer_loyalty (
    id bigserial primary key,
    phone varchar(32) not null unique,
    total_points numeric(12, 2) not null default 0,
    purchase_count int not null default 0,
    last_reset_at timestamp,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);

create index if not exists idx_customer_loyalty_phone on customer_loyalty(phone);
