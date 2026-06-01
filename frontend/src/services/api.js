import axios from 'axios';

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 4500
});

export async function startSimulation(config) {
  const response = await http.post('/api/simulation/start', {
    studentCount: config.studentCount,
    windowCount: config.windowCount,
    simDurationTick: config.simDurationTick,
    seatCount: config.seatCount,
    maxQueueLength: config.maxQueueLength ?? config.maxQueueCapacity,
    maxSeatWaitCapacity: config.maxSeatWaitCapacity,
    maxSeatWaitTick: config.maxSeatWaitTick
  });
  return response.data;
}

export async function fetchSimulationReport() {
  const response = await http.get('/api/simulation/report');
  return response.data;
}

export async function fetchSimulationHistory(limit = 10) {
  const response = await http.get('/api/simulation/history', { params: { limit } });
  return response.data;
}

export function createSimulationSocket({ simId, onMessage, onOpen, onClose, onError }) {
  const base = import.meta.env.VITE_WS_URL || 'ws://localhost:8080/ws/simulation';
  const separator = base.includes('?') ? '&' : '?';
  const wsUrl = simId ? `${base}${separator}simId=${encodeURIComponent(simId)}` : base;
  const socket = new WebSocket(wsUrl);

  socket.onopen = event => onOpen?.(event);
  socket.onclose = event => onClose?.(event);
  socket.onerror = event => onError?.(event);
  socket.onmessage = event => {
    try {
      onMessage?.(JSON.parse(event.data));
    } catch (error) {
      console.warn('WebSocket 数据不是合法 JSON：', error);
    }
  };

  return {
    send(type, payload = {}) {
      if (socket.readyState === WebSocket.OPEN) {
        socket.send(JSON.stringify({ type, ...payload }));
      }
    },
    close() {
      socket.close();
    },
    get readyState() {
      return socket.readyState;
    }
  };
}
