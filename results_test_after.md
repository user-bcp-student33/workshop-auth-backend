# Resultados de pruebas despues de H-01

- Fecha de finalizacion: 2026-10-19 15:57:59 -05:00.
- Ambito: solo `workshop-auth-backend`.
- Resultado: **BUILD SUCCESS**, codigo de salida 0.
- Tiempo total de Maven: 44.745 segundos.
- Entorno: Windows, Maven 3.10.0, Eclipse Adoptium JDK 21.0.12.1.

## Comando ejecutado

Desde la raiz del backend, con variables limitadas al proceso de ejecucion:

```powershell
$env:JAVA_HOME = Split-Path (Split-Path (Get-Command java).Source)
$env:AUTH_DB_URL = 'jdbc:sqlite:./target/results-test-after.db'
mvn -B "-Dquarkus.http.test-port=0" test *> target\results-test-after.log
```

Se uso un puerto HTTP dinamico para evitar conflictos con servicios existentes.
La base SQLite HTTP se aislo bajo `target`; las pruebas unitarias del
repositorio crean y eliminan su propia base temporal.

## Resultados

| Clase | Tipo | Ejecutadas | Fallos | Errores | Omitidas | Tiempo |
|---|---|---:|---:|---:|---:|---:|
| AuthResourceTest | Caracterizacion HTTP/integracion Quarkus y llamadas directas al resource | 15 | 0 | 0 | 0 | 11.25 s |
| LegacyDatabaseTest | Repositorio contra SQLite temporal: caracterizacion y correccion H-01 | 15 | 0 | 0 | 0 | 0.958 s |
| **Total** | | **30** | **0** | **0** | **0** | |

La ejecucion incluye ambas clases: no todas las pruebas son unitarias puras;
`AuthResourceTest` arranca Quarkus y ejercita HTTP.

Se verificaron usuarios y roles de laboratorio, credenciales invalidas,
espacios significativos, respuestas 200/400/401/500, campos y expiracion del
token, inicializacion repetida, errores controlados de persistencia,
tratamiento literal de apostrofes y rechazo de entradas SQL manipuladas.

## Observaciones

- Las pruebas de fallo controlado de SQLite imprimen excepciones esperadas
  por el manejo actual del repositorio. Surefire confirma **0 errores**;
  esas trazas no representan pruebas fallidas.
- JaCoCo permanecio habilitado. Este comando ejecuta `test`, no `verify`:
  no genera un nuevo reporte de cobertura y este documento no atribuye
  resultados de cobertura de ejecuciones anteriores a esta corrida.
- No se modificaron codigo, contrato HTTP, dependencias ni quality gates
  para esta ejecucion. El seguimiento de hallazgos sigue exclusivamente en
  [HALLAZGOS.md](HALLAZGOS.md).

## Evidencias

- [Salida completa de Maven](target/results-test-after.log).
- [Informe AuthResourceTest](target/surefire-reports/com.bcp.workshop.auth.AuthResourceTest.txt).
- [Informe LegacyDatabaseTest](target/surefire-reports/com.bcp.workshop.auth.LegacyDatabaseTest.txt).

Los archivos bajo `target` son resultados locales regenerables; los informes
Surefire se sobrescriben en futuras ejecuciones. Este documento conserva el
resumen de esta corrida.
