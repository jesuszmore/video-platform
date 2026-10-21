export interface InitUploadRequest {
  filename: string;
  sizeBytes: number;
  contentType: string;
}

export interface InitUploadResponse {
  videoId: string;
  key: string;
  uploadId: string;
  partSize: number;
  totalParts: number;
  partUrlTtlSeconds: number;
}

export interface PartUrlResponse {
  partNumber: number;
  url: string;
}

export interface CompletedPart {
  partNumber: number;
  eTag: string;
}

export interface VideoDto {
  id: string;
  status: 'UPLOADING' | 'QUEUED' | 'PROCESSING' | 'READY' | 'FAILED' | 'ABORTED';
  sizeBytes: number;
  hlsManifestUrl?: string;
  thumbnailUrl?: string;
  durationSeconds?: number;
  errorMessage?: string;
  createdAt: string;
  updatedAt: string;
}
