# Technical Report Draft

## Architecture

This project is an automated warehouse made with Jason agents and a Java
environment. The environment keeps the physical information of the warehouse:
the grid, the zones, the robots, the containers, the shelves and their
positions. The agents decide what to do with this information.

- `supervisor`: checks shelf state and decides when there is no space. It also checks missed deadlines.
- `scheduler`: calculates T0 and the deadline, then starts and closes output cycles.
- `robot_light`, `robot_medium`, `robot_heavy_1`, `robot_heavy_2`: choose containers using their own capacity.
- `transport`: removes containers from the outbound area, like an external truck.

## BDI Model

The project follows the BDI idea used in Jason:

- Beliefs: information the agent has. For example, robots know their capacity
  with beliefs like `max_weight(30)` and they receive percepts like
  `container_available(...)` or `output_candidate(...)`.
- Desires: goals the agent wants to achieve. For example, robots have the goal
  `!work_cycle`, the supervisor has `!monitor`, and the scheduler has
  `!cycle_monitor`.
- Intentions: plans that are currently being executed. For example, when a
  robot accepts an output task, it follows the plan that goes from the shelf to
  the outbound area and then returns to parking.

This separation is useful because the environment does not tell a robot exactly
which task to do. The robot has beliefs about the world and selects a plan by
itself.

## Agent Interaction

The main interaction for one output cycle is:

```text
Environment -> Supervisor: shelf_state(...), container_available(...)
Supervisor -> Scheduler: activate_output_cycle(Type)
Scheduler -> Environment: open_output_cycle(Type)
Environment -> Robots: output_candidate(...), output_pending(...)
Robots -> Environment: claim_output(Container)
Environment -> Robot: output_task(Container,Shelf,Type)
Robot -> Environment: pickup, move_to, deliver
Robot -> Console: EVENT container_delivered
Transport -> Environment: remove_outbound(Type)
Scheduler -> Environment: close_output_cycle(Type)
Scheduler -> Console: EVENT deadline_ended
```

For the final PDF, this can be converted into a sequence diagram.

## Zones And Shelf Policy

The warehouse has three main zones:

- Inbound: containers enter the warehouse here.
- Classification: middle area used for classification.
- Outbound: delivered containers leave the warehouse here.

The shelf policy is:

- Urgent containers: `S1`, `S5`, `S8`.
- Standard and fragile containers: `S2`, `S3`, `S4`, `S6`, `S7`, `S9`.

The environment checks this policy when a robot drops a container on a shelf.
It also publishes shelf state with `shelf_state(...)`. The supervisor uses this
state to decide if there is no possible shelf for a waiting container. The
scheduler and the supervisor do not give direct tasks to the robots. The robots
see `container_available(...)` and `output_candidate(...)`, and then they try
`claim_storage(...)` or `claim_output(...)` by themselves.

Robots move in the grid step by step. The Java environment calculates a simple
path and avoids shelves and blocked cells. Robot speed is represented with
different delays between steps. When a robot stores a container, it passes
through the classification zone before going to the shelf.

The movement system also tries to avoid collisions. Before each step, the
environment checks the position of the other robots. If the next cell is busy,
the robot waits and calculates the path again. Robots also use different access
points near inbound and outbound to reduce blocking.

If a target access cell is busy, the environment chooses a nearby free cell.
After storage or delivery, robots return to their parking positions.

The environment reserves shelf capacity when a robot accepts a storage task.
This avoids two robots using the same free shelf space. If a storage task fails,
the robot calls a recovery action, and the container becomes available again.

## Important Plan Examples

Robot output plan:

```asl
+output_task(CId, ShelfId, Type) : state(claiming) <-
    -+state(working);
    !go_shelf(ShelfId);
    pickup(CId);
    !go_outbound;
    deliver(CId);
    !go_park;
    -+state(idle).
```

This plan shows the physical sequence followed by a robot after it wins a
claim.

Supervisor no-space plan:

```asl
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
```

The supervisor makes the no-space decision from state percepts. This keeps the
environment thinner.

Scheduler cycle plan:

```asl
+!activate_output_cycle(Type) : not cycle_active(_) <-
    read_time;
    ?time(T0);
    !deadline_for(Type,T0,Deadline);
    open_output_cycle(Type);
    +active_cycle(Type,Deadline);
    .print("EVENT | time=", T0, " | agent=scheduler | type=output_phase_started | data=", Type);
    .print("EVENT | time=", T0, " | agent=scheduler | type=deadline_started | data=", Type).
```

