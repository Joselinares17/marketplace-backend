# B002: matriz de runtime y construcción

Fecha de revisión: 2026-11-16. Estado: preparado para revisión, sin commit.

## Contexto y decisión

El repositorio contiene el backend Maven en `marketplace-backend/`; los comandos
del Wrapper se ejecutan desde esa carpeta. Se trabaja en `dev`, con promociones
`dev -> test -> main` mediante merge commits. No se crearon ramas nuevas.

El proyecto generado ya contenía una aplicación, una prueba `contextLoads`, una
configuración Testcontainers y un lanzador de desarrollo. También incluía JPA,
Redis, seguridad, Flyway, Docker Compose y observabilidad sin capacidades de
negocio que los requirieran. B002 conserva los fuentes y reduce las dependencias.

| Componente | Versión / política | Gestión y compatibilidad |
| --- | --- | --- |
| Java | JDK 25; release 25; preview habilitado | Enforcer exige `[25,26)`. Se verificó con Amazon Corretto 25.0.5+10-LTS. No se fija proveedor ni ruta personal. |
| Maven | 3.9.11 exacta | Wrapper y Enforcer `[3.9.11]`; Boot requiere Maven >= 3.6.3. |
| Maven Wrapper | 3.3.4, only-script | Sin wrapper JAR; scripts oficiales con el ajuste Windows descrito abajo. |
| Spring Boot | 4.1.2 | Parent exacto; admite Java 17 a 27, incluido 25. |
| Spring Framework | 7.0.10 | Gestionado por Boot, sin override. |
| Spring Modulith | BOM 2.1.1 | Publicación estable basada en Boot 4.1.1; se conserva sobre la actualización de parche Boot 4.1.2. Las dependencias y la verificación modular se incorporarán en B003. |
| Maven Compiler | 3.15.0 | Gestionado por el parent Boot; release 25 y enablePreview. |
| Maven Surefire | 3.5.6 | Gestionado por Boot; JVM con `--enable-preview`; falla si no encuentra pruebas. |
| Maven Enforcer | 3.6.3 | Versión explícita para imponer JDK y Maven en validate. |
| JUnit Jupiter | 6.0.3 | Gestionado por Boot, obtenido mediante starter webmvc-test. |
| Testcontainers | 2.0.5 | Gestionado por Boot; dependencias de test para conservar compilables los fuentes generados. |

No se utilizan versiones snapshot/milestone de herramientas o dependencias.
`0.0.1-SNAPSHOT` sigue siendo la versión del artefacto de desarrollo propio.
Las versiones de compilador, Surefire, JUnit y Testcontainers se comprobaron en
el POM de dependencias 4.1.2 resuelto localmente; no se sobrescriben.

## Dependencias iniciales y capacidades futuras

Se conservan Spring MVC y su starter de pruebas, Spring Boot Testcontainers,
Testcontainers JUnit Jupiter y PostgreSQL. Los últimos se necesitan para compilar
la configuración y el lanzador generados, aunque el build mínimo no inicia Docker.

JPA/Hibernate, PostgreSQL JDBC, Flyway, Security, springdoc-openapi, Redis,
sesiones, Actuator y observabilidad se añadirán cuando se implementen sus
capacidades. No se eligen versiones de componentes que todavía no se incorporan.
El BOM de Modulith queda fijado sin activar todavía sus starters ni crear módulos.

`TestcontainersConfiguration` conserva ambos beans existentes y se activa con
el perfil Spring `containers`. `contextLoads` conserva su import y su prueba:
con el perfil por defecto verifica el contexto MVC sin servicios externos.
No se usan skips, exclusiones de pruebas ni sustitutos de base de datos.
La configuración de contenedores es soporte generado de desarrollo, no una
prueba de integración adicional; sus imágenes `latest` preexistentes no se
modificaron y ese perfil no forma parte de la validación reproducible de B002.
Su activación y configuración de servicios quedan pendientes para la capacidad
correspondiente; no se afirma que PostgreSQL o Redis estén validados.

## Preview, JDK y ejecución

Java 25 StructuredTaskScope es API preview. Se prepara compilación y ejecución
de pruebas con preview, sin agregar ejemplos ni casos de concurrencia.
No existía Maven Failsafe ni pruebas `*IT`; no se crea un ciclo de integración
vacío. Al incorporarlo se deberá pasar también `--enable-preview` a Failsafe.
El plugin Boot recibe ese argumento para `spring-boot:run`; ejecutar un futuro
JAR que use preview requiere `java --enable-preview -jar ...`.
La configuración existente de virtual threads en application.yaml se conserva.

El JDK que ejecuta Maven es también el compilador utilizado. No se selecciona un
JDK alternativo mediante toolchains.xml: Enforcer rechaza cualquier major distinto
de 25 antes de compilar. Cada equipo debe seleccionar su JDK mediante JAVA_HOME
o su IDE; las rutas personales no se guardan en Git. Los parches 25.x se permiten
para recibir actualizaciones; no se afirma reproducibilidad binaria entre
proveedores/parches. Se fija outputTimestamp para los timestamps de empaquetado.

## Wrapper e integridad

Se reemplaza la referencia inicial Maven 3.10.1 por Maven 3.9.11, una versión
estable publicada y compatible. No se depende del Maven instalado globalmente.

