import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from "@angular/router";
import {
  SidebarButtonComponent,
} from "./components/sidebar-button/sidebar-button.component";

@Component({
  selector: 'app-sidebar',
  imports: [
    SidebarButtonComponent,
    RouterLink,
    RouterLinkActive,
  ],
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SidebarComponent {
}
