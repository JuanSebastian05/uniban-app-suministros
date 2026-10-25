# Guía de prueba manual en celular o emulador (P-06)

Las pruebas automáticas no confirman el comportamiento sin conexión ni los flujos con Firebase real. Esta guía
recorre HU_01 a HU_08 y marca **[CAPTURA n]** donde conviene guardar evidencia para el informe. Tiempo: ≈ 45 min.

## 0. Preparación

**Qué necesitas:** `android-app/app/google-services.json` (carpeta privada del equipo), `android-app/local.properties`
con `api.baseUrl`, un celular o el emulador `Pixel_7`, y PostgreSQL de Windows instalado.

**Backend: NO uses la base compartida de Neon todavía.** Neon está en la migración `0005` y el código ya necesita
`0009`; contra ella los movimientos fallarían con 500. Dos opciones:

- **A (recomendada): base temporal local.** En PowerShell:

```powershell
$bin = "C:\Program Files\PostgreSQL\17\bin"; $d = "$env:TEMP\pgprueba"
& "$bin\initdb.exe" -D $d -U postgres -A trust -E UTF8 --locale=C
Start-Process "$bin\pg_ctl.exe" -ArgumentList "-D `"$d`" -o `"-p 5433`" -l `"$d.log`" start" -WindowStyle Hidden
& "$bin\psql.exe" -h localhost -p 5433 -U postgres -c "CREATE DATABASE uniban_prueba;"
cd backend
$env:DATABASE_URL = "postgresql+psycopg://postgres@localhost:5433/uniban_prueba"
$env:FIREBASE_PROJECT_ID = "<ID del proyecto, el project_id de google-services.json>"
.venv\Scripts\python.exe -m alembic upgrade head          # debe terminar en 0009
.venv\Scripts\python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

- **B: rama de desarrollo de Neon** (ver A-02 en `avisos-equipo.md`): aplicar `alembic upgrade head` ahí y apuntar
  `DATABASE_URL` a esa rama. Nunca a la base compartida.

**App:** en `local.properties`: emulador → `api.baseUrl=http://10.0.2.2:8000/`; celular físico → la IP del PC
(`http://192.168.x.x:8000/`, mismo Wi-Fi y firewall abierto al puerto 8000). Instala con `.\gradlew.bat installDebug`
desde `android-app`. Comprueba `http://localhost:8000/health` → `{"status":"ok"}`.

**Para ver la base** mientras pruebas: `psql -h localhost -p 5433 -U postgres -d uniban_prueba`.

## 1. Inicio de sesión y registro (HU_01, HU_13)

| # | Acción | Resultado esperado |
| --- | --- | --- |
| 1.1 | Abrir la app sin sesión. | Pantalla de login. Enviar vacío → «Por favor ingrese sus credenciales.» |
| 1.2 | «Crear cuenta»: nombre, documento `1020304050`, correo de prueba tuyo, contraseña de 6+ caracteres. | Entra al Inventario con 6 insumos. `SELECT email, document, role FROM users;` muestra la fila. |
| 1.3 | Cerrar sesión (ícono, confirmar). Crear **otra** cuenta con el documento `1.020.304.050`. | «Ya existe una cuenta con este documento…»; los campos siguen escritos. **[CAPTURA 1]** |
| 1.4 | Corregir el documento. | Se crea la cuenta. |
| 1.5 | Iniciar con credenciales incorrectas. | «Correo o contraseña incorrectos.» |

**Cuenta sin perfil (P-02):** en la consola de Firebase crea un usuario (correo y contraseña) que no esté en la base,
inicia sesión con él → entra al inventario y, al responder el servidor, aparece «Complete su perfil» con ese correo.
**[CAPTURA 2]** Guarda nombre y documento → vuelve al inventario y `users` tiene la fila. Prueba también «Usar otra cuenta».

## 2. Parcelas (HU_14, HU_02)

| # | Acción | Resultado esperado |
| --- | --- | --- |
| 2.1 | Parcelas → «Registrar parcela»: vacío. | Errores en nombre y área. Área `0` → error. |
| 2.2 | «La Esperanza», 2,4 ha, iniciar ciclo **Producción** hoy. | Aparece en la lista con su estado. **[CAPTURA 3]** |
| 2.3 | Registrar «la esperanza». | «Ya tienes una parcela con ese nombre.» |
| 2.4 | Registrar «El Mirador», 1,5 ha **sin** ciclo. | En la lista: «Sin ciclo activo». |
| 2.5 | Abrir «La Esperanza». | Área, ciclo activo, fecha de inicio, «En curso», día del ciclo. **[CAPTURA 4]** |
| 2.6 | Modo avión → intentar registrar otra parcela. | «Se necesita conexión…»; los datos siguen en el formulario. |
| 2.7 | Modo avión, cerrar la app por completo y abrirla; Parcelas. | Aviso «Sin conexión» y las mismas parcelas. **[CAPTURA 5]** |

