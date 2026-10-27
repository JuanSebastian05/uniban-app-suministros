# Integración con el Equipo 2: recomendaciones

> Actualizado el 26/10/2026 contra el código local y el esquema externo consultado/documentado en esa fecha. El usuario compartió una estructura de datos saliente como propuesta; no definió todavía qué respuesta espera recibir, y el acuerdo final con el Equipo 2 no está confirmado.

## Flujo

El productor solicita una recomendación en la app. La app llama al backend del Equipo 3, que toma los datos de la parcela, verifica que tenga aforamiento, forma la petición al Equipo 2, guarda la respuesta y la entrega a la app. La app conserva la última respuesta para mostrarla sin conexión.

- Ruta de nuestro backend: POST y GET /api/v1/parcels/{id}/recommendation.
- Ruta externa usada por el código: POST https://platano-logistica-api.onrender.com/api/v1/recomendar.
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
    }
  ],
  "consumptions": [
    {
      "supplyId": "66666666-6666-4666-8666-666666666666",
      "name": "Urea 46%",
      "unit": "kg",
      "quantity": 12.5,
      "date": "2026-10-05"
    }
  ],
  "losses": [
    {
      "supplyId": "77777777-7777-4777-8777-777777777777",
      "name": "Urea 46%",
      "unit": "kg",
      "quantity": 2,
      "date": "2026-10-10",
      "reason": "CLIMA"
    }
  ]
}
~~~

## Lo que el código actual envía

El backend ya arma una solicitud para el servicio. Además del formato anidado anterior, agrega campos directos que aparecen en el OpenAPI del Equipo 2: parcela, periodo, plantas estimadas, área y, cuando existe, confianza.

Los datos salen así:

| Dato | Regla actual |
| --- | --- |
| productor y parcela | Identificadores UUID de la cuenta y parcela seleccionada. |
| ciclo activo | Ciclo vigente o null si la parcela no tiene uno. |
| aforamiento | El más reciente por fecha; el periodo se deriva como semana ISO. Sin aforamiento no se llama al Equipo 2 y nuestra API responde 409 NO_AFORAMIENTO. |
| inventario | Todos los insumos del productor, de todas sus ubicaciones, incluso los que tienen saldo cero. Incluye insumos agrícolas y de empaque. |
| consumos | Registros CONSUMO de la parcela dentro de los últimos 90 días. |
| pérdidas | Registros PERDIDA del productor dentro de los últimos 90 días; no se filtran por parcela porque el modelo no las asocia a ella. |
| ajustes | No se envían como eventos; su efecto ya está en el saldo actual del inventario. |

**Pendiente de implementar:** el usuario decidió enviar al Equipo 2 solo cajas, carton y bolsas. El código actual todavía manda el inventario completo. Este filtrado no se resuelve solo agregando esos productos al catálogo; hay que cambiar la selección que forma la solicitud en el backend.

Para respetar el esquema propuesto, los nombres se guardan como cajas, carton y bolsas, todos en minúscula y sin tildes; categoría Empaque y unidad unidad. El saldo inicial de esos materiales es cero.

## Qué está confirmado y qué falta acordar

El usuario compartió inicialmente la estructura de datos saliente; no hay confirmación registrada de que el Equipo 2 la haya aprobado. En particular se deben confirmar:

- si el servicio espera los campos anidados en camelCase, los campos directos snake_case o ambos;
- si aceptará recibir únicamente inventario de empaque y qué nombres/unidades cruzará con sus recomendaciones;
- si requiere autenticación;
- si consumos y pérdidas son necesarios, y si la ventana de 90 días les sirve;
- cómo tratar una parcela sin ciclo activo y una confianza ausente;
- qué datos operativos requiere para calcular la recomendación;
- el cuerpo definitivo de respuesta, incluyendo qué representa cada fecha, cantidad, déficit y observación.

El código implementado usa el endpoint y el esquema de respuesta descritos en el OpenAPI que se revisó el 26/10/2026. Eso permitió implementar el consumo técnico, pero no equivale a un acuerdo de negocio ni a una prueba de producción con el servicio real. Revalidar el OpenAPI y cerrar el contrato con el equipo antes de usarlo como especificación final.

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
- El backend consulta el servicio externo con reintento ante fallos temporales; la app permite una espera más larga para esta operación.
- Si el servicio no responde, nuestro backend informa indisponibilidad. Si la respuesta externa no corresponde al esquema esperado, devuelve error de respuesta.
- EQUIPO2_BASE_URL puede cambiar la URL; EQUIPO2_API_KEY es opcional en el código, pues el esquema externo consultado no declaraba autenticación.
- La migración 0011 crea la tabla central de recomendaciones. El código Android usa Room 7 para guardar la última por parcela.

## Estado de despliegue

El código local incluye la migración 0011. El usuario indicó que 0010 llegó a main; no hay confirmación en esta revisión de que 0011 esté en main, aplicada en Neon o desplegada en Render. Verificar Neon y Render antes de una prueba con datos reales.
