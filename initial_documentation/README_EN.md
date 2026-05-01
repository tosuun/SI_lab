# Warehouse Management System

Multi-agent system for an automated logistics warehouse using Jason and Java.

## Overview

The project simulates a warehouse where containers arrive at an inbound area,
robots store them on shelves, and output cycles move containers to outbound
when storage space is full for a container type.

The Java environment keeps the physical state of the warehouse: grid, shelves,
robots, containers, reservations and GUI. The Jason agents make the main
decisions.

## How To Run

```bash
jason warehouse.mas2j
```

Requirements:

- Java 21 or newer.
- Jason 3.3.0 available in the environment.

If Jason is not installed globally, the project can also be started with
Gradle:

```bash
gradle run
```

## Project Structure

```text
warehouse/
  warehouse.mas2j
  logging.properties
  build.gradle
  src/
    agt/
      robot_light.asl
      robot_medium.asl
      robot_heavy_1.asl
      robot_heavy_2.asl
      scheduler.asl
      supervisor.asl
      transport.asl
    env/warehouse/
      WarehouseArtifact.java
      WarehouseView.java
      Container.java
      Shelf.java
      Robot.java
      CellType.java
  doc/
    MEMORIA_DRAFT_EN.md
    README_EN.md
    README_ES.md
```

## Agents

- `robot_light`: handles small/light containers.
- `robot_medium`: handles medium containers.
- `robot_heavy_1` and `robot_heavy_2`: handle heavy and large containers.
- `supervisor`: checks shelf space and deadline misses.
- `scheduler`: opens and closes output cycles.
- `transport`: removes delivered containers from outbound.

## Main Behavior

- Containers are generated automatically in the inbound area.
- Robots observe available containers and try to claim compatible tasks.
- Storage tasks use `claim_storage(CId)`.
- Output tasks use `claim_output(CId)`.
- The environment accepts only one claim for the same container.
- The scheduler does not assign tasks directly to robots.
- The supervisor asks the scheduler to start an output cycle when there is no
  shelf space for a container type.
- During an output cycle, robots only deliver containers of the active type.

## Shelf Policy

- Urgent containers: `shelf_1`, `shelf_5`, `shelf_8`.
- Standard and fragile containers: `shelf_2`, `shelf_3`, `shelf_4`,
  `shelf_6`, `shelf_7`, `shelf_9`.

## Main Environment Actions

- `move_to(X,Y)`
- `claim_storage(CId)`
- `claim_output(CId)`
- `pickup(CId)`
- `drop_at(ShelfId)`
- `deliver(CId)`
- `read_time`
- `open_output_cycle(Type)`
- `close_output_cycle(Type)`
- `remove_outbound(Type)`
- `scan_surroundings`
- `recover_task(CId)`

## Required Event Format

```text
EVENT | time=T | agent=NAME | type=EVENT_TYPE | data=VALUE
```

Important event types:

- `no_space_detected`
- `output_phase_started`
- `deadline_started`
- `container_delivered`
- `deadline_missed`
- `deadline_ended`
