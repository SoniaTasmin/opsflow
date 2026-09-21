import { InMemorySecurityStorage } from './in-memory-storage';

describe('InMemorySecurityStorage', () => {
  it('keeps values only in memory', () => {
    const storage = new InMemorySecurityStorage();
    storage.write('token', 'secret');

    expect(storage.read('token')).toBe('secret');
    // Nothing reaches the browser stores, where an XSS bug could read it back.
    expect(localStorage.getItem('token')).toBeNull();
    expect(sessionStorage.getItem('token')).toBeNull();
  });

  it('returns null for an unknown key', () => {
    expect(new InMemorySecurityStorage().read('missing')).toBeNull();
  });

  it('removes and clears', () => {
    const storage = new InMemorySecurityStorage();
    storage.write('a', '1');
    storage.write('b', '2');

    storage.remove('a');
    expect(storage.read('a')).toBeNull();

    storage.clear();
    expect(storage.read('b')).toBeNull();
  });
});
