# Production container support

This directory contains the backend-specific runtime configuration used by the
full CLARIN-PL stack in the sibling `dspace-angular` repository:

```bash
cd ../dspace-angular/docker/production
./manage.sh init
./manage.sh up
```

The backend image is built from this checkout. At startup it waits for
PostgreSQL, applies Flyway migrations and then starts Tomcat as the unprivileged
`dspace` user (UID 1100).

Do not add credentials to `local.cfg`. Runtime URLs, database credentials, JWT
keys and the CLARIN personal-token encryption key come from the ignored `.env`
file managed by the frontend repository's deployment scripts.
