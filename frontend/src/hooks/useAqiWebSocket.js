import { useEffect, useMemo, useRef, useState } from 'react';

const resolveDefaultWsUrl = () => {
  if (process.env.REACT_APP_AQI_WS_URL) {
    return process.env.REACT_APP_AQI_WS_URL;
  }

  if (typeof window !== 'undefined' && window.location) {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${protocol}//${window.location.hostname}:8083/ws/aqi`;
  }

  return 'ws://localhost:8083/ws/aqi';
};

const DEFAULT_WS_URL = resolveDefaultWsUrl();
const MAX_HISTORY_POINTS = 24;

const toTimestamp = (value) => {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  if (typeof value === 'string') {
    const parsed = Date.parse(value);
    if (!Number.isNaN(parsed)) return parsed;
  }
  return Date.now();
};

const normalizeHistoryPoint = (point) => {
  if (Array.isArray(point) && point.length >= 2) {
    const ts = Number(point[0]);
    const aqi = Number(point[1]);
    if (!Number.isFinite(ts) || !Number.isFinite(aqi)) return null;
    return { ts, aqi };
  }

  const ts = Number(point?.ts ?? point?.timestamp ?? point?.windowEndTS ?? Date.now());
  const aqi = Number(point?.aqi ?? point?.value ?? 0);

  if (!Number.isFinite(ts) || !Number.isFinite(aqi)) return null;

  return {
    ts,
    aqi
  };
};

const normalizeHistoryPayload = (points) => points
  .map(normalizeHistoryPoint)
  .filter(Boolean)
  .sort((a, b) => a.ts - b.ts)
  .slice(-MAX_HISTORY_POINTS);

const parseHistoryFrame = (data) => {
  const city = data?.city ?? data?.payload?.city;

  if (!city) return null;

  const payloadPoints = Array.isArray(data?.payload)
    ? data.payload
    : Array.isArray(data?.payload?.points)
      ? data.payload.points
      : Array.isArray(data?.points)
        ? data.points
        : [];

  return {
    city,
    points: normalizeHistoryPayload(payloadPoints)
  };
};

const mergeLatest = (prev, cityResult) => {
  if (!cityResult?.city) return prev;
  return {
    ...prev,
    [cityResult.city]: {
      ...(prev[cityResult.city] || {}),
      ...cityResult
    }
  };
};

const mergeHotspots = (prev, hotspotsPayload) => {
  if (!hotspotsPayload || typeof hotspotsPayload !== 'object') {
    return prev;
  }

  const merged = { ...prev };
  Object.entries(hotspotsPayload).forEach(([hotspotKey, payload]) => {
    if (!hotspotKey || !payload || typeof payload !== 'object') return;
    merged[hotspotKey] = {
      ...(merged[hotspotKey] || {}),
      ...payload
    };
  });

  return merged;
};

export const useAqiWebSocket = () => {
  const socketRef = useRef(null);
  const reconnectTimerRef = useRef(null);

  const [connectionState, setConnectionState] = useState('connecting');
  const [latestByCity, setLatestByCity] = useState({});
  const [historyByCity, setHistoryByCity] = useState({});
  const [ranking, setRanking] = useState([]);

  useEffect(() => {
    let alive = true;

    const connect = () => {
      setConnectionState('connecting');

      let socket;
      try {
        socket = new WebSocket(DEFAULT_WS_URL);
      } catch (error) {
        setConnectionState('error');
        reconnectTimerRef.current = setTimeout(connect, 3000);
        return;
      }

      socketRef.current = socket;

      socket.onopen = () => {
        if (!alive) return;
        setConnectionState('connected');
      };

      socket.onmessage = (event) => {
        if (!alive) return;

        try {
          const data = JSON.parse(event.data);

          if (data?.type === 'latestSnapshot' && data?.payload) {
            setLatestByCity(data.payload);
            return;
          }

          if (data?.type === 'rankingSnapshot' && Array.isArray(data?.payload)) {
            setRanking(data.payload);
            return;
          }

          if (data?.type === 'hotspotSnapshot' && data?.payload) {
            setLatestByCity((prev) => mergeHotspots(prev, data.payload));
            return;
          }

          if (data?.type === 'historySnapshot') {
            const parsed = parseHistoryFrame(data);
            if (!parsed) return;

            setHistoryByCity((prev) => ({
              ...prev,
              [parsed.city]: parsed.points
            }));
            return;
          }

          if (data?.type === 'cityUpdate' && data?.payload) {
            setLatestByCity((prev) => mergeLatest(prev, data.payload));

            if (data.payload?.city && Number.isFinite(Number(data.payload?.aqi))) {
              const point = {
                ts: toTimestamp(data.payload?.windowEndTS ?? data.payload?.timestamp),
                aqi: Number(data.payload.aqi)
              };

              setHistoryByCity((prev) => {
                const current = prev[data.payload.city] || [];
                return {
                  ...prev,
                  [data.payload.city]: [...current, point]
                    .sort((a, b) => a.ts - b.ts)
                    .slice(-MAX_HISTORY_POINTS)
                };
              });
            }
            return;
          }

          if (data?.type === 'cityHistoryUpdate') {
            const parsed = parseHistoryFrame(data);
            if (!parsed) return;

            setHistoryByCity((prev) => ({
              ...prev,
              [parsed.city]: parsed.points
            }));
            return;
          }

          if (data?.type === 'hotspotUpdate' && data?.payload?.key) {
            setLatestByCity((prev) => ({
              ...prev,
              [data.payload.key]: {
                ...(prev[data.payload.key] || {}),
                ...data.payload
              }
            }));
            return;
          }

          // Fallback if backend sends CityAqiResult directly
          if (data?.city && typeof data?.aqi !== 'undefined') {
            setLatestByCity((prev) => mergeLatest(prev, data));
          }
        } catch (error) {
          // ignore malformed frames without crashing UI
        }
      };

      socket.onerror = () => {
        if (!alive) return;
        setConnectionState('error');
      };

      socket.onclose = () => {
        if (!alive) return;
        setConnectionState('disconnected');
        reconnectTimerRef.current = setTimeout(connect, 3000);
      };
    };

    connect();

    return () => {
      alive = false;
      if (reconnectTimerRef.current) {
        clearTimeout(reconnectTimerRef.current);
      }
      if (socketRef.current) {
        socketRef.current.close();
      }
    };
  }, []);

  const computedRanking = useMemo(() => {
    if (ranking.length > 0) return ranking;

    return Object.values(latestByCity)
      .filter((item) => item?.city)
      .sort((a, b) => (b?.aqi ?? -1) - (a?.aqi ?? -1))
      .map((item) => ({ city: item.city, aqi: item.aqi }));
  }, [latestByCity, ranking]);

  return {
    connectionState,
    latestByCity,
    historyByCity,
    ranking: computedRanking,
    wsUrl: DEFAULT_WS_URL
  };
};
