/**
 * 低代码模块配置中心 API
 * 对接 SysModuleController 的配置读写能力
 */

export interface ModuleInfo {
  id?: number;
  projectNo?: string;
  subjectId?: number;
  moduleCode: string;
  moduleName: string;
  moduleDesc?: string;
  parentId?: number;
  primaryTable?: string;
  moduleType?: 'LIST' | 'DETAIL';
  sortOrder?: number;
}

export interface ModuleFieldItem {
  id?: number;
  tableName: string;
  columnName: string;
  displayName: string;
  sortOrder?: number;
}

export interface TableRelationItem {
  id?: number;
  mainTable: string;
  mainField: string;
  joinTable: string;
  joinField: string;
  relationType: '1:1' | '1:N' | 'N:1';
  description?: string;
}

export interface ModuleHeaderItem {
  id?: number;
  name: string;
  table: string;
  field: string;
  width?: number;
  sortOrder?: number;
  searchType?: string;
  fixed?: 'left' | 'right' | 'none';
  ellipsis?: boolean;
  sortable?: boolean;
}

export interface SysModuleMetaComplete {
  module: ModuleInfo;
  fields: ModuleFieldItem[];
  tableRelations: TableRelationItem[];
  moduleHeaders: ModuleHeaderItem[];
  statuses?: any[];
  subModules?: SysModuleMetaComplete[];
}

async function requestConfig(url: string, options: RequestInit = {}): Promise<any> {
  const res = await fetch(url, {
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      'Authorization': 'Bearer dev-test-token',
      'X-Project-No': 'school',
      'X-Subject-Id': '1',
      ...(options.headers || {})
    },
    ...options
  });

  if (!res.ok) {
    const errorText = await res.text().catch(() => '');
    throw new Error(`HTTP ${res.status}: ${errorText || res.statusText}`);
  }

  const json = await res.json();
  if (json.code === 200 || json.code === 0 || json.status === 200) {
    return json.data !== undefined ? json.data : json;
  }

  throw new Error(json.msg || json.message || '配置中心请求失败');
}

export const configApi = {
  /** 获取项目下所有模块列表 */
  async listModules(category = 1): Promise<ModuleInfo[]> {
    return requestConfig(`/api/config/modules?category=${category}`);
  },

  /** 获取模块树 */
  async getModuleTree(category = 1): Promise<any[]> {
    return requestConfig(`/api/config/modules/tree?category=${category}`);
  },

  /** 获取指定模块的完整元数据配置 */
  async getModuleComplete(moduleId: number): Promise<SysModuleMetaComplete> {
    return requestConfig(`/api/config/modules/complete/${moduleId}`);
  },

  /** 保存或编辑模块完整配置 */
  async saveModuleComplete(data: {
    module: ModuleInfo;
    fields: ModuleFieldItem[];
    tableRelations?: TableRelationItem[];
    moduleHeaders?: ModuleHeaderItem[];
    statuses?: any[];
  }): Promise<number> {
    return requestConfig('/api/config/modules/complete', {
      method: 'POST',
      body: JSON.stringify(data)
    });
  },

  /** 删除模块 */
  async deleteModule(moduleId: number): Promise<void> {
    return requestConfig(`/api/config/modules/${moduleId}`, {
      method: 'DELETE'
    });
  }
};
