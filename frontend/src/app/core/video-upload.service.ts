import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom, timeout } from 'rxjs';
import {
  CompletedPart,
  InitUploadResponse,
  PartUrlResponse,
  VideoDto,
} from './upload.models';

@Injectable({ providedIn: 'root' })
export class VideoUploadService {
  private readonly api = '/api/uploads';
  private readonly videosApi = '/api/videos';
  private readonly concurrency = 4;

  constructor(private readonly http: HttpClient) {}

  async upload(file: File, onProgress: (percent: number) => void): Promise<VideoDto> {
    const init = await firstValueFrom(
      this.http.post<InitUploadResponse>(`${this.api}/init`, {
        filename: file.name,
        sizeBytes: file.size,
        contentType: file.type || 'video/mp4',
      }),
    );

    const total = init.totalParts;
    const parts = new Map<number, string>();
    let uploaded = 0;
    const report = () => onProgress(Math.min(100, Math.round((uploaded / total) * 100)));

    const uploadPart = async (partNumber: number): Promise<void> => {
      const start = (partNumber - 1) * init.partSize;
      const blob = file.slice(start, Math.min(start + init.partSize, file.size));
      const urls = await firstValueFrom(
        this.http.get<PartUrlResponse[]>(`${this.api}/${init.videoId}/parts`, {
          params: { numbers: partNumber },
        }),
      );
      const etag = await this.putPartWithRetry(urls[0].url, blob, 3);
      parts.set(partNumber, etag);
      uploaded++;
      report();
    };

    const queue: number[] = [];
    for (let i = 1; i <= total; i++) {
      queue.push(i);
    }
    const workerCount = Math.min(this.concurrency, total);
    const workers = Array.from({ length: workerCount }, () =>
      (async () => {
        while (queue.length > 0) {
          const next = queue.shift() as number;
          await uploadPart(next);
        }
      })(),
    );
    await Promise.all(workers);

    const completed: CompletedPart[] = [...parts.entries()]
      .map(([partNumber, eTag]) => ({ partNumber, eTag }))
      .sort((a, b) => a.partNumber - b.partNumber);

    return firstValueFrom(
      this.http.post<VideoDto>(`${this.api}/${init.videoId}/complete`, { parts: completed }),
    );
  }

  getVideo(videoId: string): Promise<VideoDto> {
    return firstValueFrom(this.http.get<VideoDto>(`${this.videosApi}/${videoId}`));
  }

  listVideos(): Promise<VideoDto[]> {
    return firstValueFrom(this.http.get<VideoDto[]>(this.videosApi));
  }

  retry(videoId: string): Promise<VideoDto> {
    return firstValueFrom(this.http.post<VideoDto>(`${this.videosApi}/${videoId}/retry`, {}));
  }

  remove(videoId: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.videosApi}/${videoId}`));
  }

  async waitUntilReady(videoId: string, onStatus: (video: VideoDto) => void): Promise<VideoDto> {
    for (let attempt = 0; attempt < 400; attempt++) {
      const video = await this.getVideo(videoId);
      onStatus(video);
      if (video.status === 'READY' || video.status === 'FAILED' || video.status === 'ABORTED') {
        return video;
      }
      await new Promise((resolve) => setTimeout(resolve, 3000));
    }
    throw new Error('Timeout esperando el procesamiento del video');
  }

  private putPartWithRetry(url: string, blob: Blob, attempts: number): Promise<string> {
    return new Promise<string>((resolve, reject) => {
      const attempt = (remaining: number) => {
        this.http
          .put(url, blob, { observe: 'response', responseType: 'text' })
          .pipe(timeout(120000))
          .subscribe({
            next: (response: HttpResponse<string>) => {
              const etag = response.headers.get('ETag') ?? response.headers.get('etag') ?? '';
              if (!etag) {
                reject(new Error('S3 no devolvio ETag. Revisa ExposeHeaders: ETag en CORS.'));
                return;
              }
              resolve(etag.replace(/"/g, ''));
            },
            error: (error) => {
              if (remaining > 1) {
                setTimeout(() => attempt(remaining - 1), (4 - remaining) * 750);
              } else {
                reject(error);
              }
            },
          });
      };
      attempt(attempts);
    });
  }
}
