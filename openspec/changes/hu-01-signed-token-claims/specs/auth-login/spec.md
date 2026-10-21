# Spec Delta

## Purpose

Define the observable login contract for validating the requested client, issuing a signed token with the required identity and lifetime claims, and protecting signing material and tokens from disclosure.

## ADDED Requirements

### Requirement: Login validates the requested client
`POST /auth/login` MUST validate the request's `client` before issuing a token and MUST place that same validated value in the token's `client` claim. A missing or invalid client MUST NOT produce a successful login response or a token. The exact validity rule and public error response for an invalid client require review; no new status code or message is implied here.

#### Scenario: Valid client is reflected in the issued token
- **WHEN** valid credentials and a client accepted by the approved validation rule are submitted to `POST /auth/login`
- **THEN** the response is HTTP 200 and the issued token's `client` claim equals the submitted client

#### Scenario: Missing or invalid client cannot issue a token
- **WHEN** valid credentials are submitted with a missing or client value rejected by the approved validation rule
- **THEN** the response is not a successful login and contains no issued token

### Requirement: Successful login returns a signed token with minimum claims
Every successful login MUST return a signed token containing `sub`, `client`, `iat`, and `exp`. `sub` MUST equal the authenticated username, `client` MUST equal the validated request value, and `iat` and `exp` MUST be Unix timestamps with `exp` later than `iat`. The existing `role`, `iss`, and `aud` claims MUST be preserved. The signing algorithm and library remain an explicit review decision because Issue #11 describes HMAC while the current contract and consumers require JWT RS256.

#### Scenario: Successful token contains the required and preserved claims
- **WHEN** a login succeeds
- **THEN** the signed token contains `sub`, `client`, `iat`, `exp`, `role`, `iss`, and `aud` with the values defined by this requirement

#### Scenario: Expired token is rejected by token verification
- **WHEN** a token's `exp` is at or before the verifier's current time
- **THEN** the configured token verification path rejects the token

### Requirement: Successful response lifetime matches token expiration
The successful response MUST retain the integer `expiresIn` field with its current value of 3600 seconds. For this comparison, `now` is the instant captured when the token is issued and represented by `iat` after truncation to Unix seconds; `expiresIn` MUST differ from `exp - now` by no more than one second due to that truncation. This time reference and tolerance MUST be documented with the implementation evidence and used by the expiration test.

#### Scenario: Response lifetime agrees with the signed token
- **WHEN** a successful response and its token are inspected using the documented issuance-time reference
- **THEN** `expiresIn` is the JSON integer 3600 and its difference from `exp - now` is at most one second

### Requirement: Login preserves established response and error contracts
Except for behavior required to validate `client` and include its claim, login MUST preserve the current success response fields and existing error statuses, fields, and messages. HTTP errors produced before the login method MUST remain unchanged where applicable. This change MUST NOT alter endpoints outside `POST /auth/login`.

#### Scenario: Successful response retains existing fields
- **WHEN** a login succeeds
- **THEN** the response retains `token`, integer `expiresIn`, `user.username`, `user.displayName`, `user.role`, and the fixed `debug` value `authenticated-with-legacy-flow`

#### Scenario: Existing login validation and credential failures remain stable
- **WHEN** the request is null, username or password is missing or blank, credentials do not match, or an internal error occurs
- **THEN** the existing respective HTTP status and response fields/messages remain: null request 400 `Request is null`; missing username 400 `Username is required`; missing password 400 `Password is required`; unmatched credentials 401 `Invalid user <username> or password`; internal error 500 with `message: Internal server error` and `exception: InternalServerError`

#### Scenario: Existing pre-method HTTP failures remain stable
- **WHEN** the login route receives malformed JSON, an incompatible field type, an unsupported method, an incompatible Accept header, or an unsupported Content-Type
- **THEN** malformed JSON remains 400 with an empty body; an incompatible field type remains 400 with the runtime mapper response (for `{"username":{},"password":"x"}`: `{"objectName":"LoginRequest","attributeName":"username","line":1,"column":13,"value":null}`); an unsupported method remains 405 with an empty body; an incompatible Accept header remains 406 with an empty body; and an unsupported Content-Type remains 415 with an empty body

### Requirement: Tokens and signing secrets are excluded from logs
Authentication logs MUST NOT contain issued tokens or signing secrets. If the approved signing strategy uses a shared secret, it MUST be configured through `AUTH_TOKEN_SECRET` without a default outside the laboratory; any retained laboratory-only default MUST be explicitly documented as laboratory-exclusive. The signing algorithm and the applicability of this shared-secret configuration remain subject to the HMAC-versus-RS256 review decision.

#### Scenario: Login diagnostics redact token and signing material
- **WHEN** a successful or failed login produces authentication logs
- **THEN** the logs contain neither the issued token nor signing secret values

#### Scenario: Shared-secret configuration has no production default
- **WHEN** the approved signing strategy uses `AUTH_TOKEN_SECRET` outside the laboratory
- **THEN** the secret is supplied by configuration and no default secret is used; any laboratory-only default is identified as exclusive to laboratory use in the Skills documentation
