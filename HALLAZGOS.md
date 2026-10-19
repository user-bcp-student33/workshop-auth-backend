# Hallazgos del backend de autenticacion

Revision estatica limitada a este repositorio. Esta es la unica matriz de
seguimiento; los cambios posteriores de `2-refactor.md` deben actualizar estas
filas conservando sus identificadores. H-01 a H-08 se corrigieron o verificaron
con aprobacion humana; no se cierra trabajo fuera del alcance autorizado.

Hay una fila por cada caso de [RETO.md](RETO.md), mas H-08 (P-01 del plan),
ordenadas por severidad. Todos tienen evidencia local; ninguno se marca como "No encontrado".
En SCA se confirmaron versiones y coincidencias con advisories, sin afirmar
explotabilidad local. Las severidades son una priorizacion del laboratorio, no
puntuaciones CVSS.

| id | hallazgo | tipo | explicacion | evidencia (archivo:linea) | severidad | estado |
|---|---|---|---|---|---|---|
| H-01 | SQL: concatenacion de credenciales | Vulnerabilidad | La consulta anterior concatenaba credenciales y permitia alterar la busqueda. Resuelto con SQL constante, `PreparedStatement` y parametros vinculados; mapeo separado y recursos cerrados. Las cinco pruebas de correccion fallaron antes y pasan despues, sin alterar el contrato HTTP. | [src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:16](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L16), [src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:44](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L44), [src/test/java/com/bcp/workshop/auth/LegacyDatabaseTest.java](src/test/java/com/bcp/workshop/auth/LegacyDatabaseTest.java) | alta | Resuelto |
| H-02 | Datos: contrasenas en texto plano | Vulnerabilidad | Resuelto con PBKDF2-SHA256 y salt aleatorio; migracion SQLite transaccional versionada e idempotente y capas HTTP/servicio/repositorio/seguridad. PasswordMigrationTest falla antes y pasa despues; 57 pruebas verdes, PasswordHasher 31/33 lineas y las demas clases modificadas 100%. [Diff H-02](target/h02.diff), [antes](target/h02-correction-before.log), [despues](target/h02-after.log). | [PasswordHasher.java](src/main/java/com/bcp/workshop/auth/PasswordHasher.java), [PasswordMigration.java](src/main/java/com/bcp/workshop/auth/PasswordMigration.java), [AuthService.java](src/main/java/com/bcp/workshop/auth/AuthService.java) | alta | Resuelto |
| H-03 | Token: Base64 con secreto embebido | Vulnerabilidad | Resuelto: JWT RS256, sub/role/iss/aud/iat/exp, TTL 3600 y sin secreto en payload; se elimino el emisor legacy. TokenServiceTest falla antes y verifica firma, alteracion, clave equivocada y limite de expiracion despues; 60 pruebas verdes, TokenService 40/44 lineas. No se presume validacion externa. [Diff](target/h03.diff), [antes](target/h03-correction-before.log), [despues](target/h03-verified.log). | [TokenService.java](src/main/java/com/bcp/workshop/auth/TokenService.java), [TokenServiceTest.java](src/test/java/com/bcp/workshop/auth/TokenServiceTest.java) | alta | Resuelto |
| H-04 | Configuracion: secreto y CORS hardcodeados | Vulnerabilidad | Resuelto: claves PEM/issuer/audience y lista CORS obligatorios por entorno, sin fallback ni wildcard; arranque eager valida claves y origenes. CorsTest/TokenConfigurationTest fallan antes y pasan despues; 80 pruebas verdes y cinco arranques reales sin variable obligatoria rechazados. [Diff](target/h04.diff), [antes](target/h04-correction-before.log), [despues](target/h04-after.log), [arranque](target/h04-startup-results.json). | [TokenService.java](src/main/java/com/bcp/workshop/auth/TokenService.java), [CorsConfiguration.java](src/main/java/com/bcp/workshop/auth/CorsConfiguration.java), [application.properties](src/main/resources/application.properties) | alta | Resuelto |
| H-05 | Errores: respuesta expone detalles internos | Vulnerabilidad | Resuelto: 500 conserva claves con valores genericos; diagnostico servidor copia solo frames y clase, sin mensajes/causas sensibles. AuthFailureTest: 4 fallos antes, 4 verdes despues; suite 82 verdes y AuthResource 24/24 lineas. [Diff](target/h05.diff), [antes](target/h05-correction-before.log), [despues](target/h05-after.log). | [AuthResource.java](src/main/java/com/bcp/workshop/auth/AuthResource.java), [AuthFailureTest.java](src/test/java/com/bcp/workshop/auth/AuthFailureTest.java) | media | Resuelto |
| H-06 | SCA: Quarkus 3.27.4 y Commons Text 1.9 | Sugerencia | Resuelto: BOM/plugin 3.40.1.1, Commons Text 1.15.0 gestionado y transitivas coordinadas; sin afirmar explotabilidad local. Caracterizacion 89/89 antes/despues; SCA 11 paquetes/49 avisos antes -> 0/236 paquetes despues. [Diff](target/h06.diff), [evaluacion por aviso](target/h06-advisory-assessment.json), [tests](target/h06-after.log), [SCA](target/h06-sca-green.json). | [pom.xml:13](pom.xml#L13), [AuthService.java:45](src/main/java/com/bcp/workshop/auth/AuthService.java#L45) | media | Resuelto |
| H-08 | Cobertura: JaCoCo reporta pero no exige el 85% (P-01) | Bug | Resuelto: jacoco:check en verify exige LINE/COVEREDRATIO >=0.85 por clase, sin exclusiones. Seleccion reducida: 7 tests verdes pero build pasa antes y falla por cobertura despues; suite completa 92 verdes con gate activo. [Diff](target/h08.diff), [antes](target/h08-low-before.log), [rechazo](target/h08-low-after.log), [despues](target/h08-after.log). | [pom.xml](pom.xml), [ci.yml:21](.github/workflows/ci.yml#L21) | media | Resuelto |
| H-07 | Clean code: resource con validacion, negocio, persistencia y respuesta | Refactor | Resuelto: separacion previa de H-02 verificada con dobles de servicio; rechazos no emiten token y salida usa DTO. Estados HTTP ahora nombrados; 40 pruebas antes y suite 92 verdes despues, resource/servicio 100% lineas. [Diff](target/h07.diff), [antes](target/h07-characterization-before.log), [despues](target/h07-after.log). | [AuthResource.java](src/main/java/com/bcp/workshop/auth/AuthResource.java), [AuthServiceTest.java](src/test/java/com/bcp/workshop/auth/AuthServiceTest.java) | baja | Resuelto |

## Mapa del flujo actual

1. **Configuracion e inicializacion.** El servicio usa puerto 8080 y host local;
   el perfil prod cambia el host a todas las interfaces. CORS usa una lista
   obligatoria por entorno y Jackson ignora propiedades desconocidas
   ([application.properties:1](src/main/resources/application.properties#L1)).
   `LegacyDatabase` obtiene `AUTH_DB_URL` del entorno, con SQLite local como
   valor por defecto
   ([LegacyDatabase.java:15](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L15)).
   Su inicializacion delega a PasswordMigration: crea/migra y valida hashes
   antes de aceptar logins; los errores abortan el arranque
   ([PasswordMigration.java](src/main/java/com/bcp/workshop/auth/PasswordMigration.java)).
2. **Entrada HTTP.** `POST /auth/login` consume y produce JSON
   ([AuthResource.java](src/main/java/com/bcp/workshop/auth/AuthResource.java)).
   El DTO tiene `username`, `password` y `client`
   ([LoginRequest.java:3](src/main/java/com/bcp/workshop/auth/LoginRequest.java#L3));
   `client` no se usa en el flujo.
3. **Validacion.** El resource rechaza con 400 un request nulo, un username
   nulo/vacio o un password nulo/vacio, en ese orden
   ([AuthResource.java](src/main/java/com/bcp/workshop/auth/AuthResource.java)).
   `trim()` solo detecta blancos: se envian los valores originales al repositorio.
4. **Persistencia y decision.** Se abre una conexion, se ejecuta el SELECT con
   parametros vinculados y se convierte el primer resultado en `UserRecord`
   ([LegacyDatabase.java:44](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L44),
   [UserRecord.java:3](src/main/java/com/bcp/workshop/auth/UserRecord.java#L3)).
   El record contiene id, username, displayName y role, no el password.
   El hash se devuelve solo al servicio y se verifica con PasswordHasher.
   Si no hay resultado, o si la consulta falla y su catch registra el error,
   el repositorio devuelve `null`
   ([LegacyDatabase.java:55](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L55)).
   El resource responde 401 para ese `null`
   ([AuthResource.java](src/main/java/com/bcp/workshop/auth/AuthResource.java)).
5. **Generacion del token.** Con un usuario, TokenService firma un JWT RS256
   con claims y TTL 3600; las claves no salen en el payload
   ([TokenService.java](src/main/java/com/bcp/workshop/auth/TokenService.java)).
   No se agrego endpoint verificador ni se presupone validacion externa.
6. **Respuesta y errores.** El exito devuelve 200 con `token`, `expiresIn`,
   `user` y `debug`; `displayName` pasa por `escapeHtml4`
   ([AuthService.java](src/main/java/com/bcp/workshop/auth/AuthService.java)).
   Las excepciones que alcanzan el catch del resource producen 500 con
   `message` y `exception` genericos; diagnostico saneado solo en servidor
   ([AuthResource.java](src/main/java/com/bcp/workshop/auth/AuthResource.java)).

## Contrato y alcance de la verificacion inicial (historico)

Esta seccion describe la revision inicial y H-01, no el contrato posterior.
H-03 sustituyo el token con aprobacion expresa; el estado vigente y los
resultados finales estan en [TESTING.md](docs/TESTING.md).

- [docs/ENDPOINTS.md](docs/ENDPOINTS.md) no existia y se genero desde el codigo
  actual, sin proponer cambios al contrato.
- La prueba existente caracteriza solo un login exitoso: HTTP 200, username
  esperado y token no nulo
  ([AuthResourceTest.java:13](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java#L13)).
  Esa fue la evidencia inicial. Para H-01 se ampliaron y ejecutaron las pruebas
  como se detalla a continuacion.
- En la revision inicial no se ejecutaron payloads. Para corregir H-01 se
  probaron entradas SQL manipuladas solo contra SQLite aislado de pruebas.
  No se ejecutaron escaneos externos ni componentes ajenos al ambito.
  No se atribuyen comportamientos a consumidores externos del token.
- Para H-01 se ejecutaron pruebas de caracterizacion antes del cambio de
  persistencia, segun [REGLAS.md](REGLAS.md). No se modifico el token.

## Evidencia de correccion H-01 — 2026-10-19

Seccion historica: los resultados siguientes corresponden al cierre de H-01,
antes de H-02/H-03/H-04. Para resultados actuales ver [TESTING.md](docs/TESTING.md).

Aprobacion limitada a H-01 registrada en [PLAN.md](PLAN.md). Las pruebas usan
JDK 21 (JAVA_HOME solo para cada comando), puerto HTTP dinamico y SQLite de
laboratorio bajo `target`; no se detuvieron procesos ni se tocaron datos normales.

| Verificacion | Antes del cambio productivo | Despues |
|---|---|---|
| Caracterizacion HTTP y repositorio: usuarios, roles, 200/400/401/500, mensajes, expiracion, espacios y errores controlados | 25 pruebas aprobadas | Las mismas 25 aprobadas |
| Correccion: cuatro entradas SQL manipuladas y credenciales con apostrofes | 5 fallos esperados (4 autenticaciones indebidas y 1 credencial literal rechazada) | 5 aprobadas |
| Suite completa con `verify` | No se exige verde antes: la correccion demuestra el defecto | 30 pruebas, 0 fallos, 0 errores, 0 omitidas; BUILD SUCCESS |
| JaCoCo de LegacyDatabase | No se usa la medicion previa para aprobar el cambio | 29/29 lineas (100%), 114/114 instrucciones (100%); ramas 5/8 (62,5%) |

El umbral >= 85% se verifica sobre lineas de la clase productiva modificada,
no sobre el promedio global. Se informa tambien la metrica de ramas, que no
alcanza 85%. La medicion final usa un archivo de ejecucion nuevo, sin mezclar
clases anteriores. CodeQL, Dependency Review y JaCoCo no se modificaron ni
deshabilitaron; no se ejecutaron los dos primeros localmente.

Comandos ejecutados, todos desde la raiz del backend:

```powershell
$env:JAVA_HOME = Split-Path (Split-Path (Get-Command java).Source)
$env:AUTH_DB_URL = 'jdbc:sqlite:./target/h01-before.db'
mvn -B "-Dtest=AuthResourceTest,LegacyDatabaseTest#characterizes*" "-Dquarkus.http.test-port=0" test
mvn -B "-Dtest=LegacyDatabaseTest#rejects*+matches*" test
$env:AUTH_DB_URL = 'jdbc:sqlite:./target/h01-final-http.db'
mvn -B "-Dquarkus.http.test-port=0" "-Djacoco.destFile=target/h01-verified.exec" "-Djacoco.dataFile=target/h01-verified.exec" verify
```

Los dos primeros comandos corresponden a la revision anterior al cambio
productivo; ejecutarlos ahora ya mide el codigo corregido.
Resultados: primero **BUILD SUCCESS**, segundo **BUILD FAILURE esperado**,
tercero **BUILD SUCCESS**.

- [Caracterizacion antes](target/h01-characterization-before.log).
- [Correccion antes](target/h01-correction-before.log).
- [Verificacion final](target/h01-verified.log).
- [Reporte de cobertura](target/site/jacoco/index.html).
- [Diff completo de H-01, pruebas y documentacion](target/h01.diff).

**Ajustes tecnicos:** se agrego un constructor de alcance de paquete para
dar a cada prueba un SQLite temporal independiente; el constructor publico
conserva `AUTH_DB_URL` y su valor por defecto. Esto evita exigir una variable
de entorno nueva al ejecutar las pruebas unitarias. El fixture carga el driver
SQLite explicitamente para no depender del orden de carga de Quarkus.
Una verificacion intermedia detecto esa dependencia del driver; corregido el
fixture, la suite final pasa. No se agregaron dependencias ni se modifico
[docs/ENDPOINTS.md](docs/ENDPOINTS.md).

En esa entrega H-02/H-03/H-04 no se implementaron: la aprobacion era solo H-01.
La separacion general del resource sigue pendiente en H-07, sin introducir
una refactorizacion no autorizada.
