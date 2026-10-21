import { Component, signal } from '@angular/core';
import { VideoUploadService } from '../core/video-upload.service';
import { VideoDto } from '../core/upload.models';
import { HlsPlayerComponent } from '../player/hls-player.component';

@Component({
  selector: 'app-upload',
  standalone: true,
  imports: [HlsPlayerComponent],
  templateUrl: './upload.component.html',
  styleUrl: './upload.component.css',
})
export class UploadComponent {
  readonly progress = signal(0);
  readonly phase = signal<'idle' | 'uploading' | 'processing' | 'ready' | 'error'>('idle');
  readonly status = signal<string>('');
  readonly error = signal<string>('');
  readonly video = signal<VideoDto | null>(null);

  private readonly acceptedTypes = 'video/mp4,video/webm,video/quicktime,video/x-matroska';

  constructor(private readonly uploader: VideoUploadService) {}

  get accept(): string {
    return this.acceptedTypes;
  }

  async onFileSelected(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    this.reset();
    this.phase.set('uploading');
    try {
      const queued = await this.uploader.upload(file, (percent) => this.progress.set(percent));
      this.status.set(queued.status);
      this.phase.set('processing');
      const finalVideo = await this.uploader.waitUntilReady(queued.id, (v) => this.status.set(v.status));
      this.video.set(finalVideo);
      if (finalVideo.status === 'READY') {
        this.phase.set('ready');
      } else {
        this.fail(finalVideo.errorMessage ?? `Estado final: ${finalVideo.status}`);
      }
    } catch (err) {
      this.fail(err instanceof Error ? err.message : 'Error desconocido durante la subida');
    } finally {
      input.value = '';
    }
  }

  private reset(): void {
    this.progress.set(0);
    this.status.set('');
    this.error.set('');
    this.video.set(null);
  }

  private fail(message: string): void {
    this.error.set(message);
    this.phase.set('error');
  }
}
