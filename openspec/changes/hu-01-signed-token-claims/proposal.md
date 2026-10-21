# Proposal

## Why

GitHub Issue #11 (`HU-01 - Token firmado con expiracion y claims minimos`) pide que el token identifique el cliente que lo solicito, que su vigencia anunciada sea coherente con `exp` y que no se expongan secretos ni tokens en logs. El contrato local ya emite JWT RS256 con expiracion de 3600 segundos, pero ignora el `client` recibido; ademas, la descripcion HMAC del issue entra en conflicto con el algoritmo RS256 y los validadores de consumidores documentados localmente.

## What Changes

- **BREAKING:** validar `client` durante `POST /auth/login` y reflejar el valor validado en el claim `client`; no emitir un token sin ese claim. El request local actualmente permite omitir `client`, por lo que cambia el comportamiento de esas solicitudes.
- Exigir en todo token emitido los claims `sub`, `client`, `iat` y `exp`, manteniendo los claims locales `role`, `iss` y `aud` mientras no exista una decision aprobada que indique lo contrario.
- Mantener `expiresIn` en la respuesta y hacer verificable su coherencia con `exp - now`, documentando el instante de referencia y la tolerancia antes de fijar la asercion temporal exacta. No se propone cambiar campos ni tipos del response.
- Evitar que logs incluyan el token o material secreto. Documentar el secreto como exclusivo del laboratorio si se conserva un valor por defecto para ese entorno; fuera de laboratorio no debe existir un secreto por defecto.
- Preservar los campos, estados y mensajes actuales de login, credenciales y errores internos, excepto el rechazo que resulte necesario para validar `client`. Preservar tambien los rechazos HTTP previos al metodo cuando apliquen.
- Decision aprobada el 2026-10-21: conservar JWT RS256 y la biblioteca existente, sin agregar `AUTH_TOKEN_SECRET`. El texto HMAC del issue se reconoce como discrepancia historica, no como instruccion para cambiar el contrato.

## Capabilities

### New Capabilities
- `auth-login`: comportamiento verificable del login, validacion de `client`, claims y vigencia del token, y proteccion de datos sensibles en logs.

### Modified Capabilities
- Ninguna. El inventario local de OpenSpec no contiene specs existentes.

## Impact

Afecta el contrato de `POST /auth/login`, la validacion del request, la emision de tokens, la configuracion del material de firma y las pruebas de integracion/seguridad. Quedan dentro de alcance las pruebas observables de login valido, credenciales invalidas, expiracion, cliente invalido y ausencia de token/secreto en logs. La evidencia de implementacion esperada incluye el documento de Skills, pruebas, configuracion de `AUTH_TOKEN_SECRET` cuando aplique y evidencia de logs redactados.

Fuera de alcance: otros endpoints (incluido `/q/health`), cambios de base de datos, migracion de consumidores y cambios no relacionados del formato de errores.

Decisiones aprobadas el 2026-10-21: mantener RS256 sin secreto compartido; exigir `client` sin trim con formato `^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$`; responder 400 `{"message":"Invalid client"}` si falta, es nulo, vacio o invalido. Orden de validacion: cuerpo, username, password, client y credenciales. El JWT conserva literalmente el cliente aceptado. La spec define `now` como el instante de emision representado por `iat` y tolerancia maxima de un segundo; `expiresIn` sigue siendo el entero 3600.
