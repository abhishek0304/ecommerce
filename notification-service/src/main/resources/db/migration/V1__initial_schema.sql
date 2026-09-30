-- Baseline of the application schema. Do not edit after deployment.
create table message_deliveries (attempts integer not null, created_at datetime(6), expires_at datetime(6), next_attempt_at datetime(6), user_id bigint not null, version bigint, fingerprint varchar(64) not null, subject varchar(160) not null, recipient varchar(320), body varchar(2000) not null, failure varchar(255), id varchar(255) not null, order_id varchar(255), provider_id varchar(255), status varchar(255), type varchar(255), channel enum ('EMAIL','SMS','WHATSAPP') not null, primary key (id)) engine=InnoDB;
create table notifications (event_sequence bigint not null, occurred_at datetime(6) not null, received_at datetime(6) not null, user_id bigint not null, message varchar(512) not null, event_id varchar(255) not null, order_id varchar(255) not null, type varchar(255) not null, primary key (event_id)) engine=InnoDB;
create index IDX5ysa9ueebfqvj7l0qbigm7yp3 on message_deliveries (status, next_attempt_at);
create index IDXrysm7mr1631f799ixosacymow on message_deliveries (user_id, created_at);
create index IDX3ixq766wmaa8mfv5hovhj0my7 on notifications (user_id, received_at);
