import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { finalize } from 'rxjs';
import Swal from 'sweetalert2';

import { WeightDisplayMode, WeightReading, WeighbridgeDevice, WbmsApiService } from 'src/app/core/wbms-api.service';
import { SHARED_IMPORTS } from 'src/app/theme/shared/shared.module';

@Component({
  selector: 'app-dashboard',
  imports: [...SHARED_IMPORTS],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent implements OnInit, OnDestroy {
  devices: WeighbridgeDevice[] = [];
  readings: WeightReading[] = [];
  liveReading?: WeightReading;
  liveCellWeightsKg: number[] = [];
  liveCellCount = 0;
  liveDisplayMode: WeightDisplayMode = 'SINGLE_CELL';
  liveBvrKg?: number;
  selectedDeviceUuid = '';
  loading = false;
  capturing = false;
  actionDeviceUuid = '';
  connectedDeviceUuid = '';
  message = '';
  messageType: 'success' | 'warning' | 'danger' = 'warning';
  liveStatusLoading = false;
  private actionTimeout?: number;
  private livePollTimer?: number;
  private lastActivityAt = Date.now();
  private visibilityHandler = () => {
    if (!document.hidden) {
      this.markUserActive();
    }
  };
  private activityHandler = () => this.markUserActive();

  constructor(
    private wbmsApi: WbmsApiService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.devices = this.wbmsApi.getCachedDevices();
    this.selectDefaultDevice();
    this.refreshDashboard();
    this.scheduleLiveStatusPoll(0);
    document.addEventListener('visibilitychange', this.visibilityHandler);
    window.addEventListener('mousemove', this.activityHandler, { passive: true });
    window.addEventListener('keydown', this.activityHandler);
    window.addEventListener('click', this.activityHandler, { passive: true });
    window.addEventListener('touchstart', this.activityHandler, { passive: true });
  }

  ngOnDestroy(): void {
    document.removeEventListener('visibilitychange', this.visibilityHandler);
    window.removeEventListener('mousemove', this.activityHandler);
    window.removeEventListener('keydown', this.activityHandler);
    window.removeEventListener('click', this.activityHandler);
    window.removeEventListener('touchstart', this.activityHandler);
    if (this.livePollTimer) {
      window.clearTimeout(this.livePollTimer);
    }
    if (this.actionTimeout) {
      window.clearTimeout(this.actionTimeout);
    }
  }

  refreshDashboard(): void {
    this.loadDevices();
    this.loadReadings();
    this.loadLiveStatus(true);
  }

  loadDevices(): void {
    this.loading = true;
    this.wbmsApi.listDevices(0, 1000).subscribe({
      next: (response) => {
        if (response.status) {
          this.devices = response.data ?? [];
        } else if (response.code === 6004) {
          this.devices = [];
        }

        this.loading = false;

        this.selectDefaultDevice();

        if (!response.status && response.code !== 6004) {
          this.setMessage(response.description, 'warning');
        }
      },
      error: (error) => {
        this.loading = false;
        this.setMessage(error?.message || 'Unable to reach WBMS backend', 'danger');
      }
    });
  }

  loadReadings(): void {
    this.wbmsApi.listReadings(0, 10).subscribe({
      next: (response) => {
        this.readings = response.status ? response.data ?? [] : [];
        if (!response.status && response.code !== 6004) {
          this.setMessage(response.description, 'warning');
        }
      },
      error: (error) => {
        this.setMessage(error?.message || 'Unable to load captured readings', 'danger');
      }
    });
  }

  loadLiveStatus(force = false): void {
    if (this.liveStatusLoading) {
      return;
    }

    if (!force && this.isUserIdle()) {
      this.scheduleLiveStatusPoll(3000);
      return;
    }

    this.liveStatusLoading = true;
    this.wbmsApi.liveCaptureStatus().subscribe({
      next: (response) => {
        const status = response.data;
        this.connectedDeviceUuid = response.status && status?.connected ? status.deviceUuid || '' : '';
        if (this.connectedDeviceUuid) {
          this.selectedDeviceUuid = this.connectedDeviceUuid;
        }
        if (response.status && status?.connected && status.latestWeightKg !== undefined) {
          this.liveDisplayMode = status.weightDisplayMode || 'SINGLE_CELL';
          this.liveCellWeightsKg = status.latestCellWeightsKg || [];
          this.liveCellCount = status.cellCount || this.liveCellWeightsKg.length;
          this.liveBvrKg = status.indicatorGvmKg && status.indicatorGvmKg > 0
            ? status.indicatorGvmKg
            : status.bvrKg ?? status.latestWeightKg;
          this.liveReading = {
            uuid: 'live',
            deviceUuid: status.deviceUuid || '',
            deviceId: status.deviceId || '',
            deviceName: status.deviceName || '',
            weightKg: status.latestWeightKg,
            rawLine: status.latestRawLine || '',
            capturedAt: status.latestReadAt || new Date().toISOString()
          };
        } else {
          this.liveReading = undefined;
          this.liveCellWeightsKg = [];
          this.liveCellCount = 0;
          this.liveBvrKg = undefined;
          this.liveDisplayMode = 'SINGLE_CELL';
        }
        this.liveStatusLoading = false;
        this.cdr.detectChanges();
        this.scheduleNextLiveStatusPoll();
      },
      error: () => {
        this.connectedDeviceUuid = '';
        this.liveReading = undefined;
        this.liveCellWeightsKg = [];
        this.liveCellCount = 0;
        this.liveBvrKg = undefined;
        this.liveDisplayMode = 'SINGLE_CELL';
        this.liveStatusLoading = false;
        this.cdr.detectChanges();
        this.scheduleNextLiveStatusPoll();
      }
    });
  }

  captureReading(): void {
    this.capturing = true;
    this.message = '';

    const request = this.selectedDeviceUuid
      ? this.wbmsApi.captureReading(this.selectedDeviceUuid)
      : this.wbmsApi.captureActiveReading();

    request.subscribe({
      next: (response) => {
        this.capturing = false;
        this.setMessage(response.description, response.status ? 'success' : 'warning');
        if (response.status) {
          this.loadReadings();
        }
      },
      error: (error) => {
        this.capturing = false;
        this.setMessage(error?.message || 'Unable to capture weight', 'danger');
      }
    });
  }

  connectDevice(device: WeighbridgeDevice): void {
    if (this.actionDeviceUuid) {
      return;
    }

    if (this.isConnected(device)) {
      this.disconnectDevice(device);
      return;
    }

    this.actionDeviceUuid = device.uuid;
    this.message = '';
    this.startActionTimeout();
    this.wbmsApi
      .startCapture(device.uuid)
      .pipe(finalize(() => this.finishDeviceAction()))
      .subscribe({
      next: (response) => {
        if (response.status) {
          this.connectedDeviceUuid = device.uuid;
          this.selectedDeviceUuid = device.uuid;
          this.liveReading = undefined;
          this.setMessage(response.description, 'success');
          this.finishDeviceAction();
          return;
        }

        this.connectedDeviceUuid = '';
        this.liveReading = undefined;
        this.finishDeviceAction();
        this.showConnectionFailure(device, response.description);
      },
      error: (error) => {
        const reason = this.extractErrorMessage(error, 'Connection failed');
        this.connectedDeviceUuid = '';
        this.liveReading = undefined;
        this.finishDeviceAction();
        this.showConnectionFailure(device, reason);
      }
    });
  }

  disconnectDevice(device: WeighbridgeDevice): void {
    if (this.actionDeviceUuid) {
      return;
    }

    this.actionDeviceUuid = device.uuid;
    this.message = '';
    this.startActionTimeout();
    this.wbmsApi
      .stopCapture()
      .pipe(finalize(() => this.finishDeviceAction()))
      .subscribe({
      next: (response) => {
        if (response.status) {
          this.connectedDeviceUuid = '';
          this.liveReading = undefined;
          this.setMessage(response.description, 'success');
          this.finishDeviceAction();
          return;
        }

        this.finishDeviceAction();
        this.showConnectionFailure(device, response.description);
      },
      error: (error) => {
        const reason = this.extractErrorMessage(error, 'Disconnect failed');
        this.finishDeviceAction();
        this.showConnectionFailure(device, reason);
      }
    });
  }

  isConnected(device: WeighbridgeDevice): boolean {
    return this.connectedDeviceUuid === device.uuid;
  }

  deviceEndpoint(device: WeighbridgeDevice): string {
    if (device.connectionType === 'SERIAL') {
      return `SERIAL / COM: ${device.serialPort || '-'}`;
    }
    return `TCP/IP / IP: ${device.tcpHost || '-'}:${device.tcpPort || '-'}`;
  }

  get serialCount(): number {
    return this.devices.filter((device) => device.connectionType === 'SERIAL').length;
  }

  get tcpCount(): number {
    return this.devices.filter((device) => device.connectionType === 'TCP').length;
  }

  get activeCount(): number {
    return this.connectedDeviceUuid ? 1 : 0;
  }

  get selectedDevice(): WeighbridgeDevice | undefined {
    return this.devices.find((device) => device.uuid === this.selectedDeviceUuid);
  }

  get isMultiCellDashboard(): boolean {
    return this.liveDisplayMode === 'MULTI_CELL' && this.liveCellWeightsKg.length > 0;
  }

  get displayCellWeightsKg(): number[] {
    const count = this.liveCellCount > 0 ? this.liveCellCount : this.liveCellWeightsKg.length;
    return this.liveCellWeightsKg.slice(0, count);
  }

  private setMessage(message: string, type: 'success' | 'warning' | 'danger'): void {
    this.message = message;
    this.messageType = type;
  }

  private showConnectionFailure(device: WeighbridgeDevice, reason: string): void {
    this.finishDeviceAction();

    Swal.fire({
      icon: 'error',
      title: `Could not connect to ${device.deviceId}`,
      text: reason || 'The backend did not return a connection reason.',
      confirmButtonColor: '#1d6f59'
    }).then(() => this.loadLiveStatus());
  }

  private extractErrorMessage(error: any, fallback: string): string {
    return error?.error?.description || error?.message || fallback;
  }

  private finishDeviceAction(): void {
    if (this.actionTimeout) {
      window.clearTimeout(this.actionTimeout);
      this.actionTimeout = undefined;
    }
    this.actionDeviceUuid = '';
    this.cdr.detectChanges();
    this.loadLiveStatus(true);
    window.setTimeout(() => this.loadLiveStatus(true), 500);
  }

  private startActionTimeout(): void {
    if (this.actionTimeout) {
      window.clearTimeout(this.actionTimeout);
    }

    this.actionTimeout = window.setTimeout(() => this.finishDeviceAction(), 8000);
  }

  private selectDefaultDevice(): void {
    if (this.selectedDeviceUuid && this.devices.some((device) => device.uuid === this.selectedDeviceUuid)) {
      return;
    }

    const activeDevice = this.devices.find((device) => device.active);
    this.selectedDeviceUuid = activeDevice?.uuid || this.devices[0]?.uuid || '';
  }

  private markUserActive(): void {
    this.lastActivityAt = Date.now();
    if (document.hidden) {
      return;
    }
    if (!this.livePollTimer) {
      this.loadLiveStatus(true);
    }
  }

  private isUserIdle(): boolean {
    return Date.now() - this.lastActivityAt > 5 * 60 * 1000;
  }

  private scheduleNextLiveStatusPoll(): void {
    if (document.hidden || this.isUserIdle()) {
      this.livePollTimer = undefined;
      return;
    }

    if (!this.connectedDeviceUuid) {
      this.scheduleLiveStatusPoll(3000);
      return;
    }

    if (!this.liveReading) {
      this.scheduleLiveStatusPoll(1000);
      return;
    }

    this.scheduleLiveStatusPoll(500);
  }

  private scheduleLiveStatusPoll(delayMs: number): void {
    if (this.livePollTimer) {
      window.clearTimeout(this.livePollTimer);
    }

    this.livePollTimer = window.setTimeout(() => {
      this.livePollTimer = undefined;
      this.loadLiveStatus();
    }, delayMs);
  }
}
