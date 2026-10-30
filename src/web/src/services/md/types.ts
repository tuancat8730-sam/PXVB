// Kiểu dữ liệu của API masterdata (/api/v1/md). Khớp với PageResponse, CatalogController,
// PartLinkQueries và DataQualityService phía backend.

export interface PageResponse<T> {
  content: T[];
  page: number;
  pageSize: number;
  total: number;
}

export type CatalogRecord = Record<string, unknown>;

export interface ListParams {
  q?: string;
  page?: number;
  pageSize?: number;
  activeOnly?: boolean;
}

export interface CatalogColumn {
  name: string;
  label: string;
  type: string;
  required: boolean;
  values: string[] | null;
  refTable: string | null;
  immutable: boolean;
}

export interface CatalogMeta {
  resource: string;
  title: string;
  versioned: boolean;
  hasActive: boolean;
  keys: string[];
  columns: CatalogColumn[];
}

export interface PartOfMachine {
  linkId: string;
  sparePartId: string;
  partNo: string;
  partName: string;
  componentId: string | null;
  componentCode: string | null;
  componentName: string | null;
  taskTemplateId: string | null;
  typicalQty: number | null;
  scope: string;
}

export interface MachineOfPart {
  machineId: string;
  machineCode: string;
  machineName: string;
  scope: string;
}

export interface DataQualityIssue {
  code: string;
  severity: string;
  message: string;
  count: number;
  samples: string[];
}

export interface DataQualityReport {
  generatedAt: string;
  hasErrors: boolean;
  issues: DataQualityIssue[];
}
