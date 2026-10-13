export interface NavigationItem {
  id: string;
  title: string;
  type: 'item' | 'collapse' | 'group';
  translate?: string;
  icon?: string;
  hidden?: boolean;
  url?: string;
  classes?: string;
  external?: boolean;
  target?: boolean;
  breadcrumbs?: boolean;
  children?: NavigationItem[];
  role?: string[];
  isMainParent?: boolean;
}

export const NavigationItems: NavigationItem[] = [
  {
    id: 'operations',
    title: 'Operations',
    type: 'group',
    icon: 'icon-navigation',
    children: [
      {
        id: 'dashboard',
        title: 'Dashboard',
        type: 'item',
        classes: 'nav-item',
        url: '/dashboard',
        icon: 'ti ti-dashboard',
        breadcrumbs: false
      },
      {
        id: 'devices',
        title: 'Device Registry',
        type: 'item',
        classes: 'nav-item',
        url: '/devices',
        icon: 'ti ti-scale'
      },
      {
        id: 'simulator',
        title: 'Simulator',
        type: 'item',
        classes: 'nav-item',
        url: '/simulator',
        icon: 'ti ti-device-desktop-analytics'
      },
      {
        id: 'indicator-profiles',
        title: 'Indicator Profiles',
        type: 'item',
        classes: 'nav-item',
        url: '/indicator-profiles',
        icon: 'ti ti-adjustments-horizontal'
      }
    ]
  }
];
