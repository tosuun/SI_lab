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

## Time Control

The supervisor checks deadlines every second. If the current time is greater
than the active deadline and some containers are still not delivered, the
supervisor prints one event for each missed container:

`EVENT | time=T | agent=supervisor | type=deadline_missed | data=container_id`

These errors are only informative. The system continues running.

## Traceability

The required events use this format:

`EVENT | time=T | agent=NAME | type=EVENT_TYPE | data=VALUE`

Robots print an event when they deliver a container to outbound:

`EVENT | time=T | agent=robot_id | type=container_delivered | data=container_id`

## Tests

- Java compilation of the environment classes with `javac`.
- Parser check for the `.asl` agents.
- Parser check for `warehouse.mas2j`.
- Startup test with `RunLocalMAS` until the environment is initialized.

In my local machine the project runs with Gradle. In the Codex sandbox, Gradle
could not run because the sandbox blocks the local socket/IP operation used by
Gradle.
