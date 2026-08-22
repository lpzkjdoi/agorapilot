export const environment = {
  // URL relative : le front et l'API sont servis sur la même origine.
  // - en preprod/prod, le nginx du conteneur front proxifie /api vers le back ;
  // - en local, `ng serve` fait de même via proxy.conf.json.
  // Aucune configuration CORS n'entre donc en jeu, et cette valeur est la même
  // partout — il n'y a rien à substituer au build ni au démarrage.
  //
  // L'image front dans son ensemble, elle, n'est plus portable d'un
  // environnement à l'autre : `build-info.ts` y fige l'environnement servi
  // (badge du bandeau), et la CI le réécrit avant chaque build.
  apiUrl: '/api',
};
