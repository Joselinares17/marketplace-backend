# Marketplace Backend

Backend de un marketplace de tecnología para Lima, Perú. La plataforma permitirá comprar productos de distintas tiendas, con pedidos y pagos independientes por vendedor.

El proyecto está en configuración inicial. Este README describe las decisiones técnicas y el comportamiento que se irá implementando.

## Stack

La base del proyecto es Java 25 con Spring Boot y Maven. Usamos Spring MVC para la API, Spring Data JPA para persistencia y Flyway para mantener los cambios de esquema bajo control de versiones.

| Área | Tecnología |
|---|---|
| Organización del backend | Spring Modulith |
| Base de datos | PostgreSQL |
| Sesiones y caché | Redis/Valkey |
| Autenticación y autorización | Spring Security |
| Pruebas | JUnit y Testcontainers |
| Entorno local | Docker Compose, Mailpit y FloCI |
| Infraestructura | AWS y Terraform |
| CI/CD | GitHub Actions |

El build utiliza Java 25, Spring Boot 4.1.2, el BOM de Spring Modulith 2.1.1 y Maven Wrapper 3.9.11. Las capacidades de persistencia, seguridad e infraestructura de la tabla son decisiones para etapas posteriores; no se activan en el arranque mínimo.

## Organización del código

La aplicación será un monolito modular: se compila y despliega como una sola aplicación, con el código separado por dominio de negocio.

- `identity`: usuarios, autenticación y permisos.
- `sellers`: registro, administración y habilitación de tiendas.
- `catalog`: productos, precios y disponibilidad.
- `orders`: pedidos y seguimiento de la compra.
- `payments`: cobros, conciliación y reembolsos.
- `shipping`: cotizaciones y seguimiento de envíos.
- `disputes`: reclamos y resolución de disputas.
- `compliance`: controles y obligaciones de la plataforma.
- `notifications`: envío de correos y otras comunicaciones.

El paquete base es `com.linares.marketplace`:

```text
com.linares.marketplace
├── bootstrap       # MarketplaceBackendApplication y configuración técnica
├── contracts       # Datos y eventos neutros compartidos
└── modules
    ├── identity
    ├── sellers     # Incluye reseñas
    ├── catalog
    ├── orders
    ├── payments
    ├── shipping
    ├── disputes
    ├── compliance
    └── notifications
```

Cada módulo se declara cerrado mediante `@ApplicationModule` en su `package-info.java`. La estrategia oficial `explicitly-annotated` y `@Modulithic(additionalPackages = "com.linares.marketplace.modules")` permiten detectar los nueve módulos sin contar `modules`, `bootstrap` ni `contracts` como módulos de negocio. La prueba comprueba los nueve identificadores y sus paquetes exactos.

Por ahora `allowedDependencies = {}` prohíbe dependencias entre módulos: no hay casos de uso que las justifiquen. Cuando se necesite comunicación, se declarará la dependencia concreta y se utilizarán contratos públicos del paquete raíz del módulo, interfaces nombradas explícitamente o eventos. Los subpaquetes permanecen internos. `contracts` se reserva para datos/eventos neutros, sin lógica, entidades JPA ni tipos de SDK de proveedores.

No se crean capas vacías ni implementaciones de negocio. El arranque está en `bootstrap`, con escaneo de componentes y paquetes de autoconfiguración sobre `com.linares.marketplace`; esto conserva la raíz adecuada para futuros repositorios y entidades sin agregar JPA todavía.

Las fixtures inválidas están únicamente en `src/test/java/example/marketplacefixtures`, fuera del escaneo de la aplicación. Solo sus pruebas permiten importar las clases de test en el modelo de Modulith. La verificación real mantiene el importador por defecto, sin exclusiones personalizadas. Los tests negativos exigen `Violations` y mensajes que identifiquen el acceso interno o el ciclo, respectivamente.

## Reglas que guían la implementación

Una compra con productos de varias tiendas se divide en pedidos y pagos separados. El inventario se reserva antes de solicitar el cobro y debe poder liberarse si la operación vence o falla.

Los pagos requieren especial cuidado: una notificación puede llegar repetida, fuera de orden o después de un timeout. Por eso, el procesamiento debe ser idempotente y conservar información suficiente para consultar y conciliar resultados inciertos.

Los importes se manejan con `BigDecimal`, con reglas explícitas de precisión y redondeo. Las operaciones que deban recuperarse después de una caída tendrán estado persistente; su continuidad no dependerá únicamente de tareas en memoria.

## Desarrollo local

El entorno se preparará con Docker Compose:

- PostgreSQL para persistencia.
- Redis/Valkey para sesiones y caché.
- Mailpit para revisar correos sin enviarlos a destinatarios reales.
- FloCI para probar las integraciones AWS compatibles.

PostgreSQL y Redis/Valkey se ejecutarán como motores reales. FloCI se utilizará para emular servicios AWS y no sustituirá las validaciones en staging.

## Infraestructura y despliegue

La infraestructura estará en `infra/`, dentro de este repositorio, y se gestionará con Terraform.

El despliegue previsto utiliza Amazon ECR para almacenar imágenes y Amazon ECS para ejecutar la aplicación. Staging y producción tendrán configuración y permisos separados.

La imagen validada en staging será la misma que se promueva a producción, identificada por su digest.

## Derechos de uso

Copyright © 2026 José Linares. Todos los derechos reservados.

Este proyecto es software propietario. No se concede permiso para
usar, copiar, modificar, distribuir o comercializar su código sin
autorización expresa del titular, salvo lo permitido por la ley.

Las dependencias de terceros conservan sus respectivas licencias.
