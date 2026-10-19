# Hallazgos del backend de autenticacion

Revision estatica limitada a este repositorio. Esta es la unica matriz de
seguimiento; los cambios posteriores de `2-refactor.md` deben actualizar estas
filas conservando sus identificadores. No se ha modificado codigo.

Hay una fila por cada caso de [RETO.md](RETO.md), ordenada por severidad.
Los siete casos tienen evidencia local; ninguno se marca como "No encontrado".
En SCA se confirma la presencia de las versiones, no una CVE ni su
explotabilidad. Las severidades son una priorizacion del laboratorio, no
puntuaciones CVSS.

| id | hallazgo | tipo | explicacion | evidencia (archivo:linea) | severidad | estado |
|---|---|---|---|---|---|---|
| H-01 | SQL: concatenacion de credenciales | Vulnerabilidad | Las credenciales recibidas se insertan en el texto SQL y se ejecutan con `Statement`. Una entrada manipulada puede alterar la condicion de busqueda y obtener un usuario sin conocer su password; el resource emite un token al recibir un registro. | [src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:32](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L32), [src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:35](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L35), [src/main/java/com/bcp/workshop/auth/AuthResource.java:35](src/main/java/com/bcp/workshop/auth/AuthResource.java#L35), [src/main/java/com/bcp/workshop/auth/AuthResource.java:39](src/main/java/com/bcp/workshop/auth/AuthResource.java#L39) | alta | Pendiente |
| H-02 | Datos: contrasenas en texto plano | Vulnerabilidad | La tabla guarda passwords directamente, los usuarios de laboratorio se insertan con passwords legibles y la consulta compara el valor recibido sin hash ni salt. La lectura de la base permite recuperar las credenciales originales. | [src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:22](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L22), [src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:23](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L23), [src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:24](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L24), [src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:32](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L32) | alta | Pendiente |
| H-03 | Token: Base64 con secreto embebido | Vulnerabilidad | El token es solo Base64 de identidad, rol, vencimiento y secreto: es reversible y no tiene firma que proteja su integridad. Quien recibe uno puede leer el secreto y modificar sus campos. Existe un timestamp de expiracion, pero este repositorio no implementa un verificador; no se afirma como se valida en componentes externos. | [src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java:8](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L8), [src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java:13](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L13), [src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java:14](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L14), [src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java:15](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L15) | alta | Pendiente |
| H-04 | Configuracion: secreto y CORS hardcodeados | Vulnerabilidad | El secreto esta fijado en una constante de Java, lo que impide su rotacion por configuracion y lo acopla al codigo. CORS esta habilitado con una expresion que acepta cualquier origen, ampliando los sitios que pueden leer respuestas desde navegadores. La severidad conjunta prioriza el secreto; no implica por si sola robo de sesiones por CORS. | [src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java:8](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L8), [src/main/resources/application.properties:4](src/main/resources/application.properties#L4), [src/main/resources/application.properties:5](src/main/resources/application.properties#L5), [src/main/resources/application.properties:6](src/main/resources/application.properties#L6), [src/main/resources/application.properties:7](src/main/resources/application.properties#L7) | alta | Pendiente |
| H-05 | Errores: respuesta expone detalles internos | Vulnerabilidad | Si una excepcion alcanza el catch del resource, el HTTP 500 incluye su mensaje original y el nombre completo de su clase, revelando detalles de implementacion. Se confirma la construccion de esa respuesta, no una filtracion concreta ejecutada. Los errores de base de datos se capturan antes en el repositorio y no necesariamente llegan a este catch. | [src/main/java/com/bcp/workshop/auth/AuthResource.java:54](src/main/java/com/bcp/workshop/auth/AuthResource.java#L54), [src/main/java/com/bcp/workshop/auth/AuthResource.java:56](src/main/java/com/bcp/workshop/auth/AuthResource.java#L56), [src/main/java/com/bcp/workshop/auth/AuthResource.java:57](src/main/java/com/bcp/workshop/auth/AuthResource.java#L57), [src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:39](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L39) | media | Pendiente |
| H-06 | SCA: Quarkus 3.27.4 y Commons Text 1.9 | Sugerencia | El POM fija ambas versiones y el resource usa Commons Text para `escapeHtml4`. Corresponde investigar advisories y su aplicabilidad antes de actualizar o remover: la version declarada por si sola no demuestra una vulnerabilidad explotable ni el uso de una API vulnerable. Quedan pendientes la evaluacion de advisories y el arbol de dependencias resuelto. | [pom.xml:13](pom.xml#L13), [pom.xml:22](pom.xml#L22), [pom.xml:49](pom.xml#L49), [pom.xml:50](pom.xml#L50), [src/main/java/com/bcp/workshop/auth/AuthResource.java:45](src/main/java/com/bcp/workshop/auth/AuthResource.java#L45) | media | Pendiente |
| H-07 | Clean code: resource con validacion, negocio, persistencia y respuesta | Refactor | `login` valida el request, invoca directamente la persistencia, decide el resultado de autenticacion, genera el token y construye respuestas, con varios niveles de anidamiento. Esta mezcla dificulta pruebas aisladas y aumenta el riesgo de alterar el contrato al corregir una responsabilidad. El SQL reside en `LegacyDatabase`, no en el resource. | [src/main/java/com/bcp/workshop/auth/AuthResource.java:24](src/main/java/com/bcp/workshop/auth/AuthResource.java#L24), [src/main/java/com/bcp/workshop/auth/AuthResource.java:29](src/main/java/com/bcp/workshop/auth/AuthResource.java#L29), [src/main/java/com/bcp/workshop/auth/AuthResource.java:35](src/main/java/com/bcp/workshop/auth/AuthResource.java#L35), [src/main/java/com/bcp/workshop/auth/AuthResource.java:39](src/main/java/com/bcp/workshop/auth/AuthResource.java#L39), [src/main/java/com/bcp/workshop/auth/AuthResource.java:40](src/main/java/com/bcp/workshop/auth/AuthResource.java#L40), [src/main/java/com/bcp/workshop/auth/AuthResource.java:54](src/main/java/com/bcp/workshop/auth/AuthResource.java#L54) | baja | Pendiente |

## Mapa del flujo actual

1. **Configuracion e inicializacion.** El servicio usa puerto 8080 y host local;
   el perfil prod cambia el host a todas las interfaces. CORS permite cualquier
   origen y Jackson ignora propiedades desconocidas
   ([application.properties:1](src/main/resources/application.properties#L1)).
   `LegacyDatabase` obtiene `AUTH_DB_URL` del entorno, con SQLite local como
   valor por defecto
   ([LegacyDatabase.java:15](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L15)).
   Su `@PostConstruct` crea el directorio de datos, la tabla y dos usuarios de
   laboratorio con `INSERT OR IGNORE`; los errores se imprimen sin propagarse
   ([LegacyDatabase.java:17](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L17)).
2. **Entrada HTTP.** `POST /auth/login` consume y produce JSON
   ([AuthResource.java:15](src/main/java/com/bcp/workshop/auth/AuthResource.java#L15),
   [AuthResource.java:22](src/main/java/com/bcp/workshop/auth/AuthResource.java#L22)).
   El DTO tiene `username`, `password` y `client`
   ([LoginRequest.java:3](src/main/java/com/bcp/workshop/auth/LoginRequest.java#L3));
   `client` no se usa en el flujo.
3. **Validacion.** El resource rechaza con 400 un request nulo, un username
   nulo/vacio o un password nulo/vacio, en ese orden
   ([AuthResource.java:26](src/main/java/com/bcp/workshop/auth/AuthResource.java#L26)).
   `trim()` solo detecta blancos: se envian los valores originales al repositorio.
4. **Persistencia y decision.** Se abre una conexion, se concatena y ejecuta el
   SELECT y se convierte el primer resultado en `UserRecord`
   ([LegacyDatabase.java:31](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L31),
   [UserRecord.java:3](src/main/java/com/bcp/workshop/auth/UserRecord.java#L3)).
   El record contiene id, username, displayName y role, no el password.
   Si no hay resultado, o si la consulta falla y su catch imprime la excepcion,
   el repositorio devuelve `null`
   ([LegacyDatabase.java:39](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L39)).
   El resource responde 401 para ese `null`
   ([AuthResource.java:36](src/main/java/com/bcp/workshop/auth/AuthResource.java#L36)).
5. **Generacion del token.** Con un usuario, se invoca `LegacyTokenUtil.create`.
   Se calcula un vencimiento a 3600 segundos y se codifican con Base64 los campos
   separados por `|`, incluido el secreto
   ([LegacyTokenUtil.java:12](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L12)).
   No hay firma criptografica ni endpoint de validacion de tokens en el codigo
   de este repositorio.
6. **Respuesta y errores.** El exito devuelve 200 con `token`, `expiresIn`,
   `user` y `debug`; `displayName` pasa por `escapeHtml4`
   ([AuthResource.java:40](src/main/java/com/bcp/workshop/auth/AuthResource.java#L40)).
   Las excepciones que alcanzan el catch del resource producen 500 con
   `message` y `exception`
   ([AuthResource.java:54](src/main/java/com/bcp/workshop/auth/AuthResource.java#L54)).

## Contrato y alcance de la verificacion

- [docs/ENDPOINTS.md](docs/ENDPOINTS.md) no existia y se genero desde el codigo
  actual, sin proponer cambios al contrato.
- La prueba existente caracteriza solo un login exitoso: HTTP 200, username
  esperado y token no nulo
  ([AuthResourceTest.java:13](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java#L13)).
  No se ejecuto: esta entrega solo incorpora documentacion y revision estatica.
- No se ejecutaron payloads, escaneos externos ni componentes ajenos al ambito.
  No se atribuyen comportamientos a consumidores externos del token.
- Antes de refactorizar persistencia o token deben crearse pruebas de
  caracterizacion, segun [REGLAS.md](REGLAS.md). No se ha iniciado ese trabajo.
