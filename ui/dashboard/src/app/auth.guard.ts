import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { TokenStorageService } from './token-storage.service';

export const authGuard: CanActivateFn = (route, state) => {
  const tokenStorage = inject(TokenStorageService);
  const router = inject(Router);
  
  const token = tokenStorage.getToken();
  
  if (token && token.length > 0) {
    return true;
  }
  
  router.navigate(['/login']);
  return false;
};
