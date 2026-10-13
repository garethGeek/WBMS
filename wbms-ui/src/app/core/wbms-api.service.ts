import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';

import { environment } from 'src/environments/environment';

export interface ApiResponse<T> {
  status: boolean;
  code: number;
  description: string;
  data: T;
}

export type ConnectionType = 'SERIAL' | 'TCP';
export type ParityMode = 'NONE' | 'ODD' | 'EVEN' | 'MARK' | 'SPACE';
export type FlowControlMode = 'NONE' | 'RTS_CTS' | 'XON_XOFF';
export type StopBitsMode = 'ONE' | 'ONE_POINT_FIVE' | 'TWO';
export type WeightDisplayMode = 'SINGLE_CELL' | 'MULTI_CELL';
export type WeighbridgeProtocolType =
  | 'TANROADS_ASCII_AXLE'
  | 'GENERIC_NUMERIC'
  | 'METTLER_TOLEDO_CONTINUOUS'
  | 'METTLER_TOLEDO_SICS'
  | 'RINSTRUM_R400'
  | 'AVERY_ZM'
  | 'PRECIA_MOLEN_I30_I35'
  | 'YAOHUA_XK3190'
  | 'SENSOCAR_GENERIC';

export interface WeighbridgeDevice {
  uuid: string;
  deviceId: string;
  name: string;
  connectionType: ConnectionType;
  serialPort?: string;
  baudRate?: number;
  dataBits?: number;
  parity?: ParityMode;
  flowControl?: FlowControlMode;
  stopBits?: StopBitsMode;
  tcpHost?: string;
  tcpPort?: number;
  active: boolean;
  weightDisplayMode?: WeightDisplayMode;
  cellCount?: number;
  protocolType?: WeighbridgeProtocolType;
  indicatorProfileUuid?: string;
}

export interface WeighbridgeDeviceCreate {
  deviceId: string;
  name: string;
  connectionType: ConnectionType;
  serialPort?: string;
  baudRate?: number;
  dataBits?: number;
  parity?: ParityMode;
  flowControl?: FlowControlMode;
  stopBits?: StopBitsMode;
  tcpHost?: string;
  tcpPort?: number;
  active?: boolean;
  weightDisplayMode?: WeightDisplayMode;
  cellCount?: number;
  protocolType?: WeighbridgeProtocolType;
  indicatorProfileUuid?: string;
}

export interface DeviceConnectionTest {
  connectionType: ConnectionType;
  serialPort?: string;
  baudRate?: number;
  dataBits?: number;
  parity?: ParityMode;
  flowControl?: FlowControlMode;
  stopBits?: StopBitsMode;
  protocolType?: WeighbridgeProtocolType;
  cellCount?: number;
  tcpHost?: string;
  tcpPort?: number;
  waitSeconds?: number;
  profileTest?: boolean;
}

export interface DeviceConnectionTestResult {
  connected: boolean;
  dataReceived: boolean;
  message: string;
  sampleData?: string;
  rawHex?: string;
  parserInput?: string;
  parsedWeightKg?: number;
  parsedCellWeightsKg?: number[];
}

export interface IndicatorProfile {
  uuid: string;
  profileName: string;
  manufacturer?: string;
  model?: string;
  connectionType: ConnectionType;
  baudRate?: number;
  dataBits?: number;
  parity?: ParityMode;
  stopBits?: StopBitsMode;
  flowControl?: FlowControlMode;
  serialPort?: string;
  tcpHost?: string;
  tcpPort?: number;
  startBytes?: string;
  endBytes?: string;
  separator?: string;
  stringLength?: number;
  removeBeginning?: number;
  removeEnd?: number;
  weightTokenIndex?: number;
  scaleCount?: number;
}

export interface WeightReading {
  uuid: string;
  deviceUuid: string;
  deviceId: string;
  deviceName: string;
  weightKg: number;
  rawLine: string;
  capturedAt: string;
}

