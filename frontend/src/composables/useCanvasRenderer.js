import { STATE_COLORS } from '../utils/constants';
import { createCanteenLayout } from '../utils/layout';

function roundRect(ctx, x, y, w, h, r) {
  const radius = Math.min(r, w / 2, h / 2);
  ctx.beginPath();
  ctx.moveTo(x + radius, y);
  ctx.arcTo(x + w, y, x + w, y + h, radius);
  ctx.arcTo(x + w, y + h, x, y + h, radius);
  ctx.arcTo(x, y + h, x, y, radius);
  ctx.arcTo(x, y, x + w, y, radius);
  ctx.closePath();
}

function drawPerson(ctx, student, color) {
  ctx.save();
  ctx.translate(student.x, student.y);

  ctx.fillStyle = 'rgba(15, 23, 42, 0.13)';
  ctx.beginPath();
  ctx.ellipse(0, 10, 8, 3.2, 0, 0, Math.PI * 2);
  ctx.fill();

  ctx.fillStyle = color;
  ctx.strokeStyle = 'rgba(15, 23, 42, 0.22)';
  ctx.lineWidth = 1.1;
  roundRect(ctx, -7, -1, 14, 16, 7);
  ctx.fill();
  ctx.stroke();

  ctx.fillStyle = '#f8d4a6';
  ctx.beginPath();
  ctx.arc(0, -9, 5.2, 0, Math.PI * 2);
  ctx.fill();
  ctx.strokeStyle = 'rgba(15, 23, 42, 0.14)';
  ctx.stroke();

  ctx.fillStyle = '#1f2937';
  ctx.beginPath();
  ctx.arc(0, -11, 5.4, Math.PI, Math.PI * 2);
  ctx.fill();

  ctx.strokeStyle = 'rgba(255, 255, 255, 0.82)';
  ctx.lineWidth = 1.5;
  ctx.beginPath();
  ctx.moveTo(-3, 4);
  ctx.lineTo(3, 4);
  ctx.stroke();

  ctx.fillStyle = 'rgba(15, 23, 42, 0.55)';
  roundRect(ctx, -5, 14, 4, 3, 1.5);
  ctx.fill();
  roundRect(ctx, 1, 14, 4, 3, 1.5);
  ctx.fill();

  ctx.restore();
}

function drawStudentDots(ctx, students, compact) {
  for (const student of students) {
    ctx.fillStyle = STATE_COLORS[student.s] || '#334155';
    ctx.beginPath();
    ctx.arc(student.x, student.y, compact ? 2.5 : 4, 0, Math.PI * 2);
    ctx.fill();
  }
}

export function renderCanteen(canvas, snapshot, config, options = {}) {
  if (!canvas || !snapshot) return;
  const ctx = canvas.getContext('2d');
  const dpr = window.devicePixelRatio || 1;
  const layout = createCanteenLayout(config);

  if (canvas.width !== layout.width * dpr || canvas.height !== layout.height * dpr) {
    canvas.width = layout.width * dpr;
    canvas.height = layout.height * dpr;
  }

  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, layout.width, layout.height);
  drawBackground(ctx, layout);
  drawZones(ctx, layout);
  drawWindows(ctx, layout, snapshot.windows || []);
  drawTables(ctx, layout);

  const seatData = snapshot.seats || [];
  if (options.heatmap && snapshot.tick > 0) {
    drawSeatHeatmap(ctx, layout, seatData, snapshot.tick);
  } else {
    drawSeats(ctx, layout, seatData);
  }

  drawStudents(ctx, snapshot.students || [], config.renderThreshold || 300);
  drawOverlayText(ctx, snapshot, layout);

  if (options.heatmap && snapshot.tick > 0) {
    drawHeatmapLegend(ctx, layout);
  }
}

function drawBackground(ctx, layout) {
  ctx.fillStyle = '#f8fbff';
  roundRect(ctx, 0, 0, layout.width, layout.height, 22);
  ctx.fill();

  ctx.strokeStyle = 'rgba(0, 64, 152, 0.05)';
  ctx.lineWidth = 0.6;
  for (let x = 32; x < layout.width; x += 32) {
    ctx.beginPath();
    ctx.moveTo(x, 0);
    ctx.lineTo(x, layout.height);
    ctx.stroke();
  }
  for (let y = 32; y < layout.height; y += 32) {
    ctx.beginPath();
    ctx.moveTo(0, y);
    ctx.lineTo(layout.width, y);
    ctx.stroke();
  }

  ctx.fillStyle = 'rgba(0, 64, 152, 0.035)';
  ctx.font = '800 60px Microsoft YaHei, sans-serif';
  ctx.textAlign = 'center';
  ctx.fillText('BJTU', layout.width / 2, layout.height / 2 + 18);
}

