create table if not exists wbms.wb_indicator_profiles (
    id bigserial primary key,
    uuid varchar(100) not null unique,
    profile_name varchar(150) not null unique,
    manufacturer varchar(150), model varchar(150), connection_type varchar(30) not null,
    baud_rate integer, data_bits integer, parity varchar(30), stop_bits varchar(30), flow_control varchar(30),
    serial_port varchar(80), tcp_host varchar(255), tcp_port integer,
    start_bytes varchar(100), end_bytes varchar(100), separator varchar(100), string_length integer,
    remove_beginning integer default 0, remove_end integer default 0, weight_token_index integer default 0,
    scale_count integer default 1, active boolean default true,
    created_date timestamp, created_by varchar(100), created_by_user_name varchar(150),
    deleted boolean default false, deleted_at timestamp
);

alter table if exists wbms.wb_weight_devices add column if not exists indicator_profile_uuid varchar(100);
