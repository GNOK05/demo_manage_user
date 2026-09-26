import { computeWorkStatus, isAttendanceAdjustmentDateAllowed, localDateIso } from './work-status';

describe('computeWorkStatus', () => {
  it('treats an early checkout today as temporary out', () => {
    const today = localDateIso();

    expect(computeWorkStatus({
      date: today,
      checkInTime: `${today}T08:00:00`,
      checkOutTime: `${today}T15:00:00`,
    })).toBe('TEMP_OUT');
  });

  it('treats an early checkout from a previous day as finished', () => {
    expect(computeWorkStatus({
      date: '2000-01-01',
      checkInTime: '2000-01-01T08:00:00',
      checkOutTime: '2000-01-01T15:00:00',
    })).toBe('FINISHED');
  });

  it('uses Vietnam local date when UTC is still on the previous day', () => {
    expect(localDateIso(new Date('2026-09-25T18:00:00Z'))).toBe('2026-09-26');
  });

  it('allows yesterday and the day before yesterday in Vietnam time, but not older or future dates', () => {
    expect(isAttendanceAdjustmentDateAllowed('2026-09-26', '2026-09-27')).toBe(true);
    expect(isAttendanceAdjustmentDateAllowed('2026-09-25', '2026-09-27')).toBe(true);
    expect(isAttendanceAdjustmentDateAllowed('2026-09-24', '2026-09-27')).toBe(false);
    expect(isAttendanceAdjustmentDateAllowed('2026-09-28', '2026-09-27')).toBe(false);
  });
});