# Avisos y acuerdos vigentes del equipo

> Actualizado el 28/10/2026. Para el estado funcional, consultar [estado del proyecto](estado-proyecto.md); para los contratos externos, consultar los documentos de cada equipo. Las notas antiguas sobre ramas y PR ya no son instrucciones vigentes.

## Integración con el Equipo 1

- En el código están implementadas GET /api/v1/service/parcels y POST /api/v1/aforamientos, protegidas con X-API-Key.
- El usuario indicó que SERVICE_API_KEY ya está configurada en Render y que la migración 0010 llegó a main. No se verificaron el panel, Neon ni el despliegue en esta revisión.
- Diferencia que debe confirmarse: el acuerdo muestra areaHa como número; la ruta actual de parcelas la serializa como texto numérico.
- La primera prueba conjunta debe validar la consulta, la recepción, el reenvío idempotente y el formato de areaHa.
- No poner la clave en este documento, el TXT que se comparte o el repositorio. Compartirla con el Equipo 1 por un canal privado.

Ver [contrato de Equipo 1](contrato-equipo1.md) y [texto para compartir](integracion-equipo1.txt).

## Integración con el Equipo 2

- El cliente, el almacenamiento de recomendaciones y las rutas de solicitud/consulta están implementados en el código.
- El Equipo 2 compartió el endpoint `POST /api/v1/recomendar` y su Swagger. El cliente local usa la URL acordada y envía el encabezado obligatorio `Content-Type: application/json`.
- El Equipo 2 aceptó el JSON. El backend limita inventario y consumos a cajas, carton y bolsas; mantiene `consumptions` para uso futuro, aunque hoy el equipo no lo procesa. `losses` ya no se envía.
- Se envía una ventana sintética de 06:00 a 16:00 para el día siguiente al aforamiento (10 horas). No es un horario operativo real; vehículo, capacidad y acarreo siguen usando sus valores.
- El contrato de respuesta ya está definido. La app muestra el resumen, el plan completo, los insumos, métricas y versión del método; confirma que los IDs de parcela de la respuesta correspondan a la solicitud.
- Sigue pendiente confirmar si el Equipo 2 exige autenticación adicional a `Content-Type: application/json` y hacer una prueba real conjunta.
- Los nombres de empaque son minúsculos y sin tildes, categoría Empaque, unidad unidad y saldo inicial cero.

Ver [contrato de Equipo 2](contrato-equipo2.md) y [TXT para compartir](integracion-equipo2.txt).

## Versiones de esquema y despliegue

- En este repositorio, Alembic tiene cabeza 0012_ciclos_etapas_y_descanso y Room está en versión 10 (gestión de ciclos y estado de descanso).
- La revisión real de Neon no se puede deducir de la rama main ni del código que Render expone. Consultar la revisión antes de modificar la base.
- Render no ejecuta Alembic automáticamente. Ensayar en una base temporal, aplicar el esquema compatible y luego desplegar el código.
- Antes de una nueva migración, comprobar que Alembic tenga una sola cabeza y que la revisión pueda subirse desde la versión real de la base.
- El usuario informó que 0010 llegó a main. La aplicación de 0011/0012 y la versión activa de Render no están confirmadas en este documento.

Ver [despliegue](despliegue-render.md) y [lista de verificación](lista-verificacion-despliegue.md).

## Reglas compartidas

- Mantener credenciales, DATABASE_URL, SERVICE_API_KEY y EQUIPO2_API_KEY fuera de Git y de capturas.
- Los movimientos permitidos desde la app son ENTRADA, CONSUMO, PERDIDA y AJUSTE. SALIDA no está habilitada.
- Movimientos y actividades son idempotentes por UUID; las actividades se sincronizan antes que sus consumos asociados.
- El saldo no puede ser negativo y los movimientos no se editan: una corrección se registra como AJUSTE.
- El archivo modelo-datos/MERv2.drawio se actualizó el 26/10/2026 para representar las entidades y relaciones del backend actual.

## Antecedentes

Los avisos A-01 a A-10 de versiones anteriores contenían instrucciones temporales sobre ramas, responsables y migraciones. Se sustituyen por el resumen de esta página y el estado vigente en estado-proyecto.md. Consultar el historial de Git si se necesita el detalle de una decisión antigua.
