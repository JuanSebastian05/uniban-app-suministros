# Integración con el Equipo 2: recomendaciones

> Actualizado el 27/10/2026 contra el código local, el endpoint compartido por el Equipo 2 y su OpenAPI publicado. El consumo de la respuesta coincide con el esquema, pero faltan acuerdos sobre autenticación, campos operativos y uso de consumos/pérdidas.

## Flujo

El productor solicita una recomendación en la app. La app llama al backend del Equipo 3, que toma los datos de la parcela, verifica que tenga aforamiento, forma la petición al Equipo 2, guarda la respuesta y la entrega a la app. La app conserva la última respuesta para mostrarla sin conexión.

- Ruta de nuestro backend: POST y GET /api/v1/parcels/{id}/recommendation.
- Ruta externa usada por el código: POST https://platano-logistica-api.onrender.com/api/v1/recomendar.
- Encabezado requerido informado por el Equipo 2: `Content-Type: application/json`; nuestro cliente lo envía.
- Swagger: https://platano-logistica-api.onrender.com/docs. Su esquema acepta `inventory` como lista de objetos genéricos y la respuesta coincide con `RespuestaRecomendacion`.
- El OpenAPI no declara autenticación ni API key. Esto no prueba que no exista una regla externa; confirmarlo con el Equipo 2.
- La app Android no llama directamente al Equipo 2.
- Las rutas de nuestro backend se autentican con token Firebase del productor.

## Información que se propuso enviar

La estructura siguiente corresponde a la propuesta compartida por el usuario. Debe confirmarse con el Equipo 2 antes de considerarla un contrato aceptado:

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
      "supplyId": "88888888-8888-4888-8888-888888888888",
      "name": "carton",
      "category": "Empaque",
      "unit": "unidad",
      "quantity": 0
    },
    {
      "supplyId": "99999999-9999-4999-8999-999999999999",
      "name": "bolsas",
      "category": "Empaque",
      "unit": "unidad",
      "quantity": 0
    }
  ],
  "consumptions": [
    {
      "supplyId": "66666666-6666-4666-8666-666666666666",
      "name": "cajas",
      "unit": "unidad",
      "quantity": 12,
      "date": "2026-10-05"
    }
  ],
  "losses": [
    {
      "supplyId": "77777777-7777-4777-8777-777777777777",
      "name": "bolsas",
      "unit": "unidad",
      "quantity": 2,
      "date": "2026-10-10",
      "reason": "CLIMA"
    }
  ]
}
~~~

## Lo que el código actual envía

El backend ya arma una solicitud para el servicio. Además del formato anidado anterior, agrega campos directos que aparecen en el OpenAPI del Equipo 2: parcela, periodo, plantas estimadas, área y, cuando existe, confianza.

El cuerpo actual contiene estos datos:

| Dato | Regla actual |
| --- | --- |
| productor y parcela | Identificadores UUID de la cuenta y parcela seleccionada. |
| ciclo activo | Ciclo vigente o null si la parcela no tiene uno. |
| aforamiento | El más reciente por fecha; el periodo se deriva como semana ISO. Sin aforamiento no se llama al Equipo 2 y nuestra API responde 409 NO_AFORAMIENTO. |
| inventario | Solo cajas, carton y bolsas. Suma las existencias por ubicación; si un empaque aún no tiene un movimiento sincronizado, se envía con cantidad cero. Los nombres y la unidad se normalizan a lo acordado. |
| consumos | Registros CONSUMO de la parcela dentro de los últimos 90 días, solo si corresponden a uno de los tres empaques. |
| pérdidas | Registros PERDIDA del productor dentro de los últimos 90 días, solo si corresponden a uno de los tres empaques; el modelo no las asocia a una parcela. |
| ajustes | No se envían como eventos; su efecto ya está en el saldo actual del inventario. |

Aunque los productos de saldo cero todavía no existen como filas sincronizadas en el backend, la solicitud los incluye con identificadores estables generados con el mismo criterio del catálogo Android. Esto no sincroniza esas filas a la tabla de inventario central; solo completa el cuerpo enviado al Equipo 2.

