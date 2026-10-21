# Tareas

## 1. Caracterizar el contrato actual

- [x] 1.1 Completar las pruebas HTTP de `POST /auth/login` para registrar la respuesta exitosa, los errores existentes y que actualmente se acepta una solicitud sin `client`; verificar que `AuthResourceTest` pase antes de cambiar comportamiento funcional.
- [x] 1.2 Completar la caracterización del JWT actual comprobando firma RS256, claims `sub`, `role`, `iss`, `aud`, `iat`, `exp` y vigencia de 3600 segundos con las claves de prueba; verificar las pruebas de tokens antes de modificar su creación.

Evidencia de 1.1 y 1.2: [docs/TESTING.md](../../../docs/TESTING.md#hu-01-caracterizacion-previa-tareas-11-y-12),
2026-10-21: 35 pruebas aprobadas, sin cambios funcionales.

## 2. Cerrar el contrato de `client`

- [x] 2.1 Obtener aprobación para la regla que determina qué valores de `client` son válidos y para la respuesta HTTP ante un valor ausente o inválido; registrar la decisión en la spec antes de implementar la validación y verificar que los casos y la respuesta esperada queden explícitos.

Aprobacion humana 2026-10-21: formato, error y precedencia registrados en
proposal/spec/design; `openspec validate hu-01-signed-token-claims --strict` aprobado.

## 3. Propagar `client` y completar los claims

- [x] 3.1 Aplicar en el flujo de login la regla aprobada para `client` y propagar el valor aceptado desde `AuthResource` por `AuthService`; verificar con pruebas que los casos rechazados no emitan token y devuelvan la respuesta aprobada.
- [x] 3.2 Incluir el `client` aceptado en el token sin eliminar los claims existentes; verificar que el token contiene `sub`, `client`, `iat`, `exp`, `role`, `iss` y `aud`, y que conserva firma RS256 y compatibilidad con el contrato actual.

Evidencia de implementacion: [docs/TESTING.md](../../../docs/TESTING.md#hu-01-implementacion-y-evidencia).
3.1: 15 fallos esperados antes del cambio; suite enfocada de 58 pruebas aprobada;
tipos/limites corregidos y verify final de 117 pruebas aprobado.
3.2: clientes exactos de 1/64 caracteres, claims HTTP y verificador RS256
aprobados, incluyendo payload alterado, clave ajena y limite de expiracion.

## 4. Verificar vigencia y redacción de logs

- [x] 4.1 Verificar con pruebas deterministas que la respuesta conserva `expiresIn` entero igual a 3600 y que coincide con `exp - now` dentro de un segundo según la referencia de emisión definida en la spec.
- [x] 4.2 Verificar mediante pruebas de logs que los flujos exitosos y fallidos no registren tokens ni material privado de firma; conservar la configuración RSA actual y no agregar `AUTH_TOKEN_SECRET` mientras no se apruebe HMAC.

4.1: `AuthServiceTest.responseLifetimeMatchesSignedExpirationAtIssuance`
aprobado con reloj fijo fraccionario, firma real y `exp - now = 3600`;
referencia y tolerancia documentadas en docs/TESTING.md.
4.2: `AuthResourceTest.logsSuccessAndFailureWithoutTokenOrPrivateMaterial`
aprobado: 200/400/401 sin datos registrados y 500 saneado pese a contener
token, PEM y clave privada Base64 en mensaje/causa/suprimidas de prueba.
Configuracion RSA y dependencias sin cambios; sin secreto compartido.

## 5. Validación de integración

- [x] 5.1 Ejecutar la suite de autenticación y verificar que se mantienen los campos y errores protegidos de `POST /auth/login`, además de los casos de claims, expiración, cliente y logs definidos para este cambio.

5.1: `mvn -B -Djacoco.destFile=target/hu01-verify.exec -Djacoco.dataFile=target/hu01-verify.exec -Djacoco.append=false -l target/hu01-verify.log verify`
aprobado: 117 pruebas, 0 fallos/errores/omitidas; gate JaCoCo >=85% por clase,
minimo 91,84% (TokenService). Comandos, resultados, riesgos y gates remotos
pendientes en [docs/TESTING.md](../../../docs/TESTING.md#hu-01-implementacion-y-evidencia).