export interface LiveCaptureStatus {
  connected: boolean;
  deviceUuid?: string;
  deviceId?: string;
  deviceName?: string;
  latestWeightKg?: number;
  bvrKg?: number;
  latestCellWeightsKg?: number[];
  indicatorGvmKg?: number;
  weightDisplayMode?: WeightDisplayMode;
  cellCount?: number;
  latestRawLine?: string;
  latestReadAt?: string;
  message: string;
}

export interface SimulatorStatus {
  running: boolean;
  host: string;
  port: number;
  weightKg: number;
  stable: boolean;
  vehicleCycleRunning: boolean;
  clients: number;
  rawLine: string;
  weightDisplayMode?: WeightDisplayMode;
  cellWeightsKg?: number[];
  framingEnabled?: boolean;
  startBytes?: string;
  endBytes?: string;
  separator?: string;
}

@Injectable({ providedIn: 'root' })
export class WbmsApiService {
  private readonly apiBaseUrl = environment.apiBaseUrl;
  private readonly devicesCacheKey = 'wbms.devices';

  constructor(private http: HttpClient) {}

  listDevices(pageNo = 0, pageSize = 1000): Observable<ApiResponse<WeighbridgeDevice[]>> {
    const params = new HttpParams().set('pageNo', pageNo).set('pageSize', pageSize);
    return this.http.get<ApiResponse<WeighbridgeDevice[]>>(`${this.apiBaseUrl}/api/devices`, { params }).pipe(
      tap((response) => {
        if (response.status && Array.isArray(response.data)) {
          this.cacheDevices(response.data);
        }
      })
    );
  }

  registerDevice(payload: WeighbridgeDeviceCreate): Observable<ApiResponse<WeighbridgeDevice>> {
    return this.http.post<ApiResponse<WeighbridgeDevice>>(`${this.apiBaseUrl}/api/devices/register`, payload);
  }

  updateDevice(uuid: string, payload: WeighbridgeDeviceCreate): Observable<ApiResponse<WeighbridgeDevice>> {
    return this.http.put<ApiResponse<WeighbridgeDevice>>(`${this.apiBaseUrl}/api/devices/${uuid}`, payload);
  }

  deleteDevice(uuid: string): Observable<ApiResponse<boolean>> {
    return this.http.delete<ApiResponse<boolean>>(`${this.apiBaseUrl}/api/devices/${uuid}`);
  }

  listSerialPorts(): Observable<ApiResponse<string[]>> {
    return this.http.get<ApiResponse<string[]>>(`${this.apiBaseUrl}/api/devices/serial/ports`);
  }

  testConnection(payload: DeviceConnectionTest): Observable<ApiResponse<DeviceConnectionTestResult>> {
    return this.http.post<ApiResponse<DeviceConnectionTestResult>>(`${this.apiBaseUrl}/api/devices/test-connection`, payload);
  }

  listIndicatorProfiles(): Observable<ApiResponse<IndicatorProfile[]>> {
    return this.http.get<ApiResponse<IndicatorProfile[]>>(`${this.apiBaseUrl}/api/indicator-profiles`);
  }

  createIndicatorProfile(payload: Omit<IndicatorProfile, 'uuid'>): Observable<ApiResponse<IndicatorProfile>> {
    return this.http.post<ApiResponse<IndicatorProfile>>(`${this.apiBaseUrl}/api/indicator-profiles`, payload);
  }

  updateIndicatorProfile(uuid: string, payload: Omit<IndicatorProfile, 'uuid'>): Observable<ApiResponse<IndicatorProfile>> {
    return this.http.put<ApiResponse<IndicatorProfile>>(`${this.apiBaseUrl}/api/indicator-profiles/${uuid}`, payload);
  }

  deleteIndicatorProfile(uuid: string): Observable<ApiResponse<boolean>> {
    return this.http.delete<ApiResponse<boolean>>(`${this.apiBaseUrl}/api/indicator-profiles/${uuid}`);
  }

  startActiveCapture(): Observable<ApiResponse<boolean>> {
    return this.http.post<ApiResponse<boolean>>(`${this.apiBaseUrl}/api/live/start/active`, {});
  }

