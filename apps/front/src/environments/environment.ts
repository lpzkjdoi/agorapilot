export const environment = {
  // URL relative : le front et l'API sont servis sur la même origine.
  // - en preprod/prod, le nginx du conteneur front proxifie /api vers le back ;
  // - en local, `ng serve` fait de même via proxy.conf.json.
  // Aucune reconstruction de l'image n'est donc nécessaire d'un environnement à
  // l'autre, et aucune configuration CORS n'entre en jeu.
  apiUrl: '/api',
};
