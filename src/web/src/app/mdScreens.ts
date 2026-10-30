import type { ComponentType } from 'react';
import { CommonCatalogScreen } from '../screens/md/CommonCatalogScreen';
import { KsclDesignerScreen } from '../screens/md/KsclDesignerScreen';
import { DefectRulesScreen } from '../screens/md/DefectRulesScreen';
import { KsclFormsScreen } from '../screens/md/KsclFormsScreen';
import { PlaceholderScreen } from './PlaceholderScreen';
import type { MdScreenKey, ScreenProps } from './screenProps';

export interface MdScreen {
  key: MdScreenKey;
  label: string;
  Component: ComponentType<ScreenProps>;
}

export const MD_SCREENS: readonly MdScreen[] = [
  { key: 'ksclForms', label: 'Biểu mẫu KSCL', Component: KsclFormsScreen },
  { key: 'ksclDesigner', label: 'Trình thiết kế KSCL', Component: KsclDesignerScreen },
  { key: 'defectRules', label: 'Lỗi và luật A/B/C', Component: DefectRulesScreen },
  { key: 'maintTemplates', label: 'Bảo trì chuẩn', Component: PlaceholderScreen },
  { key: 'partLinks', label: 'Liên kết máy–phụ tùng', Component: PlaceholderScreen },
  { key: 'commonCatalog', label: 'Danh mục chung', Component: CommonCatalogScreen },
  { key: 'dataQuality', label: 'Chất lượng dữ liệu', Component: PlaceholderScreen },
];
