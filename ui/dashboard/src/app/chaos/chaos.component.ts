import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ChaosService } from './chaos.service';
import { ChaosRule } from './chaos.model';
import { finalize } from 'rxjs';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-chaos',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './chaos.component.html',
  styleUrls: ['./chaos.component.css']
})
export class ChaosComponent implements OnInit {

  rules: Record<string, ChaosRule> = {};
  topics: string[] = [];
  ruleForm: FormGroup;
  isLoading = false;
  canaryEnabled = false;

  constructor(
    private fb: FormBuilder,
    private chaosService: ChaosService
  ) {
    this.ruleForm = this.fb.group({
      topic: ['', Validators.required],
      pDrop: [0, [Validators.min(0), Validators.max(1)]],
      pDup: [0, [Validators.min(0), Validators.max(1)]],
      maxDelayMs: [0, Validators.min(0)],
      pCorrupt: [0, [Validators.min(0), Validators.max(1)]]
    });
  }

  ngOnInit(): void {
    this.loadRules();
    this.loadCanaryState();
  }

  loadCanaryState(): void {
    this.chaosService.getCanary().subscribe({
      next: (config) => {
        this.canaryEnabled = config.enabled;
      },
      error: (err) => {
        console.warn('Failed to load canary state', err);
        this.canaryEnabled = false;
      }
    });
  }

  loadRules(): void {
    this.isLoading = true;
    this.chaosService.getRules()
      .pipe(finalize(() => this.isLoading = false))
      .subscribe(data => {
        this.rules = data;
        this.topics = Object.keys(data);
      });
  }

  editRule(topic: string): void {
    const rule = this.rules[topic];
    if (rule) {
      
      const formData = { topic, ...rule };
      this.ruleForm.setValue(formData);
    }
  }

  onSubmit(): void {
    if (this.ruleForm.invalid) {
      return;
    }
    this.isLoading = true;
    const { topic, ...ruleValues } = this.ruleForm.value;
    const rule: ChaosRule = ruleValues;

    this.chaosService.updateRule(topic, rule)
      .pipe(finalize(() => this.isLoading = false))
      .subscribe(() => {
        this.loadRules(); 
        this.ruleForm.reset({
          topic: '', pDrop: 0, pDup: 0, maxDelayMs: 0, pCorrupt: 0
        });
      });
  }

  onDelete(topic: string): void {
    if (confirm(`Are you sure you want to delete the rule for topic "${topic}"?`)) {
      this.isLoading = true;
      this.chaosService.deleteRule(topic)
        .pipe(finalize(() => this.isLoading = false))
        .subscribe(() => this.loadRules());
    }
  }

  onCanaryToggle(): void {
    this.isLoading = true;
    
    this.chaosService.setCanary(this.canaryEnabled, 0.05)
      .pipe(finalize(() => this.isLoading = false))
      .subscribe({
        next: (response) => {
          this.canaryEnabled = response.enabled;
          console.log(`Canary toggled to ${this.canaryEnabled}`);
        },
        error: (err) => {
          console.error('Failed to toggle canary', err);
          
          this.canaryEnabled = !this.canaryEnabled;
        }
      });
  }
}
