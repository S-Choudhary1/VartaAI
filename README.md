# WhatsApp CRM Backend

Spring Boot backend for WhatsApp CRM with Meta WhatsApp Cloud API integration.

## Features

- ✅ JWT Authentication with role-based access control
- ✅ Contacts Management (CRUD)
- ✅ Message Templates (TEXT, MEDIA, INTERACTIVE)
- ✅ WhatsApp Message Sending via Meta Cloud API
- ✅ Webhook handling for incoming messages and delivery status
- ✅ Campaign Management with CSV bulk upload
- ✅ PostgreSQL Database with JSONB support
- ✅ Docker deployment ready

## Tech Stack

- Java 21
- Spring Boot 3.4.11
- PostgreSQL
- Spring Security + JWT
- WebFlux (WebClient) for HTTP calls
- Apache Commons CSV

## Quick Start

### Local Development

1. **Start PostgreSQL** (if not using Docker):
```bash
docker run -d -p 5432:5432 -e POSTGRES_DB=whatsappcrm -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres postgres:15
```

2. **Configure env variables** (copy `env.example` to `.env` or export in shell):
- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
- `WHATSAPP_PROVIDERS_META_*`
- `WHATSAPP_WEBHOOK_VERIFY_TOKEN`, `WHATSAPP_WEBHOOK_AUTH_TOKEN`
- `SECURITY_JWT_SECRET`

3. **Run the application**:
```bash
./mvnw spring-boot:run
```

### Docker Compose

```bash
docker-compose up --build
```

## API Endpoints

### Authentication
- `POST /api/v1/auth/login` - Login and get JWT token

### Contacts
- `GET /api/v1/contacts` - List all contacts
- `POST /api/v1/contacts` - Create/update contact
- `GET /api/v1/contacts/{id}` - Get contact by ID
- `GET /api/v1/contacts/phone/{phone}` - Get contact by phone

### Templates
- `GET /api/v1/templates` - List all templates
- `POST /api/v1/templates` - Create template (Admin/Manager)
- `GET /api/v1/templates/{id}` - Get template
- `PUT /api/v1/templates/{id}` - Update template
- `DELETE /api/v1/templates/{id}` - Delete template (Admin)

### Messages
- `POST /api/v1/messages/send` - Send message

### Campaigns
- `POST /api/v1/campaigns/upload-csv` - Upload CSV for bulk send
- `GET /api/v1/campaigns/{id}` - Get campaign status

### Auto Replies
- `POST /api/v1/auto-replies` - Create auto-reply rule (Admin/Manager)
- `GET /api/v1/auto-replies` - List auto-reply rules
- `GET /api/v1/auto-replies/feature-flag` - Get auto-reply feature flag for current client
- `PATCH /api/v1/auto-replies/feature-flag` - Enable/disable auto-reply feature (Admin/Manager)
- `GET /api/v1/auto-replies/{id}` - Get auto-reply rule by id
- `PUT /api/v1/auto-replies/{id}` - Update auto-reply rule (Admin/Manager)
- `PATCH /api/v1/auto-replies/{id}/status` - Enable/disable auto-reply rule (Admin/Manager)
- `DELETE /api/v1/auto-replies/{id}` - Delete auto-reply rule (Admin/Manager)

### Webhooks
- `GET /api/v1/webhooks/whatsapp` - Meta verification
- `POST /api/v1/webhooks/whatsapp` - Receive events

Auto-reply rules are client-scoped and support:
- exact text match (`TEXT_EXACT`)
- button payload match (`BUTTON_PAYLOAD`)
- interactive reply id match (`INTERACTIVE_REPLY_ID`)
- default fallback (`DEFAULT`)

Feature flag:
- Auto-reply execution runs only when client flag is enabled (`auto_reply_enabled = true`).

Detailed API docs for UI:
- `docs/auto-reply-api.md`

### Health
- `GET /actuator/health` - Health check

## Configuration

Core configuration is driven by environment variables. See `env.example` for the full list:

- Database: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_JPA_HIBERNATE_DDL_AUTO`
- WhatsApp: `WHATSAPP_PROVIDERS_META_API_BASE_URL`, `WHATSAPP_PROVIDERS_META_PHONE_NUMBER_ID`, `WHATSAPP_PROVIDERS_META_ACCESS_TOKEN`, `WHATSAPP_THROTTLE_MESSAGES_PER_SECOND`
- Webhooks: `WHATSAPP_WEBHOOK_VERIFY_TOKEN`, `WHATSAPP_WEBHOOK_AUTH_TOKEN`
- Security: `SECURITY_JWT_SECRET`, `SECURITY_JWT_EXPIRATION_SECONDS`
- Misc: `SERVER_PORT`, `JAVA_OPTS`, `SPRING_PROFILES_ACTIVE`

## Deployment

Built JAR is available at `target/VartaAI-0.0.1-SNAPSHOT.jar`

### Railway
1) Push the repo to GitHub and create a Railway project.  
2) Deploy from the GitHub repo; Railway auto-detects the `Dockerfile` (or set build `./mvnw -DskipTests clean package` and start `java -jar target/*.jar`).  
3) Set Variables in Railway (testing & production envs): all keys from `env.example`, plus `SPRING_PROFILES_ACTIVE` per environment.  
4) Add a Railway Postgres resource if needed and map its `DATABASE_URL` into `SPRING_DATASOURCE_URL` (user/password from the same resource).  
5) Deploy testing first, check `/actuator/health`, then deploy production.  

### AWS / other
Use the provided `Dockerfile` and configure the same environment variables.

## License

VartaAI

