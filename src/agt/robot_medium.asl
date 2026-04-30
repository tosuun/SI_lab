// Medium robot agent.
// It chooses tasks from visible containers and its own capacity.

// Beliefs: local robot identity, capacity and state.
agent_id(robot_medium).
max_weight(30).
max_size(1,2).
state(idle).

// Desire/goal: start the continuous work cycle.
!start.

+!start : true <-
    !work_cycle.

// Plans/intentions: choose and execute one task depending on current beliefs.
// Output has priority when there is an output candidate.
+!work_cycle
    : state(idle)
      & output_candidate(CId, W, H, Weight, Type, ShelfId)
      & max_weight(MaxWeight)
      & max_size(MaxW, MaxH)
      & Weight <= MaxWeight
      & W <= MaxW
      & H <= MaxH <-
    -+state(claiming);
    claim_output(CId);
    .wait(250);
    !work_cycle.

// Otherwise it tries to store a compatible inbound container.
+!work_cycle
    : state(idle)
      & container_available(CId, W, H, Weight, Type)
      & max_weight(MaxWeight)
      & max_size(MaxW, MaxH)
      & Weight <= MaxWeight
      & W <= MaxW
      & H <= MaxH <-
    -+state(claiming);
    claim_storage(CId);
    .wait(250);
    !work_cycle.

+!work_cycle : true <-
    .wait(900);
    !work_cycle.

// Storage path: inbound -> classification -> shelf -> parking.
+storage_task(CId, ShelfId) : state(claiming) <-
    -+state(working);
    !go_inbound;
    pickup(CId);
    move_to(5, 1);
    !go_shelf(ShelfId);
    drop_at(ShelfId);
    !go_park;
    -+state(idle).

// Output path: shelf -> outbound -> parking.
+output_task(CId, ShelfId, Type) : state(claiming) <-
    -+state(working);
    !go_shelf(ShelfId);
    pickup(CId);
    !go_outbound;
    deliver(CId);
    !go_park;
    -+state(idle).

// Required delivery event.
+delivered(CId, T) : agent_id(Agent) <-
    .print("EVENT | time=", T, " | agent=", Agent, " | type=container_delivered | data=", CId).

+claim_failed(CId) : true <-
    -+state(idle).

+error(ErrorType, Data) : true <-
    recover_task;
    -+state(idle).

+!go_inbound : true <- move_to(2, 1).
+!go_outbound : true <- move_to(17, 13).
+!go_park : true <- move_to(2, 3).

+!go_shelf("shelf_1") : true <- move_to(10, 1).
+!go_shelf("shelf_2") : true <- move_to(12, 1).
+!go_shelf("shelf_3") : true <- move_to(14, 1).
+!go_shelf("shelf_4") : true <- move_to(16, 1).
+!go_shelf("shelf_5") : true <- move_to(10, 5).
+!go_shelf("shelf_6") : true <- move_to(13, 5).
+!go_shelf("shelf_7") : true <- move_to(16, 5).
+!go_shelf("shelf_8") : true <- move_to(10, 9).
+!go_shelf("shelf_9") : true <- move_to(14, 9).
