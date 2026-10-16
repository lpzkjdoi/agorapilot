import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import {
  ChannelSuspensionBannerComponent,
} from "./core/channel-suspensions/components/channel-suspension-banner/channel-suspension-banner.component";
import { NavbarComponent } from "./core/layout/navbar/navbar.component";

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, NavbarComponent, ChannelSuspensionBannerComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css',
})
export class AppComponent {
  title = 'front';
}
