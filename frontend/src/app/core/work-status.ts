import { Attendance, User } from './models';

export type WorkStatus = 'NOT_CHECKED_IN' | 'WORKING' | 'TEMP_OUT' | 'FINISHED';

export const WORK_STATUS_LABEL: Record<WorkStatus, string> = {
  NOT_CHECKED_IN: 'Chưa vào làm',
  WORKING: 'Đang làm việc',
  TEMP_OUT: 'Tạm vắng / Ra ngoài',
  FINISHED: 'Đã tan làm',
};

/** Hour (24h) after which a check-out is considered "end of shift" rather than "stepped out". */
const SHIFT_END_HOUR = 17;
const SHIFT_END_MINUTE = 0;
const VIETNAM_TIME_ZONE = 'Asia/Ho_Chi_Minh';

export function parseVietnamDateTime(value: string): Date {
  return new Date(/[zZ]|[+-]\d{2}:?\d{2}$/.test(value) ? value : `${value}+07:00`);
}

export function vietnamTime(value: string): string {
  return new Intl.DateTimeFormat('vi-VN', {
    timeZone: VIETNAM_TIME_ZONE,
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).format(parseVietnamDateTime(value));
}

/**
 * Derives a live status purely from today's check-in/check-out timestamps — no schema change
 * needed. A check-out before the shift-end threshold is treated as a temporary absence; a
 * check-out at/after the threshold is treated as the end of the shift.
 */
export function computeWorkStatus(todayEntry: Pick<Attendance, 'date' | 'checkInTime' | 'checkOutTime'> | undefined): WorkStatus {
  if (!todayEntry || !todayEntry.checkInTime) return 'NOT_CHECKED_IN';
  if (!todayEntry.checkOutTime) return 'WORKING';
  if (todayEntry.date < localDateIso()) return 'FINISHED';
  const [hour, minute] = vietnamTime(todayEntry.checkOutTime).split(':').map(Number);
  return hour < SHIFT_END_HOUR || (hour === SHIFT_END_HOUR && minute < SHIFT_END_MINUTE)
    ? 'TEMP_OUT'
    : 'FINISHED';
}

export interface PersonStatusRow extends User {
  workStatus: WorkStatus;
  workStatusLabel: string;
  checkInTime?: string;
  checkOutTime?: string;
}

export interface DepartmentStatusGroup {
  departmentName: string;
  people: PersonStatusRow[];
}

export function localDateIso(date = new Date()): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: VIETNAM_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(date);
  const value = (type: Intl.DateTimeFormatPartTypes) => parts.find((part) => part.type === type)?.value ?? '';
  return `${value('year')}-${value('month')}-${value('day')}`;
}

export function isAttendanceAdjustmentDateAllowed(date: string, today = localDateIso()): boolean {
  const earliestDate = new Date(`${today}T12:00:00Z`);
  earliestDate.setUTCDate(earliestDate.getUTCDate() - 2);
  const earliest = earliestDate.toISOString().slice(0, 10);
  return date >= earliest && date <= today;
}

/** Groups a flat user list by department and attaches each user's live work status for today. */
export function groupPeopleByDepartmentWithStatus(people: User[], allAttendance: Attendance[]): DepartmentStatusGroup[] {
  const today = localDateIso();
  const todayByUser = new Map<number, Attendance>();
  for (const record of allAttendance) {
    if (record.date === today) todayByUser.set(record.userId, record);
  }

  const byDept = new Map<string, PersonStatusRow[]>();
  for (const person of people) {
    const entry = todayByUser.get(person.id);
    const status = computeWorkStatus(entry);
    const row: PersonStatusRow = {
      ...person,
      workStatus: status,
      workStatusLabel: WORK_STATUS_LABEL[status],
      checkInTime: entry?.checkInTime,
      checkOutTime: entry?.checkOutTime,
    };
    const key = person.departmentName || 'Chưa thuộc phòng ban';
    if (!byDept.has(key)) byDept.set(key, []);
    byDept.get(key)!.push(row);
  }
  return Array.from(byDept.entries())
    .sort((a, b) => a[0].localeCompare(b[0]))
    .map(([departmentName, deptPeople]) => ({ departmentName, people: deptPeople }));
}