function drawZones(ctx, layout) {
  // subtle zone label — service
  ctx.fillStyle = 'rgba(29, 78, 216, 0.35)';
  ctx.font = '700 13px Microsoft YaHei, sans-serif';
  ctx.textAlign = 'left';
  ctx.fillText('取餐区', layout.serviceArea.x + 4, layout.serviceArea.y + 16);

  // subtle zone label — seats
  ctx.fillStyle = 'rgba(21, 128, 61, 0.35)';
  ctx.fillText('座位区', layout.seatArea.x + 4, layout.seatArea.y + 16);

  // exit lane
  ctx.fillStyle = '#f1f5f9';
  roundRect(ctx, layout.exitLane.x, layout.exitLane.y, layout.exitLane.w, layout.exitLane.h, 14);
  ctx.fill();
  ctx.strokeStyle = 'rgba(100, 116, 139, 0.12)';
  ctx.lineWidth = 1;
  ctx.stroke();
  ctx.save();
  ctx.translate(layout.exitLane.x + layout.exitLane.w / 2, layout.exitLane.y + layout.exitLane.h / 2);
  ctx.rotate(-Math.PI / 2);
  ctx.fillStyle = 'rgba(100, 116, 139, 0.5)';
  ctx.font = '700 11px Microsoft YaHei, sans-serif';
  ctx.textAlign = 'center';
  ctx.fillText('离场通道', 0, 0);
  ctx.restore();

  // entrance
  ctx.fillStyle = '#dbeafe';
  roundRect(ctx, layout.entrance.x - 28, layout.entrance.y - 16, 56, 32, 12);
  ctx.fill();
  ctx.fillStyle = '#1d4ed8';
  ctx.font = '700 12px Microsoft YaHei, sans-serif';
  ctx.textAlign = 'center';
  ctx.fillText('入口', layout.entrance.x, layout.entrance.y + 4);

  // exit
  ctx.fillStyle = '#e2e8f0';
  roundRect(ctx, layout.exit.x - 32, layout.exit.y - 18, 64, 36, 14);
  ctx.fill();
  ctx.fillStyle = '#334155';
  ctx.fillText('出口', layout.exit.x, layout.exit.y + 4);

}


function drawWindows(ctx, layout, snapshotWindows) {
  const winMap = new Map(snapshotWindows.map(item => [item.id, item]));
  for (const win of layout.windows) {
    const info = winMap.get(win.id) || {};
    const qLen = info.qLen ?? 0;
    const dishName = info.dishName || win.id;
    const rank = info.popularityRank;

    ctx.fillStyle = qLen > 24 ? '#fee2e2' : qLen > 12 ? '#ffedd5' : '#dbeafe';
    roundRect(ctx, win.x - 54, win.y - 38, 108, 76, 14);
    ctx.fill();
    ctx.strokeStyle = rank && rank <= 3 ? 'rgba(220, 38, 38, 0.40)' : 'rgba(0, 64, 152, 0.18)';
    ctx.lineWidth = rank && rank <= 3 ? 2 : 1.2;
    ctx.stroke();

    ctx.fillStyle = 'rgba(15,23,42,0.4)';
    ctx.font = '700 9px Microsoft YaHei, sans-serif';
    ctx.textAlign = 'center';
    ctx.fillText(win.id, win.x, win.y - 18);

    ctx.fillStyle = rank && rank <= 3 ? '#b91c1c' : '#0f172a';
    ctx.font = '800 15px Microsoft YaHei, sans-serif';
    const shortName = dishName.length > 5 ? `${dishName.slice(0, 5)}` : dishName;
    ctx.fillText(shortName, win.x, win.y + 2);
    ctx.fillStyle = rank && rank <= 3 ? '#dc2626' : '#475569';
    ctx.font = '800 14px Microsoft YaHei, sans-serif';
    ctx.fillText(`${qLen}人${rank ? ` · 热${rank}` : ''}`, win.x, win.y + 22);

    ctx.strokeStyle = 'rgba(100, 116, 139, 0.12)';
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(win.queueX, win.queueStartY - 6);
    ctx.lineTo(win.queueX, Math.min(layout.serviceArea.y + layout.serviceArea.h - 6, win.queueStartY + 360));
    ctx.stroke();
  }
}

