import React, { useState, useEffect, useMemo, useRef } from 'react';
import { MapContainer, TileLayer, GeoJSON, Marker, Popup, useMap, useMapEvents } from 'react-leaflet';
import L from 'leaflet';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { ArrowLeft, Wind, MapPin, Activity, Droplets, CloudFog, Wifi, WifiOff } from 'lucide-react';
import 'leaflet/dist/leaflet.css';
import { CITY_META } from './config/cityMeta';
import { useAqiWebSocket } from './hooks/useAqiWebSocket';
import logo from './logo.png';

const MODEL_PAGES = [
  { id: 'prediction', label: 'Prediction', subtitle: 'Forecast trend' },
  { id: 'advection', label: 'Advection', subtitle: 'Pollution movement' },
  { id: 'kriging', label: 'Kriging', subtitle: 'Spatial interpolation' },
  { id: 'causal', label: 'Causal', subtitle: 'Cause-effect diagnostics' }
];

const DEFAULT_MAP_CENTER = [22.5937, 78.9629];
const DISTANCE_TIE_EPSILON_KM = 0.001;
const DEFAULT_MODEL_ID = MODEL_PAGES[0].id;

const LOCATION_PIN_ICON = L.divIcon({
  className: 'bg-transparent',
  html: `
    <div style="position: relative; width: 26px; height: 26px;">
      <span style="position: absolute; inset: 0; border-radius: 9999px; background: rgba(14, 165, 233, 0.28); border: 2px solid #0EA5E9;"></span>
      <span style="position: absolute; inset: 7px; border-radius: 9999px; background: #0EA5E9; border: 2px solid #ffffff;"></span>
    </div>
  `,
  iconSize: [26, 26],
  iconAnchor: [13, 13]
});

const formatPollutantValue = (value) => {
  const numericValue = Number(value);
  if (Number.isFinite(numericValue)) {
    return numericValue.toFixed(2);
  }
  return value ?? '—';
};

const formatHotspotLabel = (hotspotName = '') => hotspotName.replaceAll('_', ' ');

const toRadians = (value) => (value * Math.PI) / 180;

const calculateDistanceKm = (fromLat, fromLng, toLat, toLng) => {
  const earthRadiusKm = 6371;

  const latDelta = toRadians(toLat - fromLat);
  const lngDelta = toRadians(toLng - fromLng);

  const a = Math.sin(latDelta / 2) ** 2
    + Math.cos(toRadians(fromLat)) * Math.cos(toRadians(toLat)) * Math.sin(lngDelta / 2) ** 2;
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

  return earthRadiusKm * c;
};

const formatCoordinateLabel = ({ lat, lng }) => `Lat ${lat.toFixed(4)}, Lng ${lng.toFixed(4)}`;

const toGraphData = (history = []) => {
  const hourlyPoints = new Map();

  history
    .filter((point) => Number.isFinite(Number(point?.ts)) && Number.isFinite(Number(point?.aqi)))
    .sort((a, b) => Number(a.ts) - Number(b.ts))
    .forEach((point) => {
      const ts = Number(point.ts);
      const hourStart = new Date(ts);
      hourStart.setMinutes(0, 0, 0);

      // Keep latest AQI value in each hour bucket
      hourlyPoints.set(hourStart.getTime(), {
        ts: hourStart.getTime(),
        aqi: Number(point.aqi)
      });
    });

  return Array.from(hourlyPoints.values())
    .sort((a, b) => Number(a.ts) - Number(b.ts))
    .map((point) => ({
      time: new Date(point.ts).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      aqi: Number(point.aqi)
    }))
    .slice(-24);
};

const getAqiInfo = (aqi) => {
  if (aqi === null || aqi === 0 || typeof aqi === 'undefined' || Number.isNaN(aqi)) {
    return { color: '#9CA3AF', label: 'No Data', advice: 'Live AQI data is not available yet.' };
  }
  if (aqi <= 50) return { color: '#10B981', label: 'Good', advice: 'Great day to be active outside!' };
  if (aqi <= 100) return { color: '#FBBF24', label: 'Moderate', advice: 'Acceptable quality. Limit outdoor exertion.' };
  if (aqi <= 150) return { color: '#F97316', label: 'Unhealthy for Sensitive Groups', advice: 'Wear a mask if you have respiratory issues.' };
  if (aqi <= 200) return { color: '#EF4444', label: 'Unhealthy', advice: 'Limit outdoor exercise.' };
  if (aqi <= 300) return { color: '#8B5CF6', label: 'Very Unhealthy', advice: 'Avoid outdoor activity.' };
  return { color: '#9F1239', label: 'Hazardous', advice: 'Health alert! Remain indoors. Wear N95 masks.' };
};

