# System Knowledge: PXVB Project

## Project Overview
- **Project Name**: PXVB (Production Process Digitization for Van Bao Workshop).
- **Core Objective**: Centralize production data, automate OEE calculation, digitize material management, and provide real-time monitoring/reporting.
- **Target Users**: Operators (Tablet), Shift Leaders, Supervisors, Planners, Maintenance Techs, Warehouse Keepers, Admins, and Management (Web/Dashboard).

## Architecture & Tech Stack
- **Pattern**: Modular Monolith (Java 21, Spring Boot 3.5).
- **Database**: MySQL 8 (Compatible with 8.0/8.4). Tables grouped by prefixes: `sys`, `md`, `pl`, `mt`, `pr`, `qc`, `tx`, `pm`, `oh`, `sp`, `rp`.
- **Modules**: Each has `domain/application/infrastructure/api/contract`. No cross-module reference except via `contract`.
- **Frontend**: Angular 18 (Web Admin), React 19 (Dashboard), React Native 0.81 (Tablet).
- **Messaging/Realtime**: RabbitMQ (Events/Alerts), Redis (Cache/Lock), WebSocket + STOMP (Realtime Dashboard/Notifications).
- **Infrastructure**: On-premise servers, Nginx Reverse Proxy, Docker Compose for development.

## Core Conventions
- **Primary Keys**: UUID v7 (Application-generated).
- **Time**: Store as UTC (`DATETIME(6)`). Display as Asia/Ho_Chi_Minh (UTC+7). Include `BusinessDate` + `ShiftCode`.
- **Localization**: UI 100% Vietnamese (vi-VN).
- **Error Handling**: RFC 7807 Problem Details; Error codes: `PXVB-<MOD>-<nnnn>`.
- **Concurrency**: Optimistic concurrency with `RowVersion` (JPA `@Version`).
- **Idempotency**: Required for all tablet write commands via `Idempotency-Key` + `X-Device-Id`.
- **Data Integrity**: `CONFIRMED`/`LOCKED` data cannot be edited directly $\rightarrow$ use `AdjustmentRequest`.
- **Configuration**: No hard-coding for thresholds, shift hours, reason codes, or formulas; use versioned Masterdata.
- **Audit**: Automatic logging of all business changes to `sys.AuditLog` via Hibernate/JPA listeners.

## Business Modules Summary
- **MD (Masterdata)**: Machines, products, materials, production norms (BOM), shift definitions, stop reasons, thresholds, quality forms.
- **PL (Planning)**: Monthly/Weekly plans, production orders, shift rosters, manpower allocation.
- **MT (Material)**: Material issuance slips, actual consumption, waste tracking, ERP reconciliation.
- **PR (Production)**: Shift management, production output, machine downtime (9 reason codes A-I), OEE calculation.
- **QC (Quality)**: Inline inspections (30 min/cycle), independent audits (ĐBCL), non-conforming products (SPKPH).
- **TX (Tax)**: Tax stamp management (serial ranges), waste tracking, tax reporting.
- **PM (Maintenance)**: CMMS, maintenance plans, work orders, repair requests, MTTR/MTBF tracking.
- **OH (Overhaul)**: Annual maintenance (TDT), dossiers, assessment reports, part tracking.
- **SP (Spares)**: Spare parts inventory, receipt/issue, minimum stock alerts, ERP snapshots.
- **RP (Reporting)**: Fact-based reporting, realtime dashboards, KPI tracking (OEE, Waste, MTTR/MTBF), automated report generation.

## Development & Deployment Rules
- **Sprint 0**: Scaffolding, Building Blocks (Audit, Outbox, Idempotency, State Machine, Result/ProblemDetail mapping), Identity, Settings, Numbering.
- **Development Flow**: Migration + Entity + Seed $\rightarrow$ Domain Rules + Unit Test (Golden Data) $\rightarrow$ API + Validator + Permission + Audit + Integration Test $\rightarrow$ Event/Alert/Projection $\rightarrow$ UI $\rightarrow$ OpenAPI/Docs $\rightarrow$ AC verification.
- **Offline-First (Tablet)**: Local SQLite storage; sync via `SyncCommandHandler` (Push/Pull/Conflict).
- **Naming**: DB tables `<prefix>_<snake_case>`; Columns `snake_case`. Enums saved as `UPPER_SNAKE` strings.
- **Migrations**: Flyway per module.
