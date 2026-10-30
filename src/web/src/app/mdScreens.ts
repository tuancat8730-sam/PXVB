import type { ComponentType } from 'react';
import { PlaceholderScreen } from './PlaceholderScreen';

export type MdScreenKey =
  | 'ksclForms'
  | 'ksclDesigner'
  | 'defectRules'
  | 'maintTemplates'
  | 'partLinks'
  | 'commonCatalog'
  | 'dataQuality';

export interface MdScreen {
  key: MdScreenKey;
  label: string;
  Component: ComponentType;
}

export const MD_SCREENS: readonly MdScreen[] = [
  { key: 'ksclForms', label: 'Biểu mẫu KSCL', Component: PlaceholderScreen },
  { key: 'ksclDesigner', label: 'Trình thiết kế KSCL', Component: PlaceholderScreen },
  { key: 'defectRules', label: 'Lỗi và luật A/B/C', Component: PlaceholderScreen },
  { key: 'maintTemplates', label: 'Bảo trì chuẩn', Component: PlaceholderScreen },
  { key: 'partLinks', label: 'Liên kết máy–phụ tùng', Component: PlaceholderScreen },
  { key: 'commonCatalog', label: 'Danh mục chung', Component: PlaceholderScreen },
  { key: 'dataQuality', label: 'Chất lượng dữ liệu', Component: PlaceholderScreen },
];
