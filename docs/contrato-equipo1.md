# Integración con el Equipo 1: parcelas y aforamientos

> Estado actualizado el 26/10/2026 a partir del código del repositorio y de lo reportado por el usuario. Las rutas están implementadas. El usuario indicó que SERVICE_API_KEY está en Render y que la migración 0010 llegó a main; esta revisión no verificó el servidor ni Neon. Coordinar una prueba conjunta antes de tratar la integración desplegada como validada.

El Equipo 1 consulta las parcelas registradas en el Equipo 3 y envía los aforamientos. La app Android no se comunica directamente con el Equipo 1; consulta al backend del Equipo 3.

## Dirección y autenticación

- Servidor: https://uniban-app-suministros.onrender.com
- Las dos rutas usan la cabecera X-API-Key con la clave compartida.
- No se requiere token de Firebase.
- La clave se guarda en los secretos de Render y en la configuración privada del servidor del Equipo 1. No se incluye en este documento, el JSON ni el repositorio.
- El usuario reportó que la variable SERVICE_API_KEY ya se configuró en Render. La entrega segura de la clave al otro equipo y la prueba contra producción deben confirmarse con ellos.

## 1. Consultar parcelas

~~~http
GET /api/v1/service/parcels
X-API-Key: SU_CLAVE
~~~

Ejemplo de respuesta que entrega actualmente el backend:

~~~json
[
  {
    "id": "11111111-1111-4111-8111-111111111111",
    "name": "La Esperanza",
    "areaHa": "2.40"
  }
]
~~~

Diferencia que requiere acuerdo: el ejemplo del acuerdo inicial representa areaHa como número (2.40), pero la respuesta actual serializa el área como texto ("2.40"). Confirmar con el Equipo 1 que puede convertirlo a número o ajustar el esquema si necesitan recibir un número JSON.

La lista incluye las parcelas existentes de todos los productores, con solo identificador, nombre y área. Se debe usar el id recibido como parcelId al enviar el aforamiento.

## 2. Enviar un aforamiento

~~~http
POST /api/v1/aforamientos
X-API-Key: SU_CLAVE
Content-Type: application/json
~~~

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

| Campo | Regla implementada |
| --- | --- |
| id | UUID generado por el Equipo 1. Identifica el aforamiento y permite reintentos idempotentes. |
| parcelId | UUID existente obtenido de la lista de parcelas. |
| date | Fecha calendario en formato AAAA-MM-DD. |
| areaHa | Área aforada en hectáreas, mayor que cero. Es informativa y no se compara con el área de la parcela. |
| estimatedPlants | Número entero mayor o igual a cero. |
| confidence | Opcional; número de 0 a 1. |

### Respuestas

- 201 CREATED: primer envío aceptado.
- 200 DUPLICATE: mismo id y mismo contenido, no crea otro registro.
- 401: clave ausente o incorrecta.
- 404: la parcela indicada no existe.
- 409: se reutilizó el id con contenido distinto.
- 422: formato o valor inválido.
- 503: SERVICE_API_KEY no está configurada en el servidor.

Si no reciben respuesta, reintenten con el mismo id y el mismo contenido. Un nuevo aforamiento debe usar un UUID nuevo.

## Uso en la app y datos

El productor consulta el aforamiento más reciente en el detalle de la parcela. La app usa su propia autenticación Firebase al consultar los endpoints de parcelas; el Equipo 1 no necesita llamarlos.

La migración 0010 crea la tabla central de aforamientos. El código local también contiene HU_09 y la migración 0011 para recomendaciones. Según el usuario, 0010 está en main; el estado actual de Neon y del despliegue de Render debe comprobarse por separado.

## Prueba de integración propuesta

1. El Equipo 1 consulta las parcelas con X-API-Key.
2. Envía un aforamiento usando un parcelId real.
3. Reenvía el mismo cuerpo y UUID: debe recibir DUPLICATE.
4. El Equipo 3 confirma que aparece el aforamiento en la parcela.
5. Se documenta la prueba conjunta y se confirma si areaHa como texto en la consulta de parcelas es aceptable.
