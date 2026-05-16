# ConnectSphere Backend Deployment on Render

This repo now includes [`render.yaml`](./render.yaml) to deploy backend services individually on Render.

## Important

- `api-gateway` is deployed as a public Web Service.
- All other backend services are deployed as Private Services.
- This setup uses private networking, so **do not use Free instances** for these services.

## Manual Steps

1. Push this branch (with `render.yaml`) to GitHub.
2. In Render dashboard, create **New Blueprint Instance** from this repo.
3. When prompted for `sync: false` environment variables, fill values as described below.
4. Before first traffic, create these supporting services in the same Render region:
   - `redis` (private service or key-value compatible endpoint)
   - `rabbitmq` (private service, port `5672`)
   - `elasticsearch` (private service, port `9200`)
5. After deployment completes, open `api-gateway` public URL and test:
   - `/swagger-ui.html`
   - `/api/v1/auth/...`
   - `/api/v1/posts/...`

## Values to Provide During Blueprint Creation

Use one shared JWT secret for all services:

- `JWT_SECRET` (all non-auth services)
- `APP_JWT_SECRET` (auth-service)

Use one shared RabbitMQ user/pass for all services:

- `RABBITMQ_USERNAME`
- `RABBITMQ_PASSWORD`

### Database Variables

Auth service:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

Other services:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`

Recommended DB names:

- `connectsphere_auth`
- `post_db`
- `comment_db`
- `like_db`
- `follow_db`
- `notification_db`
- `media_db`
- `search_db`

Example JDBC format:

```text
jdbc:mysql://<railway-host>:<railway-port>/<db_name>?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
```

If your Railway MySQL requires SSL, use:

```text
jdbc:mysql://<railway-host>:<railway-port>/<db_name>?createDatabaseIfNotExist=true&useSSL=true&requireSSL=true&verifyServerCertificate=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
```

### Notification Mail Variables

- `MAIL_USERNAME`
- `MAIL_PASSWORD`
- `MAIL_FROM`

If you do not want email delivery yet, keep `MAIL_ENABLED=false` (already set in `render.yaml`).
