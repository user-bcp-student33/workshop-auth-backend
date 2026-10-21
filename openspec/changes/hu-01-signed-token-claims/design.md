# Diseño

## Contexto

La motivación se describe en `proposal.md` y los requisitos observables en `specs/auth-login/spec.md`. Antes de este cambio, la implementación y `docs/ENDPOINTS.md` establecían estas restricciones:

- `LoginRequest` ya tiene un campo `client` nullable, pero `AuthResource.login` solo valida la solicitud, el usuario y la contraseña; `AuthService.authenticate` solo recibe usuario y contraseña.
- `AuthService` crea la respuesta y delega la creación del token a `TokenService`. La respuesta incluye `token`, `expiresIn`, `user` y el valor fijo de `debug`.
- `TokenService` firma JWT con claves RSA, issuer y audience configurados, usa un `Clock` UTC y establece `exp` como `iat + 3600`. Al iniciar, valida el par de claves RSA. `application.properties` requiere issuer, audience y las rutas de las claves privada y pública; no existe una configuración `AUTH_TOKEN_SECRET`.
- `AuthResourceTest` caracteriza los campos de respuestas exitosas, errores de credenciales y validación, errores internos redactados y errores HTTP previos al método. La prueba de respuesta exitosa aún no verifica todos los claims, la firma ni el claim `client`.

El Issue #11 describe HMAC, mientras que el API documentado requiere JWT RS256. Aprobacion humana del 2026-10-21: conservar RS256 y la biblioteca actual; `client` obligatorio, sin trim, conforme a `^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$`. Ausente, nulo, vacio o invalido produce 400 con `{"message":"Invalid client"}`. Orden: cuerpo, username, password, client y comprobacion de credenciales. El valor aceptado se propaga literalmente al JWT.

## Objetivos y fuera de alcance

**Objetivos:**

- Mantener la validación de la solicitud, la identidad autenticada, los claims y la construcción de la respuesta dentro de los límites actuales del flujo de autenticación.
- Permitir que el cliente solicitado llegue a la creación del token únicamente después de ser aceptado por una regla de validación aprobada.
- Preservar los campos de respuesta, el TTL fijo, los errores existentes y el comportamiento HTTP previo al método, excepto por el comportamiento aprobado para `client` ausente o inválido.
- Evitar que los logs expongan material de firma o tokens, y hacer que las verificaciones de vigencia sean deterministas y comprobables.

**Fuera de alcance:**

- Cambiar persistencia, hashing de contraseñas, health checks, CORS, otros endpoints o aplicaciones consumidoras.
- Cambiar el algoritmo JWT, el formato de las claves, el contrato de issuer/audience, el TTL o el esquema de respuesta como parte de este cambio.
- Agregar una allowlist o restricciones de cliente distintas del formato aprobado.
- Agregar `AUTH_TOKEN_SECRET` mientras RSA siga siendo la opción de compatibilidad seleccionada.

## Contratos protegidos

- `POST /auth/login` continúa aceptando JSON sin autenticación previa con `username` y `password`; exigir `client` es el único cambio intencional de compatibilidad en la solicitud descrito por la spec.
- La respuesta exitosa sigue siendo HTTP 200 e incluye `token`, `expiresIn` entero igual a 3600, `user.username`, `user.displayName`, `user.role` y `debug: authenticated-with-legacy-flow`.
- Los tokens conservan compatibilidad de verificación RS256 y los claims existentes `sub`, `role`, `iss`, `aud`, `iat` y `exp`. El cambio agrega `client`; no elimina ni renombra claims existentes.
- Se mantienen los resultados documentados en `docs/ENDPOINTS.md` para entradas nulas o vacías, credenciales, errores internos, JSON malformado, tipos incompatibles, método, Accept y Content-Type, excepto la respuesta aprobada para validar `client`.
- Ningún login exitoso puede devolver un token sin los claims requeridos. Los logs de autenticación no deben incluir tokens ni material privado de firma.

## Componentes afectados

| Componente | Responsabilidad en este cambio |
|---|---|
| `LoginRequest` | Transporta el cliente recibido; solo acepta texto JSON sin coercion, tratando los otros tipos como cliente invalido. |
| `AuthResource` | Aplica la regla aprobada para validar el cliente en el flujo de login y conserva el límite actual de errores HTTP. |
| `AuthService` | Propaga el cliente aceptado hasta la creación del token sin cambiar la consulta de credenciales ni la verificación de contraseña. |
| `TokenService` | Agrega el claim `client` y conserva el comportamiento actual de RS256, issuer, audience, reloj y TTL. |
| `LoginResponse` | No cambia el esquema; conserva los campos de respuesta establecidos. |
| `application.properties` y documentación de configuración | Conservan la configuración de claves RSA. Documentan `AUTH_TOKEN_SECRET` solo si se aprueba por separado el uso de HMAC. |
| `AuthResourceTest` y pruebas de tokens | Primero caracterizan el comportamiento actual; después verifican cliente aprobado, claims, expiración, errores, firma y ausencia de datos sensibles en logs. |

## Decisiones

