// Supervisor agent.
// It checks storage space and deadlines.
// It does not give tasks to robots.

// Beliefs: shelf policy used to decide if a container type has space.
allowed_shelf(urgent,"shelf_1").
allowed_shelf(urgent,"shelf_5").
allowed_shelf(urgent,"shelf_8").
allowed_shelf(standard,"shelf_2").
allowed_shelf(standard,"shelf_3").
allowed_shelf(standard,"shelf_4").
allowed_shelf(standard,"shelf_6").
allowed_shelf(standard,"shelf_7").
allowed_shelf(standard,"shelf_9").
allowed_shelf(fragile,"shelf_2").
allowed_shelf(fragile,"shelf_3").
allowed_shelf(fragile,"shelf_4").
allowed_shelf(fragile,"shelf_6").
allowed_shelf(fragile,"shelf_7").
allowed_shelf(fragile,"shelf_9").

// Rule belief: true when at least one allowed shelf can store the container.
storage_possible(Type,W,H,Weight) :-
    allowed_shelf(Type,Shelf)
    & shelf_state(Shelf,FreeWeight,FreeVolume)
    & Area = W * H
    & Weight <= FreeWeight
    & Area <= FreeVolume.

// Desire/goal: keep monitoring the warehouse.
!start.

+!start : true <-
    !monitor.

// Plans/intentions: check storage and deadlines using current beliefs.
// Main loop.
+!monitor : true <-
    !check_storage;
    !check_deadlines;
    .wait(1000);
    !monitor.

// The supervisor decides if a waiting container has no possible shelf.
+!check_storage
    : not cycle_active(_)
      & container_available(CId,W,H,Weight,Type)
      & not no_space_reported(Type)
      & not storage_possible(Type,W,H,Weight) <-
    read_time;
    ?time(T);
    +no_space_reported(Type);
    .print("EVENT | time=", T, " | agent=supervisor | type=no_space_detected | data=", Type);
    .send(scheduler, achieve, activate_output_cycle(Type)).

+!check_storage : true <-
    true.

// Scheduler sends this when a new deadline starts.
+active_deadline(Type,Deadline) : true <-
    -deadline(Type,_);
    +deadline(Type,Deadline).

// Scheduler sends this when the cycle is closed.
+cycle_closed(Type) : true <-
    -no_space_reported(Type);
    -deadline(Type,_).

// The supervisor checks deadline misses using state percepts from the environment.
+!check_deadlines
    : deadline(Type,Deadline)
      & output_pending(CId,Type)
      & not missed(CId) <-
    read_time;
    ?time(T);
    !report_if_late(CId,T,Deadline).

+!check_deadlines : true <-
    true.

+!report_if_late(CId,T,Deadline) : T > Deadline <-
    +missed(CId);
    .print("EVENT | time=", T, " | agent=supervisor | type=deadline_missed | data=", CId).

+!report_if_late(CId,T,Deadline) : true <-
    true.
