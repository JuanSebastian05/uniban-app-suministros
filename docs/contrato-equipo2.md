# Contrato con el Equipo 2 (recomendaciones)

> Estado: **acordado con el Equipo 2** (grupo del equipo, 22/10/2026). Lo implementa quien haga HU_12.
> Los campos de `aforamiento` dependen del Equipo 1, que el 25/10 aceptó el contrato propuesto (ver [`guia-integracion-equipos.md`](guia-integracion-equipos.md)). Guía de implementación: la misma.

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

## Pendiente de acordar

- Qué hacer cuando no hay aforamiento del Equipo 1 para la parcela (¿se omite el bloque? ¿se envía `null`?).
- Ventana de `consumptions` y `losses`.
- Formato de la **respuesta** del Equipo 2 (míralo en su Swagger) y dónde se guarda: tabla nueva con migración
  (la `0006` está reservada para aforamientos; coordina el número según [`avisos-equipo.md`](avisos-equipo.md), A-01).
- Cuándo se dispara la llamada (¿al llegar un aforamiento? ¿bajo demanda desde HU_12?).
