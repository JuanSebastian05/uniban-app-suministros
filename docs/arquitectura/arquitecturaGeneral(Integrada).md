# Arquitectura de la solución integrada UNIBÁN

> **Estado:** propuesta del Equipo 2 (enlace: David Arango) para validar con los enlaces
> Juan José Monsalve (Equipo 3) y Andrés Felipe Flores (Equipo 1). Refleja lo que está
> desplegado y verificado el 28/10/2026; lo que no está verificado se marca como tal.

## Componentes y responsabilidades

| Componente | Equipo | Responsabilidad | Despliegue |
|---|---|---|---|
| Módulo de inferencia (visión artificial) | 1 | Detecta y cuenta plantas en imágenes de una parcela y genera el aforamiento | Lo define el Equipo 1 (no verificado por el Equipo 2) |
| App móvil Android | 3 | **Única interfaz del productor**: parcelas, ciclos, inventario sin conexión, aforamientos y recomendaciones | Dispositivo Android |
| Backend de gestión operativa | 3 | Fuente de verdad de productores, parcelas, inventario y aforamientos (D-008); hace de puente hacia el Equipo 2 | `uniban-app-suministros.onrender.com` |
| Servicio de logística e insumos | 2 | Proyecta la cosecha, calcula la hora límite de corte, programa el despacho y deriva los insumos | `platano-logistica-api.onrender.com` |
| Herramienta de demostración técnica | 2 | Simulador y comparación de modelos para la sustentación. **No** es una interfaz para el productor | App Expo (`frontend/`) y `scripts/` |

## Comunicaciones

![Arquitectura UNIBÁN: componentes de los tres equipos y sus llamadas](arquitectura.svg)

El Equipo 1 nunca le habla al Equipo 2: su aforamiento llega al backend del Equipo 3, que
lo reenvía junto con el inventario cuando el productor pide una recomendación. La línea
dorada es la única llamada que recibe el Equipo 2.

## Recorrido de extremo a extremo (demo del 29/10)

| Paso | Quién | Qué pasa |
|---|---|---|
| 1 | Equipo 1 | Descarga las parcelas con `GET /api/v1/service/parcels`. Cada una trae el UUID que usan los tres equipos. |
| 2 | Equipo 1 | Procesa la imagen en el celular: el modelo YOLO detecta y cuenta plantas y calcula la confianza media de las detecciones. |
| 3 | Equipo 1 → Equipo 3 | Envía el aforamiento con `POST /api/v1/aforamientos` (`parcelId`, fecha, área, plantas, confianza). Un reenvío idéntico responde `DUPLICATE`. |
| 4 | Equipo 3 | El productor pide la recomendación en la app; el backend toma el último aforamiento y el inventario de cajas, cartón y bolsas. |
| 5 | Equipo 3 → Equipo 2 | Despierta el servicio con `GET /salud` y envía `POST /api/v1/recomendar` con la ventana de despacho. |
| 6 | Equipo 2 | Proyecta la cosecha, calcula la hora límite de corte, arma el plan y deriva los insumos (déficit o excedente), con `version_metodo`. |
| 7 | Equipo 3 | Valida la respuesta, la guarda y la muestra en la app, también sin conexión. |

## Evitar duplicidades (punto 4 del profesor)

- **Interfaz del productor:** solo la app del Equipo 3. El Equipo 2 no ofrece pantallas para el
  productor; la app Expo del Equipo 2 se presenta únicamente como herramienta técnica para
  demostrar el motor (simulador y métricas del modelo predictivo).
- **Datos maestros:** parcelas, productores, inventario y aforamientos viven en el Equipo 3.
  El Equipo 2 no los guarda; calcula cada recomendación con lo que recibe.
- **Identificador de parcela:** el UUID que asigna el backend del Equipo 3, usado sin cambios
  por los tres equipos (D-016).

## Contratos

- Equipo 1 → Equipo 3: `docs/integraciones/integracion-equipo1.txt` (repositorio del Equipo 3).
- Equipo 3 → Equipo 2: `docs/integraciones/contrato-equipo2.md` (repositorio del Equipo 3), acordado por
  ambos equipos. La referencia técnica del servicio está en `docs/contrato-integracion.md`
  (este repositorio).

## Brechas conocidas

- La ventana de despacho real no la tiene ningún equipo; hoy es sintética (ver contrato v2).
- La heurística multiparcela (consolidar varios productores en vehículos, T-10) está
  implementada y probada, pero `/recomendar` evalúa una parcela a la vez con el vehículo por
  defecto. Se demuestra con `scripts/demo_programacion.py`.
- Autenticación Equipo 3 → Equipo 2: implementada, apagada (D-018).
