# Inicio: resumen de la finca

## Contenido y navegación

- La app completa abre **Inicio** al iniciar sesión o completar el perfil. La barra fija tiene **Inicio, Parcelas, Inventario y Actividades**. Actividades conserva su lista general.
- Cambiar entre las cuatro pestañas es inmediato, también al volver con Atrás o abrir parcelas filtradas desde el resumen. Se evita superponer el contenido y las barras inferiores durante un fundido. Los detalles y formularios usan un fundido breve de 180 ms.
- Cabecera con marca, saludo y cuenta. El nombre procede del perfil existente del backend; se guarda por UID en preferencias locales para el saludo sin conexión. Si aún no está disponible, se muestra una bienvenida genérica. El menú de cuenta permite cerrar sesión con confirmación.
- Resumen con total de parcelas y cantidades en preparación, producción y descanso. En teléfonos se distribuye en dos filas; en pantallas amplias, en una. Tocar un estado abre Parcelas con ese filtro y permite volver a «Ver todas».
- Una cuenta sin parcelas ve «Registrar mi primera parcela» en lugar de tarjetas con ceros. No se interpreta un error o una carga en curso como una cuenta vacía.
- Accesos a registrar actividad y movimiento. Las actividades necesitan una parcela con ciclo asociado, incluso si está en descanso; los movimientos necesitan inventario disponible. Ambos registros siguen funcionando con los datos locales sin conexión.
- «Atención necesaria» aparece solo si existen errores de envío. El descanso es una etapa normal y no genera alertas. Para parcelas sin ningún ciclo iniciado, una sección neutral «Para empezar» ofrece ir al detalle para iniciar el primer ciclo; no inicia ciclos automáticamente.
- «Registros recientes» combina las tres últimas actividades y movimientos independientes, por fecha del registro y luego fecha de creación. Los consumos asociados a actividades no se repiten. Se muestra la fecha real, sin inventar una hora operativa. Cada fila abre el detalle de la parcela o del insumo; si la parcela ya no está disponible, abre la lista de actividades. «Ver historial» permite elegir actividades o movimientos.
- Un único resumen de sincronización presenta pendientes, envío en curso y último envío exitoso cuando existe. No afirma «Todo sincronizado» sin un envío exitoso previo y una conexión validada. El resumen abre el centro de sincronización.
- La conectividad del dispositivo se observa con `ConnectivityManager`; tener internet no equivale a que el servidor esté disponible. Si falla el refresco de parcelas con internet, se informa del fallo de actualización sin afirmar que el dispositivo está desconectado.

## Implementación

- `ui/home/HomeScreen.kt`: pantalla, estados, adaptación al tamaño de texto y acciones.
- `ui/home/HomeViewModel.kt`: consulta reactiva de registros y conectividad, asociada al grafo autenticado.
- `data/NetworkMonitor.kt`: escucha de conectividad con liberación del callback cuando deja de observarse.
- `data/local/InventoryDao.kt`, `HomeRecord.kt`: consulta local combinada y limitada a tres registros. Se reutilizan las tablas actuales de Room, sin migración.
- `ui/navigation/`: destino de Inicio, cuatro pestañas, filtro opcional de parcelas y regreso desde los formularios.
- `data/auth/` y `data/remote/UserApi.kt`: lectura del nombre del perfil ya existente, sin nuevo endpoint.

Los ViewModel compartidos se destruyen al cerrar sesión. La variante de prueba que construye `AppNavHost` sin repositorio de parcelas conserva el inventario como destino inicial; la aplicación completa siempre proporciona ese repositorio.

## Verificación manual

1. Iniciar sesión: comprobar el saludo y que Inicio esté seleccionado. Visitar las cuatro pestañas.
2. Tocar Producción, revisar el filtro y quitarlo con «Ver todas».
3. Registrar un movimiento desde Inicio, guardarlo, volver al inventario y regresar a Inicio. Verificar saldo, registro reciente y pendientes.
4. Registrar una actividad desde Inicio; comprobar que sus consumos no ocupen filas adicionales en registros recientes.
5. Activar modo avión: verificar aviso y consulta local. Los registros nuevos deben permanecer pendientes hasta recuperar conexión.
6. Comprobar errores de sincronización en una base de prueba, y que el contador de pendientes aparezca una sola vez.
7. Probar una cuenta nueva, una parcela sin ciclo y otra en descanso: solo la primera sin ciclo recibe la sugerencia de iniciarlo.
8. Aumentar el tamaño de fuente y girar el dispositivo. La lista debe desplazarse y la barra inferior permanecer disponible.
9. Cerrar sesión desde el menú de cuenta. Atrás no debe volver a los datos privados.

No se modificó el backend ni se ejecutaron migraciones contra Neon.

## Verificación automática y visual (28/10/2026)

- `assembleDebug testDebugUnitTest lintDebug --max-workers=1 -Pkotlin.incremental=false`, con JDK 21 y la unidad temporal de HU_04: **BUILD SUCCESSFUL; 315 pruebas, cero fallos y cero errores de ejecución**.
- Pruebas nuevas: `HomeScreenTest` (8 casos), `HomeNavigationTest` (4) y `HomeRecordsTest` (3). Cubren las cuatro pestañas, filtros por estado, formularios desde Inicio, cierre de sesión, conectividad representada en pantalla, mensajes de sincronización, texto ampliado y consulta combinada de registros.
- Se actualizaron los datos de prueba antiguos de actividades para incluir `activityCycleId` y el mensaje vigente de primer ciclo. No se cambió la lógica de registro de actividades.
- Se revisaron visualmente las capturas renderizadas por Robolectric con gráficos nativos: `android-app/app/build/reports/home/inicio.png` e `inicio-registros.png`. Son datos ficticios de prueba.
- APK generado: `android-app/app/build/outputs/apk/debug/app-debug.apk`.
- Pendiente: demostración manual en celular/emulador y comprobación con la cuenta real y su conectividad. Las pruebas no contactaron Firebase, Render ni Neon.
- Tras el ajuste de transiciones entre pestañas se volvió a ejecutar `assembleDebug` y las suites `HomeNavigationTest`, `AuthNavigationTest` y `EntryFlowTest`: **19 pruebas aprobadas**, incluido el regreso desde los formularios y el cierre de sesión.
