# Contrato HTTP actual

Documento generado desde el codigo existente; describe el comportamiento que
debe preservarse, no una propuesta de cambio. El seguimiento de hallazgos vive
exclusivamente en [HALLAZGOS.md](../HALLAZGOS.md).

## POST /auth/login

Fuente: [AuthResource.java:15](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L15)
y [AuthResource.java:22](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L22).

- Base local por defecto: `http://localhost:8080`.
- Entrada: `Content-Type: application/json`.
- Salida del resource: `application/json`.
- No requiere token previo.

### Request

```json
{
  "username": "usuario",
  "password": "password",
  "client": "angular-workshop"
}
```

`username` y `password` son strings obligatorios, no nulos ni vacios tras
`trim()`. Los valores originales, sin recortar, se usan en la consulta.
`client` es un string opcional del DTO y no interviene en la autenticacion.
Jackson esta configurado para ignorar propiedades desconocidas.

Fuentes: [LoginRequest.java:3](../src/main/java/com/bcp/workshop/auth/LoginRequest.java#L3),
[AuthResource.java:26](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L26)
y [application.properties:8](../src/main/resources/application.properties#L8).

### Respuesta 200

```json
{
  "token": "<token generado>",
  "expiresIn": 3600,
  "user": {
    "username": "<username almacenado>",
    "displayName": "<nombre almacenado con escape HTML4>",
    "role": "<rol almacenado>"
  },
  "debug": "authenticated-with-legacy-flow"
}
```

`token` es string y `expiresIn` es un numero de segundos. Los valores de
`user` proceden del registro encontrado. No se garantiza el orden de las
propiedades JSON.

El formato de token implementado actualmente es Base64 UTF-8 de
`username|role|expiry|SECRET`, donde `expiry` es un timestamp Unix en segundos,
calculado como el instante de emision mas 3600 segundos. Contiene un secreto
de laboratorio y no una firma. Esta descripcion no afirma como lo interpretan
otros componentes.

Fuentes: [AuthResource.java:39](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L39)
y [LegacyTokenUtil.java:12](../src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L12).

### Respuestas de validacion y error

| HTTP | Condicion dentro del resource | Cuerpo JSON |
|---|---|---|
| 400 | Request nulo | `{"message":"Request is null"}` |
| 400 | Username nulo, vacio o solo blancos | `{"message":"Username is required"}` |
| 400 | Password nulo, vacio o solo blancos | `{"message":"Password is required"}` |
| 401 | El repositorio devuelve `null` | `{"message":"Invalid user <username recibido> or password"}` |
| 500 | Una excepcion alcanza el catch del resource | `{"message":"<mensaje de la excepcion o Unknown error si es nulo>","exception":"<nombre completo de la clase>"}` |

Las validaciones se evaluan en el orden de la tabla. El 401 tambien puede
ocurrir ante un error de consulta, ya que el repositorio captura la excepcion
y devuelve `null`. El 500 no es una garantia de respuesta para todo error de
base de datos.

Los errores de deserializacion, JSON malformado o negociacion HTTP previos a
la llamada del metodo los gestiona Quarkus; el codigo revisado no define
un cuerpo personalizado para ellos.

Fuentes: [AuthResource.java:26](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L26),
[AuthResource.java:54](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L54)
y [LegacyDatabase.java:39](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L39).

## GET /q/health

Endpoint de salud anunciado en [README.md](../README.md), provisto por
`quarkus-smallrye-health`
([pom.xml:40](../pom.xml#L40)). No existe un resource de salud personalizado
en el codigo fuente revisado. Este documento no fija un cuerpo de respuesta
de la extension que no se haya verificado en ejecucion.
