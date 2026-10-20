# Plan propuesto: H-05, H-06, H-07 y cobertura automatica

## Summary

Plan del 2026-10-19, limitado a este backend. **Implementacion aprobada por
el usuario el 2026-10-19**, en orden H-05/H-06/H-07/P-01, con cierre tras
pruebas verdes por item. Se aprobaron los valores genericos propuestos para
500 y el enforcement del 85%. H-02/H-03/H-04 tuvieron aprobacion previa y estan resueltos;
su evidencia permanece en [TESTING.md](docs/TESTING.md) y [HALLAZGOS.md](HALLAZGOS.md).
El seguimiento de esta ejecucion permanece exclusivamente en HALLAZGOS.md.

## Scope

- Dentro: alcances independientes siguientes; pruebas primero, cambio despues,
  diff y verificacion propios antes de pasar al siguiente.
- Fuera: reimplementar H-01/H-02/H-03/H-04, otros componentes, migraciones reales,
  nuevos endpoints, rate limiting o cambios de health no solicitados.

## Item: H-05 - Errores: respuesta expone detalles internos

- **Evidencia/problema:** [AuthResource.java:33](src/main/java/com/bcp/workshop/auth/AuthResource.java#L33)
  devuelve mensaje/clase de excepcion sin registrar el detalle en ese catch;
  [AuthFailureTest.java:26](src/test/java/com/bcp/workshop/auth/AuthFailureTest.java#L26)
  caracteriza esa divulgacion. Tipo: Vulnerabilidad; prioridad media del backlog.
- **Alcance/cambio:** sanear exclusivamente el 500 del resource, conservando
  HTTP 500 y claves string `message`/`exception`, como selecciono el usuario.
  Proponer valores constantes `Internal server error` / `InternalServerError`
  (este ultimo es un codigo publico, no una clase Java). Registrar diagnostico
  y stacktrace solo en servidor, con saneamiento para no volcar credenciales,
  hashes, tokens, claves ni mensajes sensibles de excepciones.
- **Archivos:** [AuthResource.java](src/main/java/com/bcp/workshop/auth/AuthResource.java),
  [AuthFailureTest.java](src/test/java/com/bcp/workshop/auth/AuthFailureTest.java),
  [ENDPOINTS.md](docs/ENDPOINTS.md), [TESTING.md](docs/TESTING.md)
  y fila H-05 de [HALLAZGOS.md](HALLAZGOS.md).
- **Prueba antes:** ejecutar caracterizacion 500 con mensaje y mensaje nulo;
  agregar regresion HTTP con excepcion que contiene un marcador sensible:
  cuerpo exacto generico, sin mensaje original ni nombre de clase. Debe fallar
  antes. Capturar logs con herramientas de prueba existentes y exigir diagnostico
  util sin marcador sensible. Despues, repetir ambos escenarios y suite HTTP.
- **Limite contractual:** H-05 pide precisamente retirar detalles del error;
  actualizar solo ejemplo/semantica del 500 y su nota de seguridad. Preservar
  200/400/401, errores del framework, campos exitosos, JWT, CORS y health.

## Item: H-06 - SCA: Quarkus 3.27.4 y Commons Text 1.9

- **Evidencia/problema:** versiones en [pom.xml:13](pom.xml#L13) y
  [pom.xml:58](pom.xml#L58); uso observado:
  [AuthService.java:45](src/main/java/com/bcp/workshop/auth/AuthService.java#L45),
  `escapeHtml4`. La version por si sola no prueba explotabilidad.
- **Alcance/cambio:** obtener arbol resuelto y contrastar advisories oficiales/
  alertas accesibles, rangos afectados y ruta realmente usada, incluidas
  transitivas. Elegir versiones corregidas compatibles con Java 21; actualizar
  Quarkus mediante su BOM/plugin coordinados, sin overrides arbitrarios.
  Conservar escape HTML4; no sustituirlo manualmente ni silenciar alertas.
- **Archivos:** [pom.xml](pom.xml), [README.md](README.md),
  [TESTING.md](docs/TESTING.md) y fila H-06 de [HALLAZGOS.md](HALLAZGOS.md).
  Pruebas: [AuthServiceTest.java](src/test/java/com/bcp/workshop/auth/AuthServiceTest.java)
  y [AuthResourceTest.java](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java).
  No modificar workflows ni contrato para acomodar una actualizacion.
- **Prueba antes:** caracterizar nombre con caracteres HTML4, comillas,
  ampersand y Unicode, mas matriz HTTP/JWT/CORS/health. Guardar arbol y
  evidencia SCA inicial; si hay CVE aplicable, demostrar version afectada antes
  y corregida despues sin ejecutar exploits. Repetir contrato y analisis tras
  actualizar. Si no hay CVE aplicable, documentar investigacion, no inventar rojo.
- **Cierre condicionado:** registrar identificador, fuente, version corregida
  y aplicabilidad por aviso. Sin acceso/evidencia suficiente, mantener Pendiente;
  no declarar toda dependencia segura ni seleccionar hoy una version a ciegas.

## Item: H-07 - Clean code: separacion de responsabilidades

- **Evidencia/problema actual:** [AuthResource.java:22](src/main/java/com/bcp/workshop/auth/AuthResource.java#L22)
  ya delega a [AuthService.java:23](src/main/java/com/bcp/workshop/auth/AuthService.java#L23);
  SQL y hash estan separados y la salida usa
  [LoginResponse.java](src/main/java/com/bcp/workshop/auth/LoginResponse.java).
  La mezcla original se redujo en H-02: no rehacerla ni presentar ese cambio
  previo como implementacion nueva. Restan cierre verificable y literales HTTP.
- **Alcance/cambio:** verificar Resource HTTP -> Service -> Repository/seguridad,
  DTO propio, metodos con responsabilidad unica y guard clauses. Reemplazar
  400/401/500 del resource por `Response.Status` nombrados; conservar textos
  contractuales y reutilizar helpers existentes, sin nueva jerarquia/abstraccion.
  H-05 es dueno del saneamiento 500; este item no lo vuelve a implementar.
- **Archivos:** [AuthResource.java](src/main/java/com/bcp/workshop/auth/AuthResource.java),
  [AuthServiceTest.java](src/test/java/com/bcp/workshop/auth/AuthServiceTest.java),
  [AuthResourceTest.java](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java),
  [TESTING.md](docs/TESTING.md) y fila H-07 de [HALLAZGOS.md](HALLAZGOS.md).
- **Prueba antes:** ampliar pruebas de servicio con dobles para usuario ausente,
  password incorrecto, respuesta publica y ausencia de emision en rechazos;
  ejecutar matriz HTTP vigente tras H-05/H-06. Repetirla sin diferencias despues.
  Revisar dependencias de capas en el diff: no exigir un rojo artificial a un
  refactor de comportamiento preservado.

## Item: P-01 - Propuesta adicional: enforcement JaCoCo del 85%

- **Identidad/evidencia:** candidato nuevo al redactar el plan; tras aprobar
  implementacion, registrado como H-08 antes de ejecutarlo. Tipo: deuda de validacion.
  [pom.xml:89](pom.xml#L89) configura agente/reporte, sin goal `check`;
  [ci.yml:21](.github/workflows/ci.yml#L21) ejecuta `verify`. El reporte por si
  solo no hace fallar el build bajo el umbral de [REGLAS.md](REGLAS.md).
- **Alcance/cambio:** agregar `jacoco:check` en `verify`, despues del reporte,
  limite LINE/COVEREDRATIO >= 0.85 por clase productiva, sin exclusiones para
  inflar cobertura. Aplicarlo a todas las clases es mas estricto que medir solo
  modificadas y evita introducir calculo diferencial/herramientas nuevas.
- **Archivos:** [pom.xml](pom.xml), [TESTING.md](docs/TESTING.md).
  Tras aprobar implementacion, registrar una fila nueva en
  [HALLAZGOS.md](HALLAZGOS.md), con ID libre, antes de ejecutar el item.
- **Prueba antes:** con datos JaCoCo nuevos bajo `target`, ejecutar solo una
  seleccion reducida de pruebas y `verify`: hoy no rechaza cobertura insuficiente.
  Tras el cambio, esa misma seleccion debe fallar por el limite, no por tests.
  Luego suite completa con otro archivo nuevo debe pasar; conservar ambos logs
  y CSV/XML. No cambiar fuentes/tests para falsear la medicion.

## Supuestos

- Java 21/Maven existentes, SQLite y claves ficticias exclusivamente bajo `target`;
  ninguna dependencia nueva presupuestada. En la fase documental no se ejecutaron
  pruebas; las ejecuciones posteriores constan en [TESTING.md](docs/TESTING.md).

## Decisiones tecnicas por tomar (pendientes o resueltas)

- Resueltas: aprobacion de implementacion y textos del 500 registrada arriba;
  incluir enforcement y dejar SQL->401 fuera. Tras investigar H-06 se selecciono
  BOM/plugin Quarkus 3.40.1.1 y Commons Text 1.15.0 gestionado por BOM;
  fuentes y validacion en [TESTING.md](docs/TESTING.md).

## Riesgos y mitigaciones

- Clientes que dependan del detalle 500: advertir cambio limitado H-05 y
  caracterizar forma/tipos, sin inspeccionar consumidores externos.
- Actualizacion altera errores del framework/JWT: suite completa y comparacion
  contractual; detenerse ante incompatibilidad, no adaptar tests para ocultarla.

## Criterios de aceptacion (medibles)

- Cada item tiene diff propio y evidencia antes/despues; no avanzar sin verde.
- Cero mensajes/clases internos en 500; diagnostico seguro solo en servidor.
- >=85% de lineas por clase modificada, ramas informadas aparte; P-01 demuestra
  rechazo bajo umbral. CodeQL/Dependency Review intactos, sin excepciones nuevas.

## Plan de validacion

- **Unitarias:** pruebas por item indicadas arriba, primero y con fixtures aislados.
- **Funcionales:** matriz de ambos endpoints y JWT/CORS; solo H-05 cambia contrato.
- **Seguridad:** no SQL concatenado, secretos/logs sensibles ni alertas silenciadas.
- **Evidencia:** desde raiz, JDK 21, `QUARKUS_HTTP_TEST_PORT=0` y
  `AUTH_DB_URL=jdbc:sqlite:./target/plan-validation.db`; selectores `mvn -B -Dtest=... test`,
  despues `mvn -B verify` con archivo JaCoCo nuevo por corrida. Registrar comandos
  completos, salidas, fallos/pendientes, diffs y reportes en [TESTING.md](docs/TESTING.md).
  Estados exclusivamente en [HALLAZGOS.md](HALLAZGOS.md).

## Tareas sugeridas por rol

- **Chapter/PO:** aprobar alcances y excepcion contractual H-05.
- **Tech Lead:** revisar contrato 500; decidir versiones con evidencia; revisar capas/gates.
- **Backend:** escribir caracterizacion; implementar por item; capturar diffs y logs.
- **QA:** preparar fixtures; comparar matriz HTTP; verificar cobertura y rojo/verde.
- **Frontend:** solo coordinar 500; no leer/modificar otros componentes.
- **Cyber:** contrastar advisories y revisar saneamiento de diagnosticos.

## Preguntas abiertas

- SQL->401: [LegacyDatabase.java:54](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L54)
  devuelve `null` ante fallo SQL; cambiarlo alteraria el contrato explicitamente
  documentado. Se deja fuera por decision del usuario, sin corregirlo en H-05/H-07.
- Que advisories/alertas vigentes aplican a dependencias resueltas? H-06 debe
  responderlo con fuentes; esta inspeccion no es una auditoria exhaustiva.

Documentos consultados: [ENDPOINTS.md](docs/ENDPOINTS.md),
[GOOD_PRACTICES.md](docs/GOOD_PRACTICES.md), [REGLAS.md](REGLAS.md),
[HALLAZGOS.md](HALLAZGOS.md), [RETO.md](RETO.md), [README.md](README.md)
y plan anterior; contrastados con codigo, pruebas, POM y workflows enlazados.
