import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { AdminComponent } from './theme/layout/admin/admin.component';

const appRoutes: Routes = [
  {
    path: '',
    component: AdminComponent,
    children: [
      {
        path: '',
        redirectTo: '/dashboard',
        pathMatch: 'full'
      },
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard.component').then((c) => c.DashboardComponent)
      },
      {
        path: 'devices',
        loadComponent: () => import('./features/devices/devices.component').then((c) => c.DevicesComponent)
      },
      {
        path: 'simulator',
        loadComponent: () => import('./features/simulator/simulator.component').then((c) => c.SimulatorComponent)
      },
      {
        path: 'indicator-profiles',
        loadComponent: () => import('./features/indicator-profiles/indicator-profiles.component').then((c) => c.IndicatorProfilesComponent)
      },
      {
        path: '**',
        redirectTo: '/dashboard'
      }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forRoot(appRoutes)],
  exports: [RouterModule]
})
export class AppRoutingModule {}
