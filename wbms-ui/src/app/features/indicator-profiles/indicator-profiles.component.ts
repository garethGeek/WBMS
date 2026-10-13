import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { Subscription, timer, switchMap } from 'rxjs';
import Swal from 'sweetalert2';
import { ConnectionType, DeviceConnectionTest, DeviceConnectionTestResult, FlowControlMode, IndicatorProfile, ParityMode, StopBitsMode, WbmsApiService } from 'src/app/core/wbms-api.service';
import { SHARED_IMPORTS } from 'src/app/theme/shared/shared.module';

@Component({
  selector: 'app-indicator-profiles',
  imports: [...SHARED_IMPORTS],
  templateUrl: './indicator-profiles.component.html',
  styleUrl: './indicator-profiles.component.scss'
})
export class IndicatorProfilesComponent implements OnInit, OnDestroy {
  profiles: IndicatorProfile[] = [];
  result?: DeviceConnectionTestResult;
  testing = false;
  saving = false;
  message = '';
  messageType: 'success' | 'warning' | 'danger' = 'warning';
  private testSubscription?: Subscription;
  private framingSubscription?: Subscription;
  editingUuid = '';

  connectionTypes: ConnectionType[] = ['SERIAL', 'TCP'];
  profileForm = this.fb.nonNullable.group({
    profileName: ['', Validators.required], manufacturer: [''], model: [''], connectionType: ['TCP' as ConnectionType],
    serialPort: [''], baudRate: [9600], dataBits: [8], parity: ['NONE' as ParityMode], stopBits: ['ONE' as StopBitsMode], flowControl: ['NONE' as FlowControlMode],
    tcpHost: ['127.0.0.1'], tcpPort: [3000], framingMode: ['STX_ETX' as 'STX_ETX' | 'UNFRAMED'], startBytes: ['02'], endBytes: ['03'], separator: ['SPACE'], stringLength: [0], removeBeginning: [0], removeEnd: [0], weightTokenIndex: [0], scaleCount: [1]
  });

  constructor(private fb: FormBuilder, private api: WbmsApiService, private cdr: ChangeDetectorRef) {}
  ngOnInit(): void {
    this.loadProfiles();
    this.framingSubscription = this.profileForm.controls.framingMode.valueChanges.subscribe(() => this.syncFramingFields());
  }
  ngOnDestroy(): void { this.stopTest(); this.framingSubscription?.unsubscribe(); }
  get isSerial(): boolean { return this.profileForm.controls.connectionType.value === 'SERIAL'; }

  loadProfiles(): void { this.api.listIndicatorProfiles().subscribe({ next: r => { if (r.status) this.profiles = r.data || []; }, error: () => this.setMessage('Unable to load indicator profiles', 'danger') }); }

  test(): void {
    this.stopTest(); this.testing = true; this.result = undefined; this.message = '';
    this.testSubscription = timer(0, 1500).pipe(switchMap(() => this.api.testConnection(this.testPayload()))).subscribe({
      next: r => { this.result = r.data; if (!r.status && !r.data?.connected) this.setMessage(r.description, 'warning'); this.cdr.detectChanges(); },
      error: e => { this.setMessage(e?.error?.description || e?.message || 'Profile test failed', 'danger'); this.stopTest(); }
    });
  }
  stopTest(): void { this.testSubscription?.unsubscribe(); this.testSubscription = undefined; this.testing = false; }
  save(): void {
    if (this.profileForm.invalid) { this.profileForm.markAllAsTouched(); return; }
    this.saving = true;
    const request = this.editingUuid
      ? this.api.updateIndicatorProfile(this.editingUuid, this.profilePayload())
      : this.api.createIndicatorProfile(this.profilePayload());
    request.subscribe({ next: r => { this.saving = false; this.setMessage(r.description, r.status ? 'success' : 'warning'); if (r.status) { this.editingUuid = ''; this.loadProfiles(); } }, error: e => { this.saving = false; this.setMessage(e?.error?.description || e?.message || 'Unable to save profile', 'danger'); } });
  }
  private testPayload(): DeviceConnectionTest {
    const v = this.profileForm.getRawValue();
    return { ...v, ...this.framingPayload(), protocolType: 'GENERIC_NUMERIC', cellCount: v.scaleCount, waitSeconds: 2, profileTest: true };
  }
  private framingPayload(): { startBytes: string; endBytes: string } {
    return this.profileForm.controls.framingMode.value === 'UNFRAMED' ? { startBytes: '', endBytes: '' } : { startBytes: this.profileForm.controls.startBytes.value, endBytes: this.profileForm.controls.endBytes.value };
  }
  private profilePayload(): Omit<IndicatorProfile, 'uuid'> {
    const { framingMode, ...value } = this.profileForm.getRawValue();
    return { ...value, ...this.framingPayload() } as Omit<IndicatorProfile, 'uuid'>;
  }
  framingModeChanged(): void {
    this.syncFramingFields();
  }
  private syncFramingFields(): void {
    if (this.profileForm.controls.framingMode.value === 'UNFRAMED') {
      this.profileForm.controls.startBytes.setValue('', { emitEvent: false });
      this.profileForm.controls.endBytes.setValue('', { emitEvent: false });
    } else {
      if (!this.profileForm.controls.startBytes.value) this.profileForm.controls.startBytes.setValue('02', { emitEvent: false });
      if (!this.profileForm.controls.endBytes.value) this.profileForm.controls.endBytes.setValue('03', { emitEvent: false });
    }
  }
  private setMessage(message: string, type: 'success' | 'warning' | 'danger'): void { this.message = message; this.messageType = type; }

  adjustCharacterCount(controlName: 'removeBeginning' | 'removeEnd', amount: number): void {
    const control = this.profileForm.controls[controlName];
    control.setValue(Math.max(0, (control.value || 0) + amount));
  }

  editProfile(profile: IndicatorProfile): void {
    this.stopTest();
    this.editingUuid = profile.uuid;
    this.profileForm.patchValue({ ...profile, profileName: profile.profileName || '', framingMode: profile.startBytes && profile.endBytes ? 'STX_ETX' : 'UNFRAMED' });
    this.syncFramingFields();
    this.message = '';
  }

  cancelEdit(): void {
    this.editingUuid = '';
    this.profileForm.reset({ profileName: '', manufacturer: '', model: '', connectionType: 'TCP', serialPort: '', baudRate: 9600, dataBits: 8, parity: 'NONE', stopBits: 'ONE', flowControl: 'NONE', tcpHost: '127.0.0.1', tcpPort: 3000, framingMode: 'STX_ETX', startBytes: '02', endBytes: '03', separator: 'SPACE', stringLength: 0, removeBeginning: 0, removeEnd: 0, weightTokenIndex: 0, scaleCount: 1 });
  }

  deleteProfile(profile: IndicatorProfile): void {
    Swal.fire({ icon: 'warning', title: `Delete ${profile.profileName}?`, text: 'Devices assigned to it will fall back to their built-in protocol.', showCancelButton: true, confirmButtonText: 'Delete', confirmButtonColor: '#dc3545' }).then(result => {
      if (!result.isConfirmed) return;
      this.api.deleteIndicatorProfile(profile.uuid).subscribe({ next: r => { this.setMessage(r.description, r.status ? 'success' : 'warning'); if (r.status) { if (this.editingUuid === profile.uuid) this.cancelEdit(); this.loadProfiles(); } }, error: e => this.setMessage(e?.error?.description || e?.message || 'Unable to delete profile', 'danger') });
    });
  }
}
