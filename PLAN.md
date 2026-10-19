# Plan de correccion del login

## 1. Summary

Propuesta documental para H-01, H-02, H-03 y H-04, confirmados como
**Pendiente** en [HALLAZGOS.md](HALLAZGOS.md). Seleccion confirmada por el
usuario; motivo indicado: H-01 por riesgo alto y correccion SQL acotada.
Los otros tres tambien tienen severidad alta; no se presume otro motivo.
No se modifica codigo ni se cambia el estado de la matriz.

## 2. Scope

- **Dentro:** este backend; SQL parametrizado, almacenamiento seguro de
  passwords, propuesta de token firmado y configuracion externa.
- **Fuera:** otros componentes, nuevos endpoints, correcciones H-05/H-06/H-07
  como objetivos independientes, implementacion y ejecucion de migraciones.
- [docs/ENDPOINTS.md](docs/ENDPOINTS.md) permanece intacto. Se conservan
  request, validaciones, mensajes, codigos HTTP, campos de respuesta, escape
  HTML4 y expiracion de 3600 segundos. Las entradas SQL manipuladas dejaran de
  autenticar: es la correccion buscada, no una garantia a preservar.

## 3. Supuestos

- Java 21, SQLite y herramientas existentes en [pom.xml](pom.xml).
- Las pruebas futuras usaran una base temporal bajo `target`, mediante
  `AUTH_DB_URL`, sin tocar datos de uso normal ni registrar credenciales.
- No se conocen los origenes autorizados ni los consumidores reales del
  token; no se inspeccionaran otros repositorios para inferirlos.

## 4. Decisiones tecnicas por tomar y propuesta por hallazgo

### H-01 — Concatenacion SQL

- **Problema:** [LegacyDatabase.java:32](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L32)
  concatena ambas credenciales en una consulta ejecutada con `Statement`.
- **Cambio:** usar `PreparedStatement` con dos parametros vinculados, sin
  interpolacion ni escape manual. Conservar el mapeo de `UserRecord` y
  `try-with-resources`. Al abordar H-02, la busqueda evolucionara a username
  parametrizado y verificacion del hash fuera del repositorio.
- **Archivos:** [LegacyDatabase.java](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java)
  y [AuthResourceTest.java](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java);
  nuevo `src/test/java/com/bcp/workshop/auth/LegacyDatabaseTest.java`.
- **Caracterizacion previa:** usuarios de laboratorio validos, password
  incorrecto, usuario inexistente, espacios significativos y mapeo de campos.
  Fijar HTTP 200/401 y cuerpos antes de cambiar SQL.
- **Regresion posterior:** comillas y metacaracteres se tratan como datos;
  no autentican otro usuario. Sin dependencias nuevas.

### H-02 — Passwords en texto plano

- **Problema:** [LegacyDatabase.java:22](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L22)
  almacena e inicializa passwords legibles; la consulta los compara directamente.
- **Cambio:** repositorio busca por username con parametros; servicio verifica
  un hash lento con salt aleatorio por password. Propuesta sin dependencias:
  PBKDF2-HMAC-SHA256 del JDK, formato versionado y comparacion constante de
  claves derivadas. Costo, longitud y salt se fijaran con constantes nombradas
  y evidencia de calibracion; es una propuesta, no una convencion ya existente.
  No truncar ni recortar passwords.
- **Migracion propuesta:** convertir registros existentes en una transaccion
  antes de aceptar logins, con version de esquema explicita; conservar
  identidades y roles, hacerla idempotente y revertir ante error. Sembrar solo
  hashes en bases nuevas; no usar fallback a texto plano ni clasificar por
  prefijos ambiguos. Acordar respaldo local protegido y tratamiento de copias
  SQLite: actualizar filas no garantiza borrar restos del archivo o backups.