const getLiveIcon = (color, options = {}) => {
  const { isSelected = false } = options;

  return L.divIcon({
    className: 'bg-transparent',
    html: `
      <div class="relative flex h-6 w-6 items-center justify-center">
        <span class="animate-ping absolute inline-flex h-full w-full rounded-full opacity-75" style="background-color: ${color};"></span>
        <span class="relative inline-flex rounded-full h-4 w-4 shadow-lg" style="background-color: ${color}; border: ${isSelected ? 3 : 2}px solid #ffffff;"></span>
      </div>
    `,
    iconSize: [24, 24],
    iconAnchor: [12, 12]
  });
};

const MapUpdater = ({ center, zoom }) => {
  const map = useMap();
  const previousRef = useRef({ center: null, zoom: null });
  const lat = center?.[0];
  const lng = center?.[1];

  useEffect(() => {
    const current = previousRef.current;
    const previousLat = current.center?.[0];
    const previousLng = current.center?.[1];

    const centerChanged = lat !== previousLat || lng !== previousLng;
    const zoomChanged = zoom !== current.zoom;

    if (centerChanged || zoomChanged) {
      map.setView([lat, lng], zoom, { animate: centerChanged });
      previousRef.current = { center: [lat, lng], zoom };
    }
  }, [lat, lng, zoom, map]);

  return null;
};

const MapLocationPicker = ({ enabled = true, onPick }) => {
  useMapEvents({
    click(event) {
      if (!enabled) return;

      onPick({
        lat: Number(event.latlng.lat),
        lng: Number(event.latlng.lng)
      });
    }
  });

  return null;
};

const CityBoundary = ({ city }) => {
  const [geoData, setGeoData] = useState(null);

  useEffect(() => {
    if (!city?.geojson || typeof fetch !== 'function') return;

    fetch(city.geojson)
      .then((res) => res.json())
      .then((data) => setGeoData(data))
      .catch(() => {});
  }, [city?.geojson]);

  if (!geoData) return null;

  return (
    <GeoJSON
      data={geoData}
      style={{ color: '#94A3B8', weight: 1.5, fillOpacity: 0.02 }}
    />
  );
};

const PollutantCard = ({ label, value, unit, icon: Icon, color }) => (
  <div className="bg-gray-700/50 p-2 lg:p-3 rounded-lg flex items-center justify-between border-l-2" style={{ borderLeftColor: color }}>
    <div className="flex items-center space-x-2">
      <Icon className="w-4 h-4 lg:w-5 lg:h-5 text-gray-400" />
      <span className="text-xs lg:text-sm text-gray-300 font-medium">{label}</span>
    </div>
    <div className="text-right">
      <span className="block font-bold text-md lg:text-lg text-white">{formatPollutantValue(value)}</span>
      <span className="text-[10px] lg:text-xs text-gray-400">{unit}</span>
    </div>
  </div>
);

const PollutantsPanel = ({ live }) => (
  <div className="bg-gray-800 rounded-xl p-4 shadow-xl shrink-0">
    <h3 className="text-md font-semibold mb-3 flex items-center border-b border-gray-700 pb-2">
      <CloudFog className="w-4 h-4 mr-2 text-gray-400" />
      Key Pollutants Breakdown
    </h3>
    <div className="grid grid-cols-2 gap-2">
      <PollutantCard label="PM2.5" value={live?.pm25 ?? '—'} unit="µg/m³" icon={CloudFog} color="#EF4444" />
      <PollutantCard label="PM10" value={live?.pm10 ?? '—'} unit="µg/m³" icon={CloudFog} color="#F97316" />
      <PollutantCard label="Ozone" value={live?.ozone ?? '—'} unit="µg/m³" icon={Droplets} color="#3B82F6" />
      <PollutantCard label="NO₂" value={live?.no2 ?? '—'} unit="µg/m³" icon={Wind} color="#FBBF24" />
      <PollutantCard label="SO₂" value={live?.so2 ?? '—'} unit="µg/m³" icon={Wind} color="#A855F7" />
      <PollutantCard label="CO" value={live?.co ?? '—'} unit="mg/m³" icon={CloudFog} color="#9CA3AF" />
    </div>
  </div>
);

const AdvicePanel = ({ aqi }) => {
  const info = getAqiInfo(aqi);

  return (
    <div className="bg-gray-800 rounded-xl p-4 shadow-xl border-t-4 shrink-0" style={{ borderColor: info.color }}>
      <h3 className="text-md font-semibold mb-2 flex items-center">
        <MapPin className="w-4 h-4 mr-2 text-gray-400" />
        Healthcare Advice
      </h3>
      <p className="text-gray-300 text-sm">
        {info.advice}
      </p>
    </div>
  );
};

