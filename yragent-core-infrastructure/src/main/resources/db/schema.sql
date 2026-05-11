create table if not exists yr_task (
    task_id varchar(64) primary key,
    current_stage varchar(32) not null,
    user_input text not null,
    created_at varchar(32) not null,
    updated_at varchar(32) not null
);

create table if not exists yr_trace_run (
    trace_run_id varchar(64) primary key,
    task_id varchar(64) not null,
    status varchar(16) not null,
    created_at varchar(32) not null
);

create table if not exists yr_trace_node (
    trace_node_id varchar(64) primary key,
    trace_run_id varchar(64) not null,
    stage_type varchar(32) not null,
    passed integer not null,
    summary text,
    created_at varchar(32) not null
);

create table if not exists yr_memory_record (
    memory_id varchar(64) primary key,
    memory_type varchar(32) not null,
    content text not null,
    created_at varchar(32) not null
);
