agent_id(robot_heavy_1).
max_weight(100).
max_size(2,3).
state(idle).

!start.

+!start : true <-
    !work_cycle.

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
    .wait(350);
    !work_cycle.

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
    .wait(350);
    !work_cycle.

+!work_cycle : true <-
    .wait(1000);
    !work_cycle.

+storage_task(CId, ShelfId) : state(claiming) <-
    -+state(working);
    !go_inbound;
    pickup(CId);
    move_to(5, 1);
    !go_shelf(ShelfId);
    drop_at(ShelfId);
    !go_park;
    -+state(idle).

+output_task(CId, ShelfId, Type) : state(claiming) <-
    -+state(working);
    !go_shelf(ShelfId);
    pickup(CId);
    !go_outbound;
    deliver(CId);
    !go_park;
    -+state(idle).

+delivered(CId, T) : agent_id(Agent) <-
    .print("EVENT | time=", T, " | agent=", Agent, " | type=container_delivered | data=", CId).

+claim_failed(CId) : true <-
    -+state(idle).

+error(ErrorType, Data) : true <-
    recover_task;
    -+state(idle).

+!go_inbound : true <- move_to(0, 1).
+!go_outbound : true <- move_to(18, 12).
+!go_park : true <- move_to(3, 3).

+!go_shelf("shelf_1") : true <- move_to(10, 1).
+!go_shelf("shelf_2") : true <- move_to(12, 1).
+!go_shelf("shelf_3") : true <- move_to(14, 1).
+!go_shelf("shelf_4") : true <- move_to(16, 1).
+!go_shelf("shelf_5") : true <- move_to(10, 5).
+!go_shelf("shelf_6") : true <- move_to(13, 5).
+!go_shelf("shelf_7") : true <- move_to(16, 5).
+!go_shelf("shelf_8") : true <- move_to(10, 9).
+!go_shelf("shelf_9") : true <- move_to(14, 9).