  startCapture(deviceUuid: string): Observable<ApiResponse<boolean>> {
    return this.http.post<ApiResponse<boolean>>(`${this.apiBaseUrl}/api/live/start/${deviceUuid}`, {});
  }

  stopCapture(): Observable<ApiResponse<boolean>> {
    return this.http.post<ApiResponse<boolean>>(`${this.apiBaseUrl}/api/live/stop`, {});
  }

  liveCaptureStatus(): Observable<ApiResponse<LiveCaptureStatus>> {
    const params = new HttpParams().set('_ts', Date.now());
    return this.http.get<ApiResponse<LiveCaptureStatus>>(`${this.apiBaseUrl}/api/live/status`, { params });
  }

  getLatestReading(): Observable<ApiResponse<WeightReading>> {
    return this.http.get<ApiResponse<WeightReading>>(`${this.apiBaseUrl}/api/readings/latest`);
  }

  listReadings(pageNo = 0, pageSize = 10): Observable<ApiResponse<WeightReading[]>> {
    const params = new HttpParams().set('pageNo', pageNo).set('pageSize', pageSize);
    return this.http.get<ApiResponse<WeightReading[]>>(`${this.apiBaseUrl}/api/readings`, { params });
  }

  captureActiveReading(): Observable<ApiResponse<WeightReading>> {
    return this.http.post<ApiResponse<WeightReading>>(`${this.apiBaseUrl}/api/readings/capture/active`, {});
  }

  captureReading(deviceUuid: string): Observable<ApiResponse<WeightReading>> {
    return this.http.post<ApiResponse<WeightReading>>(`${this.apiBaseUrl}/api/readings/capture/${deviceUuid}`, {});
  }

  simulatorStatus(): Observable<ApiResponse<SimulatorStatus>> {
    const params = new HttpParams().set('_ts', Date.now());
    return this.http.get<ApiResponse<SimulatorStatus>>(`${this.apiBaseUrl}/api/simulator/status`, { params });
  }

  startSimulator(port = 2016, weightKg = 0, weightDisplayMode: WeightDisplayMode = 'SINGLE_CELL', cellWeightsKg: number[] = [], framingEnabled = false, startBytes = '02', endBytes = '03', separator = '049', profileUuid = ''): Observable<ApiResponse<SimulatorStatus>> {
    return this.http.post<ApiResponse<SimulatorStatus>>(`${this.apiBaseUrl}/api/simulator/start`, {
      port,
      weightKg,
      weightDisplayMode,
      cellWeightsKg
      ,framingEnabled,startBytes,endBytes,separator,profileUuid
    });
  }

  stopSimulator(): Observable<ApiResponse<SimulatorStatus>> {
    return this.http.post<ApiResponse<SimulatorStatus>>(`${this.apiBaseUrl}/api/simulator/stop`, {});
  }

  setSimulatorWeight(weightKg: number, stable = true, cellWeightsKg: number[] = []): Observable<ApiResponse<SimulatorStatus>> {
    return this.http.post<ApiResponse<SimulatorStatus>>(`${this.apiBaseUrl}/api/simulator/weight`, {
      weightKg,
      stable,
      cellWeightsKg
    });
  }

  runSimulatorDemo(): Observable<ApiResponse<SimulatorStatus>> {
    return this.http.post<ApiResponse<SimulatorStatus>>(`${this.apiBaseUrl}/api/simulator/demo`, {});
  }

  getCachedDevices(): WeighbridgeDevice[] {
    const cached = localStorage.getItem(this.devicesCacheKey);
    if (!cached) {
      return [];
    }

    try {
      const devices = JSON.parse(cached);
      return Array.isArray(devices) ? devices : [];
    } catch {
      return [];
    }
  }

  private cacheDevices(devices: WeighbridgeDevice[]): void {
    localStorage.setItem(this.devicesCacheKey, JSON.stringify(devices));
  }
}
