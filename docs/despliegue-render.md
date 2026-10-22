# Despliegue del backend en Render

> URL pública: **https://uniban-app-suministros.onrender.com/**

El backend FastAPI está publicado en Render (plan gratuito) para que la app en el celular y los otros equipos
(Equipo 1 y Equipo 2) puedan usarlo sin depender de un computador encendido. La base de datos sigue siendo la
de **Neon**, compartida por el equipo.

## Configuración del servicio

| Campo | Valor |
| --- | --- |
| Repositorio | `JuanSebastian05/uniban-app-suministros` |
| Rama | `main` (cada push a `main` redespliega) |
| Root Directory | `backend` |
| Runtime | Python 3 |
| Build Command | `pip install -r requirements.txt` |
| Start Command | `uvicorn app.main:app --host 0.0.0.0 --port $PORT` |
| Instancia | Free |

### Variables de entorno (se cargan en el panel de Render, nunca en el repositorio)

| Variable | Valor |
| --- | --- |
| `DATABASE_URL` | Cadena de Neon con el prefijo `postgresql+psycopg://` y `sslmode=require` (**secreto**). |
| `FIREBASE_PROJECT_ID` | ID del proyecto de Firebase. |
| `ENVIRONMENT` | `production` |
| `PYTHON_VERSION` | `3.12.7` |

`CORS_ORIGINS` no es necesario: la app Android no usa CORS. Ningún valor secreto debe subirse al repositorio (RT_14).

## Comprobar que funciona

1. <https://uniban-app-suministros.onrender.com/health> debe responder `{"status":"ok"}`.
2. <https://uniban-app-suministros.onrender.com/docs> muestra la documentación OpenAPI.
3. En la app, registrar un movimiento: el contador de pendientes baja a cero.

## Conectar la app Android

En `android-app/local.properties` (no se versiona):

```
api.baseUrl=https://uniban-app-suministros.onrender.com/
```

La URL termina en `/`. Recompilar e instalar **sobre** la versión anterior (sin desinstalar), para no perder
movimientos pendientes. Ya no se necesita `adb reverse` ni `uvicorn` local.

## Cosas a tener en cuenta

- **Se duerme tras 15 minutos sin uso.** La primera petición tarda cerca de un minuto. La app lo tiene en cuenta
  (P-02): espera hasta 90 s por la respuesta (`NetworkModule.READ_TIMEOUT_SECONDS`), manda un `GET /health` al
  abrirse para despertarlo mientras el productor llena el formulario, y el registro y la creación de parcelas
  avisan «puede tardar hasta un minuto». Los movimientos pendientes siguen reintentándose. Antes de una prueba o
  demo conviene abrir `/health` en el navegador.
- **Migraciones:** no se ejecutan en Render. Se aplican con `alembic upgrade head` desde un equipo con la
  `DATABASE_URL` de Neon (idealmente en una rama de desarrollo de Neon antes de tocar la base compartida).
  El código desplegado debe corresponder a la versión de la base: hoy, migración `0005`.
- **Base compartida:** el servidor y los equipos de desarrollo usan la misma base de Neon.
- **Autenticación:** todas las rutas de `/api/v1` exigen el token de Firebase de un productor registrado. Los otros
  equipos necesitarán una clave de servicio (pendiente de implementar, ver integración con Equipo 1 y Equipo 2).
- **Solución de problemas:** `Could not open requirements file` → el *Root Directory* no es `backend` o la rama
  no contiene esa carpeta. Errores `500` tras actualizar → la base no tiene la última migración.
