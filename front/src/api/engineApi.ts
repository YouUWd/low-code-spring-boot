/**
 * 数据引擎前端 API 契约层
 * 对接 DataEngineApi 的 query, batchQuery, save, batchSave
 */

export interface HeaderMeta {
  table: string;
  field: string;
  name: string;
  width?: number;
  searchType?: string;
  sortable?: boolean;
}

export interface FieldMeta {
  tableName: string;
  columnName: string;
  displayName: string;
  sortOrder?: number;
}

export interface EngineModuleMeta {
  moduleId: number;
  moduleCode: string;
  moduleName: string;
  primaryTable: string;
  headers?: HeaderMeta[];
  fields?: FieldMeta[];
  permissions?: any[];
}

export interface EngineDataResult<T> {
  meta: EngineModuleMeta;
  data: T;
}

export interface DataPage<T> {
  pageNo: number;
  pageSize: number;
  total: number;
  records: T[];
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

export const engineApi = {
  /** 动态数据集/列表查询 (统一走通用数据引擎) */
  async query(req: {
    moduleId: number;
    viewMode?: 'LIST' | 'DETAIL' | 'ALL';
    pageNo?: number;
    pageSize?: number;
    filters?: Record<string, any>;
    orderBy?: string;
    orderDirection?: 'ASC' | 'DESC';
  }): Promise<EngineDataResult<DataPage<any>>> {
    const endpoint = '/api/data/engine/query';
    const remoteData = await requestBackend(endpoint, {
      method: 'POST',
      body: JSON.stringify(req)
    });
    if (remoteData?.meta?.permissions) {
      try {
        const { useRoleStore } = await import('../stores/roleStore');
        useRoleStore().updateModulePermissions(remoteData.meta.permissions);
      } catch (_) {}
    }
    return remoteData;
  },

  /** 标准同构保存 (入参 tables 与 detail 响应结构 1:1 镜像对应) */
  async save(saveReq: { moduleId: number; tables: Record<string, any> }): Promise<{ id: number; masterId: number }> {
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

  /** 按 ID 获取单条学生核心档案 (模块 105: MOD-STUDENT-DETAIL，统一走 /api/data/engine/query) */
  async getStudentById(id: number): Promise<any> {
    const res = await requestBackend('/api/data/engine/query', {
      method: 'POST',
      body: JSON.stringify({
        moduleId: 105,
        filters: { id },
        viewMode: 'DETAIL',
        pageNo: 1,
        pageSize: 1
      })
    });
    const records = res?.data?.records || res?.records || [];
    const record = records[0] || {};
    return {
      student: record.student || {},
      clazz: record.clazz || {},
      student_profile: record.student_profile || {}
    };
  },

  /** 按 ID 获取单条课程排课全景档案 (模块 102: MOD-SCHOOL-COURSE) */
  async getCourseById(id: number): Promise<any> {
    const res = await requestBackend('/api/data/engine/query', {
      method: 'POST',
      body: JSON.stringify({
        moduleId: 102,
        filters: { id },
        viewMode: 'DETAIL',
        pageNo: 1,
        pageSize: 1
      })
    });
    const records = res?.data?.records || res?.records || [];
    const record = records[0] || {};
    return {
      course: record.course || {},
      teacher: record.teacher || {},
      course_syllabus: record.course_syllabus || {},
      course_schedule: record.course_schedule || []
    };
  },

  /** 按需独立查询学生的选修课程与成绩 (模块 103: MOD-STUDENT-COURSE) */
  async queryStudentCourses(studentId: number): Promise<{ headers: HeaderMeta[]; records: any[] }> {
    const res = await this.query({
      moduleId: 103,
      filters: { student_id: studentId },
      pageNo: 1,
      pageSize: 50
    });
    const records = res?.data?.records || [];
    return {
      headers: res?.meta?.headers || [],
      records: records.map((r: any) => r.student_course || r)
    };
  },

  /** 懒加载独立查询学生的荣誉表彰记录 (模块 104: MOD-STUDENT-AWARD) */
  async queryStudentAwards(studentId: number): Promise<{ headers: HeaderMeta[]; records: any[] }> {
    const res = await this.query({
      moduleId: 104,
      filters: { student_id: studentId },
      pageNo: 1,
      pageSize: 50
    });
    const records = res?.data?.records || [];
    return {
      headers: res?.meta?.headers || [],
      records: records.map((r: any) => r.student_award || r)
    };
  }
};

