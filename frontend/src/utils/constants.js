export const STUDENT_STATES = {
  ENTER: 'ENTER',
  PATHFINDING: 'PATHFINDING',
  QUEUEING: 'QUEUEING',
  ORDERING: 'ORDERING',
  WAITING_FOR_SEAT: 'WAITING_FOR_SEAT',
  SEEK_SEAT: 'SEEK_SEAT',
  EATING: 'EATING',
  LEAVING: 'LEAVING'
};

export const STATE_LABELS = {
  ENTER: '入场',
  PATHFINDING: '寻路',
  QUEUEING: '排队',
  ORDERING: '打饭',
  WAITING_FOR_SEAT: '端盘等座',
  SEEK_SEAT: '穿梭寻座',
  EATING: '就餐',
  LEAVING: '离开'
};

export const STATE_COLORS = {
  ENTER: '#0ea5e9',
  PATHFINDING: '#2563eb',
  QUEUEING: '#f97316',
  ORDERING: '#7c3aed',
  WAITING_FOR_SEAT: '#be185d',
  SEEK_SEAT: '#0891b2',
  EATING: '#16a34a',
  LEAVING: '#64748b'
};

export const DEFAULT_CONFIG = {
  studentCount: 1500,
  windowCount: 10,
  simDurationTick: 5400,
  seatCount: 240,
  maxQueueCapacity: 20,
  orderingTime: 28,
  eatingTime: 480,
  mealPeriod: 'LUNCH',
  renderThreshold: 300,
  tickInterval: 100
};

export const CANVAS_SIZE = {
  width: 1600,
  height: 900
};
