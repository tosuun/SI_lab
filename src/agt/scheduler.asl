!start.

+!start : true <-
    !cycle_monitor.

+!activate_output_cycle(Type) : true <-
    start_output_cycle(Type).

+cycle_started(Type, T0, Deadline) : true <-
    .print("EVENT | time=", T0, " | agent=scheduler | type=output_phase_started | data=", Type);
    .print("EVENT | time=", T0, " | agent=scheduler | type=deadline_started | data=", Type);
    .send(transport, achieve, clear_outbound(Type)).

+cycle_finished(Type, T) : true <-
    .print("EVENT | time=", T, " | agent=scheduler | type=deadline_ended | data=", Type).

+!cycle_monitor : true <-
    check_output_cycles;
    .wait(1000);
    !cycle_monitor.
