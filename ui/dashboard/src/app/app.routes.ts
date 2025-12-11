import { Routes } from '@angular/router';
import { OrdersComponent } from './orders/orders.component';
import { DlqComponent } from './dlq/dlq.component';
import { ChaosComponent } from './chaos/chaos.component';
import { SloComponent } from './slo/slo.component';
import { LoginComponent } from './login/login.component';
import { authGuard } from './auth.guard';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'orders', component: OrdersComponent, canActivate: [authGuard] },
  { path: 'dlq', component: DlqComponent, canActivate: [authGuard] },
  { path: 'chaos', component: ChaosComponent, canActivate: [authGuard] },
  { path: 'slo', component: SloComponent, canActivate: [authGuard] },
  { path: '', redirectTo: '/orders', pathMatch: 'full' }
];