- **Archivos:** [LegacyDatabase.java](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java),
  [AuthResource.java](src/main/java/com/bcp/workshop/auth/AuthResource.java),
  [AuthResourceTest.java](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java)
  y [README.md](README.md); nuevos `AuthService.java`, `PasswordHasher.java` y
  `PasswordMigration.java` en `src/main/java/com/bcp/workshop/auth`, con pruebas
  homonimas en el paquete de test.
- **Caracterizacion previa:** fixture de esquema legado, login de ambos roles,
  password incorrecto, espacios y respuesta completa. Luego verificar migracion
  repetida sin cambios, rollback, ausencia de texto plano en filas, salts
  distintos para passwords iguales y rechazo de hashes invalidos con error
  controlado y logging sin secretos.

### H-03 — Token Base64 con secreto embebido

- **Problema:** [LegacyTokenUtil.java:13](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L13)
  codifica identidad, rol, vencimiento y secreto sin firma.
- **Cambio deseado:** emisor dedicado de tokens firmados, con claims explicitos
  de identidad, rol y expiracion; claves externas, nunca dentro del payload.
  **Bloqueado bajo el contrato actual:** este describe expresamente el formato
  Base64 con secreto. No reemplazarlo por JWT, agregar campos o aceptar ambos
  formatos como atajo; tampoco afirmar que externalizar el secreto resuelve H-03.
  No existe una correccion completa compatible con esa representacion.
- **Archivos candidatos, solo tras resolver el bloqueo:** [LegacyTokenUtil.java](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java),
  [AuthResource.java](src/main/java/com/bcp/workshop/auth/AuthResource.java),
  [application.properties](src/main/resources/application.properties),
  [pom.xml](pom.xml) y [README.md](README.md); nuevo `TokenService.java` y tests
  de token. Una biblioteca mantenida de firma/JOSE se justificaria frente a
  criptografia casera; no se selecciona ni agrega ninguna en esta entrega.
- **Caracterizacion previa:** decodificar token de prueba, comprobar campos,
  rol y timestamp entre los instantes anterior/posterior mas 3600; verificar
  igualdad con `expiresIn`, sin imprimir secreto ni token.
  Pruebas futuras de firma, alteracion y expiracion requieren acordar primero
  formato y verificador. No se agrega un endpoint de validacion.

### H-04 — Secreto y CORS hardcodeados

