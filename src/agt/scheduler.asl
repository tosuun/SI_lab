// Scheduler agent.
// It starts and closes output cycles.
// It does not print delivery or missed deadline events.

!start.

+!start : true <-
    !cycle_monitor.

// The supervisor asks for this when one type is full.
+!activate_output_cycle(Type) : true <-
    start_output_cycle(Type).

// Output cycle started at T0.
+cycle_started(Type, T0, Deadline) : true <-
    .print("EVENT | time=", T0, " | agent=scheduler | type=output_phase_started | data=", Type);
    .print("EVENT | time=", T0, " | agent=scheduler | type=deadline_started | data=", Type);
    .send(transport, achieve, clear_outbound(Type)).

// The cycle ends when all containers of that type are done.
+cycle_finished(Type, T) : true <-
    .print("EVENT | time=", T, " | agent=scheduler | type=deadline_ended | data=", Type).

// Check cycle status.
+!cycle_monitor : true <-
    check_output_cycles;
    .wait(1000);
    !cycle_monitor.
