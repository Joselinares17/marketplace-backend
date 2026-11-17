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

Las versiones concretas de las dependencias se fijarán al configurar el build y comprobar su compatibilidad.

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

Cada módulo expone los contratos que necesitan los demás y mantiene sus clases internas encapsuladas. Spring Modulith permitirá verificar esos límites y detectar dependencias circulares.

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

Los comandos de arranque, compilación y pruebas se añadirán cuando el proyecto base esté configurado.

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
