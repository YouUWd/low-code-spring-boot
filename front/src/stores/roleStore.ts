import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { engineApi } from '../api/engineApi';

export interface RoleDef {
  id: number;
  name: string;
  code: string;
  tag: string;
  avatar: string;
  desc: string;
}

export interface FieldPermission {
  tableName: string;
  columnName: string;
  apply: number; // 0-否, 1-是
  view: number;  // 0-否, 1-是
  edit: number;  // 0-否, 1-是
}

export const useRoleStore = defineStore('roleStore', () => {
  // 可选角色清单
  const availableRoles: RoleDef[] = [
    {
      id: 1,
      name: '教务管理员',
      code: 'ADMIN',
      tag: '👑 全局最高权限',
      avatar: '🛡️',
      desc: '具备全模块全部字段的申请(Apply)、查看(View)与日常编辑(Edit)权限'
    },
    {
      id: 2,
      name: '普通任课教师',
      code: 'TEACHER',
      tag: '🎓 教学与打分专员',
      avatar: '👨‍🏫',
      desc: '敏感档案(身份证/电话)脱敏不可见；学生姓名学号只读；开放选课成绩独家录入与修改'
    },
    {
      id: 3,
      name: '学生本人',
      code: 'STUDENT',
      tag: '👤 档案与成绩查看',
      avatar: '🎒',
      desc: '仅可查看个人档案与成绩信息，全部编辑修改权限关闭'
    }
  ];

  // 当前选中角色 (默认教务管理员)
  const currentRoleId = ref<number>(1);

  const currentRole = computed(() => {
    return availableRoles.find(r => r.id === currentRoleId.value) || availableRoles[0];
  });

  // 响应式存储由当前模块 query meta 驱动的真实权限规则表 (key 为 `${tableName}.${columnName}`)
  const dynamicPermissions = ref<Record<string, FieldPermission>>({});

  /** 由 /engine/query 响应中的 meta.permissions 驱动更新当前上下文权限 */
  function updateModulePermissions(permissions: any[]) {
    if (!Array.isArray(permissions) || permissions.length === 0) {
      return;
    }
    const permMap: Record<string, FieldPermission> = { ...dynamicPermissions.value };
    permissions.forEach((item: any) => {
      if (item.tableName && item.columnName) {
        const key = `${item.tableName}.${item.columnName}`;
        permMap[key] = {
          tableName: item.tableName,
          columnName: item.columnName,
          apply: Number(item.apply ?? 1),
          view: Number(item.view ?? 1),
          edit: Number(item.edit ?? 1)
        };
      }
    });
    dynamicPermissions.value = permMap;
  }

  /** 判断当前角色对特定物理列是否可查看 (view)，100% 以 meta.permissions 为准 */
  function canView(tableName: string, columnName: string): boolean {
    const key = `${tableName}.${columnName}`;
    const perm = dynamicPermissions.value[key];
    if (perm !== undefined) {
      return perm.view === 1;
    }
    return true; // 默认可见
  }

  /** 判断当前角色对特定物理列是否可编辑修改 (edit)，100% 以 meta.permissions 为准 */
  function canEdit(tableName: string, columnName: string): boolean {
    const key = `${tableName}.${columnName}`;
    const perm = dynamicPermissions.value[key];
    if (perm !== undefined) {
      return perm.edit === 1;
    }
    return false; // 未授权默认只读不可编辑
  }

  /** 判断当前角色对特定物理列是否可新增申请填报 (apply)，100% 以 meta.permissions 为准 */
  function canApply(tableName: string, columnName: string): boolean {
    const key = `${tableName}.${columnName}`;
    const perm = dynamicPermissions.value[key];
    if (perm !== undefined) {
      return perm.apply === 1;
    }
    return false; // 未授权默认不可申请
  }

  /** 判断当前上下文或指定表中是否有任意字段具备编辑权限 */
  function hasAnyEditPermission(tableName?: string): boolean {
    const list = Object.values(dynamicPermissions.value);
    if (list.length === 0) return false;
    if (tableName) {
      return list.some(p => p.tableName === tableName && p.edit === 1);
    }
    return list.some(p => p.edit === 1);
  }

  /** 判断当前上下文或指定表中是否有任意字段具备查看权限 */
  function hasAnyViewPermission(tableName?: string): boolean {
    const list = Object.values(dynamicPermissions.value);
    if (list.length === 0) return false;
    if (tableName) {
      return list.some(p => p.tableName === tableName && p.view === 1);
    }
    return list.some(p => p.view === 1);
  }

  /** 切换角色 */
  function switchRole(roleId: number) {
    currentRoleId.value = roleId;
    dynamicPermissions.value = {};
    try {
      localStorage.setItem('current_role_id', String(roleId));
    } catch (_) {}
  }

  return {
    availableRoles,
    currentRoleId,
    currentRole,
    dynamicPermissions,
    updateModulePermissions,
    canView,
    canEdit,
    canApply,
    hasAnyEditPermission,
    hasAnyViewPermission,
    switchRole
  };
});
