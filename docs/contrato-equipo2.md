# Contrato con el Equipo 2 (recomendaciones)

> Estado: **implementado en el backend** (26/10/2026, rama `feat/integracion-equipo2`) y acordado con el Equipo 2 (grupo del
> equipo, 22/10/2026). Los campos de `aforamiento` vienen del Equipo 1 (contrato en [`contrato-equipo1.md`](contrato-equipo1.md)).
> Guía de implementación: [`guia-integracion-equipos.md`](guia-integracion-equipos.md). La app muestra el resultado en HU_12.

El backend del Equipo 3 envía los datos de una parcela al servicio de logística del Equipo 2 y guarda las
recomendaciones que devuelve (alimentan HU_12). La app Android **no** llama a ese servicio: solo habla con
nuestro backend (RT_11).

## Endpoint del Equipo 2

| Dato | Valor |
| --- | --- |
| Método y URL | `POST https://platano-logistica-api.onrender.com/api/v1/recomendar` |
| Cabecera obligatoria | `Content-Type: application/json` |
| Documentación (Swagger) | <https://platano-logistica-api.onrender.com/docs> |

El servicio está en Render (plan gratuito): la primera llamada tras un rato sin uso tarda ≈ 1 minuto en
despertar; usa un tiempo de espera generoso y no lo trates como error.

## Cuerpo que enviamos

```json
{
  "producerId": "uuid",
  "parcel": {
    "id": "uuid",
    "name": "La Esperanza",
    "areaHa": 2.40,
    "activeCycle": { "id": "uuid", "startDate": "2026-01-15", "status": "PRODUCCION" }
  },
  "aforamiento": {
    "date": "2026-10-29",
    "period": "2026-W44",
    "estimatedPlants": 1850,
    "confidence": 0.87
  },
  "inventory": [
    { "supplyId": "uuid", "name": "Urea 46%", "category": "Fertilizante", "unit": "kg", "quantity": 82.5 }
  ],
  "consumptions": [
    { "supplyId": "uuid", "name": "Urea 46%", "unit": "kg", "quantity": 12.5, "date": "2026-10-05" }
  ],
  "losses": [
    { "supplyId": "uuid", "name": "Urea 46%", "unit": "kg", "quantity": 2.0, "date": "2026-10-10", "reason": "CLIMA" }
  ]
}
```

## De dónde sale cada campo

| Campo | Fuente en nuestra base | Nota |
| --- | --- | --- |
| `producerId` | `users.producer_id` del dueño de la parcela | |
| `parcel.*` | `parcels` (HU_14); `areaHa` = `area_ha` | |
| `parcel.activeCycle` | `production_cycles` con `end_date IS NULL` (HU_14) | Puede no existir: la parcela sin ciclo activo envía `null`. Confirmar con el Equipo 2 si lo aceptan. |
| `aforamiento.date`, `estimatedPlants`, `confidence` | **Equipo 1** (HU_09) | Pendiente de su contrato. `period` parece ISO semana (`2026-W44`). |
| `inventory[]` | `inventory` + `supplies` del productor (`quantity` = existencia actual) | Una fila por insumo. |
| `consumptions[]` | `movements` tipo `CONSUMO` de la parcela y su ciclo (HU_05) | `date` = fecha del movimiento. Falta acordar la ventana de tiempo (¿ciclo completo o últimos N días?). |
| `losses[]` | `movements` tipo `PERDIDA` (HU_05) | `reason`: `DESPERDICIO`, `MAL_MANEJO`, `CLIMA`, `OTRO`. Las pérdidas no llevan parcela: definir si se envían las del productor. |

