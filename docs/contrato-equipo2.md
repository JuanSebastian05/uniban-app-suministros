# Contrato de integración con el Equipo 2

**Propósito:** enviar al servicio del Equipo 2 los datos operativos de una parcela y recibir una recomendación de corte, despacho e insumos. Este documento separa la llamada que hace nuestra app a nuestro backend de la llamada que nuestro backend hace al Equipo 2.

## Recorrido de la información

1. El productor solicita una recomendación desde la aplicación.
2. La app llama a nuestro backend con su token de Firebase.
3. Nuestro backend reúne los datos de la parcela, el aforamiento más reciente, los empaques y sus consumos recientes; después llama al servicio del Equipo 2.
4. Nuestro backend guarda la respuesta, la transforma al formato de la app y la devuelve al productor.

La app no llama directamente al Equipo 2.

## A. Llamada de la aplicación a nuestro backend

Para pedir una recomendación nueva:

~~~http
POST /api/v1/parcels/11111111-1111-4111-8111-111111111111/recommendation
Authorization: Bearer <FIREBASE_ID_TOKEN>
~~~

No requiere cuerpo JSON. El productor debe ser dueño de la parcela y esta debe tener al menos un aforamiento enviado por el Equipo 1.

Para consultar la recomendación más reciente ya guardada:

~~~http
GET /api/v1/parcels/11111111-1111-4111-8111-111111111111/recommendation
Authorization: Bearer <FIREBASE_ID_TOKEN>
~~~

El POST solicita y guarda una nueva respuesta. El GET no vuelve a llamar al Equipo 2; entrega la recomendación más reciente disponible.

## B. Llamada de nuestro backend al Equipo 2

~~~http
POST https://platano-logistica-api.onrender.com/api/v1/recomendar
Content-Type: application/json
~~~

