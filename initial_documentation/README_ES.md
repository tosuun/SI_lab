# Sistema de gestion de almacen

Sistema multiagente para un almacen logistico automatizado usando Jason y
Java.

## Resumen

El proyecto simula un almacen donde llegan contenedores a la zona inbound, los
robots los almacenan en estanterias y los ciclos de salida mueven contenedores
a outbound cuando no queda espacio para un tipo.

El entorno Java guarda el estado fisico del almacen: grid, estanterias, robots,
contenedores, reservas e interfaz grafica. Los agentes Jason toman las
decisiones principales.

## Como ejecutar

```bash
jason warehouse.mas2j
```

Requisitos:

- Java 21 o superior.
- Jason 3.3.0 disponible en el entorno.

Si Jason no esta instalado globalmente, tambien se puede ejecutar con Gradle:

```bash
gradle run
```

## Estructura del proyecto

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

## Agentes

- `robot_light`: mueve contenedores pequenos y ligeros.
- `robot_medium`: mueve contenedores medianos.
- `robot_heavy_1` y `robot_heavy_2`: mueven contenedores pesados y grandes.
- `supervisor`: comprueba espacio disponible y deadlines incumplidos.
- `scheduler`: abre y cierra ciclos de salida.
- `transport`: retira contenedores entregados en outbound.

## Comportamiento principal

- Los contenedores se generan automaticamente en inbound.
- Los robots observan contenedores disponibles y reclaman tareas compatibles.
- Las tareas de almacenamiento usan `claim_storage(CId)`.
- Las tareas de salida usan `claim_output(CId)`.
- El entorno acepta solo una reclamacion para cada contenedor.
- El scheduler no asigna tareas directamente a los robots.
- El supervisor pide al scheduler iniciar un ciclo de salida cuando no hay
  espacio para un tipo de contenedor.
- Durante un ciclo de salida, los robots solo entregan contenedores del tipo
  activo.

## Politica de estanterias

- Urgent: `shelf_1`, `shelf_5`, `shelf_8`.
- Standard y fragile: `shelf_2`, `shelf_3`, `shelf_4`, `shelf_6`, `shelf_7`,
  `shelf_9`.

## Acciones principales del entorno

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

## Formato de eventos

```text
EVENT | time=T | agent=NAME | type=EVENT_TYPE | data=VALUE
```

Tipos importantes:

- `no_space_detected`
- `output_phase_started`
- `deadline_started`
- `container_delivered`
- `deadline_missed`
- `deadline_ended`
