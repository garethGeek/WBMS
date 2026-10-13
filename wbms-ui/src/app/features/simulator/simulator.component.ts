import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';

import { IndicatorProfile, SimulatorStatus, WeightDisplayMode, WbmsApiService } from 'src/app/core/wbms-api.service';
import { SHARED_IMPORTS } from 'src/app/theme/shared/shared.module';

@Component({
  selector: 'app-simulator',
  imports: [...SHARED_IMPORTS],
  templateUrl: './simulator.component.html',
  styleUrl: './simulator.component.scss'
})
export class SimulatorComponent implements OnInit, OnDestroy {
  simulator?: SimulatorStatus;
  simulatorPort = 2016;
  simulatorWeight = 32480;
  simulatorStable = true;
  simulatorMode: WeightDisplayMode = 'SINGLE_CELL';
  simulatorCellWeights = '565,300,600,100';
  simulatorFramingEnabled = false;
  simulatorStartBytes = '02';
  simulatorEndBytes = '03';
  simulatorSeparator = 'SPACE';
  simulatorProfileUuid = '';
  indicatorProfiles: IndicatorProfile[] = [];
  private simulatorModeChanged = false;
  private simulatorFramingChanged = false;
  busy = false;
  busyAction = '';
  statusLoading = false;
  message = '';
  messageType: 'success' | 'warning' | 'danger' = 'warning';
  private pollTimer?: number;

  constructor(
    private wbmsApi: WbmsApiService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.loadStatus();
    this.wbmsApi.listIndicatorProfiles().subscribe({ next: (response) => { if (response.status) this.indicatorProfiles = response.data || []; } });
    this.pollTimer = window.setInterval(() => this.loadStatus(false), 1500);
  }

  ngOnDestroy(): void {
    if (this.pollTimer) {
      window.clearInterval(this.pollTimer);
    }
  }

  loadStatus(showError = true, force = false): void {
    if (this.statusLoading && !force) {
      return;
    }

    this.statusLoading = true;
    this.wbmsApi.simulatorStatus().subscribe({
      next: (response) => {
        if (response.status) {
          this.simulator = response.data;
          this.simulatorPort = response.data?.port || this.simulatorPort;
          this.simulatorWeight = response.data?.weightKg ?? this.simulatorWeight;
          this.simulatorStable = response.data?.stable ?? this.simulatorStable;
          if (!this.simulatorModeChanged) {
          this.simulatorMode = response.data?.weightDisplayMode || this.simulatorMode;
          if (!this.simulatorFramingChanged) {
            this.simulatorFramingEnabled = response.data?.framingEnabled ?? this.simulatorFramingEnabled;
            this.simulatorStartBytes = response.data?.startBytes || this.simulatorStartBytes;
            this.simulatorEndBytes = response.data?.endBytes || this.simulatorEndBytes;
            this.simulatorSeparator = response.data?.separator || this.simulatorSeparator;
          }
          }
          if (!this.simulatorModeChanged && response.data?.cellWeightsKg?.length) {
            this.simulatorCellWeights = response.data.cellWeightsKg.join(',');
          }
        } else if (showError) {
          this.setMessage(response.description, 'warning');
        }
        this.statusLoading = false;
        this.cdr.detectChanges();
      },
      error: (error) => {
        if (showError) {
          this.setMessage(error?.error?.description || error?.message || 'Unable to load simulator status', 'danger');
        }
        this.statusLoading = false;
        this.cdr.detectChanges();
      }
    });
  }

  startSimulator(): void {
    if (this.busy) {
      return;
    }

    this.busy = true;
    this.busyAction = 'Starting simulator...';
    this.message = '';
    const cells = this.parseCellWeights();
    if (this.simulatorMode === 'MULTI_CELL' && !cells.length) {
      this.setMessage('Enter comma-separated cell values, for example 565,300,600,100.', 'warning');
      this.finishAction();
      return;
    }
    this.wbmsApi.startSimulator(this.simulatorPort, this.simulatorWeight, this.simulatorMode, cells, this.simulatorFramingEnabled, this.simulatorStartBytes, this.simulatorEndBytes, this.simulatorSeparator, this.simulatorProfileUuid).subscribe({
      next: (response) => {
        this.simulator = response.data;
        this.simulatorModeChanged = false;
        this.simulatorFramingChanged = false;
        this.simulatorMode = response.data?.weightDisplayMode || this.simulatorMode;
        this.setMessage(response.description, response.status ? 'success' : 'danger');
        this.finishAction(true);
      },
      error: (error) => {
        this.setMessage(error?.error?.description || error?.message || 'Unable to start simulator', 'danger');
        this.finishAction();
      }
    });
  }

