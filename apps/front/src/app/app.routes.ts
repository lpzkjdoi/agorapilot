import { Routes } from '@angular/router';
import {
  DashboardPageComponent,
} from "./features/dashboard/pages/dashboard-page/dashboard-page";

export const routes: Routes = [
  {
    title: "Dashboard",
    path: 'dashboard',
    component: DashboardPageComponent,
  },
];
