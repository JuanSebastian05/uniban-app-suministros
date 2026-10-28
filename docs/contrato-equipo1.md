# Contrato de integración con el Equipo 1

**Propósito:** permitir que el Equipo 1 consulte las parcelas disponibles y envíe a nuestro backend los aforamientos realizados para ellas.

## Quién llama a quién

- El Equipo 1 consulta y envía datos al backend de Uniban Suministros (Equipo 3).
- La aplicación móvil no recibe aforamientos directamente del Equipo 1. Los consulta a nuestro backend.
- Dirección base de nuestro backend: `https://uniban-app-suministros.onrender.com`.
- Rutas incluidas en este contrato: `GET /api/v1/service/parcels` y `POST /api/v1/aforamientos`.

## Autenticación

Ambas rutas requieren la cabecera `X-API-Key`. El valor es una clave compartida entre los equipos y se configura como secreto en el servidor. No se usa un token de Firebase para estas dos llamadas.

~~~http
X-API-Key: <clave-compartida>
~~~

La clave no debe aparecer en el código, en Postman compartido ni en este documento.

## 1. Consultar las parcelas

~~~http
GET /api/v1/service/parcels
X-API-Key: <clave-compartida>
~~~

Respuesta `200 OK`: lista de parcelas. Si todavía no hay parcelas, devuelve una lista vacía.

~~~json
[
  {
    "id": "11111111-1111-4111-8111-111111111111",
    "name": "La Esperanza",
    "areaHa": "2.40"
  }
]
~~~

El identificador `id` es el valor que se debe enviar como `parcelId` al registrar el aforamiento. Esta ruta lista parcelas de todos los productores y solo expone identificador, nombre y área; no devuelve datos personales ni de ciclo productivo.

**Diferencia frente al ejemplo acordado:** el ejemplo inicial del contrato mostraba `areaHa` como número. La respuesta actual del backend lo serializa como texto con dos decimales (por ejemplo, `"2.40"`). El Equipo 1 debe confirmar que puede convertirlo a número; si exige un número JSON, habrá que ajustar la serialización antes de declarar este punto compatible.

## 2. Enviar un aforamiento

~~~http
POST /api/v1/aforamientos
X-API-Key: <clave-compartida>
Content-Type: application/json
~~~

Cuerpo:

~~~json
{
  "id": "22222222-2222-4222-8222-222222222222",
  "parcelId": "11111111-1111-4111-8111-111111111111",
  "date": "2026-10-29",
  "areaHa": 2.40,
  "estimatedPlants": 1850,
  "confidence": 0.87
}
~~~

| Campo | Significado y regla |
| --- | --- |
| `id` | UUID creado por el Equipo 1 para identificar este aforamiento. Debe conservarse en los reintentos. |
| `parcelId` | UUID de una parcela devuelta por la consulta anterior. |
| `date` | Fecha del aforamiento con formato `AAAA-MM-DD`. |
| `areaHa` | Área medida, en hectáreas; debe ser mayor que cero. Es informativa y no se compara con el área registrada de la parcela. |
| `estimatedPlants` | Cantidad estimada de plantas; entero mayor o igual a cero. |
| `confidence` | Confianza entre 0 y 1. Es opcional. |

### Respuestas del registro

| HTTP | Resultado |
| --- | --- |
| `201 Created` | Aforamiento nuevo guardado. El cuerpo indica `status: "CREATED"`. |
| `200 OK` | Ya existía el mismo UUID con el mismo contenido. No crea una segunda fila y responde `status: "DUPLICATE"`. |
| `401 Unauthorized` | Falta la clave o no coincide. |
| `404 Not Found` | El UUID de parcela no existe. |
| `409 Conflict` | Se reutilizó el UUID del aforamiento con contenido diferente. |
| `422 Unprocessable Entity` | El JSON, el formato de fecha o alguno de los valores no cumple las reglas. |
| `503 Service Unavailable` | El servidor no tiene configurada la clave de servicio. |

Ejemplo de respuesta al primer envío:

~~~json
{
  "id": "22222222-2222-4222-8222-222222222222",
  "status": "CREATED"
}
~~~

Ejemplo al reenviar exactamente el mismo aforamiento:

~~~json
{
  "id": "22222222-2222-4222-8222-222222222222",
  "status": "DUPLICATE"
}
~~~

Si una llamada falla o no se recibe respuesta, reintentar con el mismo UUID y el mismo contenido. Para un nuevo aforamiento se debe generar otro UUID.

## Estado y validación

Las dos rutas están implementadas en el código de este repositorio. Este documento describe ese contrato; por sí solo no demuestra que la versión desplegada ni la base de datos de producción estén actualizadas. La validación operativa se completa al probar las dos llamadas contra el servidor y confirmar la recepción conjunta con ambos equipos.