Los **ajustes** (`AJUSTE`, [`hu05.md`](hu05.md#ajustes-ajuste-24102026)) ya están reflejados en `inventory[].quantity`; no se
envían como lista aparte.

## Lo que dice su OpenAPI (leído el 26/10/2026, `/openapi.json`, solo lectura)

- `POST /api/v1/recomendar` no declara **ninguna autenticación**. `SolicitudRecomendacion` **no tiene campos obligatorios**:
  todo es opcional y varios campos traen valores por defecto de demostración (`area_ha` 2,4; `calidad_confianza` 0,87;
  `tiempo_acarreo_min` 35; `vehiculo_id` «V-3»; `capacidad_vehiculo_racimos` 420). Dice aceptar «formato directo o formato del
  Equipo 3»; `producerId`, `parcel`, `aforamiento` e `inventory` son objetos libres (no describe sus claves).
- Campos directos que sí describe: `parcela_id`, `periodo`, `num_plantas_estimado`, `calidad_confianza`, `area_ha`,
  `tiempo_acarreo_min`, `ventana_despacho` (`inicio`, `fin`), `inventario` (mapa insumo → cantidad), `vehiculo_id` y
  `capacidad_vehiculo_racimos`.
- No declara `consumptions` ni `losses`: se envían igual (el esquema no prohíbe campos extra), pero **no sabemos si los usan**.
- Respuesta `RespuestaRecomendacion`: `parcela_id`, `periodo`, `volumen_racimos`, `fecha_limite_corte`, `cumple_ventana`,
  `version_metodo`, `item_plan` (`fecha_corte`, `orden`, `carga_estimada_racimos`, `cumple_ventana`, `vehiculo_id`, `motivo`),
  `insumos[]` (`insumo`, `fecha`, `cantidad_requerida`, `inventario_disponible`, `deficit`) y `metricas`
  (`cumplimiento_ventana`, `uso_capacidad`). **No trae «exceso»**; la única observación en texto es `item_plan.motivo`.
- Existe además `POST /api/v1/ingesta/operacion` (datos operativos del Equipo 3: acopio, vehículos, ventana de despacho,
  inventario y consumos). **No lo usamos**: ver «Brechas».

## Cómo lo implementa el backend

`POST /api/v1/parcels/{id}/recommendation` (token de Firebase, solo parcelas del productor) arma la solicitud, llama al Equipo 2,
valida y guarda la respuesta; `GET` devuelve la última guardada. Detalle y API en [`hu12.md`](hu12.md).

| Tema | Decisión |
| --- | --- |
| Variables | `EQUIPO2_BASE_URL` (por defecto `https://platano-logistica-api.onrender.com`) y `EQUIPO2_API_KEY` (opcional; hoy su API no la pide; si se configura, va en `X-API-Key`). |
| Espera | Antes del POST, una **llamada de calentamiento** `GET /salud` (hasta 60 s, de mejor esfuerzo: se ignora su resultado) para despertar su servicio en Render. Luego el POST con 90 s por intento y **un reintento** (5 s después) ante tiempo agotado, error de red o 5xx. Un 4xx no se reintenta. Peor caso ≈ 4 min. |
| Fallos | Sin respuesta → `503` `EQUIPO2_UNAVAILABLE`; respuesta que no cumple su contrato o solicitud rechazada → `502` `EQUIPO2_BAD_RESPONSE`. El detalle del error de ellos nunca se reenvía a la app. |
| Solicitud | Formato del contrato (camelCase) **y** campos directos (`parcela_id`, `periodo`, `num_plantas_estimado`, `calidad_confianza`, `area_ha`). Así no dependemos de cómo mapeen los objetos anidados. |
| `period` | Semana ISO de la **fecha del último aforamiento**: `2026-W44`. Supuesto: el contrato solo decía «parece ISO semana». |
| Sin aforamiento | **No se llama al Equipo 2.** Aunque su esquema lo permite, usaría sus valores por defecto de demostración y devolvería una recomendación inventada. Respuesta `409` `NO_AFORAMIENTO` con mensaje en español. |
| Sin ciclo activo | Se envía `parcel.activeCycle = null`. |
| Ventana de consumos y pérdidas | Últimos **90 días**. Consumos: los de **esa parcela**. Pérdidas: las **del productor** (no llevan parcela). Supuesto: no estaba acordado. |
| Inventario | El de todas las ubicaciones del productor (una fila por insumo, existencia actual, ajustes incluidos). |
| Confianza ausente | No se envía `calidad_confianza` (el Equipo 2 usaría su 0,87 por defecto). |
| Datos de otros | Nunca se envían datos de otro productor. |

## Brechas y supuestos pendientes de confirmar con el Equipo 2

- **Datos operativos.** No enviamos acopio, vehículos, tiempo de acarreo ni ventana de despacho: el Equipo 2 usa sus valores por
  defecto (35 min, vehículo «V-3», 420 racimos, su ventana). La recomendación depende de ellos. `POST /ingesta/operacion` podría
  alimentarlos; no está en el alcance acordado.
- **Insumos.** Su respuesta habla de insumos de **empaque** (cajas, cartón, bolsas); nuestro inventario son insumos de cultivo
  (urea, fertilizante…). Hay que confirmar si cruzan nombres o si `inventory` les sirve de algo.
- **`consumptions` y `losses`** no figuran en su esquema.
- **`parcela_id`.** Ellos hablan de ids como «P-018»; enviamos nuestro UUID.
- **Exceso.** HU_12 pide «déficit o exceso»; su respuesta solo trae déficit. **Brecha de la HU**: no calculamos el exceso (la app
  no calcula, solo muestra).
- **Observaciones.** Solo `item_plan.motivo` (cuando no cumple la ventana); la app lo muestra como observación.
- **Fechas.** `fecha_limite_corte` y `fecha_corte` llegan sin zona horaria; se muestran tal cual.
