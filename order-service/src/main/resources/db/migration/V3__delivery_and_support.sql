create table delivery_rules (postal_prefix varchar(6) not null, fee decimal(19,2) not null, free_above decimal(19,2) not null, min_days integer not null, max_days integer not null, active bit not null, primary key(postal_prefix)) engine=InnoDB;
alter table purchase_orders add column delivery_fee decimal(19,2) not null default 0.00;
alter table purchase_orders add column delivery_min_days integer;
alter table purchase_orders add column delivery_max_days integer;
create table support_tickets (id varchar(255) not null, version bigint, user_id bigint not null, order_id varchar(36), subject varchar(150) not null, status varchar(20) not null, created_at datetime(6) not null, primary key(id), index ix_ticket_user_time(user_id,created_at)) engine=InnoDB;
create table support_messages (ticket_id varchar(255) not null, message_number integer not null, text varchar(2000) not null, admin bit not null, created_at datetime(6) not null, primary key(ticket_id,message_number), constraint fk_support_ticket foreign key(ticket_id) references support_tickets(id)) engine=InnoDB;
