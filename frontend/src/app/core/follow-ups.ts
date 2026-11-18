import { Injectable, computed, inject, signal } from '@angular/core';
import { FollowUp } from '../api/types';
import { ClaimsApi } from './claims-api';
import { Session } from './session';

/** Follow-ups for the signed-in user, shared by the nav badge and the Follow-ups page. */
@Injectable({ providedIn: 'root' })
export class FollowUps {
  private readonly api = inject(ClaimsApi);
  private readonly session = inject(Session);

  readonly items = signal<FollowUp[]>([]);
  /** Replies staff have not acted on yet. */
  readonly replies = computed(() => this.items().filter((f) => f.status === 'ANSWERED'));
  /** Questions still waiting for the claimant. */
  readonly waiting = computed(() => this.items().filter((f) => f.status === 'OPEN'));
  /** The badge: what this user has to act on. A claimant answers questions; staff review replies. */
  readonly toDo = computed(() => (this.session.isStaff() ? this.replies() : this.waiting()).length);

  load() {
    this.api.followUps(this.session.isStaff()).subscribe((items) => this.items.set(items));
  }
}
