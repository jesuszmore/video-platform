import { Routes } from '@angular/router';
import { GalleryComponent } from './gallery/gallery.component';
import { UploadComponent } from './upload/upload.component';

export const routes: Routes = [
  { path: '', component: GalleryComponent },
  { path: 'upload', component: UploadComponent },
  { path: '**', redirectTo: '' },
];
