import { Component, OnInit } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import Swal from 'sweetalert2';

import {
  ConnectionType,
  FlowControlMode,
  ParityMode,
  StopBitsMode,
  WeighbridgeProtocolType,
  WeightDisplayMode,
  WeighbridgeDevice,
  WeighbridgeDeviceCreate,
  IndicatorProfile,
  WbmsApiService
} from 'src/app/core/wbms-api.service';
import { SHARED_IMPORTS } from 'src/app/theme/shared/shared.module';

@Component({
  selector: 'app-devices',
  imports: [...SHARED_IMPORTS],
  templateUrl: './devices.component.html',
  styleUrl: './devices.component.scss'
})
export class DevicesComponent implements OnInit {
  devices: WeighbridgeDevice[] = [];
  serialPorts: string[] = [];
  loading = false;
  saving = false;
  editingDeviceUuid = '';
  message = '';
  messageType: 'success' | 'warning' | 'danger' = 'warning';
  indicatorProfiles: IndicatorProfile[] = [];

  connectionTypes: ConnectionType[] = ['SERIAL', 'TCP'];

  deviceForm = this.fb.nonNullable.group({
    deviceId: ['', [Validators.required]],
    name: ['', [Validators.required]],
    connectionType: ['SERIAL' as ConnectionType, [Validators.required]],
    serialPort: [''],
    baudRate: [9600],
    dataBits: [8],
    parity: ['NONE' as ParityMode],
    flowControl: ['NONE' as FlowControlMode],
    stopBits: ['ONE' as StopBitsMode],
    weightDisplayMode: ['SINGLE_CELL' as WeightDisplayMode],
    cellCount: [1],
    protocolType: ['GENERIC_NUMERIC' as WeighbridgeProtocolType],
    tcpHost: [''],
    tcpPort: [4001],
    active: [true]
    ,indicatorProfileUuid: ['']
  });

  constructor(
    private fb: FormBuilder,
    private wbmsApi: WbmsApiService
  ) {}

  ngOnInit(): void {
    this.devices = this.wbmsApi.getCachedDevices();
    this.loadDevices();
    this.loadSerialPorts();
    this.loadIndicatorProfiles();
  }

  loadDevices(): void {
    this.loading = true;
    this.wbmsApi.listDevices().subscribe({
      next: (response) => {
        if (response.status) {
          this.devices = response.data ?? [];
        } else if (response.code === 6004) {
          this.devices = [];
        }

        this.loading = false;
        if (!response.status && response.code !== 6004) {
          this.setMessage(response.description, 'warning');
        }
      },
      error: (error) => {
        this.loading = false;
        this.setMessage(error?.message || 'Unable to load devices', 'danger');
      }
    });
  }

  loadSerialPorts(): void {
    this.wbmsApi.listSerialPorts().subscribe({
      next: (response) => {
        this.serialPorts = response.status ? response.data ?? [] : [];
      },
      error: () => {
        this.serialPorts = [];
      }
    });
  }

  loadIndicatorProfiles(): void {
    this.wbmsApi.listIndicatorProfiles().subscribe({ next: (response) => { this.indicatorProfiles = response.status ? response.data ?? [] : []; }, error: () => this.indicatorProfiles = [] });
  }

  saveDevice(): void {
    if (this.deviceForm.invalid) {
      this.deviceForm.markAllAsTouched();
      this.setMessage('Device ID, name, and connection type are required.', 'warning');
      return;
    }

    const raw = this.deviceForm.getRawValue();
    const isSerial = raw.connectionType === 'SERIAL';
    const selectedProtocol = raw.protocolType as string;
    const selectedProfileUuid = selectedProtocol.startsWith('PROFILE:')
      ? selectedProtocol.substring('PROFILE:'.length)
      : raw.indicatorProfileUuid || undefined;
    const payload: WeighbridgeDeviceCreate = {
      deviceId: raw.deviceId.trim(),
      name: raw.name.trim(),
      connectionType: raw.connectionType,
      serialPort: isSerial ? raw.serialPort?.trim() : undefined,
      baudRate: isSerial ? raw.baudRate : undefined,
      dataBits: isSerial ? raw.dataBits : undefined,
      parity: isSerial ? raw.parity : undefined,
      flowControl: isSerial ? raw.flowControl : undefined,
      stopBits: isSerial ? raw.stopBits : undefined,
      weightDisplayMode: raw.weightDisplayMode,
      cellCount: raw.weightDisplayMode === 'MULTI_CELL' ? raw.cellCount : 1,
      protocolType: selectedProtocol.startsWith('PROFILE:') ? 'GENERIC_NUMERIC' : raw.protocolType,
      tcpHost: !isSerial ? raw.tcpHost?.trim() : undefined,
      tcpPort: !isSerial ? raw.tcpPort : undefined,
      active: raw.active
      ,indicatorProfileUuid: selectedProfileUuid
    };

    this.saving = true;
    const request = this.editingDeviceUuid
      ? this.wbmsApi.updateDevice(this.editingDeviceUuid, payload)
      : this.wbmsApi.registerDevice(payload);

    request.subscribe({
      next: (response) => {
        this.saving = false;
        this.setMessage(response.description, response.status ? 'success' : 'warning');
        if (response.status) {
          if (response.data) {
            this.editDevice(response.data);
          }
          this.loadDevices();
        }
      },
      error: (error) => {
        this.saving = false;
        this.setMessage(error?.message || 'Unable to save device', 'danger');
      }
    });
  }

