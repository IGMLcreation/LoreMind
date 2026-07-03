-- Co-MJ : une horloge peut s'avancer AUTOMATIQUEMENT quand un événement lié survient
-- (un Fait bascule / une quête se termine / une séance se clôt). triggerRef = nom du fait
-- ou id de quête selon le type. Compatible Postgres et H2 (MODE=PostgreSQL).
alter table clocks add column trigger_type varchar(32) not null default 'NONE';
alter table clocks add column trigger_ref varchar(255);