const AqiTrendChart = ({ graphData, lineColor, emptyMessage }) => (
  <div className="flex-1 min-h-0 w-full">
    {graphData.length === 0 ? (
      <div className="h-full w-full flex items-center justify-center text-sm text-gray-400">
        {emptyMessage}
      </div>
    ) : (
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={graphData} margin={{ top: 5, right: 10, left: -20, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="#374151" vertical={false} />
          <XAxis dataKey="time" stroke="#9CA3AF" tick={{ fontSize: 10 }} tickMargin={5} />
          <YAxis stroke="#9CA3AF" tick={{ fontSize: 10 }} domain={['auto', 'auto']} width={40} />
          <Tooltip
            contentStyle={{ backgroundColor: '#1F2937', borderColor: '#374151', borderRadius: '8px' }}
            itemStyle={{ color: lineColor, fontWeight: 'bold' }}
          />
          <Line
            type="monotone"
            dataKey="aqi"
            name="AQI Level"
            stroke={lineColor}
            strokeWidth={3}
            dot={false}
            activeDot={{ r: 6 }}
          />
        </LineChart>
      </ResponsiveContainer>
    )}
  </div>
);

const ModelPageButtons = ({ selectedModelId, onSelectModel }) => (
  <div className="flex items-stretch gap-2 min-w-max">
    {MODEL_PAGES.map((model) => {
      const isActive = selectedModelId === model.id;
      return (
        <button
          key={model.id}
          onClick={() => onSelectModel(model.id)}
          className={`rounded-lg border p-2 text-left transition-colors min-w-[190px] ${
            isActive
              ? 'border-teal-400 bg-teal-500/20'
              : 'border-gray-600 bg-gray-700/40 hover:bg-gray-700'
          }`}
        >
          <span className="block font-semibold text-xs md:text-sm text-white">{model.label}</span>
          <span className="block text-[10px] md:text-xs text-gray-300 mt-0.5">{model.subtitle}</span>
        </button>
      );
    })}
  </div>
);

const PickedLocationMarker = ({ location, label, nearestInfo }) => {
  const markerRef = useRef(null);

  useEffect(() => {
    markerRef.current?.openPopup();
  }, [location?.lat, location?.lng]);

  if (!location) return null;

  const nearestHotspotNames = nearestInfo?.nearestHotspots?.map((hotspot) => hotspot.label) || [];
  const hasNearestAqi = Number.isFinite(nearestInfo?.nearestAqi);

  return (
    <Marker ref={markerRef} position={[location.lat, location.lng]} icon={LOCATION_PIN_ICON}>
      <Popup autoPan>
        <div className="text-sm space-y-1">
          <strong>{label || formatCoordinateLabel(location)}</strong>
          <div>
            AQI: {hasNearestAqi ? Number(nearestInfo.nearestAqi).toFixed(1) : '—'}
          </div>
          {nearestHotspotNames.length > 0 ? (
            <div>
              Source: {nearestHotspotNames.join(', ')}
              {nearestInfo?.usedAverage ? ' (average)' : ''}
            </div>
          ) : (
            <div>No hotspot metadata found in this city.</div>
          )}
        </div>
      </Popup>
    </Marker>
  );
};

export default function App() {
  const [selectedCityId, setSelectedCityId] = useState(null);
  const [selectedModelId, setSelectedModelId] = useState(DEFAULT_MODEL_ID);
  const [activePage, setActivePage] = useState('dashboard');
  const [analysisScope, setAnalysisScope] = useState('city');
  const [selectedHotspotId, setSelectedHotspotId] = useState('');
  const [hotspotsByCity, setHotspotsByCity] = useState({});
  const [selectedCityLocation, setSelectedCityLocation] = useState(null);
  const [selectedCityLocationLabel, setSelectedCityLocationLabel] = useState('');

  const { connectionState, latestByCity, historyByCity, ranking, wsUrl } = useAqiWebSocket();

  useEffect(() => {
    let isMounted = true;

    if (typeof fetch !== 'function') return undefined;

    fetch('/hotspots.json')
      .then((res) => (res.ok ? res.json() : Promise.resolve({})))
      .then((data) => {
        if (!isMounted || typeof data !== 'object' || data === null) return;
        setHotspotsByCity(data);
      })
      .catch(() => {});

    return () => {
      isMounted = false;
    };
  }, []);

  const citiesData = useMemo(() => {
    const byName = new Map(CITY_META.map((city) => [city.name, city]));

    const fromRanking = ranking
      .map((entry) => {
        const cityMeta = byName.get(entry.city);
        if (!cityMeta) return null;
        const latest = latestByCity[entry.city] || {};
        return {
          ...cityMeta,
          aqi: entry.aqi,
          live: latest
        };
      })
      .filter(Boolean);

    if (fromRanking.length > 0) return fromRanking;

    return CITY_META
      .map((city) => ({
        ...city,
        aqi: latestByCity[city.name]?.aqi ?? null,
        live: latestByCity[city.name] || {}
      }))
      .sort((a, b) => (b.aqi ?? -1) - (a.aqi ?? -1));
  }, [latestByCity, ranking]);

  const selectedCity = useMemo(
    () => citiesData.find((city) => city.id === selectedCityId) || null,
    [citiesData, selectedCityId]
  );
  const selectedCityCenter = useMemo(
    () => (selectedCity ? [selectedCity.lat, selectedCity.lng] : DEFAULT_MAP_CENTER),
    [selectedCity]
  );

  const cityGraphData = useMemo(() => {
    if (!selectedCity) return [];
    return toGraphData(historyByCity[selectedCity.name] || []);
  }, [historyByCity, selectedCity]);

  const selectedCityAqiInfo = useMemo(
    () => getAqiInfo(selectedCity?.aqi),
    [selectedCity?.aqi]
  );

  const selectedModel = useMemo(
    () => MODEL_PAGES.find((page) => page.id === selectedModelId) || null,
    [selectedModelId]
  );

  const cityHotspots = useMemo(() => {
    if (!selectedCity) return [];

    const raw = hotspotsByCity[selectedCity.name];
    if (!raw || typeof raw !== 'object') return [];

    return Object.entries(raw)
      .map(([id, coords]) => {
        const lat = Number(coords?.lat ?? coords?.latitude);
        const lng = Number(coords?.lon ?? coords?.lng ?? coords?.longitude);

        if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null;

        return {
          id,
          label: formatHotspotLabel(id),
          lat,
          lng
        };
      })
      .filter(Boolean);
  }, [hotspotsByCity, selectedCity]);

  useEffect(() => {
    setSelectedModelId(null);
    setSelectedHotspotId('');
    setSelectedCityLocation(null);
    setSelectedCityLocationLabel('');
  }, [selectedCityId]);

  useEffect(() => {
    if (cityHotspots.length === 0) {
      setSelectedHotspotId('');
      if (analysisScope === 'hotspot') {
        setAnalysisScope('city');
      }
      return;
    }

    setSelectedHotspotId((previous) => {
      if (cityHotspots.some((hotspot) => hotspot.id === previous)) return previous;
      return cityHotspots[0].id;
    });
  }, [analysisScope, cityHotspots]);

  const selectedHotspot = useMemo(
    () => cityHotspots.find((hotspot) => hotspot.id === selectedHotspotId) || null,
    [cityHotspots, selectedHotspotId]
  );

  const hotspotLiveById = useMemo(() => {
    if (!selectedCity || cityHotspots.length === 0) return {};

    const byId = {};
    cityHotspots.forEach((hotspot) => {
      const keyCandidates = [
        `${selectedCity.name}:${hotspot.id}`,
        hotspot.id
      ];

      for (const key of keyCandidates) {
        const payload = latestByCity[key];
        if (payload) {
          byId[hotspot.id] = payload;
          break;
        }
      }
    });

    return byId;
  }, [cityHotspots, latestByCity, selectedCity]);

  const hotspotGraphData = useMemo(() => {
    if (!selectedCity || !selectedHotspot) return [];

    const keyCandidates = [
      `${selectedCity.name}:${selectedHotspot.id}`,
      selectedHotspot.id
    ];

    for (const key of keyCandidates) {
      const points = historyByCity[key];
      if (Array.isArray(points) && points.length > 0) {
        return toGraphData(points);
      }
    }

    return [];
  }, [historyByCity, selectedCity, selectedHotspot]);

  const hotspotLiveData = useMemo(() => {
    if (!selectedHotspot) return null;
    return hotspotLiveById[selectedHotspot.id] || null;
  }, [hotspotLiveById, selectedHotspot]);

  const hotspotModeActive = analysisScope === 'hotspot' && cityHotspots.length > 0 && !!selectedHotspot;

  const resolvedGraphData = hotspotModeActive && hotspotGraphData.length > 0
    ? hotspotGraphData
    : cityGraphData;

  const resolvedLive = hotspotModeActive
    ? ({ ...(selectedCity?.live || {}), ...(hotspotLiveData || {}) })
    : (selectedCity?.live || {});

  const resolvedAqi = hotspotModeActive
    ? (hotspotLiveData?.aqi ?? selectedCity?.aqi)
    : selectedCity?.aqi;

  const resolvedAqiInfo = getAqiInfo(resolvedAqi);

  const analysisTargetLabel = hotspotModeActive
    ? selectedHotspot?.label
    : selectedCity?.name;

  const analysisMapCenter = hotspotModeActive && selectedHotspot
    ? [selectedHotspot.lat, selectedHotspot.lng]
    : selectedCity
      ? [selectedCity.lat, selectedCity.lng]
      : DEFAULT_MAP_CENTER;

  const analysisZoom = hotspotModeActive ? 12 : 11;

  const locationFromNearestHotspot = useMemo(() => {
    if (!selectedCityLocation || cityHotspots.length === 0) return null;

    const sortedByDistance = cityHotspots
      .map((hotspot) => ({
        ...hotspot,
        distanceKm: calculateDistanceKm(
          selectedCityLocation.lat,
          selectedCityLocation.lng,
          hotspot.lat,
          hotspot.lng
        )
      }))
      .sort((a, b) => a.distanceKm - b.distanceKm);

    const nearestDistanceKm = sortedByDistance[0]?.distanceKm;
    if (!Number.isFinite(nearestDistanceKm)) return null;

    const nearestHotspots = sortedByDistance.filter(
      (hotspot) => Math.abs(hotspot.distanceKm - nearestDistanceKm) <= DISTANCE_TIE_EPSILON_KM
    );

    const nearestAqiValues = nearestHotspots
      .map((hotspot) => Number(hotspotLiveById[hotspot.id]?.aqi))
      .filter((aqiValue) => Number.isFinite(aqiValue));

    return {
      nearestHotspots,
      nearestDistanceKm,
      nearestAqi: nearestAqiValues.length > 0
        ? nearestAqiValues.reduce((sum, value) => sum + value, 0) / nearestAqiValues.length
        : null,
      usedAverage: nearestAqiValues.length > 1
    };
  }, [cityHotspots, hotspotLiveById, selectedCityLocation]);

  useEffect(() => {
    if (!selectedCityLocation) {
      setSelectedCityLocationLabel('');
      return undefined;
    }

    const fallbackLabel = formatCoordinateLabel(selectedCityLocation);
    setSelectedCityLocationLabel(fallbackLabel);

    if (typeof fetch !== 'function') return undefined;

    const controller = new AbortController();
    const lat = selectedCityLocation.lat.toFixed(6);
    const lng = selectedCityLocation.lng.toFixed(6);
    const url = `https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=${lat}&lon=${lng}`;

    fetch(url, {
      signal: controller.signal,
      headers: {
        Accept: 'application/json'
      }
    })
      .then((response) => (response.ok ? response.json() : Promise.resolve(null)))
      .then((payload) => {
        if (!payload) return;

        const placeName = payload?.name
          || payload?.address?.suburb
          || payload?.address?.neighbourhood
          || payload?.address?.city_district
          || payload?.address?.city
          || payload?.display_name;

        if (!placeName || typeof placeName !== 'string') return;
        setSelectedCityLocationLabel(placeName.split(',').slice(0, 3).join(', '));
      })
      .catch(() => {});

    return () => {
      controller.abort();
    };
  }, [selectedCityLocation]);

  const navigateToIndiaDashboard = () => {
    setSelectedCityId(null);
    setSelectedModelId(null);
    setActivePage('dashboard');
    setAnalysisScope('city');
    setSelectedHotspotId('');
    setSelectedCityLocation(null);
    setSelectedCityLocationLabel('');
  };

  const openCityDashboard = (cityId) => {
    setSelectedCityId(cityId);
    setSelectedModelId(null);
    setActivePage('dashboard');
  };

  const openAnalyticsPage = () => {
    setSelectedModelId(null);
    setAnalysisScope('city');
    setActivePage('analytics');
  };

  return (
    <div className="min-h-screen bg-gray-900 text-gray-100 font-sans p-4 md:p-8">
      <header className="mb-6 flex items-center justify-between border-b border-gray-700 pb-4">
        <div className="flex items-center space-x-3">
          <img src={logo} alt="Logo" className="w-10 h-10 object-contain" />
          <h1 className="text-2xl md:text-3xl font-bold tracking-tight">AtmosTrack</h1>
        </div>
        <button
          onClick={() => openAnalyticsPage({ scope: 'city' })}
          disabled={citiesData.length === 0}
          className={`px-4 py-2 rounded-lg text-sm font-medium border transition-colors ${
            activePage === 'analytics'
              ? 'bg-teal-500/20 border-teal-400 text-teal-300'
              : 'bg-gray-800 border-gray-600 text-gray-100 hover:bg-gray-700'
          } disabled:opacity-50 disabled:cursor-not-allowed`}
        >
          Analytics
        </button>
      </header>

      {activePage === 'analytics' ? (
        <div className="space-y-4 animate-in fade-in slide-in-from-bottom-4 duration-500">
          <div className="flex items-start justify-between gap-4 overflow-x-auto">
            <button
              onClick={() => setActivePage('dashboard')}
              className="flex items-center px-4 py-2 bg-gray-800 hover:bg-gray-700 rounded-lg transition-colors text-sm font-medium w-fit"
            >
              <ArrowLeft className="w-4 h-4 mr-2" /> Back to Dashboard
            </button>
            <div className="flex items-center space-x-3 bg-gray-800 px-6 py-2 rounded-full w-fit shrink-0">
              <span className="text-xl font-bold">Analytics</span>
            </div>
          </div>

          <div className="bg-gray-800 rounded-xl p-8 shadow-xl min-h-[300px] flex items-center justify-center text-gray-300 text-sm">
            Analytics page is reserved for upcoming features.
          </div>
        </div>
      ) : !selectedCity ? (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          <div className="lg:col-span-1 bg-gray-800 rounded-xl p-6 shadow-xl overflow-y-auto max-h-[52vh] lg:max-h-[calc(100vh-220px)]">
            <h2 className="text-xl font-semibold mb-6 flex items-center">
              <Activity className="w-5 h-5 mr-2 text-gray-400" />
              Live City Rankings
            </h2>
            <div className="mb-4 text-xs text-gray-400 flex items-center justify-between gap-3">
              <span className="flex items-center gap-1">
                {connectionState === 'connected' ? (
                  <>
                    <Wifi className="w-3 h-3 text-green-400" /> Connected
                  </>
                ) : (
                  <>
                    <WifiOff className="w-3 h-3 text-amber-400" /> {connectionState}
                  </>
                )}
              </span>
              <span className="truncate max-w-[180px]">{wsUrl}</span>
            </div>
            <div className="space-y-4">
              {citiesData.map((city, index) => {
                const info = getAqiInfo(city.aqi);
                return (
                  <div
                    key={city.id}
                    onClick={() => openCityDashboard(city.id)}
                    className="flex items-center justify-between p-4 bg-gray-700/50 rounded-lg cursor-pointer hover:bg-gray-700 transition-colors border-l-4"
                    style={{ borderLeftColor: info.color }}
                  >
                    <div className="flex items-center">
                      <span className="text-gray-400 w-6 font-mono">{index + 1}.</span>
                      <span className="font-medium text-lg ml-2">{city.name}</span>
                    </div>
                    <div className="flex flex-col items-end">
                      <span className="font-bold text-xl" style={{ color: info.color }}>{city.aqi ?? '—'}</span>
                      <span className="text-xs text-gray-400">{info.label}</span>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          <div className="lg:col-span-2 bg-gray-800 rounded-xl p-4 shadow-xl h-[52vh] min-h-[320px] lg:h-[calc(100vh-220px)] lg:max-h-[620px] relative z-0">
            <MapContainer center={DEFAULT_MAP_CENTER} zoom={5} className="w-full h-full rounded-lg" zoomControl>
              <TileLayer
                url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                attribution="&copy; OpenStreetMap contributors"
              />
              {citiesData.map((city) => {
                const info = getAqiInfo(city.aqi);
                return (
                  <Marker
                    key={city.id}
                    position={[city.lat, city.lng]}
                    icon={getLiveIcon(info.color)}
                    eventHandlers={{ click: () => openCityDashboard(city.id) }}
                  >
                    <Popup className="bg-white text-gray-900 rounded-md">
                      <div className="text-center font-sans p-1 cursor-pointer" onClick={() => openCityDashboard(city.id)}>
                        <strong className="block text-lg">{city.name}</strong>
                        <span className="text-sm font-bold block mt-1" style={{ color: info.color }}>AQI: {city.aqi ?? '—'}</span>
                        <span className="text-xs text-blue-600 font-bold mt-1 block hover:underline">Click to drill down</span>
                      </div>
                    </Popup>
                  </Marker>
                );
              })}
            </MapContainer>
          </div>
        </div>
      ) : !selectedModel ? (
        <div className="space-y-4 animate-in fade-in slide-in-from-bottom-4 duration-500">
          <div className="flex items-start justify-between gap-4 overflow-x-auto">
            <div className="flex items-start gap-2 min-w-max">
              <button
                onClick={navigateToIndiaDashboard}
                className="flex items-center px-4 py-2 bg-gray-800 hover:bg-gray-700 rounded-lg transition-colors text-sm font-medium w-fit"
              >
                <ArrowLeft className="w-4 h-4 mr-2" /> Back to Dashboard
              </button>
              <ModelPageButtons
                selectedModelId={selectedModelId}
                onSelectModel={setSelectedModelId}
              />
            </div>
            <div className="flex items-center space-x-4 bg-gray-800 px-6 py-2 rounded-full w-fit shrink-0">
              <span className="text-xl font-bold">{selectedCity.name}</span>
              <div className="h-6 w-px bg-gray-600"></div>
              <span className="font-bold text-xl flex items-center" style={{ color: selectedCityAqiInfo.color }}>
                <span className="relative flex h-3 w-3 mr-2">
                  <span className="animate-ping absolute inline-flex h-full w-full rounded-full opacity-75" style={{ backgroundColor: selectedCityAqiInfo.color }}></span>
                  <span className="relative inline-flex rounded-full h-3 w-3" style={{ backgroundColor: selectedCityAqiInfo.color }}></span>
                </span>
                AQI: {selectedCity.aqi ?? '—'} ({selectedCityAqiInfo.label})
              </span>
            </div>
          </div>

          <div className="bg-gray-800 rounded-xl p-4 shadow-xl">
            <h3 className="text-md font-semibold mb-3 flex items-center">
              <MapPin className="w-4 h-4 mr-2 text-gray-400" />
              Scope Selection
            </h3>
            <div className="flex flex-wrap items-center gap-3">
              <button
                onClick={() => setAnalysisScope('city')}
                className="px-4 py-2 rounded-lg text-sm font-medium border bg-teal-500/20 border-teal-400 text-teal-300"
              >
                Citywise
              </button>
              <button
                onClick={() => {
                  setAnalysisScope('hotspot');
                  setSelectedModelId(DEFAULT_MODEL_ID);
                }}
                disabled={cityHotspots.length === 0}
                className="px-4 py-2 rounded-lg text-sm font-medium border transition-colors bg-gray-700/40 border-gray-600 text-gray-200 hover:bg-gray-700 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                Hotspots Wise
              </button>
              {cityHotspots.length === 0 && (
                <span className="text-xs text-amber-300">
                  No hotspot metadata found for this city.
                </span>
              )}
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 lg:h-[calc(100vh-280px)] lg:max-h-[620px]">
            <div className="lg:col-span-2 flex flex-col gap-4 h-full">
              <div className="bg-gray-800 rounded-xl p-4 shadow-xl relative z-0 flex-1 min-h-[320px] h-[48vh] lg:h-full">
                <MapContainer center={selectedCityCenter} zoom={11} className="w-full h-full rounded-lg" doubleClickZoom={false}>
                  <TileLayer
                    url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                    attribution="&copy; OpenStreetMap contributors"
                  />
                  <MapUpdater center={selectedCityCenter} zoom={11} />
                  <MapLocationPicker onPick={setSelectedCityLocation} />
                  <CityBoundary city={selectedCity} />
                  <Marker position={[selectedCity.lat, selectedCity.lng]} icon={getLiveIcon(selectedCityAqiInfo.color)}>
                    <Popup>
                      <div className="text-sm">
                        <strong>{selectedCity.name}</strong>
                        <div>AQI: {selectedCity.aqi ?? '—'}</div>
                      </div>
                    </Popup>
                  </Marker>
                  <PickedLocationMarker
                    location={selectedCityLocation}
                    label={selectedCityLocationLabel}
                    nearestInfo={locationFromNearestHotspot}
                  />
                </MapContainer>
              </div>
            </div>

            <div className="flex flex-col gap-4 h-full overflow-hidden">
              <PollutantsPanel live={selectedCity.live} />
              <AdvicePanel aqi={selectedCity.aqi} />
              <div className="bg-gray-800 rounded-xl p-4 shadow-xl flex-1 flex flex-col min-h-0">
                <h3 className="text-md font-semibold mb-2 flex items-center shrink-0">
                  <Activity className="w-4 h-4 mr-2 text-gray-400" />
                  24H AQI Trend (Hourly)
                </h3>
                <AqiTrendChart
                  graphData={cityGraphData}
                  lineColor={selectedCityAqiInfo.color}
                  emptyMessage={`Waiting for Redis history stream for ${selectedCity.name}`}
                />
              </div>
            </div>
          </div>
        </div>
      ) : (
        <div className="space-y-4 animate-in fade-in slide-in-from-bottom-4 duration-500">
          <div className="flex items-start justify-between gap-4 overflow-x-auto">
            <div className="flex items-start gap-2 min-w-max">
              <button
                onClick={() => setSelectedModelId(null)}
                className="flex items-center px-4 py-2 bg-gray-800 hover:bg-gray-700 rounded-lg transition-colors text-sm font-medium w-fit"
              >
                <ArrowLeft className="w-4 h-4 mr-2" /> Back to {selectedCity.name} Dashboard
              </button>
              <ModelPageButtons selectedModelId={selectedModelId} onSelectModel={setSelectedModelId} />
            </div>
            <div className="flex items-center space-x-4 bg-gray-800 px-6 py-2 rounded-full w-fit shrink-0">
              <span className="text-xl font-bold">{selectedCity.name}</span>
              <div className="h-6 w-px bg-gray-600"></div>
              <span className="font-semibold text-base text-teal-300">{selectedModel.label} Page</span>
            </div>
          </div>

          <div className="bg-gray-800 rounded-xl p-4 shadow-xl">
            <h3 className="text-md font-semibold mb-3 flex items-center">
              <MapPin className="w-4 h-4 mr-2 text-gray-400" />
              Scope Selection
            </h3>
            <div className="flex flex-wrap items-center gap-3">
              <button
                onClick={() => setAnalysisScope('city')}
                className={`px-4 py-2 rounded-lg text-sm font-medium border transition-colors ${
                  analysisScope === 'city'
                    ? 'bg-teal-500/20 border-teal-400 text-teal-300'
                    : 'bg-gray-700/40 border-gray-600 text-gray-200 hover:bg-gray-700'
                }`}
              >
                Citywise
              </button>
              <button
                onClick={() => setAnalysisScope('hotspot')}
                disabled={cityHotspots.length === 0}
                className={`px-4 py-2 rounded-lg text-sm font-medium border transition-colors ${
                  analysisScope === 'hotspot'
                    ? 'bg-teal-500/20 border-teal-400 text-teal-300'
                    : 'bg-gray-700/40 border-gray-600 text-gray-200 hover:bg-gray-700'
                } disabled:opacity-50 disabled:cursor-not-allowed`}
              >
                Hotspots Wise
              </button>
              {analysisScope === 'hotspot' && cityHotspots.length > 0 && (
                <select
                  value={selectedHotspotId}
                  onChange={(event) => setSelectedHotspotId(event.target.value)}
                  className="bg-gray-700 border border-gray-600 text-gray-100 text-sm rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-teal-400"
                >
                  {cityHotspots.map((hotspot) => (
                    <option key={hotspot.id} value={hotspot.id}>
                      {hotspot.label}
                    </option>
                  ))}
                </select>
              )}
              {cityHotspots.length === 0 && (
                <span className="text-xs text-amber-300">
                  No hotspot metadata found for this city.
                </span>
              )}
            </div>
          </div>

          <div className={`grid grid-cols-1 gap-6 ${selectedModel.id === 'causal' ? '' : 'lg:grid-cols-3 lg:h-[calc(100vh-280px)] lg:max-h-[640px]'}`}>
            {selectedModel.id !== 'causal' && (
              <div className="lg:col-span-2 bg-gray-800 rounded-xl p-4 shadow-xl relative z-0 min-h-[320px] h-[48vh] lg:h-full">
                <MapContainer center={analysisMapCenter} zoom={analysisZoom} className="w-full h-full rounded-lg" doubleClickZoom={false}>
                  <TileLayer
                    url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                    attribution="&copy; OpenStreetMap contributors"
                  />
                  <MapUpdater center={analysisMapCenter} zoom={analysisZoom} />
                  <MapLocationPicker enabled={!hotspotModeActive} onPick={setSelectedCityLocation} />
                  <CityBoundary city={selectedCity} />

                  {hotspotModeActive ? (
                    cityHotspots.map((hotspot) => {
                      const isSelected = hotspot.id === selectedHotspotId;
                      const hotspotAqi = Number(hotspotLiveById[hotspot.id]?.aqi);
                      const hasHotspotAqi = Number.isFinite(hotspotAqi);
                      const hotspotAqiInfo = getAqiInfo(hasHotspotAqi ? hotspotAqi : selectedCity?.aqi);
                      const markerColor = hotspotAqiInfo.color;

                      return (
                        <Marker
                          key={hotspot.id}
                          position={[hotspot.lat, hotspot.lng]}
                          icon={getLiveIcon(markerColor, { isSelected })}
                          eventHandlers={{ click: () => setSelectedHotspotId(hotspot.id) }}
                        >
                          <Popup>
                            <div className="text-sm">
                              <strong>{hotspot.label}</strong>
                              <div>{selectedCity.name}</div>
                              <div>AQI: {hasHotspotAqi ? hotspotAqi : '—'}</div>
                            </div>
                          </Popup>
                        </Marker>
                      );
                    })
                  ) : (
                    <>
                      <Marker
                        position={[selectedCity.lat, selectedCity.lng]}
                        icon={getLiveIcon(resolvedAqiInfo.color)}
                      >
                        <Popup>
                          <div className="text-sm">
                            <strong>{selectedCity.name}</strong>
                            <div>AQI: {selectedCity.aqi ?? '—'}</div>
                          </div>
                        </Popup>
                      </Marker>
                      <PickedLocationMarker
                        location={selectedCityLocation}
                        label={selectedCityLocationLabel}
                        nearestInfo={locationFromNearestHotspot}
                      />
                    </>
                  )}
                </MapContainer>
              </div>
            )}

            <div className="flex flex-col gap-4 h-full overflow-hidden">
              <div className="bg-gray-800 rounded-xl p-4 shadow-xl border-l-4" style={{ borderLeftColor: resolvedAqiInfo.color }}>
                <h3 className="text-md font-semibold mb-1">{selectedModel.label}</h3>
                <p className="text-sm text-gray-300">{selectedModel.subtitle}</p>
                <p className="text-xs text-gray-400 mt-2">
                  Scope: <span className="text-gray-200 font-medium">{analysisTargetLabel}</span>
                </p>
                <p className="text-xs text-gray-400">
                  AQI: <span style={{ color: resolvedAqiInfo.color }} className="font-semibold">{resolvedAqi ?? '—'} ({resolvedAqiInfo.label})</span>
                </p>
              </div>

              <PollutantsPanel live={resolvedLive} />
              <AdvicePanel aqi={resolvedAqi} />

              <div className="bg-gray-800 rounded-xl p-4 shadow-xl flex-1 flex flex-col min-h-0">
                <h3 className="text-md font-semibold mb-2 flex items-center shrink-0">
                  <Activity className="w-4 h-4 mr-2 text-gray-400" />
                  24H AQI Trend (Hourly)
                </h3>
                <AqiTrendChart
                  graphData={resolvedGraphData}
                  lineColor={resolvedAqiInfo.color}
                  emptyMessage={`Waiting for Redis history stream for ${analysisTargetLabel}`}
                />
                {hotspotModeActive && hotspotGraphData.length === 0 && (
                  <p className="mt-2 text-xs text-amber-300">
                    Hotspot history is not streamed yet. Showing city-level history for {selectedCity.name}.
                  </p>
                )}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