  editDevice(device: WeighbridgeDevice): void {
    this.editingDeviceUuid = device.uuid;
    this.message = '';
    this.deviceForm.reset({
      deviceId: device.deviceId || '',
      name: device.name || '',
      connectionType: device.connectionType || 'SERIAL',
      serialPort: device.serialPort || '',
      baudRate: device.baudRate || 9600,
      dataBits: device.dataBits || 8,
      parity: device.parity || 'NONE',
      flowControl: device.flowControl || 'NONE',
      stopBits: device.stopBits || 'ONE',
      weightDisplayMode: device.weightDisplayMode || 'SINGLE_CELL',
      cellCount: device.cellCount || (device.weightDisplayMode === 'MULTI_CELL' ? 4 : 1),
      protocolType: device.indicatorProfileUuid ? ('PROFILE:' + device.indicatorProfileUuid) as WeighbridgeProtocolType : device.protocolType || 'GENERIC_NUMERIC',
      tcpHost: device.tcpHost || '',
      tcpPort: device.tcpPort || 4001,
      active: device.active ?? true
      ,indicatorProfileUuid: device.indicatorProfileUuid || ''
    });
  }

  cancelEdit(): void {
    this.resetForm();
  }

  deleteDevice(device: WeighbridgeDevice): void {
    Swal.fire({
      icon: 'warning',
      title: `Delete ${device.deviceId}?`,
      text: 'This device will be removed from the registry.',
      showCancelButton: true,
      confirmButtonText: 'Delete',
      cancelButtonText: 'Cancel',
      confirmButtonColor: '#dc3545'
    }).then((result) => {
      if (!result.isConfirmed) {
        return;
      }

      this.wbmsApi.deleteDevice(device.uuid).subscribe({
        next: (response) => {
          this.setMessage(response.description, response.status ? 'success' : 'warning');
          if (this.editingDeviceUuid === device.uuid) {
            this.resetForm();
          }
          this.loadDevices();
        },
        error: (error) => {
          this.setMessage(error?.message || 'Unable to delete device', 'danger');
        }
      });
    });
  }

  deviceEndpoint(device: WeighbridgeDevice): string {
    if (device.connectionType === 'SERIAL') {
      return device.serialPort || '-';
    }

    return `${device.tcpHost || '-'}:${device.tcpPort || '-'}`;
  }

  get isSerial(): boolean {
    return this.deviceForm.controls.connectionType.value === 'SERIAL';
  }

  get formTitle(): string {
    return this.editingDeviceUuid ? 'Edit Device' : 'Register Device';
  }

  onDisplayModeChange(): void {
    const mode = this.deviceForm.controls.weightDisplayMode.value;
    const cellCount = this.deviceForm.controls.cellCount.value;
    if (mode === 'MULTI_CELL' && (!cellCount || cellCount < 2)) {
      this.deviceForm.controls.cellCount.setValue(4);
    }
    if (mode === 'SINGLE_CELL') {
      this.deviceForm.controls.cellCount.setValue(1);
    }
  }

  onProtocolChange(): void {
    const selected = this.deviceForm.controls.protocolType.value as string;
    this.deviceForm.controls.indicatorProfileUuid.setValue(
      selected.startsWith('PROFILE:') ? selected.substring('PROFILE:'.length) : ''
    );
  }

  private setMessage(message: string, type: 'success' | 'warning' | 'danger'): void {
    this.message = message;
    this.messageType = type;
  }

  private resetForm(): void {
    this.editingDeviceUuid = '';
    this.deviceForm.reset({
      deviceId: '',
      name: '',
      connectionType: 'SERIAL',
      serialPort: '',
      baudRate: 9600,
      dataBits: 8,
      parity: 'NONE',
      flowControl: 'NONE',
      stopBits: 'ONE',
      weightDisplayMode: 'SINGLE_CELL',
      cellCount: 1,
      protocolType: 'GENERIC_NUMERIC',
      tcpHost: '',
      tcpPort: 4001,
      active: true
      ,indicatorProfileUuid: ''
    });
  }
}
