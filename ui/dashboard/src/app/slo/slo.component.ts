import { Component, OnDestroy, OnInit } from '@angular/core';
import { SloService } from './slo.service';
import { Subscription, timer } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { SloMetrics } from './slo.model';
import { CommonModule } from '@angular/common';
import { NgxChartsModule, Color, ScaleType } from '@swimlane/ngx-charts';

@Component({
    selector: 'app-slo',
    templateUrl: './slo.component.html',
    styleUrls: ['./slo.component.css'],
    standalone: true,
    imports: [CommonModule, NgxChartsModule],
})
export class SloComponent implements OnInit, OnDestroy {

  // Chart data
  latencyData: any[] = [{ name: 'p95 Latency', series: [] }];
  dltData: any[] = [{ name: 'DLT Count', series: [] }];
  burnRateData: any[] = [{ name: 'Burn Rate (1h)', series: [] }];

  // SLO thresholds
  readonly latencySlo = 2000; // 2 seconds
  readonly burnRateSlo = 1;

  // Chart options
  latencyRefLines = [{ value: this.latencySlo, name: 'SLO' }];
  burnRateRefLines = [{ value: this.burnRateSlo, name: 'SLO' }];
  colorScheme: Color = {
    name: 'custom',
    selectable: true,
    group: ScaleType.Ordinal,
    domain: ['#5AA454', '#A10A28', '#C7B42C', '#AAAAAA']
  };

  private subscription: Subscription | undefined;

  constructor(private sloService: SloService) { }

  ngOnInit(): void {
    // Poll for metrics every 5 seconds
    this.subscription = timer(0, 5000).pipe(
      switchMap(() => this.sloService.getSloMetrics())
    ).subscribe(metrics => {
      this.updateChartData(metrics);
    });
  }

  ngOnDestroy(): void {
    if (this.subscription) {
      this.subscription.unsubscribe();
    }
  }

  private updateChartData(metrics: SloMetrics): void {
    const now = new Date();
    const maxDataPoints = 30; // Keep a rolling window of 30 data points

    // Latency
    this.latencyData[0].series.push({ name: now, value: metrics.p95Latency });
    if (this.latencyData[0].series.length > maxDataPoints) {
      this.latencyData[0].series.shift();
    }

    // DLT Count
    this.dltData[0].series.push({ name: now, value: metrics.dltCount });
    if (this.dltData[0].series.length > maxDataPoints) {
      this.dltData[0].series.shift();
    }

    // Burn Rate
    this.burnRateData[0].series.push({ name: now, value: metrics.sloBurnRate1h });
    if (this.burnRateData[0].series.length > maxDataPoints) {
      this.burnRateData[0].series.shift();
    }

    // Trigger chart update by creating new object references
    this.latencyData = [...this.latencyData];
    this.dltData = [...this.dltData];
    this.burnRateData = [...this.burnRateData];
  }
}
