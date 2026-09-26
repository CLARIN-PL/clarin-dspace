# CLARIN-PL Shibboleth SP

This directory contains the non-secret production configuration for the
CLARIN-PL Shibboleth Service Provider. The configuration was adapted from the
2025 server backup and updated for DSpace 7 and the maintained CLARIN Discovery
Service.

The private key and certificate are never stored in Git. Compose reads their
paths from `SHIBBOLETH_KEY_PATH` and `SHIBBOLETH_CERT_PATH`, mounts them
read-only, and the container copies them to files owned by the Shibboleth daemon
with restricted permissions.

The historical entity ID is intentionally preserved:

`http://www.clarin-pl.eu/shibboleth`

Changing it requires coordination with the CLARIN Service Provider Federation.
For a production URL such as `https://clarin-pl.eu`, the relevant endpoints
are:

- metadata: `https://clarin-pl.eu/shibboleth/Shibboleth.sso/Metadata`
- ACS: `https://clarin-pl.eu/shibboleth/Shibboleth.sso/SAML2/POST`
- login initiator: `https://clarin-pl.eu/shibboleth/Shibboleth.sso/Login`

For HTTPS production deployments set:

- `SHIBBOLETH_HANDLER_SSL=true`
- `SHIBBOLETH_COOKIE_PROPS=; path=/; HttpOnly; secure; SameSite=None`
- `SHIBBOLETH_SERVER_NAME` to the public host and its external HTTPS port
  (`clarin-pl.eu:443` when TLS terminates at an upstream reverse proxy)

The local HTTP profile uses `handlerSSL=false` and a non-Secure cookie so the
redirect chain can be inspected, but an institutional login can only complete
when the public callback URLs are registered by the federation.