- **Problema:** constante en [LegacyTokenUtil.java:8](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L8)
  y origen comodin en [application.properties:5](src/main/resources/application.properties#L5).
- **Cambio:** propiedad de secreto inyectada desde entorno, obligatoria al
  iniciar y sin fallback real; valor ficticio solo en perfil de pruebas.
  Mientras H-03 este bloqueado, conservar el serializado actual y advertir
  que el secreto seguira expuesto en tokens. Rotar el valor exige coordinacion
  con consumidores, sin acceder a sus archivos.
  Parametrizar CORS con lista explicita de origenes autorizados, nunca `.*`;
  acordar esos valores antes de activar la restriccion. Conservar metodos y
  cabeceras actuales salvo necesidad aprobada.
- **Archivos:** [LegacyTokenUtil.java](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java),
  [AuthResource.java](src/main/java/com/bcp/workshop/auth/AuthResource.java),
  [application.properties](src/main/resources/application.properties),
  [README.md](README.md); nuevo test de configuracion/token y test HTTP CORS.
- **Caracterizacion previa:** token y login con secreto ficticio; respuestas
  y preflight CORS actuales para origenes de prueba. Despues: arranque falla
  sin secreto, origen permitido funciona y origen ajeno no recibe permiso
  CORS (sin exigir un codigo HTTP no definido por Quarkus).

### Secuencia y Clean Code

Caracterizacion primero; H-01, luego H-02; H-04 en cambio separado y coordinado.
H-03 no se implementa mientras persista el conflicto. Seguir
[GOOD_PRACTICES.md](docs/GOOD_PRACTICES.md) y [REGLAS.md](REGLAS.md):
Resource solo HTTP, servicio para autenticacion, repositorio solo SQL y
seguridad separada. El trabajo de H-02 extraera un DTO de salida
(`LoginResponse.java`, con DTO de usuario), sin exponer el registro de
persistencia ni alterar JSON. Cada metodo tendra una responsabilidad,
guard clauses, helpers reutilizados y constantes para TTL, SQL y parametros
criptograficos; no introducir anidamiento profundo ni duplicar verificaciones.
No ampliar la limpieza a superficies ajenas.

## 5. Riesgos y mitigaciones

- **Perdida de acceso o datos:** fixture legado, transaccion, rollback,
  respaldo protegido y prueba de segundo arranque antes de migrar una base real.
- **Incompatibilidad con consumidores:** mantener contrato, bloquear H-03 y
  coordinar secreto/origenes explicitamente; no inventar compatibilidad externa.
- **Regresion HTTP o filtracion:** caracterizacion completa y pruebas de logs
  sin passwords, hashes, tokens ni claves. H-05 no se corrige incidentalmente.

## 6. Criterios de aceptacion medibles

1. Las pruebas de caracterizacion pasan antes del primer cambio productivo;
   todos los casos 200/400/401/500 documentados quedan cubiertos, incluido DTO
   nulo por invocacion directa y excepcion controlada del resource.
2. H-01: cero credenciales concatenadas; H-02: cero passwords legibles en las
   filas migradas y cero cambios de identidad/rol al repetir migracion.
3. H-04: cero secretos en clases/configuracion productiva versionada y cero
   origenes comodin; pruebas de origen permitido/ajeno y secreto ausente pasan.
4. Cobertura del codigo modificado >= 85%; contrato sin cambios. H-03 no puede
   declararse corregido mientras siga bloqueado; estados solo en la matriz.

## 7. Plan de validacion

- **Unitarias:** validaciones, mapeos, consultas, hash, migracion y token con
  fixtures aislados; tests de regresion de seguridad separados de caracterizacion.
- **Funcionales:** ampliar [AuthResourceTest.java](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java)
  para errores, ambos roles, escape HTML4, `client` opcional, campos desconocidos,
  formato/tipos JSON y CORS; no comparar orden de propiedades.
- **Seguridad:** comprobar parametros SQL, integridad de migracion y ausencia de
  secretos en salida/logs; mantener CodeQL y Dependency Review habilitados.
- **Evidencia futura:** ejecutar `mvn -B "-Dtest=AuthResourceTest" test` como
  base, selectores de los nuevos tests por cambio y `mvn -B verify` al integrar,
  como en [ci.yml](.github/workflows/ci.yml). Adjuntar resultados exactos y
  reporte JaCoCo de clases/lineas modificadas (no sustituirlo por cobertura global).
  No se han ejecutado tests: esta entrega solo crea el plan.

## 8. Tareas sugeridas por rol

- **Chapter/PO:** confirmar prioridades y resolver el bloqueo contractual.
- **Tech Lead:** aprobar limites de capas; decidir hash/migracion; resolver
  formato de token y coordinacion de configuracion.
- **Backend:** escribir caracterizacion; parametrizar SQL; implementar y probar
  hash/migracion/configuracion en cambios separados cuando se autorice.
- **Frontend:** solo confirmar origenes y consumo del token mediante coordinacion;
  no corresponde modificar ni leer su codigo en este ambito.
- **QA:** preparar fixtures; verificar matriz HTTP y regresiones; comprobar
  migracion repetida, cobertura y evidencias.
- **Cyber:** revisar parametros criptograficos, politica de claves y origenes.

## 9. Preguntas abiertas

- Que decision autorizada desbloquearia H-03 sin incumplir el contrato vigente?
- Cuales son los origenes autorizados y como se coordinaran secreto y rotacion?
- Se aprueba PBKDF2 del JDK? Que costo calibrado y politica de respaldo/migracion
  deben adoptarse antes de H-02?
