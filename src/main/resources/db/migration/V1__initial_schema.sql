create table if not exists users (
    id bigserial primary key,
    username varchar(100) not null unique,
    role varchar(32) not null,
    password varchar(255) not null
);

create table if not exists rice_products (
    id bigserial primary key,
    name varchar(150) not null unique,
    characteristics varchar(1000) not null,
    price_per_kg numeric(12, 2) not null,
    cost_per_kg numeric(12, 2) not null,
    active boolean not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table if not exists orders (
    id bigserial primary key,
    customer_name varchar(200) not null,
    customer_phone varchar(32),
    address varchar(500) not null,
    product_details text not null,
    total_price numeric(12, 2) not null,
    source varchar(32) not null,
    status varchar(64) not null,
    shipper_id bigint references users(id)
);

create index if not exists idx_orders_status on orders(status);
create index if not exists idx_orders_shipper_id on orders(shipper_id);

create table if not exists audit_logs (
    id bigserial primary key,
    action_type varchar(100) not null,
    user_id bigint not null references users(id),
    target_order_id bigint not null references orders(id),
    description text not null,
    timestamp timestamp not null
);

create index if not exists idx_audit_logs_user_id on audit_logs(user_id);
create index if not exists idx_audit_logs_target_order_id on audit_logs(target_order_id);
