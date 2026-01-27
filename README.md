# 🌐 CareerHub – Full-Stack Application

CareerHub is a full-stack educational project built with **React, Spring Boot, RabbitMQ, and PostgreSQL**.  
It demonstrates **asynchronous communication**, **scalable data flow**, and a **modular, microservices architecture**.  
The entire system is containerized with **Docker** and ready for deployment to cloud environments.
---
Trial version is available at: http://career-hub.duckdns.org/
---

## 🧭 Executive Summary

CareerHub is designed as a distributed, event-driven full-stack application.  
It integrates a React frontend with a Spring backend, PostgreSQL database, and RabbitMQ.

The platform emphasizes:

- ⚙️ **Scalability** – modular microservices-based architecture.  
- 🔐 **Security** – session and authorization management with Spring Security.  
- 📨 **Asynchronous Communication** – RabbitMQ for real-time message exchange.  
- 📊 **Data-driven UX** – interactive data visualizations in React.  
- 💎 **Responsive Design** – adaptive interface for both mobile and desktop.

---

## 🏗️ Architectural Principles

- **Separation of Concerns** – clear split between frontend, backend, broker, and persistence layers.  
- **Event-Driven Communication** – RabbitMQ as the central messaging backbone.  
- **Spring Security** – robust authentication and session management.  
- **Resiliency** – error handling, retry logic, and message durability.  
- **Containerization** – all services run in isolated Docker containers.

---

## 🧩 Service Portfolio

| Service      | Responsibility | Tech Highlights |
|--------------|----------------|-----------------|
| **Frontend** | React frontend for real-time event visualization | WebSocket, REST API, responsive UI |
| **Backend**  | API and business logic built with Spring Boot | REST, Spring Security, RabbitMQ Integration |
| **Gateway**  | API Gateway for routing and communication between services | REST Proxy, Authentication, Load Balancing |
| **Database** | Persistent storage for user and event data | PostgreSQL, Spring Data JPA |
| **Auth**     | Authentication and user sessions | Spring Security, JWT |

---

## 🧰 Technology Stack

| Layer | Technology             |
|-------|------------------------|
| **Frontend** | React, JavaScript      |
| **Backend** | Java, Spring Boot, Gradle |
| **Messaging** | RabbitMQ               |
| **Database** | PostgreSQL (containerized) |
| **Auth** | Spring Security, JWT   |
| **DevOps** | Docker, Docker Compose |

---

## 🧱 High-Level Architecture

**Interaction Flow:**

1. User logs in via frontend form (JWT-based authentication).  
2. Spring Security issues and manages authentication tokens.  
3. Backend publishes event messages to RabbitMQ.  
4. Other services subscribe to message queues.  
5. React frontend consumes updates via WebSocket or REST endpoints.  
6. Data is persisted and versioned in PostgreSQL for analysis and recovery.

---

## 🔒 Security Design

- **JWT Authentication** – stateless authorization flow.  
- **Spring Security** – session and privilege management.  
- **Role-Based Access Control (RBAC)** – user roles define access scope.  
- **Encrypted Communication** – HTTPS and secured API endpoints.  

---

## 🧪 Local Development Workflow

### Start App

```bash
docker compose up --build
```

Application services run at:
Please give ten minutes app to for real action

👉 [http://localhost:3000](http://localhost:3000) – React frontend  
👉 [http://localhost:8080](http://localhost:8080) – Gateway  
👉 [http://localhost:15672](http://localhost:15672) – RabbitMQ Management Dashboard

---

## 💻 Frontend Features

- Real-time event updates via WebSocket.  
- Interactive charts and visualizations.
- Secure JWT-based login flow.  
- Intuitive data filtering and search.  

---

## 👤 Maintainer

**Milosz Podsiadly**  
📧 [m.podsiadly99@gmail.com](mailto:m.podsiadly99@gmail.com)  
🔗 [GitHub – MiloszPodsiadly](https://github.com/MiloszPodsiadly)

---

## 🪪 License

Licensed under the [MIT License](https://opensource.org/licenses/MIT).
