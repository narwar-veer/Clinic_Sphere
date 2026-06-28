# 🏥 Clinic Sphere Backend

![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2.6-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-3.9+-C71A36?style=for-the-badge&logo=apache-maven&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7-DC382D?style=for-the-badge&logo=redis&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-green.svg?style=for-the-badge)

## 📋 Project Overview
Clinic Sphere is a production-ready doctor clinic appointment booking backend engineered with Spring Boot 3 and Java 21. The platform provides secure clinic management, automated appointment slot generation, atomic concurrency control for bookings, and asynchronous notification dispatches. Built with enterprise standards in mind, it incorporates database version control, distributed rate limiting, and structured observability.

## ✨ Key Features
- **Stateless Authentication & RBAC**: Custom JWT authentication filter paired with role-based authorization and session sliding inactivity timeouts.
- **Atomic Booking & Idempotency**: Concurrency-safe slot reservations using Redis-backed idempotency headers (`X-Idempotency-Key`) to prevent duplicate bookings.
- **Transactional Outbox Notifications**: Reliability guarantee for SMS and WhatsApp integration via Twilio and generic webhooks, preventing message loss during network failures.
- **Automated Slot Scheduling**: Dynamic daily and recurring slot generation with configurable doctor availability and capacity management.
- **Reporting & Patient Records**: Automated generation of medical record histories and Excel export streams using Apache POI.
- **Observability & Health Monitoring**: Spring Boot Actuator integration exposing Prometheus metrics for real-time application telemetry.

## 🛠️ Tech Stack
- **Core Framework**: Java 21, Spring Boot 3.2.6
- **Data & Persistence**: Spring Data JPA, Hibernate, PostgreSQL 16, Flyway Migrations
- **Caching & Resilience**: Redis 7, Spring Data Redis, Spring Retry
- **Security**: Spring Security, JJWT (`io.jsonwebtoken` 0.12.6)
- **Utilities & Tooling**: MapStruct 1.5.5, Lombok, Apache POI 5.2.5
- **Monitoring & DevOps**: Micrometer, Prometheus, Docker & Docker Compose, Maven

## 🏗️ Project Architecture
The application adheres to a modular Layered Architecture (Controller-Service-Repository pattern) strictly enforcing separation of concerns:
- **Presentation Layer**: Exposes REST API endpoints via controllers such as [AppointmentController](file:///D:/Desktop/Clinic_Sphere/src/main/java/com/clinic/controller/AppointmentController.java) and [AdminController](file:///D:/Desktop/Clinic_Sphere/src/main/java/com/clinic/controller/AdminController.java), handling request validation and DTO transformations using MapStruct.
- **Business Layer**: Enforces transactional integrity and domain logic within services like [AppointmentService](file:///D:/Desktop/Clinic_Sphere/src/main/java/com/clinic/service/AppointmentService.java) and [SlotService](file:///D:/Desktop/Clinic_Sphere/src/main/java/com/clinic/service/SlotService.java).
- **Persistence Layer**: Manages relational entities and queries via Spring Data JPA repositories with database schema changes versioned by Flyway.
- **Cross-Cutting Layer**: Implements security filters, Redis rate limiting via [LoginRateLimitFilter](file:///D:/Desktop/Clinic_Sphere/src/main/java/com/clinic/security/LoginRateLimitFilter.java), global exception handling, and scheduled outbox background workers.

## 📁 Folder Structure
```text
clinic-booking-backend/
├── src/main/java/com/clinic/
│   ├── config/              # Security, Redis, CORS, and Async configurations
│   ├── controller/          # REST Controllers handling public and admin routes
│   ├── dto/                 # Request and Response Transfer Objects
│   ├── entity/              # JPA Domain Entities and Enums
│   ├── exception/           # Global Exception Handling & custom exceptions
│   ├── mapper/              # MapStruct Interface Mappers
│   ├── repository/          # Spring Data JPA Repositories
│   ├── security/            # JWT Filters, Rate Limiters, & Security Context
│   └── service/             # Core Business Logic and Outbox Processors
├── src/main/resources/
│   ├── db/migration/        # Flyway SQL schema migration scripts
│   └── application.properties # Environment and application profiles
├── docker-compose.yml       # Multi-container orchestration (App, Postgres, Redis)
├── Dockerfile               # Containerization packaging spec
└── pom.xml                  # Maven dependencies and build configuration
```

## 🔌 API Endpoints

### Public Endpoints
| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/slots` | Retrieve available slots for a given date |
| `GET` | `/api/slots/availability` | Check slot availability across date ranges |
| `POST` | `/api/appointments` | Book an appointment (supports idempotency header) |

### Admin Endpoints (Authenticated)
| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/admin/login` | Authenticate admin and return JWT token |
| `GET` | `/api/admin/appointments` | List appointments with pagination and filters |
| `PUT` | `/api/admin/appointments/{id}` | Update appointment status or visit verification |
| `GET` | `/api/admin/appointments/export` | Download appointment history report as Excel |
| `PUT` | `/api/admin/slot-config` | Update recurring slot duration and working hours |
| `GET` | `/api/admin/patients/{id}/history` | Fetch medical record history for a patient |

## ⚙️ Installation & Setup

### Prerequisites
- JDK 21 or higher
- Apache Maven 3.9+
- Docker Engine & Docker Compose (Optional, for containerized setup)
- PostgreSQL 16 & Redis 7 (If running locally without Docker)

### Local Clone
```bash
git clone https://github.com/narwar-veer/Clinic_Sphere.git
cd Clinic_Sphere
```

## 🚀 Running the Project

### Option 1: Using Docker Compose (Recommended)
Launch the full infrastructure stack including PostgreSQL, Redis, and the Spring Boot application container:
```bash
docker-compose up -d --build
```

### Option 2: Running Locally via Maven
Ensure local PostgreSQL and Redis instances are running, then execute:
```bash
mvn clean package -DskipTests
java -jar target/clinic-booking-backend-1.0.0.jar
```
Actuator health and metrics are available at `http://localhost:8085/actuator/health`.

## 📸 Sample Screenshots
> Screenshot previews will be added here upon UI completion.
- Admin Dashboard Overview
- Patient Appointment Booking Flow
- Slot Configuration & Capacity Management Interface

## 🔮 Future Enhancements
- **OAuth2 / OIDC Integration**: Social login integration for patients and doctors via Google and GitHub.
- **WebSocket Live Updates**: Real-time slot availability updates using Spring WebSocket and STOMP.
- **Distributed Tracing**: Integration with OpenTelemetry and Zipkin for distributed tracing across services.
- **Multi-Tenant Isolation**: Schema-per-tenant isolation strategy for multi-clinic scaling.

## 🧠 What I Learned
- Designing resilient distributed systems by applying the Transactional Outbox Pattern to decouple database commits from notification processing.
- Mitigating race conditions in concurrent booking environments using Redis-based idempotency locks and atomic status updates.
- Implementing robust security practices in Spring Security, including custom rate-limiting filters to prevent brute-force login attempts.
- Enforcing database schema consistency across environments using Flyway migrations instead of automated JPA DDL generation.

## 👤 Author
- **Developer**: Veer Narwar
- **GitHub**: [@narwar-veer](https://github.com/narwar-veer)
