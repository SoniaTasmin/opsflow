import { NAV_ITEMS } from './nav.config';

describe('navigation', () => {
  it('restricts administration to administrators', () => {
    const adminItems = NAV_ITEMS.filter((item) => item.path.startsWith('/admin'));

    expect(adminItems.length).toBeGreaterThan(0);
    for (const item of adminItems) {
      expect(item.roles).toEqual(['ADMIN']);
    }
  });

  it('offers home to every role', () => {
    const home = NAV_ITEMS.find((item) => item.path === '/home');

    expect(home?.roles).toContain('USER');
    expect(home?.roles).toContain('REVIEWER');
    expect(home?.roles).toContain('ADMIN');
  });
});
