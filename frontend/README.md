# AtmosTrack Frontend

React dashboard for visualizing live AQI data from the ingestion pipeline.

## Data sources used by the frontend

- Live AQI and pollutant aggregates from WebSocket stream (derived from Redis in backend)
- City hotspot coordinates from `public/hotspots.json` (copied from `producer/src/main/resources/static/hotspots.json`)

## Redis keys to expose through WebSocket

The Flink job writes:

- `aqi:latest` (hash): latest `CityAqiResult` per city
- `aqi:ranking` (sorted set): city-wise AQI ranking
- `aqi:history:{city}` (sorted set): timestamped AQI trend for each city

Frontend socket hook (`src/hooks/useAqiWebSocket.js`) supports these message frames:

- `latestSnapshot` → payload `{ [city]: CityAqiResult }`
- `rankingSnapshot` → payload `[{ city, aqi }]`
- `historySnapshot` → payload `{ city, points[] }` (or equivalent list in `payload`)
- `cityUpdate` → payload `CityAqiResult`

## Quick start

1. Install dependencies.
2. Start the app.
3. Ensure backend WebSocket publisher is running and connected to Redis.

Set WebSocket URL via:

- `REACT_APP_AQI_WS_URL=ws://localhost:8082/ws/aqi`

## Folder cleanup done

Removed CRA prototype leftovers that were not needed for this app:

- `src/logo.svg`
- `src/App.css`
- `src/reportWebVitals.js`

Also removed the now-unused `web-vitals` dependency.
