# Personal Finance Tracker — Starter Monorepo

Tech stack:
- **Backend**: Spring Boot (Java 17), JPA/Hibernate, Security (JWT), Quartz (email reports), Kafka producer, PostgreSQL
- **ML Service**: FastAPI (Python), simple regression-based savings forecast
- **Frontend**: React + Vite + TypeScript, Chart.js dashboards
- **Infra (dev)**: Docker Compose (Postgres, Kafka+Zookeeper, Mailhog)

## Quick Start (Dev)
1) Start infra (Postgres, Kafka, Mailhog):
```bash
docker compose up -d
```
   - Postgres: `localhost:5432` (db `fintrack`, user `finuser`, pass `finpass`)
   - Kafka: `localhost:9092`
   - Mailhog UI: http://localhost:8025

2) Backend (IntelliJ): open `backend`, run `FinanceTrackerApplication` or:
```bash
./mvnw spring-boot:run
```
   App runs on http://localhost:8080

3) ML service (VS Code):
```bash
cd ml-service
python -m venv .venv && . .venv/bin/activate  # Windows: .venv\Scripts\activate
pip install -r requirements.txt
uvicorn main:app --reload --port 8001
```
   API at http://localhost:8001/docs

4) Frontend (VS Code):
```bash
cd frontend
npm i
npm run dev
```
   UI on http://localhost:5173

## Default Accounts
- Register at `POST /auth/register`, then login at `POST /auth/login` to receive a JWT.
- Use the JWT (`Authorization: Bearer <token>`) for protected endpoints.

## Scheduled Email Reports
- Quartz job runs daily at 09:00 (configurable) and sends to the user's email.
- In dev, emails appear in Mailhog (http://localhost:8025).

## Event Tracking
- A `transaction_created` event is produced to Kafka on new transactions (dev stub enabled).

## ML Insights
- Backend calls ML service at `http://localhost:8001/predict/savings` to forecast next month's savings.
- Provide recent months' income & expenses; the service returns a simple regression-based prediction.

## Notes
- This is a scaffold with sensible defaults and TODOs. Extend entities, validations, and error handling as you go.
