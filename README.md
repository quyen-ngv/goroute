# GoRoute - Travel Planner API

A comprehensive travel planning application backend built with Spring Boot 3.4.1, featuring trip management, collaborative planning, expense tracking, and real-time updates.

## 🚀 Features

### Core Features (Implemented)
- ✅ **Authentication**: Email/password and Google OAuth login with JWT
- ✅ **Trip Management**: Create, update, delete trips with dates and budget
- ✅ **Collaborative Planning**: Invite members, manage roles (owner/editor/viewer)
- ✅ **Activity Management**: Add places to itinerary with day/time scheduling
- ✅ **Expense Tracking**: Track expenses, split bills, budget overview
- ✅ **Check-ins**: Manual check-in at locations with ratings and photos
- ✅ **Real-time Updates**: Activity log and notifications

### Upcoming Features
- 🚧 Google Maps Integration (search, place details, routes)
- 🚧 Push Notifications via Firebase
- 🚧 Bill Scanning with OCR
- 🚧 Route Optimization
- 🚧 Weather Forecast
- 🚧 Group Photos
- 🚧 Voting/Polls

## 🛠 Tech Stack

- **Framework**: Spring Boot 3.4.1
- **Java**: 21
- **Database**: PostgreSQL 15+ with Flyway migrations
- **Cache**: Redis + Caffeine (local cache)
- **Message Queue**: Kafka
- **ORM**: MyBatis
- **Security**: JWT (jjwt 0.11.5), BCrypt
- **Build Tool**: Maven

## 📋 Prerequisites

- Java 21+
- Maven 3.8+
- PostgreSQL 15+
- Redis 7+
- Docker & Docker Compose (optional, for local development)

## 🏃 Quick Start

### 1. Using Docker Compose (Recommended)

```bash
# Start PostgreSQL and Redis
docker-compose up -d

# Run the application
./mvnw spring-boot:run
```

### 2. Manual Setup

#### Start PostgreSQL
```bash
docker run -d \
  --name goroute-postgres \
  -e POSTGRES_DB=goroute \
  -e POSTGRES_USER=goroute \
  -e POSTGRES_PASSWORD=goroute123 \
  -p 5432:5432 \
  postgres:15
```

#### Start Redis
```bash
docker run -d \
  --name goroute-redis \
  -p 6379:6379 \
  redis:7-alpine
```

#### Run Application
```bash
# Install dependencies
./mvnw clean install

# Run Flyway migrations
./mvnw flyway:migrate

# Start application
./mvnw spring-boot:run
```

The API will be available at `http://localhost:8080`

## 🔧 Configuration

### Environment Variables

Create a `.env` file or set environment variables:

```bash
# Database
DB_URL=jdbc:postgresql://localhost:5432/goroute
DB_USERNAME=goroute
DB_PASSWORD=goroute123

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# JWT
JWT_SECRET=your-secret-key-here

# Google Maps (optional)
GOOGLE_MAPS_API_KEY=your-api-key

# Firebase (optional)
FIREBASE_CREDENTIALS_PATH=/path/to/firebase-credentials.json
FIREBASE_PROJECT_ID=your-project-id

# AWS S3 (optional)
AWS_S3_BUCKET=goroute-media
AWS_REGION=ap-southeast-1
AWS_ACCESS_KEY=your-access-key
AWS_SECRET_KEY=your-secret-key
```

### Profiles

- `local`: Local development (default)
- `dev`: Development environment
- `staging`: Staging environment
- `prod`: Production environment

```bash
# Run with specific profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## 📚 API Documentation

### Base URL
```
http://localhost:8080/v1/api
```

### Authentication

All endpoints (except `/auth/**`) require JWT token:
```
Authorization: Bearer {access_token}
```

### Key Endpoints

#### Authentication
- `POST /auth/register` - Register new user
- `POST /auth/login` - Login with email/password
- `POST /auth/google` - Login with Google
- `POST /auth/refresh` - Refresh access token
- `POST /auth/logout` - Logout

#### Trips
- `GET /trips` - List all trips (filter by status)
- `POST /trips` - Create new trip
- `GET /trips/{id}` - Get trip details
- `PUT /trips/{id}` - Update trip
- `DELETE /trips/{id}` - Delete trip

#### Trip Members
- `POST /trips/{id}/members` - Invite member
- `POST /trips/{id}/members/{memberId}/accept` - Accept invitation
- `DELETE /trips/{id}/members/{memberId}` - Remove member

#### Activities
- `GET /trips/{tripId}/activities` - List activities (filter by day)
- `POST /trips/{tripId}/activities` - Add activity
- `PUT /trips/{tripId}/activities/{id}` - Update activity
- `DELETE /trips/{tripId}/activities/{id}` - Delete activity

#### Expenses
- `GET /trips/{tripId}/expenses` - List expenses
- `POST /trips/{tripId}/expenses` - Add expense
- `GET /trips/{tripId}/expenses/overview` - Budget overview
- `DELETE /trips/{tripId}/expenses/{id}` - Delete expense

#### Check-ins
- `POST /trips/{tripId}/activities/{activityId}/checkin` - Check-in
- `GET /trips/{tripId}/checkins` - List check-ins

### Example Requests

#### Register
```bash
curl -X POST http://localhost:8080/v1/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "John Doe",
    "email": "john@example.com",
    "password": "password123"
  }'
```

#### Create Trip
```bash
curl -X POST http://localhost:8080/v1/api/trips \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Summer in Da Nang",
    "startDate": "2026-06-15",
    "endDate": "2026-06-22",
    "destination": "Da Nang, Vietnam",
    "budget": 15000000,
    "currency": "VND"
  }'
```

## 🗄 Database Schema

### Core Tables
- `users` - User accounts
- `refresh_tokens` - JWT refresh tokens
- `trips` - Trip information
- `trip_members` - Trip collaborators
- `activities` - Itinerary items
- `expenses` - Expense tracking
- `expense_splits` - Bill splitting
- `checkins` - Location check-ins
- `notifications` - User notifications

See `src/main/resources/db/migration/` for complete schema.

## 🧪 Testing

```bash
# Run all tests
./mvnw test

# Run specific test
./mvnw test -Dtest=AuthServiceTest

# Run with coverage
./mvnw test jacoco:report
```

## 📦 Build & Deploy

### Build JAR
```bash
./mvnw clean package -DskipTests
```

### Build Docker Image
```bash
docker build -t goroute-api:latest .
```

### Run Docker Container
```bash
docker run -d \
  -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/goroute \
  -e REDIS_HOST=host.docker.internal \
  --name goroute-api \
  goroute-api:latest
```

## 🔍 Monitoring

### Health Check
```bash
curl http://localhost:8080/actuator/health
```

### Metrics
```bash
curl http://localhost:8080/actuator/metrics
```

### Prometheus Metrics
```bash
curl http://localhost:8080/actuator/prometheus
```

## 📝 Logging

Logs are written to:
- Console: Colored output for development
- File: `logs/goroute.log` (rotated daily, max 30 days)

Log levels:
- `DEBUG`: Development details
- `INFO`: General information
- `WARN`: Warnings
- `ERROR`: Errors and exceptions

## 🤝 Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open Pull Request

## 📄 License

This project is licensed under the MIT License.

## 👥 Team

- Backend: Spring Boot + PostgreSQL + Redis
- Frontend: Flutter (separate repository)
- DevOps: Docker + Kubernetes

## 📞 Support

For issues and questions:
- GitHub Issues: [Create an issue](https://github.com/your-org/goroute/issues)
- Email: support@goroute.app

---

**Built with ❤️ for travelers**
