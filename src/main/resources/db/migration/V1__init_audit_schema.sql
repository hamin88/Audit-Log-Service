create table if not exists ledger_head (
    id varchar(64) not null primary key,
    latest_event_id uuid null,
    version bigint
);

create table if not exists audit_events (
    event_id uuid not null primary key,
    timestamp timestamp(6) with time zone not null,
    event_type varchar(255) not null,
    actor_id varchar(255) not null,
    resource_type varchar(255) not null,
    resource_id varchar(255) not null,
    payload text not null,
    previous_hash varchar(64) not null,
    current_hash varchar(64) not null,
    status varchar(32) not null,
    archived_at timestamp(6) with time zone null
);

insert into ledger_head (id, latest_event_id, version)
select 'HEAD', null, 0
where not exists (select 1 from ledger_head where id = 'HEAD');
