import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import {
  DashboardPageComponent,
} from "./features/dashboard/pages/dashboard-page/dashboard-page";

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, DashboardPageComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css',
})
export class AppComponent {
  title = 'front';
}