1. **Conservar el contrato de firma actual en este cambio.** Mantener JWT RS256, el issuer/audience configurados y la validación existente del par de claves RSA. Se consideró HMAC porque lo menciona el Issue #11, pero adoptarlo entraría en conflicto con el contrato documentado para consumidores y requeriría una decisión de migración separada. Esta decisión no introduce configuración de secreto compartido. Si se requiere HMAC, detenerse y revisar el alcance, el contrato de compatibilidad con consumidores y la configuración antes de implementar.

2. **Mantener el manejo de `client` en el flujo de login.** Aplicar el formato aprobado sin trim despues de validar cuerpo/username/password y antes de comprobar credenciales. El cliente ausente o invalido produce 400 con `{"message":"Invalid client"}` sin llamar al servicio ni emitir token. Propagar el valor exacto aceptado por AuthService hasta TokenService. Se conserva la precedencia previa de cuerpo y credenciales vacias; la validacion de client prevalece sobre credenciales incorrectas no vacias.

3. **Usar el reloj de emisión del token como referencia de vigencia.** Seguir derivando `iat` y `exp` de un mismo instante UTC capturado, con `exp = iat + 3600`. Comparar `expiresIn` con la referencia de emisión definida en la spec y permitir como máximo la diferencia de un segundo documentada por truncamiento. No cambiar el TTL público para resolver redondeos del reloj.

4. **Conservar la redacción actual de logs.** Mantener diagnósticos genéricos de autenticación; las pruebas deben verificar que valores representativos de tokens y claves no aparezcan en los logs capturados. No registrar cuerpos de solicitudes ni el cliente aceptado salvo que exista una justificación y revisión independientes.

## Estrategia incremental y caracterización

Antes de cambiar el comportamiento de autenticación, ejecutar y conservar la suite HTTP actual que caracteriza la forma de la respuesta exitosa, fallos de credenciales, validación de entradas, redacción de errores internos y errores de Quarkus previos al método. En particular, registrar que actualmente una solicitud sin `client` tiene éxito; ese es el comportamiento de compatibilidad que cambia el nuevo requisito.

Luego avanzar en pasos pequeños y verificables por separado:

1. Reforzar la caracterización del token antes de cambiar su creación: decodificarlo y verificar `sub`, `role`, `iss`, `aud`, `iat` y `exp`; validar la firma RS256 con la clave pública de prueba configurada y registrar la relación actual de 3600 segundos.
2. Cuando se aprueben la regla de aceptación del cliente y la respuesta de error, agregar pruebas de aceptación/rechazo de la solicitud y comprobar que los rechazos no devuelvan un token.
3. Agregar aserciones de propagación y del claim para clientes aceptados, conservando las verificaciones de claims y campos de respuesta existentes.
4. Agregar pruebas deterministas de los límites temporales usando el punto de inyección del reloj de `TokenService`, y pruebas de logs con valores centinela de token/clave que confirmen que no aparecen.
5. Reejecutar la suite HTTP completa de caracterización de login para comprobar que se mantienen los estados, campos, headers y cuerpos de error protegidos.

## Riesgos y trade-offs

- **Podrían rechazarse clientes que antes se aceptaban.** El contrato actual indica que `client` es opcional y se ignora; exigirlo es un cambio deliberado de compatibilidad. Mitigación: aprobar la regla y el error explícitos, y coordinar la actualización de los clientes.
- **Las descripciones de firma discrepan.** El Issue #11 menciona HMAC, pero el repositorio requiere RS256. Conservar RS256 en este cambio; cualquier cambio de algoritmo requiere aprobación separada y migración de consumidores, no una decisión implícita de implementación.
- **La precisión del reloj puede volver frágiles las comparaciones ingenuas.** Comparar con la referencia de emisión capturada en `iat` y la tolerancia de un segundo de la spec; evitar esperas de reloj real en pruebas.
- **Las pruebas de logs pueden pasar por alto rutas alternativas.** Cubrir flujos representativos exitosos y fallidos con valores sensibles centinela; revisar logs del framework y de excepciones si se agregan nuevos mensajes.
- **El requisito de secreto compartido podría no aplicar a RSA.** No agregar un `AUTH_TOKEN_SECRET` sin propósito; documentar la configuración RSA existente y por qué la cláusula de secreto del issue es condicional mientras se conserve ese algoritmo.

## Migración y reversión

No se requiere migración de base de datos ni rotación de claves mientras se conserven RS256 y el contrato actual de respuesta/token. Desplegar el cambio solo después de aprobar el requisito de cliente y su error, y verificar qué consumidores dejarían de funcionar si omiten `client`.

Si la nueva validación de cliente causa fallos de login inesperados, revertir la versión de la aplicación para restaurar el comportamiento anterior que permitía omitirlo. Mantener sin cambios el par de claves RSA y la configuración de issuer/audience para que los tokens sigan siendo verificables durante la reversión. No revertir emitiendo tokens HMAC ni debilitando la verificación de firmas. Si una propuesta futura cambia el algoritmo o el material de claves, definir antes una migración coordinada y su plan de reversión.