import { Routes } from '@angular/router';
import { authGuard, permissionGuard, signedInGuard, signedOutGuard } from "./core/auth/auth.guards";
import {
  DashboardPageComponent,
} from "./features/dashboard/pages/dashboard-page/dashboard-page";

export const routes: Routes = [
  {
    title: "Connexion",
    path: 'connexion',
    // Pages hors application : elles ne servent qu'une fois par session.
    loadComponent: () => import("./features/auth/pages/login-page/login-page.component")
      .then((m) => m.LoginPageComponent),
    canActivate: [signedOutGuard],
  },
  {
    title: "Mot de passe",
    path: 'mot-de-passe',
    loadComponent: () => import("./features/auth/pages/change-password-page/change-password-page.component")
      .then((m) => m.ChangePasswordPageComponent),
    canActivate: [signedInGuard],
  },
  // Toute l'application exige un compte connecté au mot de passe définitif, et
  // chaque page son droit de lecture. Sans lui, la garde mène à la première page
  // permise (`homePath`).
  // Hors tableau de bord, la page d'arrivée, les pages sont chargées à la
  // demande : le bundle initial butait sur son budget de 500 kB à chaque ajout.
  {
    path: '',
    canActivateChild: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        title: "Dashboard",
        path: 'dashboard',
        canActivate: [permissionGuard('publications:read')],
        component: DashboardPageComponent,
      },
      {
        title: "Campagnes",
        path: 'campagnes',
        canActivate: [permissionGuard('campaigns:read')],
        loadComponent: () => import("./features/campaigns/pages/campaigns-page/campaigns-page.component")
          .then((m) => m.CampaignsPageComponent),
      },
      {
        title: "Calendrier",
        path: 'calendrier',
        canActivate: [permissionGuard('publications:read')],
        loadComponent: () => import("./features/calendar/pages/calendar-page/calendar-page.component")
          .then((m) => m.CalendarPageComponent),
      },
      {
        title: "Publications",
        path: 'publications',
        canActivate: [permissionGuard('publications:read')],
        loadComponent: () => import("./features/publications/pages/publications-page/publications-page.component")
          .then((m) => m.PublicationsPageComponent),
      },
      {
        title: "Médiathèque",
        path: 'medias',
        canActivate: [permissionGuard('medias:read')],
        loadComponent: () => import("./features/medias/pages/medias-page/medias-page.component")
          .then((m) => m.MediasPageComponent),
      },
      {
        title: "Comptes",
        path: 'comptes',
        canActivate: [permissionGuard('users:manage')],
        loadComponent: () => import("./features/users/pages/users-page/users-page.component")
          .then((m) => m.UsersPageComponent),
      },
      {
        title: "Aucun droit",
        path: 'aucun-droit',
        loadComponent: () => import("./features/auth/pages/no-access-page/no-access-page.component")
          .then((m) => m.NoAccessPageComponent),
      },
    ],
  },
  { path: '**', redirectTo: 'dashboard' },
];
