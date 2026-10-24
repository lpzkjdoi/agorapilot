import { ALL_PERMISSIONS, matchingProfile, PROFILES } from './permissions';

describe('permissions', () => {
  it('should list each of the 13 permissions exactly once', () => {
    expect(ALL_PERMISSIONS.length).toBe(13);
    expect(new Set(ALL_PERMISSIONS).size).toBe(13);
  });

  it('should nest the profiles: each one holds everything the previous one does', () => {
    for (let i = 1; i < PROFILES.length; i++) {
      expect(PROFILES[i].permissions).toEqual(expect.arrayContaining(PROFILES[i - 1].permissions));
    }
  });

  it('should keep the account administration for the Administrator profile only', () => {
    const holders = PROFILES.filter((profile) => profile.permissions.includes('users:manage'));
    expect(holders.map((profile) => profile.label)).toEqual(['Administrateur']);
  });

  it('should recognise a profile whatever the order, and nothing else', () => {
    expect(matchingProfile(['medias:read', 'publications:read', 'campaigns:read'])?.label).toBe('Lecteur');
    expect(matchingProfile(['medias:read'])).toBeNull();
    expect(matchingProfile([])).toBeNull();
  });
});
