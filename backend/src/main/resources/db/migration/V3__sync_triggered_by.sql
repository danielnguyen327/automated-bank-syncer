-- What started each sync: someone pressing Sync ('manual') or the every-3-hours schedule ('schedule').
alter table sync_run
  add column triggered_by text not null default 'manual'
    check (triggered_by in ('manual', 'schedule'));
