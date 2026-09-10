/**
 * 数据引擎前端 API 契约层
 * 对接 DataEngineApi 的 query, batchQuery, save, batchSave
 */

export interface ModuleHeaderNodeDTO {
  moduleId?: number;
  label: string;
  fieldId?: number;
  dataIndex?: string;
  children?: ModuleHeaderNodeDTO[];
}

export interface HeaderMeta {
  fieldId?: number;
  table?: string;
  field?: string;
  name: string;
  dataIndex?: string;
  width?: number;
  searchType?: string;
  sortable?: boolean;
  modulePath?: number[];
  sortOrder?: number;
  moduleId?: number; // 可选兼容别名
}

/**
 * 第一性原理辅助：将 100% 同构的树形 Header 递归展开为表格列定义
 */
export function flattenHeaderTree(
  node: ModuleHeaderNodeDTO,
  currentPath: number[] = []
): HeaderMeta[] {
  if (!node) return [];
  const nextPath = node.moduleId ? [...currentPath, node.moduleId] : currentPath;
  const result: HeaderMeta[] = [];

  if (node.children && node.children.length > 0) {
    for (const child of node.children) {
      if (child.dataIndex) {
        // 叶子字段节点
        const parts = child.dataIndex.split('.');
        result.push({
          fieldId: child.fieldId,
          table: parts[0] || '',
          field: parts[1] || child.dataIndex,
          name: child.label,
          dataIndex: child.dataIndex,
          moduleId: nextPath[nextPath.length - 1],
          modulePath: nextPath
        });
      } else {
        // 子模块节点
        result.push(...flattenHeaderTree(child, nextPath));
      }
    }
  } else if (node.dataIndex) {
    const parts = node.dataIndex.split('.');
    result.push({
      fieldId: node.fieldId,
      table: parts[0] || '',
      field: parts[1] || node.dataIndex,
      name: node.label,
      dataIndex: node.dataIndex,
      moduleId: nextPath[nextPath.length - 1],
      modulePath: nextPath
    });
  }

  return result;
}

export interface FieldMeta {
  id?: number;
  fieldId?: number;
  moduleId?: number;
  tableName: string;
  columnName: string;
  displayName: string;
  sortOrder?: number;
  modulePath?: number[];
}

export interface ModuleNodeMeta {
  id: number;
  parentId: number;
  moduleCode: string;
  moduleName: string;
  primaryTable?: string;
  sortOrder?: number;
}

export interface EngineModuleMeta {
  moduleId: number;
  moduleCode: string;
  moduleName: string;
  moduleDesc?: string;
  primaryTable: string;
  moduleNodes?: ModuleNodeMeta[];
  headers?: HeaderMeta[];
  fields?: FieldMeta[];
  permissions?: any[];
}

export interface EngineHeaderResp {
  fields: FieldMeta[];
}

export interface EngineDataResult<T> {
  meta: EngineModuleMeta;
  data: T;
}

export interface DataPage<T> {
  pageNo: number;
  pageSize: number;
  total: number;
  header?: ModuleHeaderNodeDTO;
  records: T[];
  [key: string]: any;
}

export interface DynamicSaveReq {
  moduleId: number;
  record?: Record<string, any>;
  records?: Array<Record<string, any>>;
  tables?: Record<string, any>;
}

