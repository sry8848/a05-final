# Development Collaboration Environment Design

## Goal

Make this repository shareable on GitHub for frontend collaboration without forcing collaborators to install MySQL, Redis, and RabbitMQ manually.

## Chosen Approach

Use Docker Compose only for development dependencies, while keeping frontend and backend application code running locally.

This keeps hot reload and debugging simple:

- Frontend keeps using Vite locally.
- Backend keeps using Maven and the existing Spring profiles locally.
- Docker is only responsible for MySQL, Redis, RabbitMQ, and optional schema initialization.

## Why This Approach

Compared with running both frontend and backend inside containers, this has lower friction on Windows and is easier for novice collaborators to debug.

Compared with README-only onboarding, this removes the most painful setup work: local database, cache, and message queue installation.

## Design Details

### 1. Backend dependency containers

Add `docker-compose.yml` with:

- `mysql`
- `redis`
- `rabbitmq`
- `db-init` one-off helper service for schema initialization

The defaults must match the current backend dev profile so collaborators can start the backend with minimal extra configuration.

### 2. Frontend API base configuration

Introduce a shared frontend API base helper that:

- defaults to relative `/api/v1` for local Vite proxy development
- supports `VITE_API_BASE_URL` for remote backend collaboration
- resolves backend-returned relative resource URLs such as avatar and audio links

### 3. Documentation

Add a root onboarding README that explains:

- what to start with Docker
- how to initialize the database
- how to run frontend and backend locally
- how to point frontend to a remote backend

Add a root `.env.example` for compose ports/defaults and frontend API base configuration.

## Error Handling

- Keep frontend default behavior unchanged when `VITE_API_BASE_URL` is unset.
- Normalize trailing slashes so collaborators do not need exact formatting.
- Preserve already-absolute `http`, `https`, `ws`, and `wss` URLs.

## Verification

- Add unit tests for the API base helper.
- Run the new helper test first to verify the missing behavior fails.
- Run frontend test files that cover unchanged default API behavior.
- Run a frontend production build after implementation.
