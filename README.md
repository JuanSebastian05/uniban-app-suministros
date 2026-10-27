# Uniban App Suministros

Aplicación Android offline-first para que productores de plátano gestionen inventario, movimientos, parcelas y actividades. Un backend FastAPI central sincroniza los datos, recibe aforamientos del Equipo 1 y consulta recomendaciones del Equipo 2.

> **Estado de referencia: 27/10/2026.** HU_01–10, HU_12–14 y las integraciones de servicio con los Equipos 1 y 2 están implementadas en el código local. HU_11 está parcialmente implementada. El tipo de movimiento **SALIDA** todavía no se puede registrar desde la app. El estado del código y las verificaciones de despliegue se distinguen en [estado del proyecto](docs/estado-proyecto.md).

## Qué resuelve

- Registra usuarios productores, parcelas y ciclos productivos.
- Mantiene el inventario disponible sin conexión, registra entradas, consumos, pérdidas y ajustes, y conserva historial por insumo.
- Registra actividades productivas y sus consumos relacionados.
- Sincroniza actividades y movimientos con el backend cuando vuelve la conexión.
- Recibe del Equipo 1 el aforamiento de una parcela y lo muestra al productor.
- Envía información operativa al Equipo 2 para solicitar recomendaciones y las guarda para consulta sin conexión.

La app Android se comunica únicamente con nuestro backend. El Equipo 1 escribe en las rutas de servicio del backend y el backend se comunica con el Equipo 2.

## Componentes

| Componente | Tecnología y responsabilidad |
| --- | --- |
| App Android | Kotlin, Jetpack Compose, Room, Retrofit, WorkManager; uso offline-first. |
| Identidad | Firebase Authentication; el backend verifica el token y el rol de productor. |
| Backend | FastAPI, SQLAlchemy, Alembic y PostgreSQL. |
| Base local | Room, actualmente versión 8. |
| Base central | PostgreSQL; configuración de producción documentada para Neon. |
| Servicio web | Render, URL https://uniban-app-suministros.onrender.com. |

## Recorrido de datos

~~~text
Productor → App Android → Room → sincronización WorkManager → FastAPI → PostgreSQL
Equipo 1 → GET parcelas / POST aforamientos → FastAPI → PostgreSQL → App Android
App Android → FastAPI → POST recomendación → servicio del Equipo 2
~~~

## Catálogo inicial

Una cuenta nueva recibe seis insumos agrícolas de demostración y tres materiales de empaque: **cajas**, **carton** y **bolsas**. Los nombres de empaque están en minúsculas y sin tildes; su categoría es **Empaque**, la unidad es **unidad** y el saldo inicial es cero. El código también incorpora esos tres productos a cuentas ya existentes sin sobrescribir movimientos ni cantidades. Al pedir una recomendación, el backend envía únicamente esos tres empaques; si alguno todavía no se ha sincronizado desde el celular, lo incluye con saldo cero.

## Ejecutar en local

### App Android

Requisitos: Android Studio, JDK 17 o 21 compatible con Gradle, Android SDK 35 y emulador o dispositivo con Android 8.0 (API 26) o superior.

1. Abre la carpeta android-app en Android Studio.
2. Configura android-app/local.properties a partir de local.properties.example; por defecto, el emulador usa http://10.0.2.2:8000/.
3. Para autenticación real, coloca el archivo privado google-services.json en android-app/app/. No lo subas a Git. En builds debug se puede usar el modo demo.
4. Sincroniza Gradle y ejecuta la configuración app.

La barra principal permite acceder a Parcelas e Inventario; la pestaña Actividades está disponible desde la navegación de la app.

### Backend

Requisitos: Python 3.11 o superior y PostgreSQL 15 o superior.

~~~powershell
cd backend
python -m venv .venv
.venv\Scripts\Activate.ps1
pip install -r requirements.txt
Copy-Item .env.example .env
# Editar DATABASE_URL y FIREBASE_PROJECT_ID en .env
.venv\Scripts\python.exe -m alembic upgrade head
.venv\Scripts\python.exe -m uvicorn app.main:app --reload
~~~

- Salud: http://localhost:8000/health
- API interactiva: http://localhost:8000/docs
- La cabeza del historial de migraciones en este repositorio es 0011_recomendaciones; use alembic upgrade head en una base local vacía.
- Configura SERVICE_API_KEY localmente para ensayar las rutas del Equipo 1. Mantén las claves y cadenas de conexión fuera del repositorio.

## Integraciones entre equipos

- **Equipo 1 → Equipo 3:** GET /api/v1/service/parcels y POST /api/v1/aforamientos, con X-API-Key. La clave se configura como secreto. El contrato está en [contrato del Equipo 1](docs/contrato-equipo1.md).
- **Equipo 3 → Equipo 2:** el backend usa POST /api/v1/recomendar; la app pide la recomendación a nuestro backend. El contrato y las diferencias pendientes están en [contrato del Equipo 2](docs/contrato-equipo2.md).
- Según el usuario, SERVICE_API_KEY ya está configurada en Render y la migración 0010 llegó a main. No se ha confirmado en esta revisión que la versión desplegada de Render y la base Neon tengan la migración 0011, ni una prueba real conjunta con ambos equipos.

## Documentación del proyecto

- [Estado del código y tareas de integración](docs/estado-proyecto.md)
- [Arquitectura Android y backend](docs/arquitectura/README.md)
- [Historias de usuario](docs/hu01.md) a [HU_14](docs/hu14.md), incluyendo [HU_10](docs/hu10.md) y [HU_11](docs/hu11.md)
- [Contrato y guía operativa del Equipo 1](docs/contrato-equipo1.md) y [TXT para compartir](docs/integracion-equipo1.txt)
- [Contrato del Equipo 2](docs/contrato-equipo2.md) y [TXT para compartir](docs/integracion-equipo2.txt)
- [Despliegue Render y Neon](docs/despliegue-render.md) y [lista de migraciones](docs/lista-verificacion-despliegue.md)
- [Guía de prueba manual](docs/guia-prueba-celular.md)
- [Actualización técnica del 26/10/2026](docs/avances/Actualizacion-tecnica-y-avances-2026-10-26.pdf). El PDF de avances de semanas 5–6 se conserva como registro histórico.
- [Requisitos originales](docs/requisitos/HU_y_Requisitos_Tecnicos_Equipo3.pdf) y [anteproyecto](docs/avances/AnteproyectoInicial-PI1-Equipo3-Uniban.pdf): documentos de referencia académica; pueden conservar decisiones anteriores al código actual.

## Límites conocidos

- La app registra ENTRADA, CONSUMO, PERDIDA y AJUSTE; SALIDA no está habilitada en el formulario.
- La sincronización actual es de subida de movimientos y actividades; el backend sí expone consultas de inventario y parcelas, pero la app no reconstruye todo el inventario desde el servidor al reinstalar.
- La solicitud de recomendación envía cajas, carton y bolsas, incluso con saldo cero; conserva consumos de empaque para posible uso futuro, pero omite pérdidas. Incluye una ventana sintética de despacho de 06:00 a 16:00 para el día después del aforamiento.
- El contrato exacto del Equipo 2 y algunos despliegues de producción requieren confirmación conjunta; consulte los contratos antes de tratar las muestras como acuerdos cerrados.
