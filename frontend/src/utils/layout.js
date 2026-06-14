import { CANVAS_SIZE } from './constants';

function chooseBestGrid(tableCount, areaW, areaH) {
  let best = { cols: 4, rows: Math.ceil(tableCount / 4), score: -Infinity };
  for (let cols = 4; cols <= 14; cols += 1) {
    const rows = Math.ceil(tableCount / cols);
    const cellW = areaW / cols;
    const cellH = areaH / rows;
    const score = Math.min(cellW, cellH * 1.18);
    if (score > best.score) best = { cols, rows, score };
  }
  return { cols: best.cols, rows: best.rows };
}

export function createCanteenLayout({ windowCount = 8, seatCount = 180 } = {}) {
  const { width, height } = CANVAS_SIZE;

  const entrance = { x: 70, y: height - 48 };
  const exit     = { x: width - 70, y: height - 48 };

  const serviceArea  = { x: 110, y: 22, w: 1380, h: 255 };
  const seatArea     = { x: 110, y: 300, w: 1290, h: 500 };
  const exitLane     = { x: 1430, y: 300, w: 44, h: 500 };

  // invisible zone — waiting students scatter along the left edge
  const waitingZone  = { x: 18, y: 300, w: 80, h: 500 };

  const windows = Array.from({ length: windowCount }, (_, index) => {
    const usableW = serviceArea.w - 140;
    const gap = Math.min(130, Math.max(68, usableW / Math.max(windowCount - 1, 1)));
    const centeredStartX = serviceArea.x + serviceArea.w / 2 - ((windowCount - 1) * gap) / 2;
    const x = windowCount === 1 ? serviceArea.x + serviceArea.w / 2 : centeredStartX + index * gap;
    // 窗口数 >12 时切换为紧凑"浮标"样式，避免画布拥挤
    const compact = windowCount > 12;
    return {
      id: `W${index + 1}`,
      x,
      y: serviceArea.y + 78,
      compact,
      boxW: compact ? 60 : 108,
      boxH: compact ? 62 : 76,
      queueX: x,
      queueStartY: serviceArea.y + 155,
      queueGap: 12
    };
  });

  const tableCount = Math.ceil(seatCount / 4);
  const innerPaddingX = 28;
  const innerPaddingY = 30;
  const nominalGapX = 12;
  const nominalGapY = 12;
  const { cols: tableCols, rows: tableRows } = chooseBestGrid(tableCount, seatArea.w, seatArea.h);
  const usableW = seatArea.w - innerPaddingX * 2 - nominalGapX * (tableCols - 1);
  const usableH = seatArea.h - innerPaddingY * 2 - nominalGapY * (tableRows - 1);
  const cellW = usableW / tableCols;
  const cellH = usableH / tableRows;
  const tableW = Math.min(48, Math.max(26, cellW * 0.44));
  const tableH = Math.min(34, Math.max(20, cellH * 0.34));
  const chairGap = Math.min(16, Math.max(9, Math.min(cellW, cellH) * 0.17));

  const tables = Array.from({ length: tableCount }, (_, tableIndex) => {
    const col = tableIndex % tableCols;
    const row = Math.floor(tableIndex / tableCols);
    return {
      id: `T${tableIndex + 1}`,
      x: seatArea.x + innerPaddingX + col * (cellW + nominalGapX) + cellW / 2,
      y: seatArea.y + innerPaddingY + row * (cellH + nominalGapY) + cellH / 2,
      w: tableW,
      h: tableH,
      col,
      row
    };
  });

  const seats = [];
  const positions = [
    { key: 'top', dx: 0, dy: table => -table.h / 2 - chairGap },
    { key: 'right', dx: table => table.w / 2 + chairGap, dy: 0 },
    { key: 'bottom', dx: 0, dy: table => table.h / 2 + chairGap },
    { key: 'left', dx: table => -table.w / 2 - chairGap, dy: 0 }
  ];

  for (let index = 0; index < seatCount; index += 1) {
    const tableIndex = Math.floor(index / 4);
    const seatIndex = index % 4;
    const table = tables[tableIndex];
    const position = positions[seatIndex];
    const dx = typeof position.dx === 'function' ? position.dx(table) : position.dx;
    const dy = typeof position.dy === 'function' ? position.dy(table) : position.dy;
    seats.push({
      id: `S${index + 1}`,
      tableId: table.id,
      position: position.key,
      x: table.x + dx,
      y: table.y + dy,
      col: table.col,
      row: table.row
    });
  }

  return { width, height, entrance, exit, serviceArea, waitingZone, seatArea, exitLane, windows, seats, tables };
}

export function getWaitingSpot(layout, index) {
  const zone = layout.waitingZone;
  const paddingX = 10;
  const paddingY = 30;
  const usableW = Math.max(40, zone.w - paddingX * 2);
  const cols = Math.max(2, Math.min(4, Math.floor(usableW / 20)));
  const row = Math.floor(index / cols);
  const col = index % cols;
  const gapX = cols > 1 ? usableW / (cols - 1) : 0;
  const baseY = Math.min(zone.y + zone.h - 14, zone.y + paddingY + row * 22);
  const jitterX = Math.sin((index + 1) * 1.73) * 4;
  const jitterY = Math.cos((index + 1) * 2.11) * 4;
  return {
    x: Math.min(zone.x + zone.w - 10, Math.max(zone.x + 10, zone.x + paddingX + col * gapX + jitterX)),
    y: Math.min(zone.y + zone.h - 14, Math.max(zone.y + paddingY, baseY + jitterY))
  };
}

export function getQueueSpot(window, queueIndex) {
  return { x: window.queueX, y: window.queueStartY + queueIndex * window.queueGap };
}

export function getWaitingRoamingSpot(layout, index, tick = 0) {
  const base = getWaitingSpot(layout, index);
  const zone = layout.waitingZone;
  const minX = zone.x + 8;
  const maxX = zone.x + zone.w - 8;
  const minY = zone.y + 24;
  const maxY = zone.y + zone.h - 14;
  const wanderX = Math.sin((tick + index * 17) / 18) * 5;
  const wanderY = Math.cos((tick + index * 23) / 22) * 5;
  return {
    x: Math.min(maxX, Math.max(minX, base.x + wanderX)),
    y: Math.min(maxY, Math.max(minY, base.y + wanderY))
  };
}
