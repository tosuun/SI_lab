// Supervisor agent.
// It checks storage space and deadlines.
// It does not give tasks to robots. (TR: sadece kontrol ve izleme)

!start.

+!start : true <-
    !monitor.

// Main loop.
+!monitor : true <-
    check_storage;
    check_deadlines;
    .wait(1000);
    !monitor.

// No space: report it and ask the scheduler to start output.
+no_space(Type, T) : true <-
    .print("EVENT | time=", T, " | agent=supervisor | type=no_space_detected | data=", Type);
    .send(scheduler, achieve, activate_output_cycle(Type)).

// Missed deadline: print one event for each container.
+deadline_missed(CId, Type, T) : true <-
    .print("EVENT | time=", T, " | agent=supervisor | type=deadline_missed | data=", CId).