  stopSimulator(): void {
    if (this.busy) {
      return;
    }

    this.busy = true;
    this.busyAction = 'Stopping simulator...';
    this.message = '';
    this.wbmsApi.stopSimulator().subscribe({
      next: (response) => {
        this.simulator = response.data;
        this.setMessage(response.description, response.status ? 'success' : 'warning');
        this.finishAction(true);
      },
      error: (error) => {
        this.setMessage(error?.error?.description || error?.message || 'Unable to stop simulator', 'danger');
        this.finishAction();
      }
    });
  }

  setSimulatorWeight(): void {
    if (this.busy) {
      return;
    }

    this.busy = true;
    this.busyAction = 'Updating simulator weight...';
    this.message = '';
    this.wbmsApi.setSimulatorWeight(this.simulatorWeight, this.simulatorStable, this.parseCellWeights()).subscribe({
      next: (response) => {
        this.simulator = response.data;
        this.setMessage(response.description, response.status ? 'success' : 'warning');
        this.finishAction(true);
      },
      error: (error) => {
        this.setMessage(error?.error?.description || error?.message || 'Unable to update simulator weight', 'danger');
        this.finishAction();
      }
    });
  }

  runDemo(): void {
    if (this.busy) {
      return;
    }

    this.busy = true;
    this.busyAction = 'Starting vehicle cycle...';
    this.message = '';
    this.wbmsApi.runSimulatorDemo().subscribe({
      next: (response) => {
        this.simulator = response.data;
        this.setMessage(response.description, response.status ? 'success' : 'warning');
        this.finishAction(true);
      },
      error: (error) => {
        this.setMessage(error?.error?.description || error?.message || 'Unable to run simulator demo', 'danger');
        this.finishAction();
      }
    });
  }

  zero(): void {
    this.simulatorWeight = 0;
    this.simulatorStable = true;
    this.setSimulatorWeight();
  }

  onSimulatorModeChange(mode: WeightDisplayMode): void {
    this.simulatorMode = mode;
    this.simulatorModeChanged = true;
  }

  onProfileChange(uuid: string): void {
    this.simulatorProfileUuid = uuid;
    const profile = this.indicatorProfiles.find((item) => item.uuid === uuid);
    if (!profile) return;
    this.simulatorFramingChanged = true;
    this.simulatorFramingEnabled = true;
    this.simulatorStartBytes = profile.startBytes || '02';
    this.simulatorEndBytes = profile.endBytes || '03';
    this.simulatorSeparator = profile.separator || '';
  }

  onFramingChange(): void {
    this.simulatorFramingChanged = true;
  }

  private setMessage(message: string, type: 'success' | 'warning' | 'danger'): void {
    this.message = message;
    this.messageType = type;
  }

  get canStart(): boolean {
    return !this.busy && !this.simulator?.running;
  }

  get canStop(): boolean {
    return !this.busy && !!this.simulator?.running;
  }

  get canControlRunningSimulator(): boolean {
    return !this.busy && !!this.simulator?.running;
  }

  private parseCellWeights(): number[] {
    return this.simulatorCellWeights
      .split(',')
      .map((value) => Number(value.trim()))
      .filter((value) => Number.isFinite(value));
  }

  private finishAction(forceReload = false): void {
    this.busy = false;
    this.busyAction = '';
    this.statusLoading = false;
    this.cdr.detectChanges();
    this.loadStatus(false, forceReload);
    window.setTimeout(() => this.loadStatus(false, true), 500);
  }
}
