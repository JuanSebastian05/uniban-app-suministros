# Despliegue del backend en Render y base Neon

> Guía actualizada el 28/10/2026. En esta revisión no se consultó el panel de Render ni Neon. El usuario informó que SERVICE_API_KEY está configurada en Render y que la migración 0010 llegó a main.

## Servicio

| Campo | Configuración documentada |
| --- | --- |
| URL | https://uniban-app-suministros.onrender.com |
| Repositorio | JuanSebastian05/uniban-app-suministros |
| Rama | main |
| Directorio raíz | backend |
| Build | pip install -r requirements.txt |
| Inicio | uvicorn app.main:app --host 0.0.0.0 --port $PORT |
| Base de datos | PostgreSQL en Neon |

Confirma estos valores en el panel si cambia el servicio o la rama.

## Variables de entorno

Se configuran en el panel Environment de Render. No se guardan en Git ni se copian en documentos o capturas.

| Variable | Uso |
| --- | --- |
| DATABASE_URL | Conexión secreta a Neon en formato SQLAlchemy/psycopg y con SSL. |
| FIREBASE_PROJECT_ID | Proyecto que firma los tokens de Firebase usados por la app. |
| SERVICE_API_KEY | Clave para X-API-Key en las rutas del Equipo 1. Según el usuario, ya está configurada. |
| EQUIPO2_BASE_URL | URL del servicio del Equipo 2; el backend tiene una URL por defecto. |
| EQUIPO2_API_KEY | Opcional según el contrato del Equipo 2. |
| ENVIRONMENT | Entorno del servicio; usar production para Render. |
| CORS_ORIGINS | Solo se necesita si un cliente web requiere CORS. |

Si cambia una variable, guardar y esperar a que Render complete el redeploy. Para rotar SERVICE_API_KEY, coordinar el nuevo valor con el Equipo 1 por un canal privado.

## Migraciones de base de datos

Render ejecuta la API, pero no aplica las migraciones de Alembic automáticamente. El historial de esta copia del repositorio llega hasta 0012_ciclos_etapas_y_descanso.

Antes de cada despliegue que incorpore cambios de esquema:

1. Revisa la revisión actual en Neon y confirma que la aplicación local apunta a la base correcta.
2. Ensaya la migración en una rama o base temporal con alembic upgrade head y verifica la revisión.
3. Aplica en la base compartida según el procedimiento acordado por el equipo.
4. Despliega el código compatible con el esquema.
5. Comprueba el estado del servicio, OpenAPI y el flujo funcional afectado.

El usuario indicó que la migración 0010 llegó a main. No se conoce aquí si las migraciones 0011 y 0012 llegaron a main, se aplicaron en Neon o quedaron desplegadas. Consulta la [lista de verificación](lista-verificacion-despliegue.md) y el [estado del proyecto](../gestion/estado-proyecto.md); no uses esta frase como evidencia de la revisión de producción.

## Comprobaciones del servicio

- Salud: https://uniban-app-suministros.onrender.com/health
- Swagger: https://uniban-app-suministros.onrender.com/docs
- OpenAPI: https://uniban-app-suministros.onrender.com/openapi.json

Render en plan gratuito puede detener el servicio cuando queda inactivo; la primera solicitud tras la pausa puede tardar. La app contempla tiempos de espera largos para el despertar del backend. Antes de una demostración, abre /health y luego valida las rutas que se usarán.

Ver una ruta en OpenAPI no demuestra que la migración esté aplicada ni que el flujo end-to-end funcione.

## Configurar la app para Render

En android-app/local.properties, configura la URL base https://uniban-app-suministros.onrender.com/. Instala la actualización sin desinstalar la aplicación para conservar los datos locales pendientes. No publiques google-services.json ni local.properties.

## Estado informado por el usuario

- SERVICE_API_KEY quedó configurada en Render.
- La migración 0010 llegó a main.
- Las migraciones 0011 y 0012, y su despliegue, no están confirmados en esta revisión.
- No se confirmó una prueba productiva conjunta con el Equipo 1 ni una llamada real al servicio del Equipo 2.
