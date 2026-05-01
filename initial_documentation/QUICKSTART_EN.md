# Quick Start

## Run

From the project root:

```bash
jason warehouse.mas2j
```

This starts the Jason MAS defined in `warehouse.mas2j`.

Alternative with Gradle:

```bash
gradle run
```

## Expected Result

- The environment starts.
- A GUI opens if the machine is not running in headless mode.
- Containers appear in inbound.
- Four robots are created: `robot_light`, `robot_medium`, `robot_heavy_1` and
  `robot_heavy_2`.
- Robots claim compatible storage tasks and move containers to shelves.
- When a type has no storage space, an output cycle starts.

## Quick Checks

Check the console for lines like:

```text
EVENT | time=T | agent=supervisor | type=no_space_detected | data=standard
EVENT | time=T | agent=scheduler | type=output_phase_started | data=standard
EVENT | time=T | agent=robot_medium | type=container_delivered | data=container_1
```

## Current Task Flow

Storage:

```text
container_available(...)
robot -> claim_storage(CId)
environment -> storage_task(CId,ShelfId)
robot -> pickup(CId)
robot -> drop_at(ShelfId)
```

Output:

```text
output_candidate(...)
robot -> claim_output(CId)
environment -> output_task(CId,ShelfId,Type)
robot -> pickup(CId)
robot -> deliver(CId)
```

The scheduler opens output cycles, but it does not directly assign containers
to robots.

## If It Does Not Start

- Check that Java 21 or newer is installed.
- Run from the project root, where `warehouse.mas2j` is located.
- Check that Jason 3.3.0 is available in the environment.
- If Jason is not installed globally, use `gradle run`.
