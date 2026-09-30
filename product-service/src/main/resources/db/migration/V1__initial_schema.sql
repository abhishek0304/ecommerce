-- Baseline of the application schema. Do not edit after deployment.
create table products (active bit not null, price decimal(19,2) not null, stock_quantity integer not null, created_at datetime(6) not null, id bigint not null auto_increment, updated_at datetime(6) not null, version bigint, sku varchar(64) not null, category varchar(100), name varchar(150) not null, description varchar(2000), image_url varchar(2048), primary key (id)) engine=InnoDB;
create table stock_reservation_items (line_number integer not null, price decimal(19,2) not null, quantity integer not null, product_id bigint not null, name varchar(255) not null, reservation_id varchar(255) not null, primary key (line_number, reservation_id)) engine=InnoDB;
create table stock_reservations (user_id bigint not null, version bigint, id varchar(255) not null, state varchar(255) not null, primary key (id)) engine=InnoDB;
alter table products add constraint UKfhmd06dsmj6k0n90swsh8ie9g unique (sku);
alter table stock_reservation_items add constraint FKd5pyb9swta0l83lrji6vc97jx foreign key (reservation_id) references stock_reservations (id);
