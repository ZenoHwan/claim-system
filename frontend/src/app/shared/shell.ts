import { DatePipe } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatToolbarModule } from '@angular/material/toolbar';
import { NotificationItem } from '../api/types';
import { Notifications } from '../core/notifications';
import { Session } from '../core/session';

/** Signed-in frame for both areas: toolbar, navigation, notification bell and the routed page. */
@Component({
  selector: 'app-shell',
  imports: [
    DatePipe,
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
    MatBadgeModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatToolbarModule,
  ],
  template: `
    <mat-toolbar class="bar">
      <a class="brand" [routerLink]="session.isStaff() ? '/admin/queue' : '/claims'">
        {{ session.isStaff() ? 'Claims Admin' : 'Claims Portal' }}
      </a>
      @if (session.isStaff()) {
        <nav>
          <a matButton routerLink="/admin/queue" routerLinkActive="active">Queue</a>
          <a matButton routerLink="/admin/my-claims" routerLinkActive="active">My claims</a>
          @if (session.isManager()) {
            <a matButton routerLink="/admin/dashboard" routerLinkActive="active">Dashboard</a>
          }
        </nav>
      }
      <span class="spacer"></span>
      <button matIconButton [matMenuTriggerFor]="inbox" aria-label="Notifications">
        <mat-icon
          [matBadge]="notifications.unreadCount()"
          [matBadgeHidden]="notifications.unreadCount() === 0"
          matBadgeColor="warn"
          aria-hidden="false"
          >notifications</mat-icon
        >
      </button>
      <button matButton [matMenuTriggerFor]="account">{{ session.current()?.name }}</button>
    </mat-toolbar>

    <mat-menu #inbox="matMenu" class="inbox-menu">
      @for (item of notifications.items(); track item.id) {
        <button mat-menu-item class="inbox-item" [class.unread]="!item.read" (click)="open(item)">
          <span class="message">{{ item.message }}</span>
          <span class="muted when">{{ item.createdAt | date: 'd MMM, HH:mm' }}</span>
        </button>
      } @empty {
        <p class="muted empty">No notifications yet</p>
      }
    </mat-menu>

    <mat-menu #account="matMenu">
      <p class="muted empty">{{ session.current()?.role }} · {{ session.current()?.market }}</p>
      <button mat-menu-item (click)="session.signOut()">Sign out</button>
    </mat-menu>

    <router-outlet />
  `,
  styles: `
    .bar {
      gap: 8px;
      border-bottom: 1px solid var(--mat-sys-outline-variant);
      background: var(--mat-sys-surface-container);
    }
    .brand {
      font: var(--mat-sys-title-large);
      color: inherit;
      text-decoration: none;
      margin-right: 16px;
    }
    .spacer {
      flex: 1;
    }
    .active {
      background: var(--mat-sys-secondary-container);
    }
    .empty {
      margin: 8px 16px;
    }
    .inbox-item {
      height: auto;
      padding-block: 8px;
    }
    .inbox-item .message {
      display: block;
      white-space: normal;
    }
    .inbox-item.unread .message {
      font-weight: 500;
    }
    .inbox-item .when {
      font: var(--mat-sys-body-small);
    }
  `,
})
export class Shell implements OnInit, OnDestroy {
  protected readonly session = inject(Session);
  protected readonly notifications = inject(Notifications);
  private readonly router = inject(Router);

  ngOnInit() {
    this.notifications.connect();
  }

  ngOnDestroy() {
    this.notifications.disconnect();
  }

  protected open(item: NotificationItem) {
    this.notifications.markRead(item);
    void this.router.navigate([this.session.isStaff() ? '/admin/claims' : '/claims', item.claimId]);
  }
}
