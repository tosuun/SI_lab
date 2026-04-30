// Scheduler agent.
// It starts and closes output cycles.
// It does not print delivery or missed deadline events.

delta_t(30).

!start.

+!start : true <-
    !cycle_monitor.

// The supervisor asks for this when one type is full.
+!activate_output_cycle(Type) : not cycle_active(_) <-
    read_time;
    ?time(T0);
    !deadline_for(Type,T0,Deadline);
    open_output_cycle(Type);
    +active_cycle(Type,Deadline);
    .print("EVENT | time=", T0, " | agent=scheduler | type=output_phase_started | data=", Type);
    .print("EVENT | time=", T0, " | agent=scheduler | type=deadline_started | data=", Type);
    .send(supervisor, tell, active_deadline(Type,Deadline));
    .send(transport, achieve, clear_outbound(Type)).

+!activate_output_cycle(Type) : true <-
    true.

+!deadline_for(urgent,T0,Deadline) : delta_t(DT) <-
    Deadline = T0 + DT.

+!deadline_for(standard,T0,Deadline) : delta_t(DT) <-
    Deadline = T0 + 3 * DT.

+!deadline_for(fragile,T0,Deadline) : delta_t(DT) <-
    Deadline = T0 + 3 * DT.

// The scheduler decides when all pending containers of the active type are done.
+!cycle_monitor
    : active_cycle(Type,Deadline)
      & not output_pending(_,Type) <-
    read_time;
    ?time(T);
    close_output_cycle(Type);
    -active_cycle(Type,Deadline);
    .print("EVENT | time=", T, " | agent=scheduler | type=deadline_ended | data=", Type);
    .send(supervisor, tell, cycle_closed(Type));
    .wait(1000);
    !cycle_monitor.

+!cycle_monitor : true <-
    .wait(1000);
    !cycle_monitor.
