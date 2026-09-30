-- Baseline of the application schema. Do not edit after deployment.
create table cart_items (quantity integer not null, product_id bigint not null, user_id bigint not null, primary key (user_id, product_id)) engine=InnoDB;
create table carts (user_id bigint not null, version bigint, primary key (user_id)) engine=InnoDB;
alter table cart_items add constraint FKfo8ym9koys22r8r1lgthfos5o foreign key (user_id) references carts (user_id);