function drawTables(ctx, layout) {
  for (const table of layout.tables || []) {
    ctx.fillStyle = '#fffbeb';
    ctx.strokeStyle = 'rgba(146, 64, 14, 0.18)';
    ctx.lineWidth = 1;
    roundRect(ctx, table.x - table.w / 2, table.y - table.h / 2, table.w, table.h, 7);
    ctx.fill();
    ctx.stroke();

    ctx.fillStyle = 'rgba(146, 64, 14, 0.06)';
    roundRect(ctx, table.x - table.w / 2 + 4, table.y - table.h / 2 + 4, table.w - 8, table.h - 8, 5);
    ctx.fill();
  }
}

function drawChair(ctx, seat, occupied, reserved) {
  const chairSize = 12;
  const radius = 5;
  const x = seat.x - chairSize / 2;
  const y = seat.y - chairSize / 2;

  ctx.fillStyle = occupied ? '#fecaca' : reserved ? '#fde68a' : '#bbf7d0';
  ctx.strokeStyle = occupied ? 'rgba(220, 38, 38, 0.45)' : reserved ? 'rgba(217, 119, 6, 0.45)' : 'rgba(22, 163, 74, 0.40)';
  ctx.lineWidth = 1;
  roundRect(ctx, x, y, chairSize, chairSize, radius);
  ctx.fill();
  ctx.stroke();

  ctx.strokeStyle = occupied ? 'rgba(127, 29, 29, 0.30)' : reserved ? 'rgba(146, 64, 14, 0.32)' : 'rgba(21, 128, 61, 0.28)';
  ctx.lineWidth = 1.4;
  ctx.beginPath();
  if (seat.position === 'top' || seat.position === 'bottom') {
    ctx.moveTo(x + 3, seat.position === 'top' ? y + 10 : y + 2);
    ctx.lineTo(x + chairSize - 3, seat.position === 'top' ? y + 10 : y + 2);
  } else {
    ctx.moveTo(seat.position === 'left' ? x + 10 : x + 2, y + 3);
    ctx.lineTo(seat.position === 'left' ? x + 10 : x + 2, y + chairSize - 3);
  }
  ctx.stroke();
}

function drawSeats(ctx, layout, seats) {
  const occupiedMap = new Map(seats.map(seat => [seat.id, seat.occupied]));
  const reservedMap = new Map(seats.map(seat => [seat.id, seat.reserved]));
  for (const seat of layout.seats) {
    drawChair(ctx, seat, occupiedMap.get(seat.id), reservedMap.get(seat.id));
  }
}

function drawStudents(ctx, students, threshold) {
  if (students.length > threshold) {
    drawStudentDots(ctx, students, true);
    return;
  }

  for (const student of students) {
    drawPerson(ctx, student, STATE_COLORS[student.s] || '#334155');
  }
}

function drawOverlayText(ctx, snapshot, layout) {
  // top-left info
  ctx.fillStyle = 'rgba(255, 255, 255, 0.90)';
  roundRect(ctx, 14, 14, 192, 52, 13);
  ctx.fill();
  ctx.strokeStyle = 'rgba(15, 23, 42, 0.06)';
  ctx.lineWidth = 1;
  ctx.stroke();
  ctx.fillStyle = '#0f172a';
  ctx.font = '800 13px Microsoft YaHei, sans-serif';
  ctx.textAlign = 'left';
  ctx.fillText(`Tick：${snapshot.tick}`, 28, 36);
  ctx.fillStyle = '#475569';
  ctx.font = '700 11px Microsoft YaHei, sans-serif';
  ctx.fillText(`在场：${snapshot.stats?.activeCount ?? 0}`, 28, 54);

  // waiting-for-seat badge (left edge, vertically centered)
  const waitCount = snapshot.stats?.waitingSeatCount ?? 0;
  if (waitCount > 0) {
    const bx = 14;
    const by = layout.seatArea.y + 30;
    const label = `等座 ${waitCount} 人`;
    ctx.font = '800 12px Microsoft YaHei, sans-serif';
    const tw = ctx.measureText(label).width;
    const pw = tw + 20;

    ctx.fillStyle = 'rgba(220, 38, 38, 0.12)';
    roundRect(ctx, bx, by, pw, 28, 10);
    ctx.fill();
    ctx.strokeStyle = 'rgba(220, 38, 38, 0.35)';
    ctx.lineWidth = 1.2;
    ctx.stroke();

    ctx.fillStyle = '#dc2626';
    ctx.textAlign = 'left';
    ctx.fillText(label, bx + 10, by + 19);
  }
}

