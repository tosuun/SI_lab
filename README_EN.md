# Warehouse Management System

**Multi-agent system for an automated logistics warehouse**

University of Vigo - Intelligent Systems - 2025-2026

## Quick Start

```bash
gradle run
```

Requirements:

- Java 21+
- Jason 3.3.0
- Gradle

## Project Structure

```text
warehouse/
├── warehouse.mas2j
├── src/
│   ├── agt/
│   │   ├── robot_light.asl
│   │   ├── robot_medium.asl
│   │   ├── robot_heavy_1.asl
│   │   ├── robot_heavy_2.asl
│   │   ├── scheduler.asl
│   │   ├── supervisor.asl
│   │   └── transport.asl
│   └── env/warehouse/
│       ├── WarehouseArtifact.java
│       ├── WarehouseView.java
│       ├── Container.java
│       ├── Shelf.java
│       ├── Robot.java
│       └── CellType.java
├── doc/
│   ├── README_EN.md
│   └── MEMORIA_DRAFT_EN.md
├── build.gradle
└── logging.properties
```

## Implemented Behavior

- Containers arrive in the inbound area.
- Robots store containers on shelves using the required shelf policy.
- Urgent containers use shelves `S1`, `S5`, and `S8`.
- Standard and fragile containers use shelves `S2`, `S3`, `S4`, `S6`, `S7`, and `S9`.
- The supervisor detects when there is no storage space for a container type.
- The scheduler starts an output cycle and a deadline for that type.
- During an output cycle, robots only move containers from the active type.
- The transport agent removes delivered containers from outbound.
- The system prints required events with this format:

```text
EVENT | time=T | agent=NAME | type=EVENT_TYPE | data=VALUE
```

## Main Agents

- `supervisor`: checks storage space and missed deadlines.
- `scheduler`: starts output cycles and deadlines.
- `robot_light`: small containers.
- `robot_medium`: medium containers.
- `robot_heavy_1` and `robot_heavy_2`: heavy and large containers.
- `transport`: external outbound removal.

## Report

Use [doc/MEMORIA_DRAFT_EN.md](doc/MEMORIA_DRAFT_EN.md) as the base for the final report.

The final PDF should be saved as:

```text
doc/memoria.pdf
```

## Final ZIP

Create a ZIP with the complete `warehouse/` folder and `doc/memoria.pdf`.

Suggested name:

```text
warehouse.zip
```
