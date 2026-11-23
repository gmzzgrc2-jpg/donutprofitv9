# DonutSMP AH Helper v2.2.0 (Fabric 1.21.11)

Mod client-side para el `/ah` (Auction House) de **DonutSMP**: resalta ofertas baratas o con palabras clave,
muestra el precio unitario en el tooltip y trae una pantalla de configuración animada (compatible con Mod Menu).

## Funciones
- **Subasta (/ah):** resalta por precio máximo (total o unitario), palabras clave o ambos (AND/OR), con tooltip de precio unitario.
- **Mercado:** aprende el precio normal (mediana) de cada item mientras navegas el /ah y lo compara en el tooltip
  (`Mercado: $80K c/u -35%`). El filtro de **gangas** resalta ofertas X % por debajo de la mediana. El historial se guarda en `config/donutah_prices.json`.
  Los libros/armas encantados se agrupan por encantamiento, así que no se mezclan precios distintos.
- **Alertas de chat:** sonido + aviso en pantalla cuando mencionan tu nombre o aparece una palabra tuya (ej. `sold`, `tpa`).
- **Modo venta:** invierte el filtro de precio (resalta precios MAYORES), útil en `/orders` o al vender.
- **Menús extra:** analiza otros menús con precios en el lore (`/ahhelper title orders`).

Todo es informativo: el mod **no** hace clics, compras ni acciones por ti.

## Requisitos
- Minecraft 1.21.11 · Fabric Loader 0.18.4+ · Fabric API 0.141.3+1.21.11 · Java 21
- Mod Menu 17.0.0 (opcional)

## Compilar
```bash
gradle wrapper --gradle-version 8.14.3   # una sola vez, genera ./gradlew
./gradlew build                          # jar en build/libs/
```
El workflow de GitHub Actions (`.github/workflows/build.yml`) hace lo mismo y sube el jar como artefacto.

## Comandos (`/ahhelper`)
| Comando | Efecto |
|---|---|
| `/ahhelper` o `config` | Abre la pantalla de configuración |
| `toggle` | Activa/desactiva el mod |
| `maxprice <50000 / 250K / 1.5M / off>` | Filtro de precio máximo |
| `search <texto / clear>` | Palabras clave separadas por coma |
| `unit` | Alterna comparación por precio unitario o total |
| `lore` | Buscar palabras clave también en el lore |
| `sound` | Alerta sonora ON/OFF |
| `market` | Activa/desactiva el rastreo de precios |
| `deals [5-90]` | Activa el filtro de gangas (opcional: % mínimo de descuento) |
| `stats` | Items rastreados, muestras y gangas vistas |
| `clearprices` | Borra el historial de precios |
| `sell` | Modo venta (resalta precios mayores o iguales) |
| `mention` | Alerta al mencionar tu nombre ON/OFF |
| `alerts <palabras / clear>` | Palabras de alerta en el chat, separadas por coma |
| `status` | Resumen de la configuración |
| `debug` | Muestra en el chat el título de cada menú que abres |
| `title <texto / clear>` | Trata como subasta los menús cuyo título contenga ese texto |
| `scanall` | Analiza todos los contenedores (último recurso) |

## Si no resalta nada en /ah
1. `/ahhelper debug`, abre `/ah` y mira el título que aparece.
2. Si dice «NO detectado»: `/ahhelper title <parte del título>` (o `/ahhelper scanall`).

Configuración en `config/donutah.json`.
