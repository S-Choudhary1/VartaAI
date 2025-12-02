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

- Java 17
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

2. **Configure application.yml**:
- Update `whatsapp.providers.meta.*` with your Meta credentials
- Update `security.jwt.secret` with a secure secret

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

### Webhooks
- `GET /api/v1/webhooks/whatsapp` - Meta verification
- `POST /api/v1/webhooks/whatsapp` - Receive events

### Health
- `GET /actuator/health` - Health check

## Configuration

Update `src/main/resources/application.yml`:

```yaml
whatsapp:
  providers:
    meta:
      apiBaseUrl: https://graph.facebook.com/v14.0
      phoneNumberId: YOUR_PHONE_NUMBER_ID
      accessToken: YOUR_ACCESS_TOKEN

security:
  jwt:
    secret: YOUR_SECRET_KEY
    expirationSeconds: 3600
```

## Deployment

Built JAR is available at `target/VartaAI-0.0.1-SNAPSHOT.jar`

For AWS deployment, use the provided `Dockerfile` and configure environment variables:
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

## License

VartaAI

