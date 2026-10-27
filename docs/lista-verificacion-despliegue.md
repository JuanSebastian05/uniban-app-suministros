# Lista de verificación para migraciones y despliegues

> Procedimiento vigente para cambios futuros. La copia local de este repositorio tiene como cabeza Alembic 0011_recomendaciones. El usuario indicó que 0010 llegó a main; la revisión real de Neon, la presencia de 0011 en main y el despliegue actual de Render deben confirmarse cada vez.

## 1. Identificar qué se va a desplegar

- [ ] Revisar qué migraciones y rutas introduce el cambio.
- [ ] Confirmar la rama y el commit que se pretende publicar.
- [ ] Consultar la revisión actual de Neon. No inferirla a partir de main, Render o documentación histórica.
- [ ] Confirmar una sola cabeza de Alembic en el código que se desplegará.
- [ ] Revisar el contrato de los equipos externos y las variables que requiere el cambio.

## 2. Ensayar la migración

Usar una base temporal o una rama de desarrollo de Neon. No ensayar contra la base compartida de producción.

~~~powershell
cd backend
.venv\Scripts\python.exe -m alembic heads
.venv\Scripts\python.exe -m alembic current
.venv\Scripts\python.exe -m alembic upgrade head
.venv\Scripts\python.exe -m alembic current
.venv\Scripts\python.exe -m alembic check
~~~

Revisar restricciones, índices y relaciones que agrega el cambio. Conservar un punto de restauración acordado antes de modificar la base compartida.

## 3. Aplicar y desplegar

- [ ] Confirmar la URL de base de datos en un canal seguro antes de ejecutar Alembic.
- [ ] Acordar con el equipo una ventana breve si otras personas usan Neon.
- [ ] Aplicar la migración aprobada y guardar la revisión resultante.
- [ ] Desplegar el código compatible con el esquema.
- [ ] Esperar que Render termine y comprobar los registros de inicio.
- [ ] Consultar /health y /openapi.json.
- [ ] Probar una petición representativa de la función modificada.
- [ ] Registrar en estado-proyecto.md qué revisión se aplicó y qué prueba se ejecutó.

## 4. Particularidades de las integraciones

- Equipo 1: comprobar SERVICE_API_KEY en el panel de secretos sin copiar el valor a logs o documentación; probar consulta de parcelas y envío duplicado del mismo aforamiento.
- Equipo 2: comprobar las variables de destino y validar la solicitud y respuesta con el contrato acordado antes de usar el servicio real.
- El repositorio contiene 0010_aforamientos y 0011_recomendaciones. No significa que ambas estén aplicadas en Neon ni que estén desplegadas.

## 5. Recuperación

No ejecutar downgrade en la base compartida como respuesta automática a un fallo. Identificar primero si el problema es de código, configuración o esquema y acordar una recuperación compatible con los datos. No borrar datos para hacer coincidir una migración.