function heatColor(t) {
  if (t <= 0) return 'rgb(59,130,246)';
  if (t <= 0.25) {
    const p = t / 0.25;
    return `rgb(${Math.round(59 + p * (6 - 59))},${Math.round(130 + p * (182 - 130))},${Math.round(246 + p * (212 - 246))})`;
  }
  if (t <= 0.5) {
    const p = (t - 0.25) / 0.25;
    return `rgb(${Math.round(6 + p * (250 - 6))},${Math.round(182 + p * (204 - 182))},${Math.round(212 + p * (21 - 212))})`;
  }
  if (t <= 0.75) {
    const p = (t - 0.5) / 0.25;
    return `rgb(${Math.round(250 + p * (249 - 250))},${Math.round(204 + p * (115 - 204))},${Math.round(21 + p * (22 - 21))})`;
  }
  const p = (t - 0.75) / 0.25;
  return `rgb(${Math.round(249 + p * (220 - 249))},${Math.round(115 + p * (38 - 115))},${Math.round(22 + p * (38 - 22))})`;
}

function drawSeatHeatmap(ctx, layout, snapshotSeats, tick) {
  const occMap = new Map(snapshotSeats.map(s => [s.id, s.occupancyTicks ?? 0]));
  const values = snapshotSeats.map(s => s.occupancyTicks ?? 0);
  const maxOcc = Math.max(1, ...values);
  const sorted = [...values].sort((a, b) => a - b);
  const p10 = sorted[Math.floor(sorted.length * 0.1)] || 0;
  const p90 = sorted[Math.floor(sorted.length * 0.9)] || maxOcc;
  const range = Math.max(1, p90 - p10);

  for (const seat of layout.seats) {
    const occ = occMap.get(seat.id) ?? 0;
    const raw = Math.max(0, Math.min(1, (occ - p10) / range));
    const ratio = Math.pow(raw, 0.55);

    const glowR = 20 + ratio * 10;
    const grad = ctx.createRadialGradient(seat.x, seat.y, 0, seat.x, seat.y, glowR);
    const c = heatColor(ratio);
    grad.addColorStop(0, c.replace('rgb', 'rgba').replace(')', `,${0.45 + ratio * 0.35})`));
    grad.addColorStop(1, c.replace('rgb', 'rgba').replace(')', ',0)'));
    ctx.fillStyle = grad;
    ctx.beginPath();
    ctx.arc(seat.x, seat.y, glowR, 0, Math.PI * 2);
    ctx.fill();

    const size = 15;
    const x = seat.x - size / 2;
    const y = seat.y - size / 2;
    ctx.fillStyle = c;
    ctx.strokeStyle = `rgba(0,0,0,${0.1 + ratio * 0.2})`;
    ctx.lineWidth = 1.4;
    roundRect(ctx, x, y, size, size, 5);
    ctx.fill();
    ctx.stroke();

    if (occ === 0) {
      ctx.fillStyle = 'rgba(148,163,184,0.35)';
      roundRect(ctx, x, y, size, size, 5);
      ctx.fill();
    }
  }
}

function drawHeatmapLegend(ctx, layout) {
  const lx = layout.width - 220;
  const ly = layout.height - 50;
  const lw = 180;
  const lh = 14;

  ctx.fillStyle = 'rgba(255,255,255,0.94)';
  roundRect(ctx, lx - 14, ly - 22, lw + 28, lh + 42, 12);
  ctx.fill();
  ctx.strokeStyle = 'rgba(15,23,42,0.1)';
  ctx.lineWidth = 1;
  ctx.stroke();

  ctx.fillStyle = '#334155';
  ctx.font = '800 11px Microsoft YaHei, sans-serif';
  ctx.textAlign = 'center';
  ctx.fillText('座位占用热力', lx + lw / 2, ly - 6);

  for (let i = 0; i < lw; i++) {
    ctx.fillStyle = heatColor(i / lw);
    ctx.fillRect(lx + i, ly, 1, lh);
  }

  ctx.strokeStyle = 'rgba(15,23,42,0.15)';
  roundRect(ctx, lx, ly, lw, lh, 4);
  ctx.stroke();

  ctx.fillStyle = '#64748b';
  ctx.font = '700 10px Microsoft YaHei, sans-serif';
  ctx.textAlign = 'left';
  ctx.fillText('低', lx, ly + lh + 14);
  ctx.textAlign = 'right';
  ctx.fillText('高', lx + lw, ly + lh + 14);
}
