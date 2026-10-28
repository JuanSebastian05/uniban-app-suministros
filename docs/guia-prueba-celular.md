# Guía de prueba manual en celular o emulador

> Esta guía prueba la app y el backend actual. No reemplaza una prueba de contrato con el Equipo 1 o el Equipo 2. Registrar en [estado del proyecto](estado-proyecto.md) la fecha, el dispositivo, la versión instalada y los pasos que pasaron.

## Preparación segura

Se necesita Android Studio, Firebase de prueba configurado en google-services.json, un emulador o celular, y un backend apuntando a una base de datos temporal. Evitar datos ficticios en la base Neon compartida.

En el backend local:

1. Configurar PostgreSQL temporal y variables privadas en backend/.env.
2. Aplicar el esquema completo del repositorio con alembic upgrade head; en esta copia la revisión de cabeza es 0012.
3. Iniciar FastAPI y confirmar /health.
4. Configurar api.baseUrl en android-app/local.properties: el emulador usa http://10.0.2.2:8000/; un celular físico usa la IP local del computador.
5. Instalar la app debug sin desinstalar una instalación que contenga datos pendientes.

Las cuentas nuevas muestran seis insumos agrícolas más cajas, carton y bolsas; los materiales de empaque tienen unidad unidad y saldo inicial cero.

## 1. Inicio, registro y perfiles

- Iniciar sin sesión y comprobar el formulario de acceso.
- Crear un productor de prueba y verificar que entra a la app.
- Confirmar que un documento duplicado se rechaza.
- Probar una cuenta Firebase sin perfil backend y completar el perfil.
- Cerrar sesión e iniciar con otra cuenta; confirmar que no se muestra el inventario local de la primera.

## 2. Inventario y movimientos

Con un insumo que tenga saldo, comprobar:

- Registrar una entrada y observar el aumento.
- Registrar un consumo ligado a parcela/ciclo y observar el descuento.
- Registrar una pérdida con motivo.
- Intentar consumir más de la existencia y confirmar que no se guarda un saldo negativo.
- Registrar un ajuste de aumento y de disminución con motivo.
- Consultar el historial, filtrar movimientos y confirmar que su saldo concuerda.
- Confirmar que SALIDA no aparece como opción registrable.
- Confirmar los nombres exactos, unidad y saldo inicial cero de los tres productos de empaque.

## 3. Parcelas, ciclos y actividades

- Crear una parcela con ciclo activo y otra sin ciclo.
- Consultar el detalle, área y estado del ciclo.
- Cambiar de En preparación a Producción y confirmar que el estado se actualiza en la lista y el detalle.
- Finalizar el ciclo y confirmar que aparece En descanso; iniciar otro y confirmar que comienza En preparación.
- Intentar registrar una parcela sin conexión; confirmar que se informa que requiere conexión.
- Registrar una actividad productiva con un insumo; comprobar que actividad y consumo quedan relacionados.
- En descanso, registrar siembra o cosecha: debe aparecer un aviso orientativo y permitir guardar la actividad.
- Probar una cantidad mayor que el saldo; no debe guardarse parcialmente la actividad.
- Ver la actividad en el detalle de parcela y en el historial del movimiento asociado.

## 4. Sincronización y modo avión

- Con backend disponible, crear movimiento y actividad; confirmar que los pendientes se sincronizan.
- Desde Inventario, abrir “Estado de sincronización”; revisar el total, la hora del último envío exitoso y la sección de errores.
- Con conexión disponible y registros pendientes, tocar “Reintentar ahora”; confirmar que el trabajo queda en cola y que los contadores se actualizan sin reiniciar la app.
- Desconectar el backend o activar modo avión antes de registrar un movimiento; confirmar que queda pendiente y no se altera la última hora exitosa.
- Provocar un rechazo 4xx controlado en un entorno de prueba; confirmar que el registro aparece en la lista central con un motivo legible. Restaurar el backend y reintentar.
- Detener temporalmente el backend, crear un movimiento y una actividad y verificar que quedan pendientes localmente.
- Reiniciar el backend y confirmar que la actividad se sincroniza antes que los consumos asociados.
- Activar modo avión, cerrar y abrir la app, y consultar inventario e historial local.
- Confirmar que otra cuenta del mismo teléfono no ve los datos locales de la cuenta anterior.

## 5. Aforamientos del Equipo 1

En una base de prueba con una parcela y SERVICE_API_KEY ficticia:

- Consultar GET /api/v1/service/parcels con X-API-Key.
- Enviar POST /api/v1/aforamientos con una parcela válida.
- Reenviar el mismo id y el mismo cuerpo; debe responder DUPLICATE.
- Consultar la parcela desde la app y confirmar que aparece el aforamiento más reciente.

Esto valida el código local, pero no prueba por sí mismo Render ni el sistema real del Equipo 1. La respuesta de parcelas serializa actualmente areaHa como texto; registrar si el cliente lo acepta.

## 6. Recomendación del Equipo 2

Este paso llama al servicio externo real y puede crear tráfico o guardar datos. Ejecutarlo solo cuando el equipo haya confirmado el contrato y se cuente con una parcela de prueba con aforamiento.

- Solicitar una recomendación desde el detalle de parcela.
- Confirmar que se muestra respuesta, fecha, plan e insumos.
- Volver a abrir sin conexión; debe mostrarse la última respuesta local.
- Registrar los campos enviados y recibidos sin incluir claves ni datos personales.

El backend envía solo cajas, carton y bolsas, también cuando su saldo sea cero; conserva consumos de esos empaques para posible uso futuro, excluye pérdidas y agrega una ventana sintética de 06:00 a 16:00 para el día siguiente al aforamiento. El Equipo 2 actualmente no procesa `consumptions`. Antes de una prueba real, confirmar autenticación adicional y registrar la respuesta recibida. Ver [contrato-equipo2.md](contrato-equipo2.md).

## 7. Evidencia y resultado

Registrar cada caso como pasó, falló o no probado. Guardar capturas útiles para el informe sin mostrar claves, cadenas de conexión, correos personales o google-services.json. Actualizar el estado del proyecto solo con resultados efectivamente observados.