Documentación publicada por el Equipo 2: [Swagger](https://platano-logistica-api.onrender.com/docs).

El formato aceptado para esta integración incluye los datos anidados acordados y los campos directos publicados por el Equipo 2. Nuestro backend envía ambos para que el receptor pueda leer el formato que utiliza. Los campos que describen la misma parcela o el mismo período deben coincidir.

### Solicitud que genera actualmente el backend

~~~json
{
  "producerId": "33333333-3333-4333-8333-333333333333",
  "parcel": {
    "id": "11111111-1111-4111-8111-111111111111",
    "name": "La Esperanza",
    "areaHa": 2.4,
    "activeCycle": {
      "id": "44444444-4444-4444-8444-444444444444",
      "startDate": "2026-01-15",
      "status": "PRODUCCION"
    }
  },
  "aforamiento": {
    "date": "2026-10-29",
    "period": "2026-W44",
    "estimatedPlants": 1850,
    "confidence": 0.87
  },
  "inventory": [
    {
      "supplyId": "55555555-5555-4555-8555-555555555555",
      "name": "cajas",
      "category": "Empaque",
      "unit": "unidad",
      "quantity": 80
    },
    {
      "supplyId": "66666666-6666-4666-8666-666666666666",
      "name": "carton",
      "category": "Empaque",
      "unit": "unidad",
      "quantity": 120
    },
    {
      "supplyId": "77777777-7777-4777-8777-777777777777",
      "name": "bolsas",
      "category": "Empaque",
      "unit": "unidad",
      "quantity": 200
    }
  ],
  "consumptions": [
    {
      "supplyId": "55555555-5555-4555-8555-555555555555",
      "name": "cajas",
      "unit": "unidad",
      "quantity": 12,
      "date": "2026-10-25"
    }
  ],
  "ventana_despacho": {
    "inicio": "2026-10-30T06:00:00",
    "fin": "2026-10-30T16:00:00"
  },
  "parcela_id": "11111111-1111-4111-8111-111111111111",
  "periodo": "2026-W44",
  "num_plantas_estimado": 1850,
  "area_ha": 2.4,
  "calidad_confianza": 0.87
}
~~~

Los UUID del ejemplo son ilustrativos. En una llamada real:

- `producerId` es el identificador UUID interno del productor; no es el UID de Firebase.
- `parcel.id` y `parcela_id` identifican la misma parcela.
- `areaHa` corresponde al área registrada de la parcela. `area_ha` corresponde al área incluida en el aforamiento.
- `period` y `periodo` representan la semana ISO del aforamiento.
- `activeCycle` puede ser `null` si la parcela no tiene un ciclo activo. `confidence` contiene la confianza recibida o `null` si no existe; `calidad_confianza` se omite en ese caso.

### Reglas de los datos enviados

| Dato | Qué se envía |
| --- | --- |
| Inventario | Solo `cajas`, `carton` y `bolsas`, siempre con unidad `unidad` y categoría `Empaque`. Si un material aún no tiene inventario sincronizado, se incluye con cantidad cero. Las existencias se suman entre las ubicaciones del productor. |
| Consumos | Movimientos de tipo CONSUMO de esos tres materiales, asociados a la parcela y registrados en los últimos 90 días. El Equipo 2 confirmó que por ahora no los utiliza; se mantienen para una posible calibración futura. |
| Pérdidas | No se envían. |
| Ventana de despacho | Dato sintético para la demostración: del día siguiente al aforamiento, desde las 06:00 hasta las 16:00 (10 horas). Se entrega como fecha y hora local sin zona horaria explícita. No representa una cita real de despacho. |

## C. Respuesta del Equipo 2

El Equipo 2 confirmó este formato de respuesta:

~~~json
{
  "parcela_id": "11111111-1111-4111-8111-111111111111",
  "periodo": "2026-W44",
  "volumen_racimos": 145.6,
  "fecha_limite_corte": "2026-10-29T13:55:00",
  "cumple_ventana": true,
  "version_metodo": "determinista-v1",
  "item_plan": {
    "parcela_id": "11111111-1111-4111-8111-111111111111",
    "fecha_corte": "2026-10-29T13:55:00",
    "vehiculo_id": "V-3",
    "orden": 1,
    "carga_estimada_racimos": 146,
    "cumple_ventana": true,
    "motivo": null
  },
  "insumos": [
    {
      "insumo": "cajas",
      "fecha": "2026-10-29",
      "cantidad_requerida": 123.76,
      "inventario_disponible": 80,
      "deficit": 43.76
    }
  ],
  "metricas": {
    "cumplimiento_ventana": 1.0,
    "uso_capacidad": 0.35
  }
}
~~~

Este ejemplo fue compartido por el Equipo 2 con datos de prueba. Los campos de fecha y hora siguen el formato ISO 8601. En el acuerdo compartido no se especifica una zona horaria; ese detalle aún debe aclararse entre los equipos.

Nuestro backend comprueba que la respuesta incluya la parcela solicitada tanto en el nivel principal como en el plan, además de validar fechas, tipos, rangos y nombres de insumo. Si la respuesta no cumple el formato esperado, la recomendación no se guarda y la app recibe un error.

La respuesta debe incluir periodo ISO, volumen no negativo, fecha límite, cumplimiento, versión, plan, lista de insumos y métricas. El orden del plan debe ser al menos 1; su carga y las cantidades de insumos deben ser no negativas. El cumplimiento de ventana debe estar entre 0 y 1 y el uso de capacidad no puede ser negativo. El vehículo y el motivo del plan pueden ser `null`. Los nombres aceptados en `insumo` son `cajas`, `carton` y `bolsas`.

### Lo que recibe la aplicación

La respuesta del Equipo 2 usa nombres en español y snake_case. Nuestro backend la entrega a la app con nombres camelCase y agrega el identificador y la fecha de generación del registro:

| Respuesta del Equipo 2 | Campo que recibe la app |
| --- | --- |
| `parcela_id`, `periodo`, `version_metodo` | `parcelId`, `period`, `sourceVersion` |
| `volumen_racimos`, `fecha_limite_corte`, `cumple_ventana` | `volumeBunches`, `cutDeadline`, `meetsWindow` |
| `item_plan` | `plan`, con fecha, vehículo, orden, carga, cumplimiento y motivo |
| `insumos` | `supplies`, con nombre, fecha, cantidad requerida, disponible y déficit |
| `metricas` | `metrics`, con cumplimiento de ventana y uso de capacidad |
| — | `id` y `generatedAt`, creados por nuestro backend |

La app muestra los datos recibidos; no recalcula el plan, el déficit ni las métricas.

## Errores y comportamiento de la llamada

| HTTP de nuestro backend | Significado |
| --- | --- |
| `201 Created` | Se obtuvo y guardó una nueva recomendación. |
| `401 Unauthorized` / `403 Forbidden` | El token de Firebase falta, no es válido o la cuenta no tiene acceso. |
| `404 Not Found` | La parcela no pertenece al productor, o el GET no encuentra una recomendación guardada. |
| `409 Conflict` | La parcela todavía no tiene un aforamiento del Equipo 1. |
| `502 Bad Gateway` | El servicio rechazó la solicitud o devolvió una respuesta inválida. |
| `503 Service Unavailable` | El servicio del Equipo 2 no respondió tras los intentos configurados. |

Antes del POST, el cliente hace una consulta de calentamiento a `GET /salud` para ayudar a despertar el servicio. Luego espera hasta 90 segundos por intento y reintenta una vez ante fallos temporales.

## Configuración y estado

- La URL base se configura con `EQUIPO2_BASE_URL`; por defecto usa `https://platano-logistica-api.onrender.com`.
- La información compartida por el Equipo 2 identifica `Content-Type: application/json` como cabecera obligatoria. Su documentación publicada no declara una autenticación adicional. Nuestro cliente solo agrega `X-API-Key` si se configura `EQUIPO2_API_KEY`; su uso debe acordarse entre los equipos si llegara a ser requerido.
- El código de este repositorio implementa la preparación, envío, validación, almacenamiento y entrega de la recomendación. Esta guía no certifica por sí sola el despliegue ni una prueba de extremo a extremo en producción; eso se confirma con una llamada conjunta usando datos de prueba.
