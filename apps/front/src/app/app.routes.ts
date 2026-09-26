import { Routes } from '@angular/router';
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
];
