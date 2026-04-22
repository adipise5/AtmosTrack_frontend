import { render, screen } from '@testing-library/react';
import App from './App';

jest.mock('./hooks/useAqiWebSocket', () => ({
  useAqiWebSocket: () => ({
    connectionState: 'connected',
    latestByCity: {},
    historyByCity: {},
    ranking: [],
    wsUrl: 'ws://localhost:8082/ws/aqi'
  })
}));

jest.mock('react-leaflet', () => ({
  MapContainer: ({ children }) => <div data-testid="map-container">{children}</div>,
  TileLayer: () => null,
  GeoJSON: () => null,
  Marker: ({ children }) => <div>{children}</div>,
  Popup: ({ children }) => <div>{children}</div>,
  useMap: () => ({ flyTo: jest.fn() }),
  useMapEvents: () => ({ getZoom: () => 11 })
}));

test('renders AtmosTrack heading', () => {
  render(<App />);
  const heading = screen.getByText(/AtmosTrack/i);
  expect(heading).toBeInTheDocument();
});
