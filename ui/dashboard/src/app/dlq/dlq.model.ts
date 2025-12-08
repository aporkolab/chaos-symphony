// We can expand this later if the API provides more structured message data
export type DlqMessage = string;

export interface DlqTopic {
  name: string;
  messageCount?: number;
}
