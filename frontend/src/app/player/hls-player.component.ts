import {
  AfterViewInit,
  Component,
  ElementRef,
  Input,
  OnChanges,
  OnDestroy,
  SimpleChanges,
  ViewChild,
} from '@angular/core';

@Component({
  selector: 'app-hls-player',
  standalone: true,
  template: '<video #video class="player" controls playsinline></video>',
  styles: [
    ':host{display:block}',
    '.player{width:100%;border-radius:10px;background:#000;aspect-ratio:16/9}',
  ],
})
export class HlsPlayerComponent implements AfterViewInit, OnChanges, OnDestroy {
  @ViewChild('video', { static: true }) private videoRef!: ElementRef<HTMLVideoElement>;

  @Input() src?: string;

  private hls?: import('hls.js').default;

  ngAfterViewInit(): void {
    void this.attach();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (!changes['src'].firstChange) {
      void this.attach();
    }
  }

  ngOnDestroy(): void {
    this.destroy();
  }

  private async attach(): Promise<void> {
    const element = this.videoRef?.nativeElement;
    if (!element) {
      return;
    }
    this.destroy();
    if (!this.src) {
      element.removeAttribute('src');
      element.load();
      return;
    }
    const { default: Hls } = await import('hls.js');
    if (Hls.isSupported()) {
      const hls = new Hls();
      hls.loadSource(this.src);
      hls.attachMedia(element);
      this.hls = hls;
    } else {
      element.src = this.src;
    }
  }

  private destroy(): void {
    this.hls?.destroy();
    this.hls = undefined;
  }
}
