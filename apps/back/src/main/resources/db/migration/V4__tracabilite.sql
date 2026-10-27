-- Traçabilité : qui a créé et modifié en dernier une publication, qui a programmé
-- une diffusion. Pseudos recopiés, comme dans le journal : un compte n'est jamais
-- supprimé, mais la ligne se lit seule. Vides pour ce qui existait avant.
ALTER TABLE publications ADD COLUMN created_by VARCHAR(32);
ALTER TABLE publications ADD COLUMN updated_by VARCHAR(32);
ALTER TABLE publication_occurrences ADD COLUMN created_by VARCHAR(32);

-- Le journal accueille les actions qui ne laissent pas de ligne derrière elles :
-- une diffusion déplacée, reprise ou annulée, une publication supprimée, un canal
-- suspendu puis repris.
ALTER TABLE security_events DROP CONSTRAINT security_events_type_check;
ALTER TABLE security_events ADD CONSTRAINT security_events_type_check CHECK (type IN (
    'LOGIN_SUCCEEDED', 'LOGIN_FAILED', 'ACCOUNT_LOCKED', 'PASSWORD_CHANGED',
    'ACCOUNT_CREATED', 'PERMISSIONS_CHANGED', 'ACCOUNT_DISABLED', 'PASSWORD_RESET',
    'PUBLICATION_DELETED', 'OCCURRENCE_RESCHEDULED', 'OCCURRENCE_RETRIED', 'OCCURRENCE_CANCELLED',
    'CHANNEL_SUSPENDED', 'CHANNEL_RESUMED'
));
