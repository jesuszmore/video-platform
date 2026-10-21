import { HttpInterceptorFn } from '@angular/common/http';

// En local/docker la auth JWT esta desactivada y el backend identifica al usuario
// por la cabecera X-User-Id. Usamos "demo" para que coincida con los scripts e2e.
// En prod el backend ignora esta cabecera y exige JWT.
export const LOCAL_USER = 'demo';

export const userHeaderInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.url.startsWith('/api')) {
    return next(req.clone({ setHeaders: { 'X-User-Id': LOCAL_USER } }));
  }
  return next(req);
};
