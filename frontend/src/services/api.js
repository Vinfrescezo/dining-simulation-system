import axios from 'axios';

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 60000
});

export async function startSimulation(config) {
  const response = await http.post('/api/simulation/start', {
    studentCount: config.studentCount,
    windowCount: config.windowCount,
    simDurationTick: config.simDurationTick,
    seatCount: config.seatCount,
    maxQueueLength: config.maxQueueLength ?? config.maxQueueCapacity,
    orderingTime: config.orderingTime,
    eatingTime: config.eatingTime
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
