!start.

+!start : true <-
    !monitor.

+!monitor : true <-
    check_storage;
    check_deadlines;
    .wait(1000);
    !monitor.

+no_space(Type, T) : true <-
    .print("EVENT | time=", T, " | agent=supervisor | type=no_space_detected | data=", Type);
    .send(scheduler, achieve, activate_output_cycle(Type)).

+deadline_missed(CId, Type, T) : true <-
    .print("EVENT | time=", T, " | agent=supervisor | type=deadline_missed | data=", CId).
