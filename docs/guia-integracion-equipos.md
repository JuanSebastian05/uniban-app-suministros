# Guía actual de integración con los Equipos 1 y 2

> Actualizada el 27/10/2026 a partir del código del repositorio, el endpoint compartido y el OpenAPI del Equipo 2. Para el estado vigente del código, consultar [estado del proyecto](estado-proyecto.md). Las muestras no sustituyen la confirmación de los contratos con los otros equipos.

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
- forma un cuerpo con productor, parcela, ciclo activo, aforamiento e inventario de cajas, carton y bolsas;
- calcula el periodo como semana ISO derivada de la fecha del aforamiento;
- conserva consumos recientes de esos empaques para posible calibración futura, aunque el Equipo 2 actualmente no los procesa; no envía pérdidas;
- agrega una ventana de despacho sintética de 06:00 a 16:00 para el día siguiente al aforamiento;
- envía `Content-Type: application/json` a `POST https://platano-logistica-api.onrender.com/api/v1/recomendar`;
- completa con saldo cero los empaques aún no sincronizados desde el celular y suma existencias entre ubicaciones;
- procesa y guarda la respuesta del servicio.

Los consumos incluidos se limitan a esos empaques y a los últimos 90 días. La ventana de 10 horas es un dato sintético para la demostración, no un horario real. La categoría de los productos es Empaque, la unidad es unidad y los nombres van en minúscula y sin tildes. Ver [contrato del Equipo 2](contrato-equipo2.md).

## Verificación y acuerdos que faltan

- Equipo 1: prueba contra el servidor desplegado, incluida respuesta idempotente al reenviar el mismo aforamiento y aceptación del formato actual de areaHa.
- Equipo 2: confirmar si requiere autenticación adicional a `Content-Type` y terminar de acordar la forma y significado de su respuesta.
- Probar el cuerpo y respuesta con el servicio real usando una parcela de prueba autorizada; esta guía no registra una llamada productiva.
- Confirmar versión de Render y revisión Alembic de Neon antes de una demostración productiva.

No hay que entregar secretos dentro de este documento. La configuración de la clave se describe en el contrato del Equipo 1.