Distribución: https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip

Se descargó el ZIP y se cotejó con su SHA-512 publicado:

```text
03e2d65d4483a3396980629f260e25cac0d8b6f7f2791e4dc20bc83f9514db8d0f05b0479e699a5f34679250c49c8e52e961262ded468a20de0be254d8207076
```

Su SHA-256 calculado se fija en distributionSha256Sum:

```text
0d7125e8c91097b36edb990ea5934e6c68b4440eef4ea96510a0f6815e7eeadb
```

El Wrapper comprueba este SHA-256 al descargar Maven; una caché ya extraída
no se revalida en cada ejecución. `wrapperSha256Sum` no aplica a only-script.
Los scripts se cotejaron con la distribución oficial 3.3.4 de Central, cuyo
checksum publicado disponible es SHA-1 (no SHA-256/SHA-512); no equivale a una
verificación criptográfica moderna de procedencia. `mvnw` no necesitó cambios
de contenido, pero se preparó el modo Git 100755 para Unix.

El primer intento de mvnw.cmd falló con «No se puede indizar en una matriz nula»:
el script oficial indexaba `.Target[0]` incluso cuando `.m2` no era un enlace.
Se conserva el script oficial con un ajuste mínimo: consultar Target y comprobar
su existencia antes de indexarlo. El build posterior ejercitó esa corrección.

## Verificaciones ejecutadas en Windows

Se seleccionó el Corretto 25.0.5 instalado únicamente en el entorno del proceso.
No se cambiaron JAVA_HOME global ni configuraciones personales.

| Comando / comprobación | Resultado real |
| --- | --- |
| `java -version` inicial | Java 17.0.19; incompatible con B002. |
| `git switch dev` | Rama existente dev; cambio previo de .gitignore conservado. |
| `./mvnw.cmd -v` con JDK 25 | Maven 3.9.11, Java 25.0.5, Windows 11 amd64. |
| `./mvnw.cmd clean verify` con JDK 25 | BUILD SUCCESS, compilación, pruebas y empaquetado Boot completados. |
| Surefire XML | MarketplaceBackendApplicationTests: 1 prueba, 0 fallos, 0 errores, 0 omitidas; contextLoads. |
| `./mvnw.cmd -B -ntp validate` con JDK 17 | BUILD FAILURE esperado en RequireJavaVersion con mensaje que exige JDK 25. |

Se observaron avisos de Unsafe de Guice/Maven y de carga dinámica del agente
Mockito; no causaron fallos. No se ocultaron mediante flags ni se sobrescribieron
dependencias gestionadas por Boot para silenciarlos.

No se ejecutó el build en Linux/macOS: esta sesión dispone de Windows y no se
encontraron bash/wsl en PATH. Pendientes en esos sistemas, desde el backend:

```sh
./mvnw -v
./mvnw clean verify
```

No se validó el perfil containers, infraestructura, servicios externos ni
comportamiento de concurrencia preview. Tampoco se ejecutó aún una prueba de
límites de módulos: corresponde a B003.

## Fuentes oficiales consultadas

- [Spring Boot 4.1.2: requisitos](https://docs.spring.io/spring-boot/system-requirements.html).
- [POM parent Boot 4.1.2 publicado](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-starter-parent/4.1.2/spring-boot-starter-parent-4.1.2.pom).
- [BOM Boot 4.1.2](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.2/spring-boot-dependencies-4.1.2.pom).
- [Modulith: releases; 2.1.1 actualiza a Boot 4.1.1](https://github.com/spring-projects/spring-modulith/releases/tag/2.1.1).
- [BOM Modulith 2.1.1 publicado](https://repo.maven.apache.org/maven2/org/springframework/modulith/spring-modulith-bom/2.1.1/spring-modulith-bom-2.1.1.pom).
- [Apéndice Modulith](https://docs.spring.io/spring-modulith/reference/appendix.html): la tabla de compatibilidad todavía menciona 2.0 snapshot; se contrastó con la release estable, no se tomó esa tabla desactualizada como prueba de compatibilidad 2.1.
- [Maven 3.9.11 release](https://maven.apache.org/docs/3.9.11/release-notes.html).
- [Maven Wrapper e integridad](https://maven.apache.org/tools/wrapper/).
- [Distribución Wrapper 3.3.4](https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper-distribution/3.3.4/).
- [Enforcer: JDK](https://maven.apache.org/enforcer/enforcer-rules/requireJavaVersion.html) y [rangos](https://maven.apache.org/enforcer/enforcer-rules/versionRanges.html).
- [Java 25: concurrencia estructurada](https://docs.oracle.com/en/java/javase/25/core/structured-concurrency.html) y [preview](https://docs.oracle.com/en/java/javase/25/language/preview-language-vm-features.html).

## Selección del commit

docs/ sigue ignorado por la decisión previa del repositorio. Para versionar
únicamente este ADR se utiliza git add -f sobre el archivo exacto, sin cambiar
la política del resto de docs/. El cambio previo de .gitignore queda fuera.
El permiso ejecutable de mvnw ya quedó preparado en el índice; los restantes
cambios de B002 se dejan sin staging para revisión. No se hizo commit ni push.