async function requestBackend(url: string, options: RequestInit = {}): Promise<any> {
  let currentRoleId = 1;
  try {
    const stored = localStorage.getItem('current_role_id');
    if (stored) currentRoleId = Number(stored) || 1;
  } catch (_) {}

  const res = await fetch(url, {
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      'Authorization': 'Bearer dev-test-token',
      'role-id': String(currentRoleId),
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

  throw new Error(json.msg || json.message || '后端请求失败');
}

export interface DynamicFilterItem {
  fieldId?: number;
  moduleId?: number;
  modulePath?: number[];
  tableName?: string;
  columnName?: string;
  value: any;
  operator?: string;
}

export interface DynamicSortItem {
  fieldId?: number;
  moduleId?: number;
  tableName?: string;
  columnName?: string;
  direction?: 'ASC' | 'DESC';
}

/**
 * 辅助函数：根据表头列定义与筛选值构建 DynamicFilterItem
 */
export function buildFilterItem(col: HeaderMeta, value: any, rootModuleId?: number): DynamicFilterItem | null {
  if (value === undefined || value === null || String(value).trim() === '') return null;
  const targetModuleId = col.moduleId || (col.modulePath && col.modulePath[col.modulePath.length - 1]) || rootModuleId;
  const operator = col.searchType === 'select' || col.searchType === 'singleSelect' ? 'EQ' : 'LIKE';
  return {
    fieldId: col.fieldId,
    moduleId: targetModuleId,
    modulePath: col.modulePath,
    tableName: col.table,
    columnName: col.field,
    value: String(value).trim(),
    operator
  };
}

/**
 * 辅助函数：根据表头列定义与排序方向构建 DynamicSortItem
 */
export function buildSortItem(col: HeaderMeta, direction: 'asc' | 'desc' | 'ASC' | 'DESC', rootModuleId?: number): DynamicSortItem {
  const targetModuleId = col.moduleId || (col.modulePath && col.modulePath[0]) || rootModuleId || 101;
  return {
    fieldId: col.fieldId,
    moduleId: targetModuleId,
    tableName: col.table,
    columnName: col.field,
    direction: direction.toUpperCase() as 'ASC' | 'DESC'
  };
}

export interface DynamicOptionReq {
  moduleId: number;
  tableName: string;
  columnName: string;
  keyword?: string;
}

export interface DynamicOptionItem {
  label: string;
  value: any;
}

export const engineApi = {
  /** 获取动态列表表头配置 (动静分离) */
  async getHeader(req: { moduleId?: number; fields?: number[] }): Promise<EngineHeaderResp> {
    const endpoint = '/api/data/engine/header';
    const remoteData = await requestBackend(endpoint, {
      method: 'POST',
      body: JSON.stringify(req)
    });
    return remoteData || { fields: [] };
  },

  /** 获取当前模块字段的下拉候选项列表 (支持 label-value 与 keyword 模糊过滤) */
  async getOptions(req: DynamicOptionReq): Promise<DynamicOptionItem[]> {
    const endpoint = '/api/data/engine/options';
    const remoteResp = await requestBackend(endpoint, {
      method: 'POST',
      body: JSON.stringify(req)
    });
    return Array.isArray(remoteResp) ? remoteResp : [];
  },

  /** 动态数据集/列表查询 (纯数据引擎，返回 DataPage，支持原子下发 header 树与同构数据) */
  async query(req: {
    moduleId?: number;
    fields?: number[];
    pageNo?: number;
    pageSize?: number;
    withHeader?: boolean;
    filters?: DynamicFilterItem[];
    sorts?: DynamicSortItem[];
    children?: any[];
  }): Promise<DataPage<any>> {
    const endpoint = '/api/data/engine/query';
    const remoteData = await requestBackend(endpoint, {
      method: 'POST',
      body: JSON.stringify(req)
    });
    if (remoteData) {
      const rootKey = req.moduleId ? String(req.moduleId) : '101';
      if (!remoteData.records && Array.isArray(remoteData[rootKey])) {
        remoteData.records = remoteData[rootKey];
      }
    }
    return remoteData;
  },

  /** 标准同构保存 (入参 record / records 与 query 响应结构 1:1 镜像对应) */
  async save(saveReq: DynamicSaveReq): Promise<{ id: number; masterId: number }> {
    const endpoint = '/api/data/engine/save';
    const remoteResp = await requestBackend(endpoint, {
      method: 'POST',
      body: JSON.stringify(saveReq)
    });

    return {
      id: remoteResp?.id || remoteResp?.masterId || 0,
      masterId: remoteResp?.masterId || remoteResp?.id || 0
    };
  },

  /** 多模块原子批量保存 (跨模块单事务强一致性落库，顺序由后端元数据拓扑驱动) */
  async batchSave(req: {
    modules: DynamicSaveReq[];
  }): Promise<{ results: Record<number, any> }> {
    const endpoint = '/api/data/engine/batch-save';
    const remoteResp = await requestBackend(endpoint, {
      method: 'POST',
      body: JSON.stringify(req)
    });

    return {
      results: remoteResp?.results || {}
    };
  },

  /** 按 ID 获取单条学生核心档案 (模块 105: MOD-STUDENT-DETAIL，统一走 /api/data/engine/query) */
  async getStudentById(id: number): Promise<any> {
    const res = await requestBackend('/api/data/engine/query', {
      method: 'POST',
      body: JSON.stringify({
        moduleId: 105,
        filters: [{ moduleId: 105, tableName: 'student', columnName: 'id', value: id, operator: 'EQ' }],
        viewMode: 'DETAIL',
        pageNo: 1,
        pageSize: 1
      })
    });
    const records = res?.data?.records || res?.records || [];
    const record = records[0] || {};
    // 兼容形态 A: record['105'].student / record['101'].student 或平铺 record.student
    const modSpace = record['105'] || record['101'] || record;
    return {
      student: modSpace.student || record.student || {},
      clazz: modSpace.clazz || record.clazz || {},
      student_profile: modSpace.student_profile || record.student_profile || {}
    };
  },

  /** 按 ID 获取单条课程排课全景档案 (模块 102: MOD-SCHOOL-COURSE) */
  async getCourseById(id: number): Promise<any> {
    const res = await requestBackend('/api/data/engine/query', {
      method: 'POST',
      body: JSON.stringify({
        moduleId: 102,
        filters: [{ moduleId: 102, tableName: 'course', columnName: 'id', value: id, operator: 'EQ' }],
        viewMode: 'DETAIL',
        pageNo: 1,
        pageSize: 1
      })
    });
    const records = res?.data?.records || res?.records || [];
    const record = records[0] || {};
    // 兼容形态 A: record['102'] 或平铺 record
    const modSpace = record['102'] || record;
    return {
      course: modSpace.course || record.course || {},
      teacher: modSpace.teacher || record.teacher || {},
      course_syllabus: modSpace.course_syllabus || record.course_syllabus || {},
      course_schedule: modSpace.course_schedule || record.course_schedule || []
    };
  },

  /** 单次请求查询学生的选修课程与成绩 (模块 103: MOD-STUDENT-COURSE) 并由后端 1:N 级联自动携带 score_items */
  async queryStudentCourses(studentId: number): Promise<{ headers: HeaderMeta[]; records: any[] }> {
    const res = await this.query({
      moduleId: 103,
      filters: [{ moduleId: 103, tableName: 'student_course', columnName: 'student_id', value: studentId, operator: 'EQ' }],
      pageNo: 1,
      pageSize: 50
    });
    const records = res?.records || res?.data?.records || [];
    const courseList = records.map((r: any) => {
      const modSpace = r['103'] || r;
      const sc = modSpace.student_course || r.student_course || {};
      const course = modSpace.course || r.course || {};
      const base = typeof r === 'object' ? r : {};

      const c = {
        ...base,
        ...course,
        ...sc,
        course_name: course.course_name || sc.course_name || base.course_name || '',
        semester: sc.semester || sc.term || base.semester || base.term || '2026-秋',
        score: sc.score !== undefined ? sc.score : base.score
      };
      c._expanded = true; // 默认直接展开
      // 直接消费后端返回的 1:N 考核分项数组 (兼容 modSpace / sc / r 等多形态)
      const rawScoreItems =
        (Array.isArray(modSpace.student_course_score_item) && modSpace.student_course_score_item) ||
        (Array.isArray(sc.student_course_score_item) && sc.student_course_score_item) ||
        (Array.isArray(r.student_course_score_item) && r.student_course_score_item) ||
        (Array.isArray(c.score_items) && c.score_items) ||
        [];
      c.score_items = rawScoreItems;
      c.student_course_score_item = rawScoreItems;
      return c;
    });

    return {
      headers: [],
      records: courseList
    };
  },

  /** 懒加载独立查询学生的荣誉表彰记录 (模块 104: MOD-STUDENT-AWARD) */
  async queryStudentAwards(studentId: number): Promise<{ headers: HeaderMeta[]; records: any[] }> {
    const res = await this.query({
      moduleId: 104,
      filters: [{ moduleId: 104, tableName: 'student_reward', columnName: 'student_id', value: studentId, operator: 'EQ' }],
      pageNo: 1,
      pageSize: 50
    });
    const records = res?.records || res?.data?.records || [];
    return {
      headers: [],
      records: records.map((r: any) => {
        const modSpace = r['104'] || r;
        return modSpace.student_award || r.student_award || r;
      })
    };
  }
};

