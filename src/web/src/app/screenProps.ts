export type MdScreenKey =
  | 'ksclForms'
  | 'ksclDesigner'
  | 'defectRules'
  | 'maintTemplates'
  | 'partLinks'
  | 'commonCatalog'
  | 'dataQuality';

export type NavigateToScreen = (key: MdScreenKey, recordId?: string) => void;

// Mỗi màn nhận bản ghi đang mở (nếu có) và hàm chuyển màn, để U-2 mở U-1 đúng biểu mẫu.
export interface ScreenProps {
  recordId: string | null;
  onNavigate: NavigateToScreen;
}
