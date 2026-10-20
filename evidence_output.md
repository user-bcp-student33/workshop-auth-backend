## Summary
- Ajustar el token de `POST /auth/login` para que contenga los claims mínimos requeridos y refleje el `client` de la solicitud.
- Comprobar la coherencia entre `expiresIn` y la expiración del token, evitar registrar datos sensibles y documentar la decisión sobre la firma.

## Scope
- **Dentro del alcance**
  - Exigir los claims `sub`, `client`, `iat` y `exp`.
  - Obtener y validar `client` desde la solicitud, e incluirlo en el token.
  - Comparar `expiresIn` con `exp - now` usando una tolerancia documentada.
  - Eliminar el secreto por defecto fuera del laboratorio o documentarlo como exclusivo del laboratorio.
  - Evitar que se registren tokens y secretos.
  - Documentar si se conserva HMAC o se adopta una biblioteca JWT.
- **Fuera del alcance**
  - La HU no especifica exclusiones; deben acordarse antes de implementar cambios adicionales.

## Supuestos
- La HU describe el endpoint actual como `POST /auth/login`, con una respuesta que incluye `token`, `expiresIn` y `user`; no confirma expresamente si esos campos deben preservarse sin cambios.
- La HU no define el criterio de validación de `client`, la tolerancia temporal ni el método de firma final; se tratan como decisiones pendientes.

## Decisiones técnicas por tomar (pendientes o resueltas)
- **Pendientes**
  - Elegir entre conservar HMAC o adoptar una biblioteca JWT y documentar la decisión.
  - Definir cómo validar `client`, incluidos los casos ausente o vacío.
  - Definir y documentar la tolerancia para comparar `expiresIn` con `exp - now`.
  - Decidir si se elimina el secreto por defecto fuera del laboratorio o se documenta su uso exclusivo de laboratorio.

## Riesgos y mitigaciones
- **Claims o expiración incoherentes:** probar los claims requeridos y la comparación de `expiresIn` con `exp - now` usando la tolerancia acordada.
- **Exposición de token, secreto u otros valores sensibles en logs:** revisar la instrumentación y aportar evidencia de que esos valores no se registran.
- **Uso del secreto por defecto fuera del laboratorio:** resolver y documentar la política antes de habilitar ese entorno.
- **Validación indefinida de `client`:** acordar las reglas y cubrir sus casos válidos e inválidos antes de implementar.

## Criterios de aceptación (medibles)
- Cada token válido contiene los cuatro claims `sub`, `client`, `iat` y `exp`.
- `expiresIn` coincide con `exp - now` dentro de la tolerancia documentada.
- Hay pruebas para login válido, credenciales inválidas y token expirado.
- En los escenarios probados no se registran tokens, secretos ni valores sensibles.
- La decisión entre HMAC y biblioteca JWT queda documentada.
- La política del secreto por defecto fuera del laboratorio queda resuelta: eliminado o documentado como exclusivo del laboratorio.

## Plan de validación
- **Pruebas unitarias**
  - Verificar presencia de los cuatro claims y la coherencia temporal según la tolerancia acordada.
  - Probar las reglas seleccionadas para validar `client`.
- **Pruebas funcionales**
  - Probar login válido, credenciales inválidas y token expirado.
  - Verificar que el claim `client` refleje el valor validado recibido en la solicitud.
- **Seguridad (comprobaciones mínimas)**
  - Revisar logs de los escenarios de prueba para confirmar que no contienen tokens, secretos ni otros valores sensibles.
  - Comprobar la política elegida para el secreto por defecto fuera del laboratorio.
- **Evidencia requerida (qué adjuntar)**
  - Resultados de pruebas de claims, expiración y errores de autenticación.
  - Documentación de la decisión de firma y de la configuración del secreto, sin incluir valores secretos.
  - Evidencia de revisión de logs redactados.
  - Este documento de intake generado desde HU-01.

## Tareas sugeridas por rol
- **Chapter/PO**
  - Confirmar el significado y los valores permitidos de `client`.
  - Confirmar si deben preservarse los campos actuales de request y response.
  - Revisar los criterios cuando se proponga la tolerancia temporal.
- **Tech Lead**
  - Decidir y documentar HMAC frente a biblioteca JWT.
  - Acordar la tolerancia temporal y la política del secreto por defecto.
  - Revisar que el diseño de claims y logs satisfaga los criterios.
- **Backend**
  - Actualizar la obtención y validación de `client` tras acordar sus reglas.
  - Implementar los claims y la comprobación temporal sin cambiar campos no aprobados.
  - Añadir pruebas de claims, expiración, errores de autenticación y logs sensibles.
- **Frontend**
  - Confirmar qué valor de `client` envía la solicitud de login.
  - Revisar compatibilidad con cualquier requisito de request que se acuerde.
  - Comunicar dependencias de integración antes de cambiar el contrato.
- **QA**
  - Preparar casos de login válido, credenciales inválidas y token expirado.
  - Verificar los claims y la tolerancia temporal acordada.
  - Revisar evidencia de que los logs no exponen valores sensibles.
- **Cyber**
  - Revisar el tratamiento del secreto fuera del laboratorio.
  - Comprobar los puntos de logging de tokens y secretos.
  - Revisar el enfoque de firma y los claims mínimos requeridos.

## Preguntas abiertas
- ¿Qué reglas exactas validan `client`, y qué ocurre si falta o está vacío?
- ¿Qué tolerancia numérica se acepta al comparar `expiresIn` con `exp - now`?
- ¿Se conserva HMAC o se adopta una biblioteca JWT?
- ¿Se elimina el secreto por defecto fuera del laboratorio o se documenta como exclusivo del laboratorio?
- ¿Qué identifica `client` y hay restricciones existentes para el request o response?
- ¿Qué aspectos quedan fuera del alcance de esta historia?
