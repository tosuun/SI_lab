# Guia de depuracion

## Primeras comprobaciones

- Ejecutar el proyecto con `jason warehouse.mas2j`.
- Comprobar que la consola muestra la inicializacion del entorno.
- Comprobar que la GUI se abre si la maquina no esta en modo headless.
- Comprobar que todos los agentes de `warehouse.mas2j` tienen un archivo
  `.asl`.

Agentes actuales:

- `robot_light`
- `robot_medium`
- `robot_heavy_1`
- `robot_heavy_2`
- `scheduler`
- `supervisor`
- `transport`

## Comprobaciones en consola

El sistema debe imprimir eventos con este formato:

```text
EVENT | time=T | agent=NAME | type=EVENT_TYPE | data=VALUE
```

Eventos importantes:

- `no_space_detected`
- `output_phase_started`
- `deadline_started`
- `container_delivered`
- `deadline_missed`
- `deadline_ended`

## Un robot no almacena contenedores

Comprobar:

- El robot tiene una capacidad compatible en `warehouse.mas2j`.
- El robot recibe perceptos `container_available(...)`.
- El robot llama a `claim_storage(CId)`.
- El entorno devuelve `storage_task(CId,ShelfId)`.
- La politica de estanterias permite ese tipo en esa estanteria.

El scheduler no asigna tareas de almacenamiento directamente. Los robots
eligen contenedores disponibles y los reclaman.

## Un robot no entrega durante salida

Comprobar:

- El supervisor detecto falta de espacio.
- El scheduler abrio un ciclo con `open_output_cycle(Type)`.
- El robot recibe `output_candidate(...)`.
- El robot llama a `claim_output(CId)`.
- El entorno devuelve `output_task(CId,ShelfId,Type)`.
- El robot llega a outbound y llama a `deliver(CId)`.

## Falla una reclamacion

Una reclamacion puede fallar si otro robot ya reclamo el mismo contenedor o si
el contenedor ya no esta disponible. Es normal en un sistema con varios robots.
El robot debe volver a `idle` y continuar su ciclo de trabajo.

## Problemas de movimiento

Comprobar:

- Las coordenadas estan dentro del grid: `0 <= X < 20`, `0 <= Y < 15`.
- La celda objetivo es alcanzable.
- El robot no intenta atravesar celdas de estanteria.
- Otros robots no bloquean el punto de acceso durante demasiado tiempo.

El entorno recalcula caminos cuando una celda esta ocupada.

## Problemas con deadlines

Comprobar:

- `scheduler.asl` inicia el deadline al abrir un ciclo de salida.
- `supervisor.asl` recibe `active_deadline(Type,Deadline)`.
- Siguen existiendo contenedores pendientes del tipo activo despues del
  deadline.

Si todos los contenedores se entregan antes del deadline, no debe aparecer
`deadline_missed`.

## Desajustes comunes de archivos

- Usar `robot_heavy_1.asl` y `robot_heavy_2.asl`, no `robot_heavy.asl`.
- Usar `jason warehouse.mas2j` cuando Jason esta configurado en el entorno.
- Usar `gradle run` solo como lanzador alternativo.
- Usar acciones basadas en claims: `claim_storage` y `claim_output`.
