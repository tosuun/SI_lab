# Debugging Guide

## First Checks

- Run the project with `jason warehouse.mas2j`.
- Check that the console shows the environment initialization.
- Check that the GUI opens if the machine is not headless.
- Check that all agents in `warehouse.mas2j` have a matching `.asl` file.

Current agents:

- `robot_light`
- `robot_medium`
- `robot_heavy_1`
- `robot_heavy_2`
- `scheduler`
- `supervisor`
- `transport`

## Useful Console Checks

The system should print events with this format:

```text
EVENT | time=T | agent=NAME | type=EVENT_TYPE | data=VALUE
```

Important events:

- `no_space_detected`
- `output_phase_started`
- `deadline_started`
- `container_delivered`
- `deadline_missed`
- `deadline_ended`

## Robot Does Not Store Containers

Check:

- The robot has a compatible capacity belief in `warehouse.mas2j`.
- The robot receives `container_available(...)` percepts.
- The robot calls `claim_storage(CId)`.
- The environment returns `storage_task(CId,ShelfId)`.
- The shelf policy allows the container type on that shelf.

The scheduler does not assign storage tasks directly. Robots choose available
containers and claim them.

## Robot Does Not Deliver During Output

Check:

- The supervisor detected a no-space situation.
- The scheduler opened an output cycle with `open_output_cycle(Type)`.
- The robot receives `output_candidate(...)`.
- The robot calls `claim_output(CId)`.
- The environment returns `output_task(CId,ShelfId,Type)`.
- The robot reaches outbound and calls `deliver(CId)`.

## Claim Fails

A claim can fail when another robot already claimed the same container or when
the container is no longer available. This is expected in a multi-robot system.
The robot should return to `idle` and continue its work cycle.

## Movement Problems

Check:

- Coordinates are inside the grid: `0 <= X < 20`, `0 <= Y < 15`.
- The target cell is reachable.
- The robot is not trying to move through shelf cells.
- Other robots are not blocking the access point for too long.

The environment recalculates paths when a cell is busy.

## Deadline Problems

Check:

- `scheduler.asl` starts the deadline when it opens an output cycle.
- `supervisor.asl` receives `active_deadline(Type,Deadline)`.
- Pending containers of the active type still exist after the deadline.

If all containers are delivered before the deadline, no `deadline_missed` event
is expected.

## Common File Mismatches

- Use `robot_heavy_1.asl` and `robot_heavy_2.asl`, not `robot_heavy.asl`.
- Use `jason warehouse.mas2j` when Jason is configured in the environment.
- Use `gradle run` only as an alternative launcher.
- Use claim-based actions: `claim_storage` and `claim_output`.
