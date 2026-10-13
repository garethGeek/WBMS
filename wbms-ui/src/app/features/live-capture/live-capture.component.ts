import { Component, OnInit } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';

import {
  ConnectionType,
  DeviceConnectionTestResult,
  WeighbridgeDevice,
  WbmsApiService
} from 'src/app/core/wbms-api.service';
import { SHARED_IMPORTS } from 'src/app/theme/shared/shared.module';

@Component({
  selector: 'app-live-capture',
  imports: [...SHARED_IMPORTS],
  templateUrl: './live-capture.component.html',
  styleUrl: './live-capture.component.scss'
})
export class LiveCaptureComponent implements OnInit {
  devices: WeighbridgeDevice[] = [];
  serialPorts: string[] = [];
  message = '';
  messageType: 'success' | 'warning' | 'danger' = 'warning';
  testResult?: DeviceConnectionTestResult;
  runningDeviceName = '';

  captureForm = this.fb.nonNullable.group({
    deviceUuid: [''],
    connectionType: ['SERIAL' as ConnectionType, [Validators.required]],
    serialPort: [''],
    tcpHost: [''],
    tcpPort: [4001],
    waitSeconds: [5],
    baudRate: [9600],
    dataBits: [8],
    parity: ['NONE'],
    flowControl: ['NONE'],
    stopBits: ['ONE'],
    protocolType: ['GENERIC_NUMERIC'],
    cellCount: [1]
  });

  constructor(
    private fb: FormBuilder,
    private wbmsApi: WbmsApiService
  ) {}

  ngOnInit(): void {
    this.loadDevices();
    this.loadSerialPorts();
  }

  loadDevices(): void {
    this.wbmsApi.listDevices().subscribe({
      next: (response) => {
        this.devices = response.status ? response.data ?? [] : [];
      },
      error: (error) => {
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

  useDevice(deviceUuid: string): void {
    const device = this.devices.find((item) => item.uuid === deviceUuid);
    if (!device) {
      return;
    }

    this.captureForm.patchValue({
      deviceUuid: device.uuid,
      connectionType: device.connectionType,
      serialPort: device.serialPort ?? '',
      tcpHost: device.tcpHost ?? '',
      tcpPort: device.tcpPort ?? 4001,
      baudRate: device.baudRate ?? 9600,
      dataBits: device.dataBits ?? 8,
      parity: device.parity ?? 'NONE',
      flowControl: device.flowControl ?? 'NONE',
      stopBits: device.stopBits ?? 'ONE',
      protocolType: device.protocolType ?? 'GENERIC_NUMERIC',
      cellCount: device.cellCount ?? 1
    });
  }

  startActive(): void {
    this.wbmsApi.startActiveCapture().subscribe({
      next: (response) => {
        this.runningDeviceName = response.status ? 'Active device' : '';
        this.setMessage(response.description, response.status ? 'success' : 'warning');
      },
      error: (error) => {
        this.setMessage(error?.message || 'Unable to start active capture', 'danger');
      }
    });
  }

  startSelected(): void {
    const uuid = this.captureForm.controls.deviceUuid.value;
    if (!uuid) {
      this.setMessage('Select a registered device first.', 'warning');
      return;
    }

    const device = this.devices.find((item) => item.uuid === uuid);
    this.wbmsApi.startCapture(uuid).subscribe({
      next: (response) => {
        this.runningDeviceName = response.status ? device?.name || device?.deviceId || 'Selected device' : '';
        this.setMessage(response.description, response.status ? 'success' : 'warning');
      },
      error: (error) => {
        this.setMessage(error?.message || 'Unable to start capture', 'danger');
      }
    });
  }

  stopCapture(): void {
    this.wbmsApi.stopCapture().subscribe({
      next: (response) => {
        this.runningDeviceName = '';
        this.setMessage(response.description, response.status ? 'success' : 'warning');
      },
      error: (error) => {
        this.setMessage(error?.message || 'Unable to stop capture', 'danger');
      }
    });
  }

  testConnection(): void {
    const raw = this.captureForm.getRawValue();
    const isSerial = raw.connectionType === 'SERIAL';

    this.wbmsApi
      .testConnection({
        connectionType: raw.connectionType,
        serialPort: isSerial ? raw.serialPort : undefined,
        baudRate: isSerial ? raw.baudRate : undefined,
        dataBits: isSerial ? raw.dataBits : undefined,
        parity: isSerial ? raw.parity : undefined,
        flowControl: isSerial ? raw.flowControl : undefined,
        stopBits: isSerial ? raw.stopBits : undefined,
        protocolType: raw.protocolType as any,
        cellCount: raw.cellCount,
        tcpHost: isSerial ? undefined : raw.tcpHost,
        tcpPort: isSerial ? undefined : raw.tcpPort,
        waitSeconds: raw.waitSeconds
      })
      .subscribe({
        next: (response) => {
          this.testResult = response.data;
          this.setMessage(response.description, response.status ? 'success' : 'warning');
        },
        error: (error) => {
          this.testResult = undefined;
          this.setMessage(error?.message || 'Connection test failed', 'danger');
        }
      });
  }

  get isSerial(): boolean {
    return this.captureForm.controls.connectionType.value === 'SERIAL';
  }

  private setMessage(message: string, type: 'success' | 'warning' | 'danger'): void {
    this.message = message;
    this.messageType = type;
  }
}
