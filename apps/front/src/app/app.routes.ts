import { Routes } from '@angular/router';
import {
  DashboardPageComponent,
} from "./features/dashboard/pages/dashboard-page/dashboard-page";
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
    title: "Publications",
    path: 'publications',
    component: PublicationsPageComponent,
  },
];