This plan shows that T0 and the deadline are handled by the scheduler.

## Why Claim-Based Tasks

I used a claim-based design instead of a scheduler that assigns tasks directly.
The reason is that the assignment asks for autonomous robots. With this design:

- The scheduler only starts output cycles.
- Robots observe available tasks.
- Each robot checks its own capacity.
- The first valid robot that claims the task gets it.
- The environment only accepts one claim for the same container.

This avoids direct task assignment and keeps the robots responsible for their
own decisions.

## Output Cycles And Deadlines

When the supervisor sees from `shelf_state(...)` that there is no free space for
a container type, it prints:

`EVENT | time=T | agent=supervisor | type=no_space_detected | data=container_type`

Then it informs the scheduler. The scheduler starts an output cycle for that
type and prints:

- `output_phase_started`
- `deadline_started`
- `deadline_ended`

I allow only one output cycle at the same time. This makes the active deadline
clear for the robots and avoids mixing different container types.

I used `DeltaT = 30` seconds. This is short enough to see the behavior in a
demo, but it still gives the robots time to move. The scheduler calculates the
deadline. Urgent containers expire at `T0 + DeltaT`. Non-urgent containers
expire at `T0 + 3 * DeltaT`.

The value is based on the movement speed used in the simulation. A robot moves
one cell every 90 ms, 140 ms or 220 ms depending on its type. A typical output
task needs to move from parking to a shelf, then to outbound, and then back to
parking. In the demo logs, many deliveries take around 5 to 15 seconds. For
this reason, 30 seconds is enough to show urgent deadlines, but it is still
possible to miss the deadline if many containers are pending. Non-urgent
containers have 90 seconds, which gives more time for standard and fragile
cycles.

## Time Control

The supervisor checks deadlines every second. If the current time is greater
than the active deadline and some containers are still not delivered, the
supervisor prints one event for each missed container:

`EVENT | time=T | agent=supervisor | type=deadline_missed | data=container_id`

These errors are only informative. The system continues running.

## Problems Found During Development

Some problems appeared while developing the system:

- Shelf reservation race condition: two robots could try to use the same free
  shelf capacity. I solved this by reserving shelf capacity when a storage task
  is accepted.
- Output cycle race condition: the scheduler could close a cycle before
  `output_pending(...)` percepts arrived. I added `cycle_ready(Type)` after a
  short wait before the scheduler is allowed to close the cycle.
- Thin environment issue: the first version had too much decision logic in the
  Java environment. I moved the no-space decision to the supervisor and the
  deadline/cycle decision to the scheduler.
- Robot task conflicts: more than one robot could see the same candidate. The
  claim action solves this because only one robot can reserve a container.
- Robot movement conflicts: robots can block each other. The environment checks
  occupied cells and recalculates paths when needed.

## Traceability

The required events use this format:

`EVENT | time=T | agent=NAME | type=EVENT_TYPE | data=VALUE`

Robots print an event when they deliver a container to outbound:

`EVENT | time=T | agent=robot_id | type=container_delivered | data=container_id`

## Tests

The main test scenarios are:

| Test | Expected result | Result |
| --- | --- | --- |
| Startup | Environment starts, 4 robots and 9 shelves appear | OK |
| Shelf policy | Urgent goes to S1/S5/S8, standard and fragile go to the other shelves | OK |
| Standard output cycle | Supervisor detects no space, scheduler starts cycle, robots deliver standard containers | OK |
| Urgent output cycle | Urgent shelves full, urgent output cycle starts with short deadline | To verify in demo |
| Deadline miss | If deadline passes and containers are still pending, supervisor prints one event per container | To verify in demo |
| Robot autonomy | Robots claim tasks by themselves, without scheduler assignment | OK |
| Cycle closing | Scheduler closes the cycle when no `output_pending(...)` remains | OK |

Example trace from a standard cycle:

```text
EVENT | time=58 | agent=supervisor | type=no_space_detected | data=standard
EVENT | time=58 | agent=scheduler | type=output_phase_started | data=standard
EVENT | time=58 | agent=scheduler | type=deadline_started | data=standard
EVENT | time=63 | agent=robot_medium | type=container_delivered | data=container_1
EVENT | time=101 | agent=scheduler | type=deadline_ended | data=standard
```
