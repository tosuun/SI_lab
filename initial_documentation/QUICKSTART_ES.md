# Inicio rapido

## Ejecutar

Desde la raiz del proyecto:

```bash
jason warehouse.mas2j
```

Esto inicia el MAS Jason definido en `warehouse.mas2j`.

Alternativa con Gradle:

```bash
gradle run
```

## Resultado esperado

- El entorno arranca.
- Se abre la GUI si la maquina no esta en modo headless.
- Aparecen contenedores en inbound.
- Se crean cuatro robots: `robot_light`, `robot_medium`, `robot_heavy_1` y
  `robot_heavy_2`.
- Los robots reclaman tareas compatibles y almacenan contenedores.
- Cuando un tipo no tiene espacio, empieza un ciclo de salida.

## Comprobaciones rapidas

Revisar en consola lineas como:

```text
EVENT | time=T | agent=supervisor | type=no_space_detected | data=standard
EVENT | time=T | agent=scheduler | type=output_phase_started | data=standard
EVENT | time=T | agent=robot_medium | type=container_delivered | data=container_1
```

## Flujo actual de tareas

Almacenamiento:

```text
container_available(...)
robot -> claim_storage(CId)
environment -> storage_task(CId,ShelfId)
robot -> pickup(CId)
robot -> drop_at(ShelfId)
```

Salida:

```text
output_candidate(...)
robot -> claim_output(CId)
environment -> output_task(CId,ShelfId,Type)
robot -> pickup(CId)
robot -> deliver(CId)
```

El scheduler abre ciclos de salida, pero no asigna contenedores directamente a
los robots.

## Si no arranca

- Comprobar que Java 21 o superior esta instalado.
- Ejecutar desde la raiz del proyecto, donde esta `warehouse.mas2j`.
- Comprobar que Jason 3.3.0 esta disponible en el entorno.
- Si Jason no esta instalado globalmente, usar `gradle run`.