## 3. Movimientos, ajustes e historial (HU_03, 04, 05, 07)

Inventario → «+ Registrar movimiento». Usa **Urea 46 %** (80 kg al inicio).

| # | Acción | Resultado esperado |
| --- | --- | --- |
| 3.1 | **Entrada** 20 kg, observación «Compra». | Confirmación «Pendiente de sincronización»; Urea 100 kg. |
| 3.2 | **Consumo** sin parcela. | Error en Parcela. |
| 3.3 | **Consumo** 12,5 kg en «La Esperanza». | Urea 87,5 kg; el formulario dice «Se asocia al ciclo activo». **[CAPTURA 6]** |
| 3.4 | **Consumo** de 500 kg. | «La cantidad supera la existencia disponible»; no cambia nada. **[CAPTURA 7]** |
| 3.5 | **Pérdida** sin motivo / «Otro» sin observación / Clima 2 kg. | Errores / errores / Urea 85,5 kg. |
| 3.6 | **Ajuste** → Disminuir 5 kg **sin** motivo. | «Describe el motivo del ajuste…». **[CAPTURA 8]** |
| 3.7 | Ajuste Disminuir 5 kg, motivo «Conteo físico». | Urea 80,5 kg; en el detalle aparece con «−». |
| 3.8 | Ajuste Disminuir 1000 kg. | Error junto a la cantidad. |
| 3.9 | Detalle de la parcela. | «Insumos consumidos» lista el consumo de 12,5 kg. |
| 3.10 | Urea → «Ver historial». | Todos los movimientos y «✓ El historial cuadra». Filtrar por tipo «Ajuste» y por parcela. **[CAPTURA 9]** |

## 4. Actividades (HU_08)

| # | Acción | Resultado esperado |
| --- | --- | --- |
| 4.1 | Pestaña **Actividades** → «+ Registrar actividad»; guardar vacío. | Errores de parcela y de tipo. |
| 4.2 | Parcela «El Mirador» (sin ciclo). | Aviso «no tiene un ciclo activo»; al guardar, error. |
| 4.3 | «La Esperanza», **Fertilización**, hoy, «Lote norte»; «+ Agregar insumo» Urea 4 kg. Guardar. | Vuelve a la lista: «Fertilización · 1 insumo usado · Pendiente de sincronización». **[CAPTURA 10]** Urea baja 4 kg. |
| 4.4 | Repetir con Urea **1000 kg**. | Error en esa fila y **no** se guarda la actividad ni se descuenta nada. |
| 4.5 | Parcelas → «La Esperanza». | Sección «Actividades» con la de 4.3. Botón «+ Registrar actividad». **[CAPTURA 11]** |
| 4.6 | Historial de Urea. | El consumo de 4 kg muestra «Actividad: Fertilización». **[CAPTURA 12]** |

## 5. Sincronización y modo avión (HU_06, HU_08)

| # | Acción | Resultado esperado |
| --- | --- | --- |
| 5.1 | Con el backend **encendido**, mira la barra superior. | El contador de pendientes llega a 0 y todo pasa a «Sincronizado». |
| 5.2 | En psql: `SELECT type, quantity, activity_id FROM movements;` y `SELECT type, date FROM activities;` | Los consumos de la actividad traen su `activity_id`; hay una fila en `activities`. **[CAPTURA 13]** |
| 5.3 | Apaga el backend (Ctrl+C). Registra una actividad con un insumo y un ajuste. | Quedan «Pendiente»; el contador suma **ambos**. |
| 5.4 | Enciende el backend. Abre la app / espera un minuto. | Se envían primero la actividad y luego sus consumos; contador en 0. |
| 5.5 | Modo avión, cierra y abre la app. | Abre en Inventario con los datos locales, sin errores de red. |

## 6. Servidor dormido (P-02)

Solo contra Render (**después** de aplicar las migraciones en Neon): deja el servicio 15 min sin uso, abre la app y
registra una parcela. Debe aparecer «puede tardar hasta un minuto» y terminar sin error de tiempo agotado.
**[CAPTURA 14]** (también sirve `https://uniban-app-suministros.onrender.com/health` en el navegador).

## 7. Qué hacer con los resultados

- Anota en `docs/estado-proyecto.md` (P-06) qué pasos pasaron y cuáles no; cada fallo es un *bug*: reporta el paso, lo
  que viste y lo esperado.
- Las capturas van al informe en los campos «Evidencia — pegar aquí» (mapa: 1 documento repetido, 2 completar perfil,
  3–5 parcelas, 6–7 consumos, 8 ajuste, 9 historial, 10–12 actividades, 13 base de datos, 14 servidor dormido).
  No captures cadenas de conexión ni el `google-services.json`.

## 8. Al terminar

```powershell
& "C:\Program Files\PostgreSQL\17\bin\pg_ctl.exe" -D "$env:TEMP\pgprueba" stop -m fast
Remove-Item "$env:TEMP\pgprueba", "$env:TEMP\pgprueba.log" -Recurse -Force
```
