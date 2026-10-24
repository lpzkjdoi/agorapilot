import { Routes } from '@angular/router';
import { authGuard, signedInGuard, signedOutGuard } from "./core/auth/auth.guards";
import {
  CampaignsPageComponent,
} from "./features/campaigns/pages/campaigns-page/campaigns-page.component";
import {
  DashboardPageComponent,
} from "./features/dashboard/pages/dashboard-page/dashboard-page";
import {
  MediasPageComponent,
} from "./features/medias/pages/medias-page/medias-page.component";
import {
  PublicationsPageComponent,
} from "./features/publications/pages/publications-page/publications-page.component";

export const routes: Routes = [
  {
    title: "Connexion",
    path: 'connexion',
    // Pages hors application, chargées à la demande : elles ne servent qu'une
    // fois par session, et le bundle initial est au ras de son budget.
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
  // Toute l'application exige un compte connecté au mot de passe définitif.
  {
    path: '',
    canActivateChild: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        title: "Dashboard",
        path: 'dashboard',
        component: DashboardPageComponent,
      },
      {
        title: "Campagnes",
        path: 'campagnes',
        component: CampaignsPageComponent,
      },
      {
        title: "Calendrier",
        path: 'calendrier',
        // Chargée à la demande : ses modales faisaient passer le bundle initial
        // au-delà du budget de 500 kB.
        loadComponent: () => import("./features/calendar/pages/calendar-page/calendar-page.component")
          .then((m) => m.CalendarPageComponent),
      },
      {
        title: "Publications",
        path: 'publications',
        component: PublicationsPageComponent,
      },
      {
        title: "Médiathèque",
        path: 'medias',
        component: MediasPageComponent,
      },
    ],
  },
  { path: '**', redirectTo: 'dashboard' },
];
