# Contrato con el Equipo 1 (aforamiento)

> Estado: **implementado en el backend** (26/10/2026, rama `feat/integracion-equipo1`). El Equipo 1 aceptó el
> contrato propuesto el 25/10 (ver [`guia-integracion-equipos.md`](guia-integracion-equipos.md)). La clave de servicio
> la genera y configura Simon en Render; **nunca** va en el repositorio.

El Equipo 1 **llama a nuestro backend** con dos peticiones HTTP. La app Android no habla con ellos (RT_11): consulta lo
recibido a través de nuestro backend (HU_09).

| Dato | Valor |
| --- | --- |
| URL base | `https://uniban-app-suministros.onrender.com` (Render se duerme a los 15 min; la primera petición tarda ≈ 1 min) |
| Autenticación | Cabecera `X-API-Key: <clave de servicio>` en **ambas** rutas. No usan el token de Firebase. |
| Documentación | `/docs` y `/openapi.json` del mismo servidor |

## Clave de servicio

| Respuesta | Cuándo |
| --- | --- |
| `401` | Falta la cabecera `X-API-Key` o no coincide (comparación en tiempo constante). |
| `503` | El servidor no tiene configurada `SERVICE_API_KEY` (nunca se acepta una clave vacía). |

## `GET /api/v1/service/parcels`

Devuelve **todas** las parcelas de todos los productores, ordenadas por nombre, solo con lo necesario (sin productor,
documento, ciclo ni más datos):

```json
[{ "id": "6f1c0a52-…", "name": "La Esperanza", "areaHa": "2.40" }]
```

`areaHa` sale con **dos decimales y como texto** (`"2.40"`), el mismo formato que `ParcelOut` en el resto de la API.
Si el Equipo 1 la necesita como número, avisar (cambio pequeño).

## `POST /api/v1/aforamientos`

```json
{
  "id": "uuid generado por el Equipo 1",
  "parcelId": "uuid de una parcela de GET /service/parcels",
  "date": "2026-10-29",
  "areaHa": 2.40,
  "estimatedPlants": 1850,
  "confidence": 0.87
}
```

| Campo | Regla |
| --- | --- |
| `id` | UUID, obligatorio. Hace idempotente el reenvío. |
| `parcelId` | UUID de una parcela existente. |
| `date` | `AAAA-MM-DD`. |
| `areaHa` | Mayor que 0 y hasta 99999,99. **Informativa**: no se contrasta con el área de la parcela. |
| `estimatedPlants` | Entero entre 0 y 2.147.483.647. |
| `confidence` | **Opcional**, entre 0 y 1. |

| Respuesta | Cuándo |
| --- | --- |
| `201` `{ "id", "status": "CREATED" }` | Primera vez. |
| `200` `{ "id", "status": "DUPLICATE" }` | Mismo `id` y mismo contenido: no se duplica. |
| `404` | La parcela no existe. |
| `409` | Mismo `id` con contenido distinto. |
| `422` | Fecha inválida, `areaHa` ≤ 0, plantas negativas o no enteras, `confidence` fuera de 0 a 1, UUID inválido. |
| `401` / `503` | Clave de servicio ausente o incorrecta / no configurada. |

Los aforamientos son de **solo inserción**: una corrección se envía con otro `id`. La app muestra el más reciente por
`date` (empata por `received_at`).

## Supuestos (no definidos con el Equipo 1; decididos aquí)

- **Redondeo.** `areaHa` se redondea a 2 decimales y `confidence` a 3 (mitad hacia arriba) al guardar. Reenviar el mismo
  `id` con más decimales sigue siendo idempotente porque se compara ya redondeado.
- **`confidence` ausente** se guarda como nulo y la app no muestra el dato (la HU dice «si existe»).
- **Varios aforamientos por parcela y fecha** se aceptan con ids distintos.
- **`period`** (semana ISO que pide el Equipo 2) **no lo envía el Equipo 1**: se derivará de `date` al armar la
  recomendación (ver [`contrato-equipo2.md`](contrato-equipo2.md)).
- **404 vs 409 por parcela inexistente:** se usa `404`, porque el recurso referenciado no existe.

## Modelo de datos

Tabla `aforamientos` (migración `0010`, sobre la `0009`): `id UUID PK`, `parcel_id UUID FK → parcels.id`, `date DATE`,
`area_ha NUMERIC(9,2)`, `estimated_plants INTEGER`, `confidence NUMERIC(4,3)` nulo, `received_at TIMESTAMPTZ`; restricciones
`area_ha > 0`, `estimated_plants >= 0` y `confidence` entre 0 y 1; índice `(parcel_id, date)`.

## Cómo entregar la clave al Equipo 1

1. Generarla (en tu máquina, sin guardarla en ningún archivo del repo):
   `python -c "import secrets; print(secrets.token_urlsafe(32))"`
2. Render → servicio del backend → **Environment** → agregar `SERVICE_API_KEY` con ese valor → guardar (Render redespliega).
3. Pasársela al Equipo 1 por un canal privado y pedirles que la envíen en `X-API-Key`.
4. Comprobar: `GET /api/v1/service/parcels` sin cabecera responde `401`, y con ella `200`.
5. Rotarla: repetir 1 y 2 y avisarles; la anterior deja de valer al redesplegar.
