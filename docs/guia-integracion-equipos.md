# Guía actual de integración con los Equipos 1 y 2

> Actualizada el 26/10/2026 a partir del código del repositorio y los acuerdos compartidos en la conversación. Para el estado vigente del código, consultar [estado del proyecto](estado-proyecto.md). Las muestras no sustituyen la confirmación de los contratos con los otros equipos.

## Resumen de responsabilidades

| Flujo | Quién inicia | Qué ocurre |
| --- | --- | --- |
| Aforamiento | Equipo 1 → backend Equipo 3 | Consulta las parcelas disponibles y registra aforamientos con clave X-API-Key. |
| Recomendación | App Equipo 3 → backend Equipo 3 → Equipo 2 | El productor solicita la recomendación; nuestro backend construye la petición, consulta el servicio y guarda su respuesta. |
| Consulta del productor | App Equipo 3 → backend Equipo 3 | La app lee parcelas, aforamiento y recomendación con token Firebase y conserva una copia local. |

## Equipo 1

Rutas implementadas:

1. GET /api/v1/service/parcels, autenticada con X-API-Key. Devuelve identificador, nombre y área de cada parcela.
2. POST /api/v1/aforamientos, autenticada con X-API-Key. Recibe el UUID generado por Equipo 1, parcelId, fecha, área aforada, plantas estimadas y confianza.

El usuario indicó que SERVICE_API_KEY ya está configurada en Render y que la migración 0010 llegó a main. La revisión no verificó el servicio en producción, la versión aplicada en Neon ni la entrega de la clave al otro equipo.

Revisar antes de la prueba conjunta: el acuerdo de parcelas presenta areaHa como número, mientras que el backend actual la devuelve como texto numérico. Ver [contrato del Equipo 1](contrato-equipo1.md) y el [TXT para compartir](integracion-equipo1.txt).

## Equipo 2

El backend tiene una ruta para pedir una recomendación para una parcela y otra para consultar la última respuesta guardada. Al pedirla:

- exige que exista un aforamiento; sin este, devuelve un conflicto y no llama al Equipo 2;
- forma un cuerpo con productor, parcela, ciclo activo, aforamiento, inventario, consumos y pérdidas;
- calcula el periodo como semana ISO derivada de la fecha del aforamiento;
- consulta el servicio del Equipo 2 y guarda la respuesta;
- limita los consumos a esa parcela y las pérdidas al productor, en ambos casos dentro de los últimos 90 días;
- hoy envía todo el inventario del productor, incluidos insumos agrícolas y registros con existencia cero.

El usuario decidió que al Equipo 2 se le envíen solo cajas, carton y bolsas; el filtro correspondiente no está implementado. La categoría de esos productos es Empaque, la unidad es unidad y los nombres son minúsculos y sin tildes. Ver [contrato del Equipo 2](contrato-equipo2.md), donde se separan el comportamiento actual y la propuesta pendiente.

## Verificación y acuerdos que faltan

- Equipo 1: prueba contra el servidor desplegado, incluida respuesta idempotente al reenviar el mismo aforamiento y aceptación del formato actual de areaHa.
- Equipo 2: confirmar el cuerpo exacto que consume, autenticación requerida, significado de los campos y si acepta solo insumos de empaque.
- Acordar si consumos/pérdidas de 90 días son útiles y suficientes, y si el endpoint de recomendaciones debe recibir campos operativos adicionales.
- Confirmar versión de Render y revisión Alembic de Neon antes de una demostración productiva.

No hay que entregar secretos dentro de este documento. La configuración de la clave se describe en el contrato del Equipo 1.
