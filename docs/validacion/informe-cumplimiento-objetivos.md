# Relación de objetivos específicos y evidencias de cumplimiento

**Proyecto:** Sistema UNIBÁN — Equipo 3, gestión de suministros  
**Componente:** aplicación Android y servicios de gestión e integración

## 1. Propósito y alcance

Este documento relaciona los objetivos específicos del [anteproyecto](../avances/AnteproyectoInicial-PI1-Equipo3-Uniban.pdf) con los resultados del componente desarrollado por el Equipo 3 y las evidencias disponibles en el repositorio. Los resultados de pruebas y las limitaciones técnicas se encuentran en el [informe de validación, pruebas y limitaciones](informe-validacion-pruebas-limitaciones.md).

La valoración distingue **cumplido**, cuando los entregables correspondientes están implementados y documentados, y **parcialmente cumplido**, cuando alguna actividad expresamente incluida en el objetivo no sigue al pie de la letra lo dicho, pero que cumple con el objetivo propuesto desde un inicio en el anteproyecto.

## 2. Matriz de cumplimiento

| N.º | Objetivo específico del anteproyecto                                                                                                                                                 | Estado                                   | Evidencias principales                                                                                                                                                                                                                                                                                                                                                                   |
| --- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | Diseñar la arquitectura de software y el modelo de datos para gestionar productores, parcelas, ciclos productivos, insumos, inventarios, movimientos y desperdicios.                 | **Cumplido**                             | [Arquitectura de la aplicación](../arquitectura/README.md), [diagrama de arquitectura](../arquitectura/arquitectura.svg), [modelo entidad-relación](../modelo-datos/MERv2.drawio) y [modelo del backend](../../backend/app/models/).                                                                                                                                                     |
| 2   | Implementar una aplicación móvil y servicios web que permitan registrar movimientos de inventario y sincronizar información bajo condiciones de conectividad limitada.               | **Cumplido**                             | [Repositorio y ejecución](../../README.md), [inventario](evidencias/02-inventario.jpeg), [formulario de movimiento](evidencias/03-movimiento.jpeg), [registros pendientes sin conexión](evidencias/05-pendientes-modo-avion.jpeg), [estado posterior de sincronización](evidencias/06-sin-pendientes-posterior.jpeg) e [informe de pruebas](informe-validacion-pruebas-limitaciones.md). |
| 3   | Integrar la solución consumiendo los datos de aforo generados por el Equipo 1 y exponiendo la información operativa requerida por el módulo de planificación logística del Equipo 2. | **Cumplido en la solución implementada** | [Contrato con el Equipo 1](../contrato-equipo1.md), [contrato con el Equipo 2](../contrato-equipo2.md), [aforamiento en la aplicación](evidencias/07-parcela-aforamiento.jpeg) y [recomendación en la aplicación](evidencias/08-recomendacion.jpeg).                                                                                                                                     |
| 4   | Validar la funcionalidad, trazabilidad, consistencia de inventario y usabilidad del sistema mediante pruebas técnicas y escenarios representativos de operación.                     | **Parcialmente cumplido**                | [Informe de validación y pruebas](informe-validacion-pruebas-limitaciones.md), [pruebas del backend](../../backend/tests/) y [pruebas de Android](../../android-app/app/src/test/).                                                                                                                                                                                                      |

## 3. Relación entre objetivos y resultados

### Objetivo 1. Arquitectura y modelo de datos

Se diseñó una arquitectura que articula la aplicación Android, el almacenamiento local, el backend, la base de datos central y los servicios de los otros equipos. El modelo contempla productores, parcelas, ciclos productivos, insumos, inventarios, movimientos y pérdidas; también incluye la información de aforamientos, recomendaciones, cosechas y despachos necesaria para el flujo implementado.

Las evidencias de este objetivo son la [descripción de arquitectura](../arquitectura/README.md), el [diagrama general](../arquitectura/arquitectura.svg), el [modelo entidad-relación](../modelo-datos/MERv2.drawio) y la implementación de las [entidades del backend](../../backend/app/models/).

### Objetivo 2. Aplicación móvil, inventario y sincronización

La aplicación permite consultar inventario y registrar entradas, consumos, pérdidas y ajustes. Los registros operativos se conservan en el dispositivo y se sincronizan con el backend cuando se dispone de conexión. El productor puede consultar el estado de los registros pendientes y de los envíos realizados.

El [informe de validación](informe-validacion-pruebas-limitaciones.md) reúne las capturas del inventario, el formulario de movimientos y los estados de sincronización, junto con los resultados disponibles de las pruebas automatizadas.

### Objetivo 3. Integración con aforamiento y planificación logística

El backend dispone de las interfaces para recibir los aforamientos del Equipo 1 y consultar recomendaciones del Equipo 2 usando información de la parcela, aforamiento, empaques y consumos. La app presenta el aforamiento y la recomendación para su consulta por el productor.

Las evidencias son los contratos del [Equipo 1](../contrato-equipo1.md) y del [Equipo 2](../contrato-equipo2.md), junto con las capturas de [aforamiento](evidencias/07-parcela-aforamiento.jpeg) y [recomendación](evidencias/08-recomendacion.jpeg). Estas capturas muestran los datos en la aplicación; el detalle de las solicitudes y respuestas está definido en los contratos. La demostración integrada complementa esta evidencia.

### Objetivo 4. Validación técnica y usabilidad

El equipo cuenta con pruebas automatizadas para el backend y Android y con escenarios visuales de consulta de inventario, parcelas, actividades, aforamientos, recomendaciones y estados de sincronización. El [informe de validación](informe-validacion-pruebas-limitaciones.md) presenta los resultados y los indicadores de los escenarios observados.

La evaluación de usabilidad con participantes, tareas y resultados aún no figura en la evidencia disponible. Por esta razón, el objetivo se registra como parcialmente cumplido, aunque ya existan pruebas técnicas y capturas de operación.

## 4. Documentos relacionados

- [Anteproyecto del Equipo 3](../avances/AnteproyectoInicial-PI1-Equipo3-Uniban.pdf).
- [Informe de validación, pruebas y limitaciones](informe-validacion-pruebas-limitaciones.md).
- [Arquitectura de la aplicación](../arquitectura/README.md) y [modelo entidad-relación](../modelo-datos/MERv2.drawio).
- [Contrato de integración con el Equipo 1](../contrato-equipo1.md) y [contrato de integración con el Equipo 2](../contrato-equipo2.md).
