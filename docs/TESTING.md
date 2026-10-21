# Pruebas del contrato HTTP

## HU-01: implementacion y evidencia

2026-10-21. Issue [#11](https://github.com/user-bcp-student33/workshop-auth-backend/issues/11),
OpenSpec `hu-01-signed-token-claims`, rama existente `feature/HU-1`.
La aprobacion humana exige `client` string con formato
`^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$`, sin trim, y 400 con exactamente
`{"message":"Invalid client"}` ante omision o valor invalido. Se conserva
RS256/SmallRye, sin `AUTH_TOKEN_SECRET`, nuevas dependencias ni cambios de gates.

### Casos y tareas verificadas

| tarea | evidencia actual |
|---|---|
| 2.1 | Aprobacion registrada en proposal/spec/design; OpenSpec estricto valido. |
| 3.1 | `AuthResourceTest`: omision, nulo, vacio, blancos, espacios, prefijos invalidos, puntuacion, no ASCII, salto de linea, 65 caracteres y tipos JSON no string producen solo `message: Invalid client`. No se llama a authenticate. Se preserva la precedencia cuerpo/username/password; client invalido precede a credenciales incorrectas. |
| 3.2 | Clientes de 1 y 64 caracteres y `Web_A-01` sobreviven sin normalizacion hasta el claim. Pruebas HTTP de ambos usuarios y `TokenServiceTest` preservan los otros claims; verificacion real RS256, payload alterado, clave ajena y limite exacto de expiracion. |
| 4.1 | `AuthServiceTest.responseLifetimeMatchesSignedExpirationAtIssuance`: reloj fijo con fraccion `.987654321`, firma verificada y respuesta real con expiresIn entero 3600. `now` es el instante de emision truncado a segundos, igual a iat; `exp - now = 3600`, dentro de tolerancia maxima de un segundo. No se usan esperas. |
| 4.2 | `AuthResourceTest.logsSuccessAndFailureWithoutTokenOrPrivateMaterial`: captura logs del paquete auth durante 200/401/400 y un 500 cuyo error incluye token emitido, PEM y clave privada Base64 en mensaje/causa/suprimidas. Solo aparece el diagnostico saneado, sin esos valores. `AuthFailureTest` mantiene ademas la cobertura HTTP 500. Claves efimeras de laboratorio. |
| 5.1 | Suite completa: 117 pruebas, 0 fallos, 0 errores, 0 omitidas. Incluye CORS, health y persistencia existentes sin cambios funcionales. JaCoCo check activo, 12 clases, todas con lineas >=85%. |

La prueba exitosa que omitia cliente se adapto intencionalmente a
`preservesSuccessfulResponseWithApprovedClient`; ahora la omision se protege
con `rejectsMissingClientOverHttp`. La caracterizacion previa se conserva
abajo como evidencia historica, no como contrato vigente.

### Comandos y resultados

Todos desde la raiz de este backend, JDK 21. Preparacion del proceso:

```powershell
$env:JAVA_HOME = Split-Path (Split-Path (Get-Command java).Source)
$env:AUTH_DB_URL = 'jdbc:sqlite:./target/hu01-client-red.db'
$env:QUARKUS_HTTP_TEST_PORT = '0'
```

| comando Maven ejecutado | resultado | log local regenerable |
|---|---|---|
| `mvn -B '-Dtest=AuthResourceTest#rejectsMissingClientOverHttp+rejectsInvalidClientBeforeCredentials+doesNotCallServiceForInvalidClient' -l target/hu01-client-red.log test` | BUILD FAILURE esperado: 15 pruebas, 15 fallos antes de implementar | [rojo client](../target/hu01-client-red.log) |
| `mvn -B '-Dtest=AuthResourceTest,AuthServiceTest,AuthFailureTest,TokenServiceTest' '-Djacoco.destFile=target/hu01-client-green.exec' '-Djacoco.append=false' -l target/hu01-client-green.log test` | BUILD SUCCESS: 58 pruebas, 0 fallos/errores/omitidas | [verde client](../target/hu01-client-green.log) |
| `mvn -B '-Dtest=AuthResourceTest#rejectsNonStringClient+preservesExactClientAtAcceptedBoundaries,AuthServiceTest#responseLifetimeMatchesSignedExpirationAtIssuance' -l target/hu01-boundaries.log test` | BUILD FAILURE esperado: 9 pruebas, 4 fallos por coercion o error distinto para tipos JSON no string | [rojo tipos](../target/hu01-boundaries.log) |
| `mvn -B '-Dtest=AuthResourceTest#rejectsNonStringClient+preservesExactClientAtAcceptedBoundaries,AuthServiceTest#responseLifetimeMatchesSignedExpirationAtIssuance' -l target/hu01-boundaries-green.log test` | BUILD SUCCESS tras corregir LoginRequest: 9 pruebas, 0 fallos/errores/omitidas | [verde tipos](../target/hu01-boundaries-green.log) |
| `mvn -B '-Dtest=AuthResourceTest#logsSuccessAndFailureWithoutTokenOrPrivateMaterial' -l target/hu01-logs.log test` | BUILD SUCCESS: 1 prueba, 0 fallos/errores/omitidas | [logs](../target/hu01-logs.log) |

Ejecucion final con otra base y datos de cobertura sin acumular ejecuciones previas:

```powershell
$env:AUTH_DB_URL = 'jdbc:sqlite:./target/hu01-verify.db'
$env:QUARKUS_HTTP_TEST_PORT = '0'
mvn -B '-Djacoco.destFile=target/hu01-verify.exec' '-Djacoco.dataFile=target/hu01-verify.exec' '-Djacoco.append=false' -l target/hu01-verify.log verify
```

**BUILD SUCCESS: 117 pruebas aprobadas**, 0 fallos/errores/omitidas.
[Log completo](../target/hu01-verify.log),
[CSV JaCoCo](../target/site/jacoco/jacoco.csv),
[reporte HTML](../target/site/jacoco/index.html).
Minimo por clase: TokenService 45/49 lineas (91,84%);
AuthResource 27/27, AuthService 19/19 y LoginRequest 3/3 (100%).
El gate sigue siendo LINE/COVEREDRATIO >=0.85 por CLASS, sin exclusiones.
Estos artefactos bajo target se regeneran; los conteos quedan registrados aqui.

### Definition of Done y riesgos

- CUMPLIDO: contratos locales y cambio aprobado de client, pruebas reproducibles,
  cobertura critica >=85%, RSA/configuracion sin cambios y logs saneados.
- CUMPLIDO: evidencia para revision en este documento, contrato en ENDPOINTS y
  decisiones en OpenSpec. No se crearon commits, ramas ni PR automaticamente.
- PENDIENTE DE OTRO GATE: CI, CodeQL, SCA y revision/aprobacion del PR remotos.
  El verify local no certifica esos controles ni se reutiliza SCA historico.
- NO APLICA AL ALCANCE: endpoints de otros componentes, HMAC/secreto compartido,
  cambios de base, health o consumidores; H-09/#12 y H-10/#13 siguen separados.
- FALTA EVIDENCIA: compatibilidad operativa en consumidores externos; no se
  leyeron ni modificaron otros repositorios. Los que omitan client tendran 400.

Reversion: desplegar la version previa si la exigencia de client interrumpe
consumidores no migrados; no hay migracion de base ni rotacion de claves.
Conservar par RSA, issuer y audience; no sustituir por HMAC ni debilitar firmas.

## HU-01: caracterizacion previa (tareas 1.1 y 1.2)

2026-10-21. Issue [#11](https://github.com/user-bcp-student33/workshop-auth-backend/issues/11),
cambio `hu-01-signed-token-claims`, rama existente `feature/HU-1`.
Solo se ampliaron pruebas existentes; no se modificaron clases productivas,
contratos HTTP, dependencias, configuracion ni gates. No se crearon pruebas
duplicadas del Dia 1 ni se implemento la validacion o propagacion de `client`.

| tarea | prueba reutilizada | comportamiento protegido |
|---|---|---|
| 1.1 | `AuthResourceTest.characterizesSuccessfulResponseWithoutClient` (dos usuarios) | Login sin `client` sigue devolviendo 200, los cuatro campos publicos, identidad/rol y `expiresIn` entero 3600; se ampliaron las aserciones de issuer, audience, `iat` dentro del intervalo HTTP y `exp - iat = 3600`. |
| 1.1 | Resto de `AuthResourceTest`, sin duplicarlo | Login con cliente, credenciales exactas, 400/401, errores 500 por llamada directa, JSON nulo/malformado, tipo incompatible, 405/406/415 y escape HTML mantienen sus expectativas. |
| 1.1 | `AuthFailureTest`, sin modificarlo | Respuestas 500 genericas por HTTP y diagnostico saneado ante fallos controlados. Su ejecucion no completa la tarea 4.2 de logs de tokens y claves. |
| 1.2 | `TokenServiceTest.emitsSignedCompactToken` | Firma valida con clave efimera y algoritmo permitido RS256; se agregaron aserciones explicitas de header RS256, issuer, audience y duracion literal de 3600 segundos, manteniendo `sub`, `role`, `iat`, `exp` y ausencia de secreto/password. |
| 1.2 | Resto de `TokenServiceTest`, sin duplicarlo | Conserva rechazo de payload alterado, clave ajena y expiracion en el limite, y validacion de archivos PEM. Reloj fijo y claves de laboratorio, sin esperas reales. |

Comando ejecutado desde la raiz de `workshop-auth-backend`, con JDK 21,
SQLite de laboratorio bajo `target` y puerto HTTP dinamico:

```powershell
$env:JAVA_HOME = Split-Path (Split-Path (Get-Command java).Source)
$env:AUTH_DB_URL = 'jdbc:sqlite:./target/hu01-characterization.db'
$env:QUARKUS_HTTP_TEST_PORT = '0'
mvn -B '-Dtest=AuthResourceTest,AuthFailureTest,TokenServiceTest' '-Djacoco.destFile=target/hu01-characterization.exec' '-Djacoco.append=false' test
```

Resultado comprobado en los informes Surefire: **35 pruebas aprobadas**:
`AuthResourceTest` 28, `AuthFailureTest` 4 y `TokenServiceTest` 3;
**0 fallos, 0 errores, 0 omitidas**. Informes regenerables en
`target/surefire-reports/TEST-com.bcp.workshop.auth.<Clase>Test.xml`.
El fixture existente genera y elimina las claves RSA de prueba.

Las aserciones verifican el estado actual antes del cambio funcional, no la
implementacion futura. El issue describe HMAC, pero proposal/design ya
reconocen esa discrepancia y la caracterizacion conserva RS256. No se
encontro una contradiccion nueva demostrada por las pruebas.
Solo se completan 1.1 y 1.2: 2.1, 3.1, 3.2, 4.1, 4.2 y 5.1 siguen pendientes.
No se ejecutaron `verify`, la suite completa, CI, CodeQL o SCA en esta etapa;
el archivo JaCoCo aislado no certifica por si solo el gate de cobertura.

## Ejecucion aprobada H-05, H-06, H-07 y P-01

Directorio de todos los comandos: raiz de este backend. JDK 21; por proceso:
`JAVA_HOME` obtenido del ejecutable Java 21, `QUARKUS_HTTP_TEST_PORT=0` y
`AUTH_DB_URL=jdbc:sqlite:./target/<item>-<etapa>.db`.
No se usaron secretos reales ni bases normales. Los diffs son comparaciones
locales incrementales de snapshots antes/despues, incluyendo pruebas y la
fila de seguimiento, sin commits ni cambios al index existente.

**Resultado final:** 92 pruebas, 0 fallos, 0 errores, 0 omitidas; BUILD SUCCESS
con JaCoCo check activo. Repeticion con ruta de datos por defecto:
2026-10-19 18:12:58 -05:00.
Cobertura de lineas >=85% por cada clase productiva; minimo 91,67% (TokenService).
AuthResource 24/24 (100%), AuthService 19/19 (100%); ramas reportadas por separado.
CSV/XML/HTML vigentes en [reporte final](../target/site/jacoco/index.html).

### H-05: errores 500 y logs

- Caracterizacion antes: `mvn -B "-Dtest=AuthResourceTest,AuthFailureTest" test`,
  salida 0, 27 pruebas verdes; [log](../target/h05-characterization-before.log).
- Correccion antes: `mvn -B "-Dtest=AuthFailureTest" test`, salida 1,
  4 fallos esperados: cuerpo con mensaje/clase internos y diagnostico ausente;
  [log](../target/h05-correction-before.log).
- Despues: `mvn -B "-Djacoco.destFile=target/h05-after.exec"
  "-Djacoco.dataFile=target/h05-after.exec" verify`, salida 0, **82 verdes**,
  0 fallos/errores/omitidas; 2026-10-19 17:53:14 -05:00, 1 min 7 s.
  Mismos escenarios con/sin mensaje y con marcador sensible pasan; captura
  de logs comprueba clase/frames y ausencia de mensaje, causas y suprimidas.
  Solo las expectativas 500 cambian intencionalmente; el resto de la
  caracterizacion permanece verde.
- AuthResource: 24/24 lineas, 14/14 ramas. Formula de lineas:
  `100 * LINE_COVERED / (LINE_COVERED + LINE_MISSED)`.
- [Diff propio](../target/h05.diff), [log final](../target/h05-after.log),
  [cobertura](../target/h05-coverage/index.html).
  Tests: [AuthFailureTest.java](../src/test/java/com/bcp/workshop/auth/AuthFailureTest.java)
  y [AuthResourceTest.java](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java).

### H-06: dependencias y compatibilidad

- Antes: `mvn -B dependency:tree "-DoutputFile=target/h06-dependencies-before.txt"`,
  salida 0; [arbol](../target/h06-dependencies-before.txt).
  Tras escribir pruebas adicionales HTML4 (comillas, ampersand, acentos, euro,
  emoji y placeholder literal), `mvn -B "-Djacoco.destFile=target/h06-before.exec"
  "-Djacoco.dataFile=target/h06-before.exec" verify`: salida 0, **89 verdes**.
  [Caracterizacion antes](../target/h06-characterization-before.log).
- Correccion SCA antes: `& .\target\h06-check-sca.ps1 -DependencyTree
  target\h06-dependencies-before.txt -OutputPrefix target\h06-sca-red`,
  salida 1: 230 coordenadas, 11 paquetes coincidentes con 49 avisos.
  [Script reproducible](../target/h06-check-sca.ps1), [log](../target/h06-sca-red.log),
  [JSON](../target/h06-sca-red.json). No se ejecutaron exploits.
- Cambio: Quarkus BOM/plugin 3.27.4 -> **3.40.1.1**, Commons Text 1.9 ->
  **1.15.0** por BOM; Netty 4.1.133.Final -> **4.1.139.Final**, Jackson
  2.21.2 -> **2.21.7**. Sin nuevos artefactos directos ni overrides transitivos.
- Despues: `mvn -B "-Djacoco.destFile=target/h06-after.exec"
  "-Djacoco.dataFile=target/h06-after.exec" verify`: salida 0, **89 verdes**,
  0 fallos/errores/omitidas; 2026-10-19 18:01:23 -05:00, 2 min 44 s.
  [Log](../target/h06-after.log). No se adaptaron errores HTTP al nuevo runtime:
  la matriz existente, firma/expiracion, CORS y health pasa sin alterar el contrato.
- Repeticion: `mvn -B dependency:tree
  "-DoutputFile=target/h06-dependencies-after.txt"` (salida 0), luego
  `& .\target\h06-check-sca.ps1 -DependencyTree target\h06-dependencies-after.txt
  -OutputPrefix target\h06-sca-green` (salida 0): **0 coincidencias en 236
  coordenadas**, incluidos scopes compile/runtime/test.
  [Arbol](../target/h06-dependencies-after.txt), [log SCA](../target/h06-sca-green.log),
  [JSON](../target/h06-sca-green.json), [diff propio](../target/h06.diff).
- Fuentes: [Apache Commons Text, CVE-2022-42889, corregido desde 1.10.0](https://commons.apache.org/proper/commons-text/security.html)
  exige interpolacion no saneada mediante `StringSubstitutor`: codigo propio
  solo usa `escapeHtml4`; no se demuestra esa ruta vulnerable.
  [Quarkus CVE-2026-50559](https://github.com/quarkusio/quarkus/security/advisories/GHSA-qcxp-gm7m-4j5v)
  afecta politicas de autenticacion por paths; no estan configuradas aqui;
  version original afectada y correccion 3.27.4.1 o streams posteriores.
  [BOM seleccionado y versiones coordinadas](https://repo.maven.apache.org/maven2/io/quarkus/platform/quarkus-bom/3.40.1.1/quarkus-bom-3.40.1.1.pom).
  Los 49 IDs, CVEs, paquetes y referencias oficiales individuales quedan en
  [evaluacion por aviso](../target/h06-advisory-assessment.json) y
  [advisories completos](../target/h06-advisories).
  Para transitivas se confirma presencia/correccion de version, no explotabilidad
  en el flujo de login ni ausencia de vulnerabilidades futuras.
- Cada clase productiva >=85% de lineas, comprobado en CSV JaCoCo;
  [reporte H-06](../target/h06-coverage/index.html).
  CodeQL/Dependency Review no ejecutados localmente; configuraciones intactas.

### H-07: cierre de capas y estados HTTP

- Antes de produccion: se agregaron pruebas de servicio con dobles: usuario
  inexistente no verifica password ni emite token; password incorrecto no
  emite token; exito verifica primero, emite despues y devuelve solo DTO publico.
  `mvn -B "-Dtest=AuthServiceTest,AuthResourceTest,AuthFailureTest" test`:
  salida 0, **40 verdes**; [log](../target/h07-characterization-before.log).
- Despues de reemplazar literales HTTP por `Response.Status`:
  `mvn -B "-Djacoco.destFile=target/h07-after.exec"
  "-Djacoco.dataFile=target/h07-after.exec" verify`: salida 0, **92 verdes**,
  0 fallos/errores/omitidas; 2026-10-19 18:06:26 -05:00, 1 min 3 s.
  [Log](../target/h07-after.log), [diff propio](../target/h07.diff).
- Las mismas pruebas de correccion pasan antes/despues: es refactor y cierre
  de separacion ya efectuada en H-02, no se inventa un fallo previo.
  Resource conserva solo HTTP/validacion; servicio coordina repositorio,
  hasher, token y DTO; no se incorporo SQL o emision criptografica al resource.
- Resource: 24/24 lineas y 14/14 ramas; servicio: 19/19 lineas y 4/4 ramas.
  Cada clase productiva >=85% de lineas. H-07 no cambia textos/campos/status
  del contrato vigente tras H-05 ni modifica persistencia o token.
  [Pruebas de servicio](../src/test/java/com/bcp/workshop/auth/AuthServiceTest.java).

### H-08 (P-01): enforcement del 85% por clase

Se registro H-08 como Pendiente antes de ejecutar este item, y se cerro solo
tras comprobar rechazo y suite verde. No se agrego alcance ajeno al P-01 aprobado.

| etapa | comando ejecutado desde la raiz | salida | resultado real |
|---|---|---:|---|
| Antes, seleccion reducida | `mvn -B "-Dtest=PasswordHasherTest" "-Djacoco.destFile=target/h08-low-before.exec" "-Djacoco.dataFile=target/h08-low-before.exec" verify` | 0 | 7 pruebas verdes; AuthResource 0/24 lineas, pero el build no rechazaba |
| Despues, misma seleccion | `mvn -B "-Dtest=PasswordHasherTest" "-Djacoco.destFile=target/h08-low-after.exec" "-Djacoco.dataFile=target/h08-low-after.exec" verify` | 1 | 7 pruebas verdes; build rechazado por `Coverage checks have not been met`, minimo 0.85 por clase |
| Despues, suite completa | `mvn -B "-Djacoco.destFile=target/h08-after.exec" "-Djacoco.dataFile=target/h08-after.exec" verify` | 0 | 92 pruebas verdes; `All coverage checks have been met`, 1 min 32 s |

Cada medicion uso un archivo exec nuevo, sin mezclar la cobertura parcial con
la completa. La suite completa previa al cambio fue H-07, tambien 92 verdes.
El fallo negativo es esperado y no un fallo de tests: verifica que el gate
ahora se ejecuta realmente. No se falseo cobertura ni se excluyeron clases.

- [Antes](../target/h08-low-before.log), [CSV antes](../target/h08-low-before.csv).
- [Rechazo posterior](../target/h08-low-after.log), [CSV posterior](../target/h08-low-after.csv).
- [Suite final](../target/h08-after.log), [diff propio](../target/h08.diff).
- Se agrego una propiedad de ruta de datos con default normal JaCoCo para
  que `check` lea el mismo archivo indicado por `-Djacoco.dataFile` en la
  medicion aislada; sin flags usa `target/jacoco.exec`.
- La regla CLASS/LINE/COVEREDRATIO >=0.85 se aplica a todas las clases, no solo
  al promedio global. PasswordHasher 31/33 (93,94%), TokenService 44/48
  (91,67%), restantes clases 100%. No se afirma >=85% de ramas:
  LegacyDatabase conserva 5/8. CodeQL y Dependency Review sin cambios;
  no se ejecutaron esos servicios localmente.
- Verificacion adicional de defaults: `mvn -B "-Djacoco.append=false" verify`,
  salida 0, 92 verdes, 1 min 22 s; reporte y check leen `target/jacoco.exec`.
  `append=false` evita mezclar datos viejos sin cambiar los defaults del POM.
  [Log](../target/h08-default-path.log).
- Empaquetado con el BOM nuevo: `mvn -B quarkus:build`, salida 0,
  BUILD SUCCESS, 2026-10-19 18:13:21 -05:00; [log](../target/h06-package.log).
  Emitio avisos no bloqueantes: `quarkus-junit5` se reubica automaticamente a
  `quarkus-junit`, y el goal explicito avisa sobre extensiones Maven pese a
  que el POM ya tiene `extensions=true`. No se ocultaron los avisos ni se
  modificaron workflows/configuracion para silenciarlos.

Documentos consultados para esta ejecucion: [PLAN.md](../PLAN.md),
[HALLAZGOS.md](../HALLAZGOS.md), [ENDPOINTS.md](ENDPOINTS.md),
[GOOD_PRACTICES.md](GOOD_PRACTICES.md), [REGLAS.md](../REGLAS.md),
[README.md](../README.md) y [RETO.md](../RETO.md).
Las fuentes externas SCA se enumeran en H-06. No se afirma validacion de
consumidores externos. La conversion SQL->401 sigue intacta por contrato;
no se implementaron los cambios dejados como preguntas abiertas.

## Resultado historico — implementacion H-02, H-03 y H-04

Finalizacion: **2026-10-19 17:43:24 -05:00**. **80 pruebas aprobadas, 0 fallos,
0 errores, 0 omitidas; BUILD SUCCESS**. Cambios realizados secuencialmente:
antes de iniciar el siguiente item se obtuvo suite verde y se actualizo su fila
en [HALLAZGOS.md](../HALLAZGOS.md). Aprobacion y decisiones en [PLAN.md](../PLAN.md).

El historial que sigue mas abajo corresponde a la entrega anterior de solo
pruebas. Sus ejemplos Base64, conteos y referencias de codigo son historicos,
no el estado actual. El contrato vigente esta en [ENDPOINTS.md](ENDPOINTS.md).

### Evidencia independiente por item

| item | caracterizacion antes | correccion antes | despues / cierre | diff incremental |
|---|---|---|---|---|
| H-02 | 42 casos HTTP/repositorio verdes | `storesOnlyVersionedHashes`: 1 fallo esperado por texto plano | 57 verdes; mismo escenario pasa, mas salts, migracion repetida, rollback y hash invalido | [h02.diff](../target/h02.diff) |
| H-03 | 27 casos HTTP verdes con token legacy | `emitsSignedCompactToken`: 1 fallo esperado, no tenia tres segmentos | 60 verdes; mismo escenario pasa, firma/alteracion/clave ajena/limite exp y claves PEM cubiertos | [h03.diff](../target/h03.diff) |
| H-04 | 29 casos HTTP/preflight permitido verdes | 2 fallos esperados: origen ajeno autorizado e issuer vacio aceptado | 80 verdes; mismas regresiones pasan; CORS invalido, issuer/audience y arranque cubiertos | [h04.diff](../target/h04.diff) |

Los diffs de cada item comparan snapshots de fuentes Java/pruebas (y POM/
properties cuando corresponden) tomados antes y despues del item, no un
`git diff main` acumulativo. El diff documental final esta en
[h02-h04-documentation.diff](../target/h02-h04-documentation.diff).
No se crearon commits ni se alteraron cambios previos ajenos.

### Comandos ejecutados y codigos de salida

Directorio: raiz `workshop-auth-backend`, JDK Adoptium 21.0.12.1, Maven 3.10.0.
Entorno de cada corrida, sin cambiar variables globales:

```powershell
$env:JAVA_HOME = Split-Path (Split-Path (Get-Command java).Source)
$env:QUARKUS_HTTP_TEST_PORT = '0'
# AUTH_DB_URL se indico por item, siempre bajo target.
```

| item/etapa | AUTH_DB_URL (prefijo jdbc:sqlite:./target/) | comando Maven | salida | resultado |
|---|---|---|---:|---|
| H-02 caracterizacion antes | h02-before.db | `mvn -B "-Dtest=AuthResourceTest,AuthFailureTest,LegacyDatabaseTest" test` | 0 | 42/42 |
| H-02 correccion antes | h02-before.db | `mvn -B "-Dtest=PasswordMigrationTest" test` | 1 | 1 fallo esperado |
| H-02 despues | h02-after.db | `mvn -B "-Djacoco.destFile=target/h02-verified.exec" "-Djacoco.dataFile=target/h02-verified.exec" verify` | 0 | 57/57 |
| H-03 caracterizacion antes | h03-before.db | `mvn -B "-Dtest=AuthResourceTest,AuthFailureTest" test` | 0 | 27/27 |
| H-03 correccion antes | h03-before.db | `mvn -B "-Dtest=TokenServiceTest" test` | 1 | 1 fallo esperado |
| H-03 despues | h03-after.db | `mvn -B "-Djacoco.destFile=target/h03-verified.exec" "-Djacoco.dataFile=target/h03-verified.exec" verify` | 0 | 60/60 |
| H-04 caracterizacion antes | h04-before.db | `mvn -B "-Dtest=AuthResourceTest,AuthFailureTest,CorsTest#permits*" test` | 0 | 29/29 |
| H-04 correccion antes | h04-before.db | `mvn -B "-Dtest=TokenConfigurationTest,CorsTest#doesNot*" test` | 1 | 2 fallos esperados |
| H-04 despues | h04-after.db | `mvn -B "-Djacoco.destFile=target/h04-after.exec" "-Djacoco.dataFile=target/h04-after.exec" verify` | 0 | 80/80 |
| Entrega final | h02-h04-release.db | `mvn -B "-Djacoco.destFile=target/h02-h04-release.exec" "-Djacoco.dataFile=target/h02-h04-release.exec" verify` | 0 | 80/80; 1 min 30 s |

Antes de cada comando de la tabla se asigno la URL completa al entorno, por
ejemplo `$env:AUTH_DB_URL = 'jdbc:sqlite:./target/h04-after.db'`.
No se puede reproducir el rojo historico contra el codigo ya corregido:
el estado previo se conserva en los diffs y logs.

- H-02: [caracterizacion antes](../target/h02-characterization-before.log),
  [correccion antes](../target/h02-correction-before.log),
  [despues](../target/h02-after.log), [cobertura](../target/h02-coverage/index.html).
- H-03: [caracterizacion antes](../target/h03-characterization-before.log),
  [correccion antes](../target/h03-correction-before.log),
  [despues](../target/h03-verified.log), [cobertura](../target/h03-coverage/index.html).
- H-04: [caracterizacion antes](../target/h04-characterization-before.log),
  [correccion antes](../target/h04-correction-before.log),
  [despues](../target/h04-after.log), [cobertura](../target/h04-coverage/index.html).
- Entrega final: [log completo](../target/h02-h04-release.log).
  Se repitio toda la suite tras limitar los directorios temporales JUnit a
  `target` y nombrar las constantes de validacion RSA/puertos. No cambio el
  comportamiento ni se tocaron gates.

Una corrida intermedia de H-02 fallo por estructura incorrecta de dos metodos
de test y fue corregida antes del cierre. En H-03 una corrida verde no alcanzaba
la cobertura por clase del cargador PEM; se agregaron pruebas de errores de
claves y se midio con otro archivo nuevo antes de cerrar el item. No se
cambiaron aserciones para ocultar un defecto ni se redujeron gates.

### Cobertura final explicita

JaCoCo 0.8.12, archivo nuevo `h02-h04-release.exec`; formula por clase:
`100 * LINE_COVERED / (LINE_COVERED + LINE_MISSED)` en el CSV/XML generado por
`verify`. Se comprobo por comando que **cada clase productiva supera 85% de
lineas**. El promedio global no sustituye este control.

| clase modificada/nueva | lineas finales | ramas finales |
|---|---:|---:|
| AuthResource | 20/20 (100%) | 16/16 |
| AuthService | 19/19 (100%) | 4/4 |
| LegacyDatabase | 28/28 (100%) | 5/8 |
| PasswordHasher | 31/33 (93,94%) | 12/14 |
| PasswordMigration | 58/58 (100%) | 10/10 |
| TokenService | 44/48 (91,67%) | 10/12 |
| CorsConfiguration | 20/20 (100%) | 25/26 |
| LoginResponse / LoginResponse.User | 1/1 cada una (100%) | Sin ramas |

En el cierre de H-02 PasswordHasher tenia 31/33 lineas y el resto de clases
modificadas 100%; al cerrar H-03 TokenService tenia 40/44 (90,91%). Se reportan
ramas sin afirmar un 85% donde no se cumple. Reporte final:
[HTML](../target/site/jacoco/index.html), [XML](../target/site/jacoco/jacoco.xml).

### Pruebas actuales y limites

- [PasswordHasherTest](../src/test/java/com/bcp/workshop/auth/PasswordHasherTest.java):
  salts independientes, verificacion exacta, formato invalido y medicion
  local de costo (153,25 ms por hash en el cierre de H-02; no es un SLA).
- [PasswordMigrationTest](../src/test/java/com/bcp/workshop/auth/PasswordMigrationTest.java):
  nuevas bases, migracion legacy, identidades, versionado, segundo arranque,
  rollback y rechazo de esquema/hash corrupto. Solo fixtures, sin base normal.
- [AuthServiceTest](../src/test/java/com/bcp/workshop/auth/AuthServiceTest.java):
  error explicito para hash invalido y representacion de credenciales redactada.
- [TokenServiceTest](../src/test/java/com/bcp/workshop/auth/TokenServiceTest.java):
  claims/firma RS256, payload alterado, clave incorrecta, exp-1 aceptado y
  exp rechazado con reloj fijo; PEM valido/invalido, claves debiles/no coincidentes.
- [TokenConfigurationTest](../src/test/java/com/bcp/workshop/auth/TokenConfigurationTest.java),
  [CorsConfigurationTest](../src/test/java/com/bcp/workshop/auth/CorsConfigurationTest.java)
  y [CorsTest](../src/test/java/com/bcp/workshop/auth/CorsTest.java):
  configuracion requerida y permiso CORS permitido/denegado. No se exige un
  status no documentado para origen ajeno.
- Se conservan health UP/DOWN, errores HTTP, DTO nulo, escape HTML y respuesta
  completa; solo cambia la expectativa de token autorizada. Los fixtures de
  fallo se adaptaron a la nueva capa Service, no a una semantica distinta.

Las claves de pruebas se generan por corrida y se eliminan. No existe una
clave privada ni origen productivo predeterminado en el repositorio.
No se afirma que consumidores externos verifiquen JWT: eso requiere su
coordinacion. H-05 y H-06 permanecen pendientes; la exposicion de clase/mensaje
en 500 se caracteriza, no se declara corregida.

### Arranque real del artefacto H-04

`mvn -B quarkus:build`: salida 0, BUILD SUCCESS
([log](../target/h04-package.log)).
Se lanzo `java -Dquarkus.http.port=0 -jar target\quarkus-app\quarkus-run.jar`
en procesos acotados con configuracion ficticia:

- Ausencia individual de cada variable `AUTH_TOKEN_ISSUER`,
  `AUTH_TOKEN_AUDIENCE`, `AUTH_TOKEN_PRIVATE_KEY`, `AUTH_TOKEN_PUBLIC_KEY` y
  `AUTH_CORS_ORIGINS`: **codigo 1 en los cinco casos**, error de configuracion.
  [Resultado](../target/h04-startup-results.json).
- Configuracion completa: servidor responsive, health 200, login 200,
  token de tres segmentos con header RS256, TTL 3600, origen permitido
  autorizado y otro sin permiso CORS.
  [Resultado](../target/h04-runtime-results.json).
- Se detuvo el servidor y se eliminaron sus dos PEM. No se detuvieron procesos
  ajenos ni se accedio a otros componentes.

## Historial anterior — entrega de pruebas previa a H-02/H-03/H-04

Ejecucion final: **2026-10-19 16:52:46 -05:00**.
Referencia: [ENDPOINTS.md](ENDPOINTS.md). Solo se ampliaron pruebas y se creo
este documento; no se cambio codigo productivo, contrato, dependencias ni gates.

## Matriz de casos ejecutados

| endpoint | caso | tipo | resultado esperado |
|---|---|---|---|
| POST /auth/login | Credenciales locales completas con `client` | Camino feliz | 200, username esperado y token no nulo |
| POST /auth/login | Ambos usuarios/roles, sin `client`, propiedad desconocida | Camino feliz / borde | 200; cuatro campos de salida, datos de usuario y TTL 3600; token legacy con timestamp consistente |
| POST /auth/login | Username ausente, nulo, vacio o solo blancos | Borde / error | 400, `{"message":"Username is required"}` |
| POST /auth/login | Password ausente, nulo, vacio o solo blancos | Borde / error | 400, `{"message":"Password is required"}` |
| POST /auth/login | Cuerpo JSON `null` por HTTP; DTO nulo por llamada directa | Error | 400, `{"message":"Request is null"}` |
| POST /auth/login | Password incorrecto, usuario inexistente, espacios significativos | Error / borde | 401, mensaje exacto con username recibido |
| POST /auth/login | Nombre con caracteres HTML y rol distinto a los roles iniciales, fixture directo | Borde | 200; escape HTML4 y mapeo sin id/password; no restringir roles a un enum inexistente |
| POST /auth/login | JSON malformado `{` | Error | 400, cuerpo vacio |
| POST /auth/login | `{"username":{},"password":"x"}` | Error | 400, cinco campos del mapper y posicion 1:13, como el ejemplo documentado |
| POST /auth/login | GET sobre la ruta de login | Error | 405, cuerpo vacio |
| POST /auth/login | `Accept: text/plain` con entrada JSON | Error | 406, cuerpo vacio |
| POST /auth/login | `Content-Type: text/plain` | Error | 415, cuerpo vacio |
| POST /auth/login | Excepcion controlada con mensaje, por HTTP y llamada directa | Error / caracterizacion legacy | 500, `message=isolated failure`, `exception=java.lang.IllegalStateException` |
| POST /auth/login | Excepcion controlada con mensaje nulo, por HTTP y llamada directa | Error / borde | 500, `message=Unknown error`, misma clase de excepcion |
| GET /q/health | GET sin autenticacion, Accept JSON | Camino feliz | 200, `{"status":"UP","checks":[]}` y Content-Type JSON |
| GET /q/health | Dos GET consecutivos sin cuerpo ni Accept explicito | Borde | Ambos 200 con `checks` vacio; no inventar campos de entrada para un GET |
| GET /q/health | Check DOWN habilitado solo en perfil de pruebas | Error condicional | 503; se verifica el estado, sin fijar un cuerpo no definido en el contrato |

Todos los errores con estado/caso concreto de [ENDPOINTS.md](ENDPOINTS.md)
tienen una prueba. Las preguntas abiertas del contrato no se convierten en
comportamientos asumidos.

Tambien se ejecutaron las 15 pruebas existentes del repositorio SQLite:
usuarios/roles, rechazo de credenciales, espacios, inicializacion repetida,
fallos controlados, parametros SQL y apostrofes literales. No son endpoints
adicionales.

## Codigo de las pruebas

El codigo completo, ejecutado y versionable esta en estos archivos; las
referencias evitan mantener una segunda copia divergente de la suite:

| archivo | contenido |
|---|---|
| [AuthResourceTest.java](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java) | Login feliz, borde, validaciones, errores Quarkus, token, escape y llamadas directas |
| [AuthFailureTest.java](../src/test/java/com/bcp/workshop/auth/AuthFailureTest.java) | 500 por HTTP con repositorio alternativo habilitado solo en su perfil |
| [HealthResourceTest.java](../src/test/java/com/bcp/workshop/auth/HealthResourceTest.java) | Salud UP sin autenticacion y requests repetidos sin cuerpo |
| [HealthDownTest.java](../src/test/java/com/bcp/workshop/auth/HealthDownTest.java) | Salud DOWN/503 mediante check alternativo de prueba |
| [LegacyDatabaseTest.java](../src/test/java/com/bcp/workshop/auth/LegacyDatabaseTest.java) | Caracterizacion y regresion SQL contra SQLite temporal |

### Login: errores HTTP añadidos

Extractos literales de [AuthResourceTest.java](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java):

```java
@Test
void rejectsJsonNullOverHttp() {
    given()
            .contentType("application/json")
            .body("null")
            .when().post("/auth/login")
            .then()
            .statusCode(400)
            .body("$", aMapWithSize(1))
            .body("message", is("Request is null"));
}

@Test
void rejectsMalformedJson() {
    given()
            .contentType("application/json")
            .body("{")
            .when().post("/auth/login")
            .then()
            .statusCode(400)
            .body(emptyString());
}

@Test
void rejectsIncompatibleFieldType() {
    given()
            .contentType("application/json")
            .body("{\"username\":{},\"password\":\"x\"}")
            .when().post("/auth/login")
            .then()
            .statusCode(400)
            .body("$", aMapWithSize(5))
            .body("objectName", is("LoginRequest"))
            .body("attributeName", is("username"))
            .body("line", is(1))
            .body("column", is(13))
            .body("value", nullValue());
}
```

### Login: 500 HTTP, no solo invocacion directa

Extractos de [AuthFailureTest.java](../src/test/java/com/bcp/workshop/auth/AuthFailureTest.java):

```java
@ParameterizedTest
@CsvSource({
        "with-message, isolated failure",
        "without-message, Unknown error"
})
void characterizesInternalErrorOverHttp(String username, String expectedMessage) {
    given()
            .contentType("application/json")
            .body(Map.of("username", username, "password", "test-only"))
            .when().post("/auth/login")
            .then()
            .statusCode(500)
            .contentType("application/json")
            .body("$", aMapWithSize(2))
            .body("message", is(expectedMessage))
            .body("exception", is(IllegalStateException.class.getName()));
}

@Alternative
@ApplicationScoped
public static class FailingDatabase extends LegacyDatabase {
    @Override
    public UserRecord findByCredentials(String username, String password) {
        String message = "with-message".equals(username) ? "isolated failure" : null;
        throw new IllegalStateException(message);
    }
}
```

`@TestProfile(FailureProfile.class)` y `getEnabledAlternatives()` activan ese
repositorio solo en los dos casos de error. Los otros tests usan el repositorio
real. No se requiere Mockito ni una nueva dependencia.

### Salud: camino feliz y borde

Metodos de [HealthResourceTest.java](../src/test/java/com/bcp/workshop/auth/HealthResourceTest.java),
anotado con `@QuarkusTest`:

```java
@Test
void reportsHealthWithoutAuthentication() {
    given()
            .accept("application/json")
            .when().get("/q/health")
            .then()
            .statusCode(200)
            .contentType("application/json")
            .body("$", aMapWithSize(2))
            .body("status", is("UP"))
            .body("checks", empty());
}

@Test
void acceptsRepeatedRequestsWithoutBodyOrAcceptHeader() {
    for (int attempt = 0; attempt < 2; attempt++) {
        given()
                .when().get("/q/health")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("$", aMapWithSize(2))
                .body("status", is("UP"))
                .body("checks", empty());
    }
}
```

### Salud: condicion de error documentada

Extractos de [HealthDownTest.java](../src/test/java/com/bcp/workshop/auth/HealthDownTest.java):

```java
@Test
void returnsServiceUnavailableWhenHealthIsDown() {
    given()
            .accept("application/json")
            .when().get("/q/health")
            .then()
            .statusCode(503);
}

public static class DownProfile implements QuarkusTestProfile {
    @Override
    public Set<Class<?>> getEnabledAlternatives() {
        return Set.of(UnavailableHealthCheck.class);
    }
}

@Alternative
@ApplicationScoped
@Readiness
public static class UnavailableHealthCheck implements HealthCheck {
    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.down("test-only-unavailable");
    }
}
```

La clase de test usa `@TestProfile(DownProfile.class)`. Esta comprobacion existe
solo en `src/test`: no agrega un check productivo ni cambia la ruta. Permite
ejercitar la regla DOWN -> 503 de la extension, sin afirmar que la configuracion
normal tenga una causa de DOWN ni inventar el cuerpo de error contractual.

## Directorio, entorno y comandos realmente ejecutados

Directorio de trabajo de todos los comandos:
`C:\Users\Usuario\Documents\T09930-gerson-odar\reqflow-workspace\workshop-auth-backend`.
JDK Eclipse Adoptium **21.0.12.1**, Maven **3.10.0**, Windows.
Variables solo del proceso; ningun cambio persistente de configuracion.

### 1. Primer selector de endpoints — codigo de salida 0

```powershell
$env:JAVA_HOME = Split-Path (Split-Path (Get-Command java).Source)
$env:AUTH_DB_URL = 'jdbc:sqlite:./target/endpoint-tests.db'
mvn -B "-Dtest=AuthResourceTest,HealthResourceTest,HealthDownTest" "-Dquarkus.http.test-port=0" test
```

**28 pruebas, 0 fallos, 0 errores, 0 omitidas; BUILD SUCCESS.**
Tiempo Maven: 27.623 s. Esta corrida precede al nuevo `AuthFailureTest`.
[Log](../target/endpoint-tests-first.log).

### 2. Primer verify completo — codigo de salida 1

```powershell
$env:JAVA_HOME = Split-Path (Split-Path (Get-Command java).Source)
$env:AUTH_DB_URL = 'jdbc:sqlite:./target/testing-suite.db'
mvn -B "-Dquarkus.http.test-port=0" "-Djacoco.destFile=target/testing-20261019-165131.exec" "-Djacoco.dataFile=target/testing-20261019-165131.exec" verify
```

**45 casos reportados, 0 fallos de asercion, 2 errores de arranque, 26 omitidos;
BUILD FAILURE.** Tiempo Maven: 26.870 s. Al reiniciar Quarkus entre perfiles,
se intento usar 8081 y ese puerto estaba ocupado. No se cambio una expectativa
de test ni se detuvo el proceso ajeno; se mantuvo el puerto dinamico mediante
variable de entorno para toda la ejecucion siguiente.
[Log del fallo conservado](../target/testing-verify-first-failure.log).

### 3. Verify final completo — codigo de salida 0

```powershell
$env:JAVA_HOME = Split-Path (Split-Path (Get-Command java).Source)
$env:AUTH_DB_URL = 'jdbc:sqlite:./target/testing-suite.db'
$env:QUARKUS_HTTP_TEST_PORT = '0'
mvn -B "-Dquarkus.http.test-port=0" "-Djacoco.destFile=target/testing-20261019-165213.exec" "-Djacoco.dataFile=target/testing-20261019-165213.exec" verify
```

**BUILD SUCCESS**, tiempo Maven **31.223 s**:

| suite | casos | fallos | errores | omitidos | segundos |
|---|---:|---:|---:|---:|---:|
| AuthResourceTest | 25 | 0 | 0 | 0 | 3.950 |
| AuthFailureTest | 2 | 0 | 0 | 0 | 4.958 |
| HealthResourceTest | 2 | 0 | 0 | 0 | 0.111 |
| HealthDownTest | 1 | 0 | 0 | 0 | 2.199 |
| LegacyDatabaseTest | 15 | 0 | 0 | 0 | 0.605 |
| **Total** | **45** | **0** | **0** | **0** | |

Salida redirigida a [testing-verify.log](../target/testing-verify.log).
Informes por clase: [surefire-reports](../target/surefire-reports).
Los stacktraces de las pruebas existentes de fallos SQLite son esperados por
el manejo actual del repositorio; no equivalen a errores de Surefire.

## Cobertura y metodo de medicion

No se modificaron fuentes productivas en esta tarea. JaCoCo mide las clases
productivas ejercitadas, no las clases de test. Se conserva ademas la medicion
especifica de `LegacyDatabase`, modificada previamente por H-01, para no usar
un promedio global como sustituto del umbral.

El `verify` final cargo **solo** `testing-20261019-165213.exec`, archivo nuevo
para la revision actual. No se reutilizo `jacoco.exec` ni el archivo del fallo.
Reporte [HTML](../target/site/jacoco/index.html) y
[XML](../target/site/jacoco/jacoco.xml).

Formula: `100 * covered / (covered + missed)`, usando el contador `LINE`
de cada clase en el XML. Objetivo de [REGLAS.md](../REGLAS.md): >= 85%.

| clase productiva | lineas | porcentaje | ramas | instrucciones |
|---|---:|---:|---:|---:|
| AuthResource | 25/25 | 100% | 14/14 (100%) | 129/129 |
| LegacyDatabase | 29/29 | 100% | 5/8 (62,5%) | 114/114 |
| LegacyTokenUtil | 3/3 | 100% | Sin contador de ramas | 16/16 |
| LoginRequest | 1/1 | 100% | Sin contador de ramas | 3/3 |
| UserRecord | 1/1 | 100% | Sin contador de ramas | 15/15 |

**Umbral de lineas cumplido**, incluida la clase previamente modificada.
No se afirma 85% de ramas para LegacyDatabase. La cobertura no demuestra que
los defectos legacy sean seguros ni mide la implementacion interna de la
extension de salud, que es una dependencia.

Comando de lectura usado tras el build:

```powershell
[xml]$report = Get-Content target\site\jacoco\jacoco.xml -Raw
foreach ($class in $report.report.package.class) {
    $counter = $class.counter | Where-Object type -eq 'LINE'
    $total = [int]$counter.covered + [int]$counter.missed
    [pscustomobject]@{
        Class = $class.name
        Covered = $counter.covered
        Missed = $counter.missed
        Percent = 100 * [int]$counter.covered / $total
    }
}
```

## Fallos, pendientes y pruebas propuestas

### Fallos ejecutados

- Fallo de infraestructura por puerto ocupado en la primera corrida completa,
  registrado arriba; resuelto en el comando, no ocultado ni saltado.
- En la corrida final no hay fallos de asercion, errores ni pruebas omitidas.

### Defectos existentes, reportados aparte

Las aserciones de caracterizacion de 500 verifican el **contrato legacy**, no
una correccion de seguridad. Se conserva pendiente H-05 por exposicion de
mensajes/clases
([AuthResource.java:54](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L54)).
Tampoco se declara correcto el tratamiento de un fallo SQL como ausencia de
usuario
([LegacyDatabase.java:55](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L55)).
Esas pruebas no se adaptaron a posteriori para esconder un fallo.

H-02/H-03/H-04 siguen pendientes: passwords legibles, token sin firma y
secreto/CORS inseguros. No se cambian sus estados ni se prueba falsamente una
firma o una expiracion validada por este backend. La matriz unica sigue siendo
[HALLAZGOS.md](../HALLAZGOS.md).

### Pendientes y propuestas, no ejecutadas

- Cuerpo de salud DOWN en una configuracion productiva real: sigue abierto en
  el contrato. El test 503 usa un fixture y no establece ese cuerpo.
- Comportamientos de infraestructura/deserializacion no concretados en el
  contrato, especialmente perfil prod: no se inventan tests o estados.
- Despues de autorizar cambios de contrato y corregir H-05, proponer regresion
  que exija mensajes genericos y ausencia de clase interna; no se ejecuta ni
  se presenta como prueba aprobada en este trabajo.
- Validacion de firma/expiracion de token solo despues de implementar ese
  comportamiento con contrato aprobado. No hay un endpoint verificador hoy.

## Documentos consultados

- [ENDPOINTS.md](ENDPOINTS.md): casos, campos y errores que se verifican.
- [REGLAS.md](../REGLAS.md): cobertura y limites del laboratorio.
- [GOOD_PRACTICES.md](GOOD_PRACTICES.md): separacion de responsabilidades y deuda.
- [HALLAZGOS.md](../HALLAZGOS.md): defectos pendientes, sin duplicar seguimiento.

Tambien se leyeron [pom.xml](../pom.xml),
[AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java),
[application.properties](../src/main/resources/application.properties) y las
pruebas enlazadas. Los logs y reportes bajo `target` se pueden regenerar y
sobrescribir en futuras ejecuciones; este documento conserva los resultados
de la corrida indicada.