Los nombres son cajas, carton y bolsas, en minúscula y sin tildes; categoría Empaque, unidad unidad y saldo inicial cero.

## Qué está confirmado y qué falta acordar

El OpenAPI publicado confirma la ruta, la cabecera `Content-Type`, campos directos (`parcela_id`, `periodo`, `num_plantas_estimado`, `area_ha`, `calidad_confianza`), los objetos de Equipo 3 (`producerId`, `parcel`, `aforamiento`, `inventory`) y el esquema de respuesta. El arreglo `inventory` acepta objetos genéricos; el esquema no describe sus propiedades internas. En particular se deben confirmar:

- si requiere autenticación adicional a la cabecera `Content-Type` documentada;
- si procesa `consumptions` y `losses`, que no aparecen en el modelo `SolicitudRecomendacion` de Swagger;
- si los nombres `cajas`, `carton` y `bolsas` y unidad `unidad` son las claves que usa para cruzar recomendaciones;
- si los valores por defecto de acarreo, vehículo, capacidad y ventana son válidos para nuestra operación;
- cómo tratar una parcela sin ciclo activo y una confianza ausente;
- qué representa cada fecha, cantidad, déficit y observación devuelta.

El backend local ya coincide con el endpoint y el esquema de respuesta publicados. No se ha hecho una llamada real de recomendación con datos de productor, por lo que la integración productiva todavía debe validarse conjuntamente.

## Respuesta que consume el código actual

Con base en el esquema OpenAPI documentado, el backend valida y guarda campos como volumen estimado, fecha límite de corte, cumplimiento de ventana, plan de corte, lista de insumos y métricas. La app los muestra en el detalle de parcela. La forma que el código espera es equivalente a:

~~~json
{
  "parcela_id": "11111111-1111-4111-8111-111111111111",
  "periodo": "2026-W44",
  "volumen_racimos": 1850,
  "fecha_limite_corte": "2026-11-02T14:30:00",
  "cumple_ventana": true,
  "version_metodo": "v0.3.1",
  "item_plan": {
    "fecha_corte": "2026-11-02T08:00:00",
    "orden": 1,
    "carga_estimada_racimos": 420,
    "cumple_ventana": true,
    "vehiculo_id": "V-3",
    "motivo": null
  },
  "insumos": [
    {
      "insumo": "cajas",
      "fecha": "2026-11-01",
      "cantidad_requerida": 120,
      "inventario_disponible": 80,
      "deficit": 40
    }
  ],
  "metricas": {
    "cumplimiento_ventana": 1.0,
    "uso_capacidad": 0.88
  }
}
~~~

La respuesta documentada no incluye un campo de exceso. La app no calcula exceso ni genera predicciones; muestra lo que recibe. Si la HU requiere exceso u observaciones por cada insumo, debe ampliarse el contrato de respuesta.

## Errores y operación técnica

- Antes de pedir la recomendación, el backend exige un aforamiento de la parcela.
- El cliente envía `Content-Type: application/json` y prueba primero `GET /salud` para despertar el servicio; esa ruta aparece en el OpenAPI.
- El backend consulta el servicio externo con reintento ante fallos temporales; la app permite una espera más larga para esta operación.
- Si el servicio no responde, nuestro backend informa indisponibilidad. Si la respuesta externa no corresponde al esquema esperado, devuelve error de respuesta.
- `EQUIPO2_BASE_URL` permite cambiar la URL. `EQUIPO2_API_KEY` se admite como configuración opcional, pero el OpenAPI no declara esa autenticación y el usuario no ha reportado que el equipo la exija.
- La migración 0011 crea la tabla central de recomendaciones. El código Android usa Room 7 para guardar la última por parcela.

## Estado de despliegue

El código local incluye la migración 0011. El usuario indicó que 0010 llegó a main; no hay confirmación en esta revisión de que 0011 esté en main, aplicada en Neon o desplegada en Render. Verificar Neon y Render antes de una prueba con datos reales.
