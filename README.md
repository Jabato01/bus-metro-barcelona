# Bus y metro Barcelona

App **libre y no oficial** de bus y metro de Barcelona, pensada ante todo para móviles Android
**sin servicios de Google**: GrapheneOS, LineageOS, /e/OS y similares. También funciona con ellos,
pero no los usa para nada.

> [!IMPORTANT]
> **Datos de la API de TMB, usados bajo sus
> [condiciones de reutilización](https://developer.tmb.cat/docs/terms-conditions).** Proyecto personal,
> sin ánimo de lucro y **sin ninguna vinculación** con TMB ni con ningún ente público. Esta app no
> incluye datos ni claves: cada persona se registra con su propia cuenta, acepta esas condiciones y
> responde del uso que haga. Puede retirarse en cualquier momento ante cualquier reclamación.
>
> La app ha sido *vibecodeada* mediante [Claude Code](https://claude.com/claude-code). Se ofrece
> **sin garantía**: lee el [aviso legal](AVISO-LEGAL.md).

## Funciones

- **Favoritos:** próximos buses y metros en tiempo real; ⚠ si un aviso les afecta.
- **Bus y Metro:** tiempos por parada o estación.
- **Gestión de favoritos:** añadir, eliminar y ordenar.
- **Cerca de mí:** paradas a menos de 100 m de tu posición.
- **Mapa:** paradas y estaciones, con opción de abrirlas en otra app de mapas.
- **Avisos de servicio** de metro y bus.
- **Cómo llegar:** planificador de rutas con búsqueda de direcciones.

## Sin servicios de Google

- Ubicación con el servicio de Android (`LocationManager`), sin la librería de Google. La rapidez
  depende del móvil; si no llega posición, en *Cómo llegar* puedes escribir el origen a mano.
- Mapas con [MapLibre](https://maplibre.org) y [OpenFreeMap](https://openfreemap.org), sin claves.
- Sin Firebase, sin notificaciones push, sin analíticas ni publicidad.

Permisos: Internet, estado de la red y ubicación.

## Credenciales de TMB

1. Regístrate en [developer.tmb.cat](https://developer.tmb.cat), lee sus
   [condiciones](https://developer.tmb.cat/docs/terms-conditions) y crea una aplicación. Las aceptas
   tú, como titular de las claves.
2. En la app, abre **⋮ → Ajustes** y pon su **APP_ID** y **APP_KEY**.

Se guardan cifradas con el almacén de claves de Android, solo en el teléfono, y solo se envían a
`api.tmb.cat` por HTTPS.

## Servicios que usa

| Servicio | Para qué |
|---|---|
| [API de TMB](https://developer.tmb.cat) | Tiempos, paradas, líneas, avisos y rutas |
| [ICGC – Geocodificador](https://eines.icgc.cat/geocodificador) | Direcciones con número |
| [Photon](https://photon.komoot.io) (OpenStreetMap) | Lugares, comercios y monumentos |
| [OpenFreeMap](https://openfreemap.org) | Mapa base |

## Compilar

Con JDK 17 y el SDK de Android (plataforma 37):

```bash
./gradlew assembleRelease
```

## Otras plataformas

La misma app para Sailfish OS y Ubuntu Touch:
[harbour-tmbbcn](https://github.com/Jabato01/harbour-tmbbcn).

## Licencia

[GNU GPL v3](LICENSE).
