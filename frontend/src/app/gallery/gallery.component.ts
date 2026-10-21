import { Component, OnDestroy, OnInit, computed, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { VideoDto } from '../core/upload.models';
import { VideoUploadService } from '../core/video-upload.service';
import { HlsPlayerComponent } from '../player/hls-player.component';

type Filter = 'ALL' | 'READY' | 'PROCESSING' | 'FAILED';

const PENDING = ['UPLOADING', 'QUEUED', 'PROCESSING'];

@Component({
  selector: 'app-gallery',
  standalone: true,
  imports: [RouterLink, DatePipe, HlsPlayerComponent],
  templateUrl: './gallery.component.html',
  styleUrl: './gallery.component.css',
})
export class GalleryComponent implements OnInit, OnDestroy {
  readonly videos = signal<VideoDto[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly filter = signal<Filter>('ALL');
  readonly selected = signal<VideoDto | null>(null);
  readonly actionId = signal<string | null>(null);

  private readonly now = signal(Date.now());

  readonly filters: Filter[] = ['ALL', 'READY', 'PROCESSING', 'FAILED'];

  readonly visible = computed(() => {
    const filter = this.filter();
    const all = this.videos();
    if (filter === 'ALL') {
      return all;
    }
    if (filter === 'PROCESSING') {
      return all.filter((v) => PENDING.includes(v.status));
    }
    return all.filter((v) => v.status === filter);
  });

  readonly counts = computed(() => {
    const all = this.videos();
    return {
      ALL: all.length,
      READY: all.filter((v) => v.status === 'READY').length,
      PROCESSING: all.filter((v) => PENDING.includes(v.status)).length,
      FAILED: all.filter((v) => v.status === 'FAILED' || v.status === 'ABORTED').length,
    } as Record<Filter, number>;
  });

  private pollHandle?: ReturnType<typeof setTimeout>;

  constructor(private readonly api: VideoUploadService) {}

  ngOnInit(): void {
    void this.load();
  }

  ngOnDestroy(): void {
    clearTimeout(this.pollHandle);
  }

  async load(silent = false): Promise<void> {
    if (!silent) {
      this.loading.set(true);
    }
    this.error.set('');
    try {
      const videos = await this.api.listVideos();
      this.videos.set(videos);
      this.schedulePoll(videos);
    } catch (err) {
      this.error.set(err instanceof Error ? err.message : 'No se pudieron cargar los videos');
    } finally {
      this.now.set(Date.now());
      this.loading.set(false);
    }
  }

  setFilter(filter: Filter): void {
    this.filter.set(filter);
  }

  open(video: VideoDto): void {
    if (video.status === 'READY' && video.hlsManifestUrl) {
      this.selected.set(video);
    }
  }

  close(): void {
    this.selected.set(null);
  }

  canRetry(video: VideoDto): boolean {
    return video.status === 'FAILED' || video.status === 'QUEUED';
  }

  async retry(video: VideoDto): Promise<void> {
    this.actionId.set(video.id);
    this.error.set('');
    try {
      await this.api.retry(video.id);
      await this.load(true);
    } catch (err) {
      this.error.set(err instanceof Error ? err.message : 'No se pudo reintentar');
    } finally {
      this.actionId.set(null);
    }
  }

  async remove(video: VideoDto): Promise<void> {
    if (!window.confirm(`Eliminar el video ${video.id.slice(0, 8)}… y sus archivos?`)) {
      return;
    }
    this.actionId.set(video.id);
    this.error.set('');
    try {
      await this.api.remove(video.id);
      this.videos.update((list) => list.filter((v) => v.id !== video.id));
    } catch (err) {
      this.error.set(err instanceof Error ? err.message : 'No se pudo eliminar');
    } finally {
      this.actionId.set(null);
    }
  }

  sizeLabel(bytes: number): string {
    if (bytes >= 1024 * 1024 * 1024) {
      return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
    }
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  elapsedLabel(video: VideoDto): string {
    if (!PENDING.includes(video.status)) {
      return '';
    }
    const since = new Date(video.updatedAt ?? video.createdAt).getTime();
    const seconds = Math.max(0, Math.round((this.now() - since) / 1000));
    if (seconds < 60) {
      return `hace ${seconds}s`;
    }
    const minutes = Math.floor(seconds / 60);
    if (minutes < 60) {
      return `hace ${minutes} min`;
    }
    return `hace ${Math.floor(minutes / 60)} h`;
  }

  private schedulePoll(videos: VideoDto[]): void {
    clearTimeout(this.pollHandle);
    if (videos.some((v) => PENDING.includes(v.status))) {
      this.pollHandle = setTimeout(() => void this.load(true), 3000);
    }
  }
}
