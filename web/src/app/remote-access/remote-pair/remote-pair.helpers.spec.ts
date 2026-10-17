import { guessDeviceKey, normalizeCode, safeReturnUrl } from './remote-pair.helpers';

describe('safeReturnUrl', () => {
  it('keeps internal paths', () => {
    expect(safeReturnUrl('/campaigns/7')).toBe('/campaigns/7');
    expect(safeReturnUrl('/sessions/19?tab=dice')).toBe('/sessions/19?tab=dice');
  });

  it('rejects external or looping targets', () => {
    for (const bad of [null, '', 'https://evil.example', '//evil.example', '/\\evil.example', '/pair', '/pair?code=1']) {
      expect(safeReturnUrl(bad)).toBe('/');
    }
  });
});

describe('guessDeviceKey', () => {
  it('recognizes tablets, phones and the rest', () => {
    expect(guessDeviceKey('Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X)')).toBe('remoteAccess.pair.defaultTablet');
    expect(guessDeviceKey('Mozilla/5.0 (Linux; Android 14; SM-X710) Safari')).toBe('remoteAccess.pair.defaultTablet');
    expect(guessDeviceKey('Mozilla/5.0 (Linux; Android 14; Pixel 8) Mobile Safari')).toBe('remoteAccess.pair.defaultPhone');
    expect(guessDeviceKey('Mozilla/5.0 (iPhone; CPU iPhone OS 17_0)')).toBe('remoteAccess.pair.defaultPhone');
    expect(guessDeviceKey('Mozilla/5.0 (Windows NT 10.0; Win64; x64)')).toBe('remoteAccess.pair.defaultDevice');
  });
});

describe('normalizeCode', () => {
  it('keeps at most six digits', () => {
    expect(normalizeCode('123 456')).toBe('123456');
    expect(normalizeCode('12a3-45678')).toBe('123456');
    expect(normalizeCode(null)).toBe('');
  });
});
