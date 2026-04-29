# Technical Report Draft

## Architecture

The system is an automated warehouse built with Jason agents and a Java
environment. The environment keeps the physical state: grid, zones, robots,
containers, shelves, occupancy and positions. The agents make the control
decisions:

- `supervisor`: checks storage space by container type and detects missed deadlines.
- `scheduler`: starts output cycles and deadlines after a supervisor warning.
- `robot_light`, `robot_medium`, `robot_heavy_1`, `robot_heavy_2`: choose visible containers by their own capacity and the current context.
- `transport`: simulates the external removal of containers from the outbound area.

## Zones And Shelf Policy

The warehouse has three main zones:

- Inbound: containers enter the warehouse here.
- Classification: middle area used for classification.
- Outbound: delivered containers leave the warehouse here.

The shelf policy is explicit:

- Urgent containers: `S1`, `S5`, `S8`.
- Standard and fragile containers: `S2`, `S3`, `S4`, `S6`, `S7`, `S9`.

The environment validates this policy when a robot drops a container on a
shelf. Robots do not receive direct assignments from the scheduler or the
supervisor. They observe `container_available(...)` and `output_candidate(...)`,
then try `claim_storage(...)` or `claim_output(...)` by themselves.

Robots move through the grid step by step. The Java environment computes a
simple path that avoids shelf and blocked cells. The different robot speeds are
shown by different delays between movement steps. When a robot stores a
container, it goes through the classification zone before reaching the shelf
access point.

The movement system also prevents robot collisions. Before each step, the
environment checks the current position of the other robots. If the next cell is
occupied, the robot waits and recalculates its path. This avoids two robots
using the same cell at the same time. Robots also use different access points
near inbound and outbound to reduce traffic jams.

If a target access cell is occupied, the environment chooses a nearby free cell
within two grid steps. After finishing storage or delivery, robots return to
their parking positions so they do not block shelf access points.

The environment also reserves shelf capacity when a robot accepts a storage
task. This prevents two robots from choosing the same remaining shelf space at
the same time. If a storage task fails, the robot calls a recovery action that
releases the reservation and makes the container available again.

## Output Cycles And Deadlines

When the supervisor detects that there is no free storage space for a container
type, it prints:

`EVENT | time=T | agent=supervisor | type=no_space_detected | data=container_type`

Then it informs the scheduler. The scheduler starts an output cycle for that
type, stops new storage work while a cycle is active, and prints:

- `output_phase_started`
- `deadline_started`
- `deadline_ended`

The selected value is `DeltaT = 30` seconds. This value is short enough to show
the behavior during a demo, but long enough to let robots move before the
deadline. Urgent containers expire at `T0 + DeltaT`. Non-urgent containers
expire at `T0 + 3 * DeltaT`.

## Time Control

The supervisor checks deadlines periodically. If the current time is greater
than the active deadline and some containers from that cycle are still not
delivered to outbound, the supervisor prints one independent event for each
missed container:

`EVENT | time=T | agent=supervisor | type=deadline_missed | data=container_id`

These violations are informational. They do not stop the system and they do not
block robot execution.

## Traceability

All required events use this format:

`EVENT | time=T | agent=NAME | type=EVENT_TYPE | data=VALUE`

Robots only print the required event when they deliver a container to outbound:

`EVENT | time=T | agent=robot_id | type=container_delivered | data=container_id`

## Tests

- Direct Java compilation of the environment classes with `javac`.
- Jason parser check for all `.asl` agents.
- Parser check for `warehouse.mas2j`.
- Direct `RunLocalMAS` startup until the environment initialization point.

In the Codex sandbox, `gradle run` fails because the sandbox cannot provide a
usable local IP address. The Gradle files were not changed.
