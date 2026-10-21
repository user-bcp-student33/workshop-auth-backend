# API HTTP del backend de autenticacion

Contrato actualizado el 2026-10-21 tras HU-01 (#11), comprobado con la suite
HTTP y verificadores criptograficos locales. Base local por defecto:
`http://localhost:8080`. JSON se entrega como `application/json; charset=UTF-8`;
el orden de propiedades no es significativo.

**El backend usa SQL parametrizado, hashes PBKDF2 y tokens JWT RS256.**
Este documento describe lo implementado y no implica validacion en consumidores externos.
No usar este laboratorio en Internet, CERT o PROD.

Configuracion de red:
[application.properties:1](../src/main/resources/application.properties#L1);
advertencia de uso: [README.md](../README.md).

## POST /auth/login

### 1. Que hace

Comprueba las credenciales de un usuario de laboratorio y, si coinciden,
entrega su identidad, rol y un token para los consumidores del backend.

Ruta y metodo:
[AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java).

### 2. Autenticacion y headers

No requiere autenticacion previa ni header `Authorization`. Las credenciales
se envian en el cuerpo, no mediante HTTP Basic.

| Header | Valor | Uso |
|---|---|---|
| `Content-Type` | `application/json` | Necesario al enviar el JSON |
| `Accept` | `application/json` | Recomendado; tambien se acepta omitirlo |

El resource declara entrada y salida JSON y no exige un token
([AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java)).

### 3. Request

Sin parametros de ruta ni query definidos por el resource.

| campo | tipo | obligatorio | descripcion |
|---|---|---|---|
| `username` | string | Si | Username exacto almacenado; no nulo, vacio ni solo blancos |
| `password` | string | Si | Password exacto; no nulo, vacio ni solo blancos |
| `client` | string | Si | Identificador exacto de 1 a 64 caracteres ASCII; formato `^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$`, sin trim ni normalizacion |

Ejemplo completo, con credenciales exclusivamente de laboratorio:

```json
{
  "username": "student",
  "password": "Workshop123!",
  "client": "angular-workshop"
}
```

`trim()` se usa solo para detectar blancos: no se recortan las credenciales
antes de compararlas. Se ignoran propiedades JSON desconocidas.
Para `client` no hay coercion de tipos JSON: numeros, booleanos, arrays y
objetos no son strings validos y reciben `400 {"message":"Invalid client"}`.
Su valor aceptado se copia literalmente al claim `client` del JWT.
Los usuarios iniciales son `student` (rol `STUDENT`) y `trainer` (rol `TRAINER`).
La base puede contener otros registros; esos roles no son un enum validado
por esta API.

Fuentes:
[LoginRequest.java:3](../src/main/java/com/bcp/workshop/auth/LoginRequest.java#L3),
[AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java),
[application.properties:8](../src/main/resources/application.properties#L8)
y [PasswordMigration.java](../src/main/java/com/bcp/workshop/auth/PasswordMigration.java).

### 4. Response exitosa

**HTTP 200 OK**, JSON:

| campo | tipo | descripcion |
|---|---|---|
| `token` | string | JWT firmado RS256; representacion compacta de tres segmentos Base64URL |
| `expiresIn` | number (entero) | Segundos de vigencia anunciada: siempre `3600` |
| `user` | object | Datos publicos del usuario autenticado |
| `user.username` | string | Username almacenado |
| `user.displayName` | string | Nombre almacenado con escape HTML4 aplicado por el servidor |
| `user.role` | string | Rol almacenado, por ejemplo `STUDENT` o `TRAINER` |
| `debug` | string | Valor fijo `authenticated-with-legacy-flow` |

Forma de respuesta con el request anterior; el unico marcador dinamico es
el token, que debe obtenerse de la instancia y nunca reutilizarse de esta guia:

```json
{
  "token": "<JWT RS256 generado por la instancia>",
  "expiresIn": 3600,
  "user": {
    "username": "student",
    "displayName": "Workshop Student",
    "role": "STUDENT"
  },
  "debug": "authenticated-with-legacy-flow"
}
```

No se devuelven `id` ni `password`.

El token anterior Base64 fue reemplazado intencionalmente: no se emiten ambos
formatos. Header `alg=RS256`; claims `sub` (username), `client` (valor aceptado), `role` (rol),
`iss` (issuer configurado), `aud` (audience configurado), `iat` y `exp`
(timestamps Unix en segundos). `exp = iat + 3600`. La biblioteca puede incluir
identificadores adicionales, que no deben considerarse campos estables del API.
`expiresIn` es la vigencia desde la emision, no el tiempo restante al recibir
la respuesta: `now` es el instante capturado, truncado a segundos y expresado
por `iat`. La diferencia respecto a `exp - now` admite como maximo un segundo.
Se conserva SmallRye JWT con claves RSA; no se agrega `AUTH_TOKEN_SECRET`.
Los consumidores deben verificar firma con la clave publica correspondiente,
algoritmo RS256, issuer, audience y vencimiento. No se agrego endpoint de
validacion; la adopcion del formato nuevo en consumidores requiere coordinacion.

Fuentes:
[AuthService.java](../src/main/java/com/bcp/workshop/auth/AuthService.java),
[TokenService.java](../src/main/java/com/bcp/workshop/auth/TokenService.java)
y [AuthResourceTest.java:39](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java#L39).

### 5. Respuestas de error

#### Errores definidos por el resource

Se comprueban cuerpo, username, password, client y credenciales en ese orden.
Un cliente invalido prevalece sobre credenciales incorrectas no vacias;
no se llama al servicio de autenticacion ni se emite token en ese caso.

| HTTP | Cuando ocurre | Ejemplo completo de cuerpo JSON |
|---|---|---|
| 400 | Cuerpo deserializado como `null`; observado enviando el JSON `null` | `{"message":"Request is null"}` |
| 400 | Username ausente, nulo, vacio o solo blancos | `{"message":"Username is required"}` |
| 400 | Username valido pero password ausente, nulo, vacio o solo blancos | `{"message":"Password is required"}` |
| 400 | Credenciales no vacias y client ausente, nulo, vacio, de tipo distinto de string o fuera del formato aprobado | `{"message":"Invalid client"}` |
| 401 | Client valido y credenciales sin coincidencia; ejemplo: `student` con password incorrecto | `{"message":"Invalid user student or password"}` |
| 500 | Excepcion que alcanza el catch del resource; probado con excepciones controladas, incluso sin mensaje o con contenido sensible | `{"message":"Internal server error","exception":"InternalServerError"}` |

En 400/401, `message` es string. En el 401 se incluye literalmente el username
recibido. En 500, `message` y `exception` son strings con los valores constantes
del ejemplo: `exception` es un codigo publico, no una clase Java. No se devuelve
el mensaje original, su clase, causas ni excepciones suprimidas. H-05 cambia
intencionalmente esos valores manteniendo estado y claves JSON.

Un error de consulta puede producir **401**, no necesariamente 500:
`LegacyDatabase` registra un error controlado y devuelve `null`, igual que cuando no
hay coincidencias. No distinguir una indisponibilidad de base de datos solo por
el codigo 401.

Fuentes:
[AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java),
[LegacyDatabase.java:55](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L55)
y [AuthResourceTest.java:112](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java#L112).

#### Errores previos al metodo, gestionados por Quarkus

Casos observados con Quarkus 3.27.4, perfil dev, sin manejadores personalizados:

| HTTP | Cuando ocurre / ejemplo de request | Ejemplo de cuerpo recibido |
|---|---|---|
| 400 | JSON malformado, por ejemplo un unico `{` | Cuerpo vacio, 0 bytes; no es `{}` ni `null` |
| 400 | Tipo incompatible: `{"username":{},"password":"x"}` | `{"objectName":"LoginRequest","attributeName":"username","line":1,"column":13,"value":null}` |
| 405 | Metodo no admitido en esta ruta; observado con `GET /auth/login` | Cuerpo vacio, 0 bytes |
| 406 | `Accept: text/plain` no compatible con la salida JSON | Cuerpo vacio, 0 bytes |
| 415 | `Content-Type: text/plain` con cuerpo `test` | Cuerpo vacio, 0 bytes |

El JSON del error de tipo proviene del mapper de Quarkus, no del resource:
`objectName` y `attributeName` son strings; `line` y `column` son enteros de la
posicion del error; `value` fue `null` en ese caso. No convertir este ejemplo
en una forma universal de todos los errores de deserializacion. Para integrar,
comprobar el estado y Content-Type antes de intentar parsear JSON.

**Pregunta abierta:** que cuerpos devuelve el runtime para otros errores de
deserializacion, limites HTTP y fallos internos de infraestructura, especialmente
en perfil prod? El codigo del componente no fija una forma comun ni permite
enumerar todos esos fallos; no se les asignan cuerpos o codigos inventados.

### 6. Notas de seguridad

- La busqueda vincula username como parametro; el servicio verifica el hash
  PBKDF2 del password ([AuthService.java](../src/main/java/com/bcp/workshop/auth/AuthService.java)).
- Los passwords persistidos usan salt independiente y 600000 iteraciones
  ([PasswordHasher.java](../src/main/java/com/bcp/workshop/auth/PasswordHasher.java)).
- El token esta firmado, no cifrado: sus claims son legibles, pero el payload
  no contiene claves ni passwords. Los tests verifican firma y expiracion
  incluso en su limite exacto. No se afirma como validan servicios externos.
- Los errores 500 son genericos. El servidor registra la clase y frames del
  fallo sin copiar mensajes, causas ni excepciones suprimidas potencialmente
  sensibles. La respuesta exitosa conserva el campo `debug` por compatibilidad.
- CORS permite solo la lista obligatoria `AUTH_CORS_ORIGINS`. Para el origen
  permitido, preflight/respuesta incluyen permiso CORS; para otro no se
  concede `Access-Control-Allow-Origin`, sin prometer un codigo HTTP concreto
  ([CorsConfiguration.java](../src/main/java/com/bcp/workshop/auth/CorsConfiguration.java)).
  Esto no sustituye autenticacion ni convierte al laboratorio en un servicio
  apto para exponer publicamente.

## GET /q/health

### 1. Que hace

Permite a una herramienta de operacion consultar si las comprobaciones de salud
del servicio reportan disponibilidad.

La ruta esta anunciada en [README.md](../README.md) y la proporciona
`quarkus-smallrye-health`
([pom.xml:40](../pom.xml#L40)), no un resource personalizado.
En la instancia actual responde `UP` con `checks` vacio: no verifica SQLite
ni la correccion de un login.

### 2. Autenticacion y headers

No requiere autenticacion ni header `Authorization`, comprobado mediante una
peticion sin credenciales. Se puede enviar `Accept: application/json`; no es
necesario enviar `Content-Type` cuando no hay cuerpo. No se encontro una politica
de autenticacion HTTP configurada para esta ruta.

### 3. Request

| campo | tipo | obligatorio | descripcion |
|---|---|---|---|
| Ninguno | No aplica | No | GET sin cuerpo, sin parametros de ruta ni query requeridos |

**Ejemplo JSON de request:** no aplica; el request real tiene **cero bytes de
cuerpo**. Enviar `{}` inventaria un cuerpo que el cliente no necesita.

Ejemplo HTTP completo:

```http
GET /q/health HTTP/1.1
Host: localhost:8080
Accept: application/json

```

### 4. Response exitosa

**HTTP 200 OK**, `Content-Type: application/json; charset=UTF-8`.

| campo | tipo | descripcion |
|---|---|---|
| `status` | string | Estado agregado; en la instancia actual, `"UP"` |
| `checks` | array | Comprobaciones individuales; actualmente vacio, sin esquema de elementos definido por codigo propio |

Ejemplo completo observado:

```json
{
  "status": "UP",
  "checks": []
}
```

No se inventan campos para elementos de `checks`: el proyecto no implementa
comprobaciones propias y la respuesta verificada no contiene ninguno.

### 5. Respuestas de error

No hay errores de negocio definidos por codigo propio para esta ruta.
La extension instalada asigna **503 Service Unavailable** cuando el resultado
de salud es `DOWN`; se comprobo en el bytecode de
`SmallRyeHealthHandlerBase` (callback `lambda$doHandle$0`) de Quarkus 3.27.4
usado por el build. El ejemplo `UP` anterior se verifico ademas por HTTP.

**Pregunta abierta — cuerpo de 503:** que comprobacion y payload `DOWN` puede
producir esta configuracion concreta? No se observo un estado `DOWN` ni hay
un check propio que permita justificar un ejemplo completo de ese cuerpo.
Por ello no se presenta un JSON ficticio como respuesta real del backend.
La extension tambien contiene una variante de errores Problem Details; no esta
configurada explicitamente en el componente y no se asume su uso.

**Pregunta abierta — otros errores:** el codigo propio no define estados ni
cuerpos para fallos internos del handler o del servidor. No hay base para
prometer que todos ellos produzcan JSON. Si el servidor esta detenido puede
no haber respuesta HTTP; eso no es una respuesta JSON de este endpoint.

### 6. Notas de seguridad

- Un `200` con `checks: []` no demuestra que SQLite este disponible ni que
  las credenciales funcionen; no usarlo como prueba funcional de autenticacion.
- No enviar passwords ni tokens para consultar salud. El endpoint es accesible
  sin credenciales en la configuracion verificada.
- Las restricciones de uso local del laboratorio y la lista CORS tambien
  aplican a esta ruta.

## Evidencia y documentos consultados

Los ejemplos de errores Quarkus se observaron inicialmente en dev y se
verificaron despues por la suite HTTP en perfil test. H-02/H-03/H-04 conservan
esos errores y la forma del JSON; cambian el token y la politica CORS de forma
intencional. Una instancia empaquetada con claves y origen ficticios verifico
200 de health y login, JWT RS256 y restriccion CORS. Se detuvo y se eliminaron
las claves al terminar. Solo se usaron bases SQLite bajo `target`, no otros
componentes ni consumidores externos.

- [Resultados actuales de pruebas](TESTING.md).
- [Verificacion del artefacto empaquetado](../target/h04-runtime-results.json).
- [Variables obligatorias y operacion](../README.md).

- [Respuestas HTTP historicas previas al cambio de token](../target/endpoints-http-evidence.json).
- [Bytecode del handler de salud de la extension instalada](../target/health-handler-bytecode.txt).
- [README.md](../README.md): ejecucion, credenciales de laboratorio y rutas.
- [REGLAS.md](../REGLAS.md): limites y reglas del laboratorio.
- Version anterior de este documento: contraste del contrato documentado.

Tambien se consultaron [pom.xml](../pom.xml), los archivos de codigo enlazados
en cada apartado, [application.properties](../src/main/resources/application.properties)
y [AuthResourceTest.java](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java).
Las evidencias de `target` son locales y regenerables, no una dependencia del
cliente.
