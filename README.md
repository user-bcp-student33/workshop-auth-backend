# Workshop Auth Backend — Java Quarkus

> **Laboratorio intencionalmente vulnerable.** Ejecutar únicamente en local, repositorios privados y ambientes aislados. No desplegar en Internet, CERT o PROD.

Backend Quarkus que autentica usuarios de laboratorio almacenados en SQLite y entrega un token consumido por el frontend Angular y la API Python.

## Ejecución

Requisitos: JDK 21 y Maven 3.9+.

```bash
mvn quarkus:dev
```

Servicio: `http://localhost:8080`

Credenciales locales:

- `student` / `Workshop123!`
- `trainer` / `Trainer123!`

## Endpoints

- `POST /auth/login`
- `GET /q/health`

## Passwords y migracion H-02

El login conserva el contrato HTTP. Las credenciales persistidas usan
PBKDF2-HMAC-SHA256 (600000 iteraciones, salt aleatorio de 16 bytes, clave de
32 bytes). La base usa `PRAGMA user_version=1`; una base legacy version 0 se
migra en una transaccion antes de aceptar logins. Un segundo arranque no
rehashea. Un fallo de migracion aborta el arranque; no hay fallback a passwords
legibles. Las pruebas solo migran fixtures, no la base normal.

Antes de usar una base existente: parar el servicio, realizar y verificar un
respaldo local protegido (incluidos WAL/SHM si existen), restringir permisos y
planificar restauracion. Tras comprobar la migracion, retirar copias legibles
segun la politica autorizada. `secure_delete` no garantiza borrar restos de
backups, snapshots o almacenamiento fisico. No registrar passwords ni hashes.

## Tokens H-03

El formato Base64 legacy se sustituyo por JWT RS256 sin compatibilidad dual.
`sub` es username y `role` conserva el rol; `iss` y `aud` son obligatorios y
configurables. `iat`/`exp` usan segundos Unix y TTL 3600.
Los consumidores deben verificar firma, issuer, audience y expiracion;
no hay endpoint nuevo de verificacion. Coordinar esta adopcion antes de usar
el sistema fuera del laboratorio.

Se agregaron `quarkus-smallrye-jwt-build` y `quarkus-smallrye-jwt`, administrados
por el BOM Quarkus existente: el primero construye/firma JOSE mediante una
biblioteca mantenida y el segundo aporta el stack JWT compatible para
verificacion, evitando criptografia de token casera. En H-03 no se cambio la
version de Quarkus; H-06 la actualizo como se detalla abajo. No se redujeron gates.

## Dependencias verificadas H-06

Quarkus BOM/plugin usan **3.40.1.1**, compatible con el JDK 21 probado.
Commons Text ya no fija 1.9: el BOM resuelve **1.15.0** y mantiene
`escapeHtml4`. El mismo BOM coordina Netty **4.1.139.Final** y Jackson
**2.21.7**, sin overrides transitivos manuales.

Se contrastaron coordenadas Maven publicas contra OSV y los advisories
referenciados: 11 paquetes/49 avisos coincidian antes; ninguno de los 236
paquetes resueltos coincide en la consulta posterior. Esto no demuestra ausencia
de vulnerabilidades desconocidas ni sustituye CodeQL/Dependency Review.
Para CVE-2022-42889, la ruta propia usa `escapeHtml4`, no `StringSubstitutor`;
para CVE-2026-50559 no hay politica HTTP por paths configurada. No se afirmo
explotabilidad local de esos casos; se actualizaron igualmente las dependencias.
Resultados, fuentes y comandos: [docs/TESTING.md](docs/TESTING.md).

## Configuracion obligatoria H-04

Antes de ejecutar `mvn quarkus:dev` o el artefacto empaquetado, suministrar:

| Variable de entorno | Contenido |
|---|---|
| `AUTH_TOKEN_PRIVATE_KEY` | Ruta a clave privada RSA PKCS#8 PEM, minimo 2048 bits |
| `AUTH_TOKEN_PUBLIC_KEY` | Ruta a clave publica RSA X.509 PEM de la misma pareja |
| `AUTH_TOKEN_ISSUER` | Issuer que los consumidores deben validar |
| `AUTH_TOKEN_AUDIENCE` | Audience acordado con los consumidores |
| `AUTH_CORS_ORIGINS` | Origenes exactos separados por comas, con esquema/host/puerto; sin ruta, wildcard, credenciales ni query |

Ninguna de estas variables tiene un secreto o una politica permisiva por
defecto. El servicio valida configuracion y pareja de claves al arrancar.
El usuario suministra las claves por un canal seguro, fuera del repositorio;
restringir permisos de la clave privada. No escribir valores privados en
`application.properties`, logs o commits. Para rotar: coordinar la clave
publica nueva con consumidores y el tratamiento de tokens vigentes antes de
cambiar la clave emisora y reiniciar; no se implemento rotacion automatica.

Las pruebas generan claves efimeras bajo `target` y las eliminan al terminar.
Para reproducir la suite sin ocupar 8081:

```powershell
$env:AUTH_DB_URL = 'jdbc:sqlite:./target/local-test.db'
$env:QUARKUS_HTTP_TEST_PORT = '0'
mvn -B verify
```

Usar JDK 21. La suite provee solo su configuracion ficticia, sin defaults para
produccion. Consultar [docs/TESTING.md](docs/TESTING.md) para evidencias y
cobertura por item.

## Propósito de la rama `main`

La rama contiene deuda técnica y de seguridad deliberada: SQL concatenado, contraseñas en texto plano, token reversible, secreto hardcodeado, CORS permisivo, método largo, manejo genérico de excepciones y dependencias con CVEs.
