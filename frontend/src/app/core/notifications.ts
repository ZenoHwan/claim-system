import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Inbox, NotificationItem } from '../api/types';
import { Session } from './session';

/** The signed-in user's inbox, kept current by a server-sent-events stream. */
@Injectable({ providedIn: 'root' })
export class Notifications {
  private readonly http = inject(HttpClient);
  private readonly session = inject(Session);

  readonly items = signal<NotificationItem[]>([]);
  readonly unreadCount = signal(0);
  /** The most recent notification pushed live; claim screens watch this to reload themselves. */
  readonly lastPushed = signal<NotificationItem | null>(null);

  private stream?: AbortController;

  /** Loads the inbox and opens the live stream. Call once when a signed-in shell appears. */
  connect() {
    this.disconnect();
    this.http.get<Inbox>('/api/notifications').subscribe((inbox) => {
      this.items.set(inbox.items);
      this.unreadCount.set(inbox.unreadCount);
    });
    this.stream = new AbortController();
    void this.listen(this.stream.signal);
  }

  disconnect() {
    this.stream?.abort();
    this.stream = undefined;
    this.items.set([]);
    this.unreadCount.set(0);
    this.lastPushed.set(null);
  }

  markRead(item: NotificationItem) {
    if (item.read) {
      return;
    }
    this.http.post(`/api/notifications/${item.id}/read`, {}).subscribe(() => {
      this.items.update((items) => items.map((i) => (i.id === item.id ? { ...i, read: true } : i)));
      this.unreadCount.update((count) => Math.max(0, count - 1));
    });
  }

  /** Opening the bell: the badge clears at once, the server records everything as read. */
  markAllSeen() {
    if (this.unreadCount() === 0) {
      return;
    }
    this.unreadCount.set(0);
    this.http.post('/api/notifications/read-all', {}).subscribe();
  }

  /** Closing the bell: drop the unread highlight, which stays while the menu is open so new items stand out. */
  clearHighlight() {
    this.items.update((items) => items.map((i) => (i.read ? i : { ...i, read: true })));
  }

  // fetch, not EventSource: EventSource cannot send the Authorization header.
  private async listen(aborted: AbortSignal) {
    while (!aborted.aborted) {
      try {
        const response = await fetch('/api/notifications/stream', {
          headers: {
            Authorization: `Bearer ${this.session.current()?.token}`,
            Accept: 'text/event-stream',
          },
          signal: aborted,
        });
        if (response.status === 401) {
          return; // the next ordinary API call will sign the user out
        }
        const reader = response.body!.pipeThrough(new TextDecoderStream()).getReader();
        let buffer = '';
        for (;;) {
          const { value, done } = await reader.read();
          if (done) {
            break;
          }
          buffer += value.replace(/\r\n/g, '\n');
          let end: number;
          while ((end = buffer.indexOf('\n\n')) >= 0) {
            this.handle(buffer.slice(0, end));
            buffer = buffer.slice(end + 2);
          }
        }
      } catch {
        // dropped connection or abort: fall through to the retry wait
      }
      if (!aborted.aborted) {
        await new Promise((resolve) => setTimeout(resolve, 3000));
      }
    }
  }

  private handle(block: string) {
    const lines = block.split('\n');
    if (
      !lines.some((line) => line.startsWith('event:') && line.slice(6).trim() === 'notification')
    ) {
      return;
    }
    const data = lines
      .filter((line) => line.startsWith('data:'))
      .map((line) => line.slice(5).trim())
      .join('\n');
    const item = JSON.parse(data) as NotificationItem;
    if (this.items().some((existing) => existing.id === item.id)) {
      return; // already loaded with the inbox
    }
    this.items.update((items) => [item, ...items]);
    this.unreadCount.update((count) => count + 1);
    this.lastPushed.set(item);
  }
}
