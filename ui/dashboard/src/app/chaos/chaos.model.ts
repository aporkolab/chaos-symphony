export enum FaultType {
  DELAY = 'DELAY',
  DUPLICATE = 'DUPLICATE',
  MUTATE = 'MUTATE',
  DROP = 'DROP'
}

export interface ChaosRule {
  pDrop: number;
  pDup: number;
  maxDelayMs: number;
  pCorrupt: number;
}

export interface ChaosSetting {
  enabled: boolean;
  probability: number;
  delayMs?: number;
}

export type ChaosSettings = {
  [key in FaultType]: ChaosSetting;
};
