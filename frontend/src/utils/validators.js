export function validateConfig(config) {
  const errors = {};
  const studentCount = Number(config.studentCount);
  const windowCount = Number(config.windowCount);
  const simDurationTick = Number(config.simDurationTick);
  const seatCount = Number(config.seatCount);
  const maxSeatWaitCapacity = Number(config.maxSeatWaitCapacity);
  const maxSeatWaitTick = Number(config.maxSeatWaitTick);

  if (!Number.isFinite(studentCount) || studentCount <= 0 || studentCount >= 5000) {
    errors.studentCount = '预计人数必须大于 0 且小于 5000';
  }

  if (!Number.isFinite(windowCount) || windowCount < 1 || windowCount > 20) {
    errors.windowCount = '窗口开放数量必须在 1 至 20 之间';
  }

  if (!Number.isFinite(simDurationTick) || simDurationTick < 60 || simDurationTick > 10800) {
    errors.simDurationTick = '模拟时长建议在 60 至 10800 秒之间（1 Tick≈1秒）';
  }

  if (!Number.isFinite(seatCount) || seatCount < 20 || seatCount > 1200) {
    errors.seatCount = '座位数建议在 20 至 1200 之间';
  }

  if (!Number.isFinite(maxSeatWaitCapacity) || maxSeatWaitCapacity < 20 || maxSeatWaitCapacity > 500) {
    errors.maxSeatWaitCapacity = '等座区上限建议在 20 至 500 之间';
  }

  if (!Number.isFinite(maxSeatWaitTick) || maxSeatWaitTick < 30 || maxSeatWaitTick > 1800) {
    errors.maxSeatWaitTick = '等座耐心建议在 30 至 1800 秒之间（1 Tick≈1秒）';
  }

  return {
    valid: Object.keys(errors).length === 0,
    errors
  };
}

export function toPayload(config) {
  return {
    studentCount: Number(config.studentCount),
    windowCount: Number(config.windowCount),
    simDurationTick: Number(config.simDurationTick),
    seatCount: Number(config.seatCount),
    maxQueueLength: Number(config.maxQueueCapacity),
    maxQueueCapacity: Number(config.maxQueueCapacity),
    maxSeatWaitCapacity: Number(config.maxSeatWaitCapacity),
    maxSeatWaitTick: Number(config.maxSeatWaitTick),
    renderThreshold: Number(config.renderThreshold)
  };
}
