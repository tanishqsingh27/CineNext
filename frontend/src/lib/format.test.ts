import { describe, expect, it } from 'vitest';
import { cinemaDates, money, showtimeLabel } from './format';

describe('cinema time and pricing', () => {
  it('uses the Indian calendar date across a UTC midnight boundary and year rollover', () => {
    const dates = cinemaDates(new Date('2026-12-31T20:00:00Z'));
    expect(dates[0].value).toBe('2027-01-01');
    expect(dates.at(-1)?.value).toBe('2027-01-07');
    expect(new Set(dates.map((date) => date.value)).size).toBe(7);
  });
  it('renders the same showtime in cinema time regardless of browser timezone', () => {
    expect(showtimeLabel('2026-11-13T04:30:00Z')).toMatch(/10:00\s*am/i);
  });
  it('converts integer paise to rupees', () => {
    expect(money(22000)).toBe('₹220');
  });
});
