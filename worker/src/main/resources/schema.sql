create table if not exists videos (
    id                uuid primary key,
    owner_id          varchar(128)  not null,
    s3_key            varchar(1024) not null,
    upload_id         varchar(512),
    status            varchar(32)   not null,
    size_bytes        bigint        not null,
    content_type      varchar(128),
    hls_manifest_url  varchar(1024),
    thumbnail_url     varchar(1024),
    duration_seconds  integer,
    error_message     varchar(2048),
    created_at        timestamp with time zone not null,
    updated_at        timestamp with time zone not null
);
