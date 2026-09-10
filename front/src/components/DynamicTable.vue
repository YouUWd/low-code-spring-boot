<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { useRoleStore } from '../stores/roleStore';
import {
  type HeaderMeta,
  type EngineModuleMeta,
  type ModuleNodeMeta,
  type DynamicFilterItem,
  type DynamicSortItem,
  type DynamicOptionItem,
  buildFilterItem,
  buildSortItem,
  engineApi
} from '../api/engineApi';
import { Eye, Edit3, ArrowUpDown, ArrowUp, ArrowDown, Filter, Settings, Sparkles, Check, RotateCcw, X } from 'lucide-vue-next';

const props = defineProps<{
  title: string;
  headers: HeaderMeta[];
  records: any[];
  meta?: EngineModuleMeta;
  loading?: boolean;
}>();

const emit = defineEmits<{
  (e: 'view-detail', record: any): void;
  (e: 'query-change', payload: { filters: DynamicFilterItem[]; sorts: DynamicSortItem[] }): void;
  (e: 'search'): void;
}>();

const roleStore = useRoleStore();

// 存储各个列在表头内配置的筛选值 (key 为 `${col.table}.${col.field}`)
const columnFilters = ref<Record<string, string>>({});

// 当前打开的表头筛选 Popover (key 为 `${col.table}.${col.field}`，为空表示关闭)
const activePopoverKey = ref<string | null>(null);

// 暂存当前正在编辑的筛选值（点击确定后生效）
const tempFilterValue = ref<string>('');

// 多列复合排序列与方向结构
export interface ColumnSortState {
  key: string;
  order: 'asc' | 'desc';
  col: HeaderMeta;
}

// 当前多字段排序状态列表 (按添加顺序维护优先级)
const sortStates = ref<ColumnSortState[]>([]);

function getSortOrder(key: string): 'asc' | 'desc' | null {
  const item = sortStates.value.find(s => s.key === key);
  return item ? item.order : null;
}

function getSortPriority(key: string): number | null {
  if (sortStates.value.length <= 1) return null;
  const idx = sortStates.value.findIndex(s => s.key === key);
  return idx >= 0 ? idx + 1 : null;
}

// =========================================================================
// 🌟 第一性原理：纯响应元数据驱动（Zero-Hardcoding）模块前缀树与表头计算算法
// =========================================================================

/**
 * 模块信息解析器：
 * - 根模块取自 props.meta (moduleId, moduleCode, moduleName)
 * - 子模块取自 props.meta.moduleNodes 数组
 */
const moduleNodeMap = computed(() => {
  const map = new Map<number, { id: number; name: string; code: string; sortOrder: number; parentId: number }>();
  
  if (props.meta) {
    if (props.meta.moduleId) {
      map.set(Number(props.meta.moduleId), {
        id: Number(props.meta.moduleId),
        name: props.meta.moduleName || `模块 ${props.meta.moduleId}`,
        code: props.meta.moduleCode || `M${props.meta.moduleId}`,
        sortOrder: 0,
        parentId: 0
      });
    }

    if (props.meta.moduleNodes && Array.isArray(props.meta.moduleNodes)) {
      props.meta.moduleNodes.forEach(node => {
        map.set(Number(node.id), {
          id: Number(node.id),
          name: node.moduleName,
          code: node.moduleCode || `M${node.id}`,
          sortOrder: node.sortOrder ?? 999,
          parentId: Number(node.parentId) || 0
        });
      });
    }
  }

  return map;
});

function getModuleInfo(mId: number) {
  const info = moduleNodeMap.value.get(mId);
  if (info) return info;
  return {
    id: mId,
    name: `模块 ${mId}`,
    code: `M${mId}`,
    sortOrder: 999,
    parentId: 0
  };
}

/** 模块拓扑树内部节点结构 */
interface DynamicTreeNode {
  id: number;
  depth: number;
  name: string;
  badge: string;
  sortOrder: number;
  columns: HeaderMeta[];
  children: Map<number, DynamicTreeNode>;
}

/** 动态多行表头单元格结构 */
export interface MultiLevelHeaderCell {
  key: string;
  title: string;
  moduleId?: number;
  badge?: string;
  count: number;
  colSpan: number;
  rowSpan: number;
  isSubModule?: boolean;
  isDirectGroup?: boolean;
  isModuleGroup?: boolean;
}

/**
 * 🌟 两级严格排序与字段平铺计算属性：
 * 规则：先根据模块的顺序（moduleNodes.sortOrder），然后在模块叶子节点内才使用列的 sortOrder！
 */
const clusteredHeaders = computed(() => {
  if (!props.headers || props.headers.length === 0) return [];
  const list = props.headers.filter(h => roleStore.canView(h.table, h.field));
  if (list.length === 0) return [];

  const rootModId = props.meta?.moduleId || (list[0].modulePath && list[0].modulePath[0]) || 101;
  const rootInfo = getModuleInfo(rootModId);

  // 1. 构建前缀多叉树
  const rootNode: DynamicTreeNode = {
    id: rootModId,
    depth: 1,
    name: rootInfo.name,
    badge: rootInfo.code.startsWith('M') ? rootInfo.code : `M${rootModId}`,
    sortOrder: 0,
    columns: [],
    children: new Map()
  };

  list.forEach(col => {
    const rawPath = (col.modulePath && col.modulePath.length > 0) ? col.modulePath : [col.moduleId || rootModId];
    let currentNode = rootNode;

    for (let i = 1; i < rawPath.length; i++) {
      const stepId = rawPath[i];
      if (!currentNode.children.has(stepId)) {
        const stepInfo = getModuleInfo(stepId);
        currentNode.children.set(stepId, {
          id: stepId,
          depth: i + 1,
          name: stepInfo.name,
          badge: stepInfo.code.startsWith('M') ? stepInfo.code : `M${stepId}`,
          sortOrder: stepInfo.sortOrder,
          columns: [],
          children: new Map()
        });
      }
      currentNode = currentNode.children.get(stepId)!;
    }

    // 字段挂载在其所属模块链条的最末端节点上
    currentNode.columns.push(col);
  });

  // 2. 深度优先先序遍历（DFS）提取排好序的平铺列列表
  // 核心原则：
  // 1) 递归子节点列表先严格按模块自身的 sortOrder 升序排列；
  // 2) 节点的直属字段在模块内部严格按字段自身 sortOrder 升序排列；
  // 3) 根直属字段排在子模块之前。
  const flatResult: HeaderMeta[] = [];

  function dfsExtract(node: DynamicTreeNode) {
    // 模块叶子节点内部独立排序
    if (node.columns.length > 0) {
      const sortedCols = [...node.columns].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0));
      flatResult.push(...sortedCols);
    }

    // 兄弟模块节点之间先根据模块自身 sortOrder 排序
    const sortedChildren = Array.from(node.children.values()).sort((a, b) => a.sortOrder - b.sortOrder);
    sortedChildren.forEach(child => dfsExtract(child));
  }

  dfsExtract(rootNode);
  return flatResult;
});

// 兼容别名
const visibleHeaders = computed(() => clusteredHeaders.value);

/**
 * 🌟 计算模块多级嵌套层次拓扑与自适应表头深度
 */
interface TreeLevel3Node {
  level3Id: number;
  title: string;
  badge?: string;
  isSubModule: boolean;
  columns: HeaderMeta[];
}

interface TreeLevel2Node {
  level2Id: number;
  title: string;
  badge: string;
  columns: HeaderMeta[];
  level3Nodes: TreeLevel3Node[];
  hasLevel3: boolean;
}

interface TreeLevel1Node {
  level1Id: number;
  title: string;
  badge: string;
  columns: HeaderMeta[];
  level2Nodes: TreeLevel2Node[];
}

const moduleTreeHierarchy = computed(() => {
  const headers = clusteredHeaders.value;
  if (headers.length === 0) return { rootNodes: [], maxDepth: 1, hasLevel3: false, hasLevel2: false };

  const rootModId = props.meta?.moduleId || headers[0]?.modulePath?.[0] || 101;
  const rootInfo = getModuleInfo(rootModId);

  // 构建展示树
  const rootNode: TreeLevel1Node = {
    level1Id: rootModId,
    title: rootInfo.name,
    badge: rootInfo.code.startsWith('M') ? rootInfo.code : `M${rootModId}`,
    columns: [],
    level2Nodes: []
  };

  headers.forEach(h => {
    rootNode.columns.push(h);
    const path = (h.modulePath && h.modulePath.length > 0) ? h.modulePath : [rootModId];

    if (path.length <= 1) {
      // 根模块直属字段（放入虚拟直属二级模块组）
      const directL2Id = rootModId;
      let l2 = rootNode.level2Nodes.find(n => n.level2Id === directL2Id);
      if (!l2) {
        l2 = {
          level2Id: directL2Id,
          title: `${rootInfo.name}直属`,
          badge: `M${directL2Id}`,
          columns: [],
          level3Nodes: [],
          hasLevel3: false
        };
        rootNode.level2Nodes.push(l2);
      }
      l2.columns.push(h);
    } else {
      const l2Id = path[1];
      const l2Info = getModuleInfo(l2Id);
      let l2 = rootNode.level2Nodes.find(n => n.level2Id === l2Id);
      if (!l2) {
        l2 = {
          level2Id: l2Id,
          title: l2Info.name,
          badge: l2Info.code.startsWith('M') ? l2Info.code : `M${l2Id}`,
          columns: [],
          level3Nodes: [],
          hasLevel3: false
        };
        rootNode.level2Nodes.push(l2);
      }
      l2.columns.push(h);

      if (path.length >= 3) {
        const l3Id = path[2];
        const l3Info = getModuleInfo(l3Id);
        let l3 = l2.level3Nodes.find(n => n.level3Id === l3Id);
        if (!l3) {
          l3 = {
            level3Id: l3Id,
            title: l3Info.name,
            badge: l3Info.code.startsWith('M') ? l3Info.code : `M${l3Id}`,
            isSubModule: true,
            columns: []
          };
          l2.level3Nodes.push(l3);
        }
        l3.columns.push(h);
      } else {
        // 二级模块直属字段，作为三级直属分组
        const l3Id = l2Id * 100;
        let l3 = l2.level3Nodes.find(n => n.level3Id === l3Id);
        if (!l3) {
          l3 = {
            level3Id: l3Id,
            title: `${l2Info.name}直属`,
            badge: undefined,
            isSubModule: false,
            columns: []
          };
          l2.level3Nodes.push(l3);
        }
        l3.columns.push(h);
      }
    }
  });

  // 检测是否存在任意 Level 3 节点
  let hasAnyLevel3 = false;
  rootNode.level2Nodes.forEach(l2 => {
    if (l2.level3Nodes.length > 1 || (l2.level3Nodes.length === 1 && l2.level3Nodes[0].isSubModule)) {
      l2.hasLevel3 = true;
      hasAnyLevel3 = true;
    }
  });

  const hasLevel2 = rootNode.level2Nodes.length > 0;
  let maxDepth = 1;
  if (hasAnyLevel3) {
    maxDepth = 4;
  } else if (hasLevel2 && rootNode.level2Nodes.length > 1) {
    maxDepth = 3;
  } else if (rootNode.columns.length > 0) {
    maxDepth = 2;
  }

  return { rootNodes: [rootNode], maxDepth, hasLevel3: hasAnyLevel3, hasLevel2 };
});

const maxHeaderDepth = computed(() => moduleTreeHierarchy.value.maxDepth);
const hasModuleGroupHeader = computed(() => maxHeaderDepth.value > 1);

/** 🌟 Row 0：第一行（根模块行） */
const level1HeaderRow = computed((): MultiLevelHeaderCell[] => {
  const { rootNodes, maxDepth } = moduleTreeHierarchy.value;
  if (maxDepth <= 1) return [];

  return rootNodes.map((r, idx) => ({
    key: `lvl1-root-${r.level1Id}-${idx}`,
    title: r.title,
    moduleId: r.level1Id,
    badge: r.badge,
    count: r.columns.length,
    colSpan: r.columns.length,
    rowSpan: 1,
    isModuleGroup: true
  }));
});

/** 🌟 Row 1：第二行（二级业务模块行） */
const level2HeaderRow = computed((): MultiLevelHeaderCell[] => {
  const { rootNodes, maxDepth, hasLevel3 } = moduleTreeHierarchy.value;
  if (maxDepth < 3) return [];

  const cells: MultiLevelHeaderCell[] = [];
  rootNodes.forEach((r, rIdx) => {
    r.level2Nodes.forEach((l2, l2Idx) => {
      const colSpan = l2.columns.length;
      // 当全表存在 Level 3 时，未拆分三级的模块（如 101 直属基本档案）纵向贯通 Row 1 和 Row 2 (rowSpan = 2)
      const rowSpan = hasLevel3 ? (l2.hasLevel3 ? 1 : 2) : 1;

      cells.push({
        key: `lvl2-mod-${l2.level2Id}-${rIdx}-${l2Idx}`,
        title: l2.title,
        moduleId: l2.level2Id,
        badge: l2.badge,
        count: colSpan,
        colSpan,
        rowSpan,
        isModuleGroup: true
      });
    });
  });

  return cells;
});

/** 🌟 Row 2：第三行（三级子模块行） */
const level3HeaderRow = computed((): MultiLevelHeaderCell[] => {
  const { rootNodes, maxDepth, hasLevel3 } = moduleTreeHierarchy.value;
  if (maxDepth < 4 || !hasLevel3) return [];

  const cells: MultiLevelHeaderCell[] = [];
  rootNodes.forEach((r, rIdx) => {
    r.level2Nodes.forEach((l2, l2Idx) => {
      if (!l2.hasLevel3) {
        // 未拆分 Level 3 的模块已在 Row 1 通过 rowSpan = 2 贯通，本行不需占位
        return;
      }

      l2.level3Nodes.forEach((l3, l3Idx) => {
        const colSpan = l3.columns.length;
        cells.push({
          key: `lvl3-sub-${l2.level2Id}-${l3.level3Id}-${rIdx}-${l2Idx}-${l3Idx}`,
          title: l3.title,
          moduleId: l3.level3Id,
          badge: l3.badge,
          count: colSpan,
          colSpan,
          rowSpan: 1,
          isSubModule: l3.isSubModule,
          isDirectGroup: !l3.isSubModule
        });
      });
    });
  });

  return cells;
});

// 是否存在任意字段配置了可编辑权限
const hasEditPermission = computed(() => {
  const dynamicList = Object.values(roleStore.dynamicPermissions);
  if (dynamicList.length > 0) {
    return dynamicList.some(p => p.edit === 1);
  }
  if (props.headers && props.headers.length > 0) {
    return props.headers.some(h => roleStore.canEdit(h.table, h.field));
  }
  return false;
});

// 是否存在任意字段配置了只读查看权限
const hasViewPermission = computed(() => {
  const dynamicList = Object.values(roleStore.dynamicPermissions);
  if (dynamicList.length > 0) {
    return dynamicList.some(p => p.view === 1);
  }
  if (props.headers && props.headers.length > 0) {
    return props.headers.some(h => roleStore.canView(h.table, h.field));
  }
  return visibleHeaders.value.length > 0;
});

// 是否展示操作区域：只要有可编辑权限或只读权限就展示，什么权限都没有则隐藏操作区域
const hasActionColumn = computed(() => {
  return hasEditPermission.value || hasViewPermission.value;
});

/** 获取列的全局唯一键，以 modulePath 链路（或 table.field）构建确定性标识 */
function getColumnKey(col: HeaderMeta): string {
  const pathKey = (col.modulePath && col.modulePath.length > 0) ? col.modulePath.join('_') : (col.moduleId || '');
  return `${pathKey}.${col.table}.${col.field}`;
}

/** 深度递归收集任意层级（直属伴生表、1:N 从表、1:N:N 孙表）中的字段值 */
function extractFieldValues(data: any, targetTable: string, targetField: string): any[] {
  if (!data) return [];
  const results: any[] = [];

  // 1. 如果当前对象直接包含目标物理表 (如 record['student_course'] 或 record['103']['student_course'])
  if (data[targetTable] !== undefined && data[targetTable] !== null) {
    const targetData = data[targetTable];
    if (Array.isArray(targetData)) {
      targetData.forEach(item => {
        if (item && item[targetField] !== undefined && item[targetField] !== null && item[targetField] !== '') {
          results.push(item[targetField]);
        }
      });
    } else if (typeof targetData === 'object') {
      if (targetData[targetField] !== undefined && targetData[targetField] !== null && targetData[targetField] !== '') {
        results.push(targetData[targetField]);
      }
    }
    if (results.length > 0) {
      return results;
    }
  }

  // 2. 深度遍历所有从表数组与模块子对象（如 student_award、student_course、子模块容器等），递归向下探查
  for (const key of Object.keys(data)) {
    const val = data[key];
    if (Array.isArray(val)) {
      for (const item of val) {
        if (item && typeof item === 'object') {
          const subRes = extractFieldValues(item, targetTable, targetField);
          if (subRes.length > 0) {
            results.push(...subRes);
          }
        }
      }
    } else if (val && typeof val === 'object') {
      // 支持递归深入纯对象模块空间（如 record['104'] 或 item['106']）
      const subRes = extractFieldValues(val, targetTable, targetField);
      if (subRes.length > 0) {
        results.push(...subRes);
      }
    }
  }

  return results;
}

/**
 * 基于 modulePath（模块路径链条）逐级下钻，再结合 table 与 field 渲染数据
 */
function getCellValuesList(record: any, col: HeaderMeta): any[] {
  if (!record || !col) return [];
  const table = col.table || (col.dataIndex ? col.dataIndex.split('.')[0] : '');
  const field = col.field || (col.dataIndex ? col.dataIndex.split('.')[1] : '');
  const { moduleId, modulePath, fieldId } = col;

  // 1. 🌟 依据 modulePath 模块下钻路径（如 [101, 103]）定位到目标模块空间，再结合 table/field 提取
  const targetModulePath = (modulePath && Array.isArray(modulePath) && modulePath.length > 0)
    ? modulePath
    : (moduleId ? [moduleId] : []);

  if (targetModulePath.length > 0) {
    let currentObjects: any[] = [record];
    for (let i = 0; i < targetModulePath.length; i++) {
      const mid = targetModulePath[i];
      const midStr = String(mid);
      const nextObjects: any[] = [];

      for (const obj of currentObjects) {
        if (!obj || typeof obj !== 'object') continue;

        // 检查内嵌的 children 自相似树
        if (obj.children && Array.isArray(obj.children)) {
          for (const cnode of obj.children) {
            if (Number(cnode.moduleId) === Number(mid) && Array.isArray(cnode.records)) {
              nextObjects.push(...cnode.records);
            }
          }
        }

        // 优先在当前模块对象的 [midStr] 空间中下钻 (严格形态1结构: record["101"]["103"])
        const modSpace = obj[midStr] !== undefined ? obj[midStr] : (obj[mid] !== undefined ? obj[mid] : null);
        if (modSpace) {
          if (Array.isArray(modSpace)) {
            for (const item of modSpace) {
              if (item && typeof item === 'object') nextObjects.push(item);
            }
          } else if (typeof modSpace === 'object') {
            nextObjects.push(modSpace);
          }
        }
      }
      currentObjects = nextObjects;
      if (currentObjects.length === 0) break;
    }

    if (currentObjects.length > 0) {
      const results: any[] = [];
      for (const modObj of currentObjects) {
        // 在目标模块对象中提取 table/field
        const vals = extractFieldValues(modObj, table, field);
        if (vals && vals.length > 0) {
          results.push(...vals);
        }
      }
      if (results.length > 0) return results;
    }
  }

  // 2. 备用兜底：若无 modulePath 或下钻未命中，直接在 record 全局递归提取 table/field
  const globalVals = extractFieldValues(record, table, field);
  if (globalVals && globalVals.length > 0) return globalVals;

  // 3. 单字段兜底
  if (record[field] !== undefined && record[field] !== null && typeof record[field] !== 'object') {
    return [record[field]];
  }
  if (record[`${table}.${field}`] !== undefined && record[`${table}.${field}`] !== null) {
    return [record[`${table}.${field}`]];
  }

  return [];
}

/**
 * 获取单元格展示值（兼容单值模式）
 */
function getCellValue(record: any, col: HeaderMeta): any {
  const list = getCellValuesList(record, col);
  if (!list || list.length === 0) return '-';
  return list.length === 1 ? list[0] : list.join('、');
}

// 结构化查询转换与父级联动
function getStructuredFilters(): DynamicFilterItem[] {
  const result: DynamicFilterItem[] = [];
  const rootModId = props.meta?.moduleId || 101;
  Object.entries(columnFilters.value).forEach(([key, filterVal]) => {
    if (!filterVal || !filterVal.trim()) return;
    const col = visibleHeaders.value.find(h => getColumnKey(h) === key);
    if (col) {
      const item = buildFilterItem(col, filterVal, rootModId);
      if (item) result.push(item);
    }
  });
  return result;
}

function getStructuredSorts(): DynamicSortItem[] {
  if (!sortStates.value || sortStates.value.length === 0) return [];
  const rootModId = props.meta?.moduleId || 101;
  return sortStates.value.map(item => buildSortItem(item.col, item.order, rootModId));
}

function notifyQueryChange() {
  const filters = getStructuredFilters();
  const sorts = getStructuredSorts();
  emit('query-change', { filters, sorts });
}

// 活跃筛选标签列表
interface ActiveFilterTag {
  key: string;
  name: string;
  value: string;
  col: HeaderMeta;
}

const activeFilterTags = computed<ActiveFilterTag[]>(() => {
  const tags: ActiveFilterTag[] = [];
  Object.entries(columnFilters.value).forEach(([key, val]) => {
    if (!val || !val.trim()) return;
    const col = visibleHeaders.value.find(h => getColumnKey(h) === key);
    if (col) {
      tags.push({
        key,
        name: col.name,
        value: val.trim(),
        col
      });
    }
  });
  return tags;
});

function removeFilterTag(key: string) {
  delete columnFilters.value[key];
  notifyQueryChange();
}

function clearAllFilters() {
  columnFilters.value = {};
  notifyQueryChange();
}

function removeSortState(key: string) {
  const idx = sortStates.value.findIndex(s => s.key === key);
  if (idx >= 0) {
    sortStates.value.splice(idx, 1);
    notifyQueryChange();
  }
}

function clearSort() {
  sortStates.value = [];
  notifyQueryChange();
}

// 切换多字段排序：未排 -> asc -> desc -> 恢复不排序
function toggleSort(col: HeaderMeta, e: Event) {
  e.stopPropagation();
  if (!col.sortable) return;
  const key = getColumnKey(col);
  const idx = sortStates.value.findIndex(s => s.key === key);
  if (idx === -1) {
    sortStates.value.push({ key, order: 'asc', col });
  } else if (sortStates.value[idx].order === 'asc') {
    sortStates.value[idx].order = 'desc';
  } else {
    sortStates.value.splice(idx, 1);
  }
  notifyQueryChange();
}

// 当前打开 Popover 列的远程候选选项列表
const popoverOptions = ref<DynamicOptionItem[]>([]);
const popoverLoading = ref<boolean>(false);

// 加载当前列在当前模块下的去重候选选项
async function loadColumnOptions(col: HeaderMeta) {
  const rootModId = props.meta?.moduleId || (col.modulePath && col.modulePath[0]) || 101;
  const targetModId = col.moduleId || (col.modulePath && col.modulePath[col.modulePath.length - 1]) || rootModId;
  popoverLoading.value = true;
  popoverOptions.value = [];
  try {
    const list = await engineApi.getOptions({
      moduleId: targetModId,
      tableName: col.table,
      columnName: col.field
    });
    popoverOptions.value = list;
  } catch (err) {
    console.warn('远程加载候选项失败，降级本地取值:', err);
    popoverOptions.value = getUniqueOptions(col).map(v => ({ label: v, value: v }));
  } finally {
    popoverLoading.value = false;
  }
}

// 筛选匹配的候选项
const filteredPopoverOptions = computed(() => {
  if (!popoverOptions.value || popoverOptions.value.length === 0) {
    return [];
  }
  const kw = (tempFilterValue.value || '').trim().toLowerCase();
  if (!kw) {
    return popoverOptions.value;
  }
  return popoverOptions.value.filter(opt => 
    String(opt.label).toLowerCase().includes(kw) || 
    String(opt.value).toLowerCase().includes(kw)
  );
});

// 点击候选项直接选择并应用筛选
function selectOption(key: string, val: any) {
  tempFilterValue.value = String(val);
  applyFilter(key);
}

// 切换打开/关闭漏斗筛选浮层
function toggleFilterPopover(col: HeaderMeta, e: Event) {
  e.stopPropagation();
  const key = getColumnKey(col);
  if (activePopoverKey.value === key) {
    activePopoverKey.value = null;
  } else {
    activePopoverKey.value = key;
    tempFilterValue.value = columnFilters.value[key] || '';
    loadColumnOptions(col);
  }
}

// 确认应用当前列筛选
function applyFilter(key: string) {
  if (tempFilterValue.value && tempFilterValue.value.trim()) {
    columnFilters.value[key] = tempFilterValue.value.trim();
  } else {
    delete columnFilters.value[key];
  }
  activePopoverKey.value = null;
  notifyQueryChange();
}

// 重置当前列筛选
function resetFilter(key: string) {
  tempFilterValue.value = '';
  delete columnFilters.value[key];
  activePopoverKey.value = null;
  notifyQueryChange();
}

// 获取某一列在当前数据集中的所有唯一值（用于 select 下拉筛选）
function getUniqueOptions(col: HeaderMeta): string[] {
  const values = new Set<string>();
  props.records.forEach(r => {
    const vals = getCellValuesList(r, col);
    if (vals && vals.length > 0) {
      vals.forEach(v => {
        if (v !== undefined && v !== null && v !== '' && v !== '-') {
          values.add(String(v));
        }
      });
    }
  });
  return Array.from(values);
}

// 点击页面其他区域自动关闭浮层
function handleDocumentClick(e: MouseEvent) {
  const target = e.target as HTMLElement;
  if (!target.closest('.th-filter-popover') && !target.closest('.th-filter-btn')) {
    activePopoverKey.value = null;
  }
}

onMounted(() => {
  document.addEventListener('click', handleDocumentClick);
});

onUnmounted(() => {
  document.removeEventListener('click', handleDocumentClick);
});

// 前端根据表头各列筛选条件自动过滤与排序数据
const filteredRecords = computed(() => {
  let list = props.records || [];

  // 1. 各列内联筛选
  Object.entries(columnFilters.value).forEach(([key, filterVal]) => {
    if (!filterVal || !filterVal.trim()) return;
    const searchStr = filterVal.trim().toLowerCase();

    // 从 visibleHeaders 中找到当前筛选列的元数据定义
    const col = visibleHeaders.value.find(h => getColumnKey(h) === key);
    if (!col) return;

    list = list.filter(r => {
      const vals = getCellValuesList(r, col);
      if (!vals || vals.length === 0) return false;
      return vals.some(v => String(v).toLowerCase().includes(searchStr));
    });
  });

  // 2. 多字段复合排序
  if (sortStates.value.length > 0) {
    list = [...list].sort((a, b) => {
      for (const sortItem of sortStates.value) {
        const col = sortItem.col;
        const order = sortItem.order;
        const valA = getCellValuesList(a, col)[0] ?? '';
        const valB = getCellValuesList(b, col)[0] ?? '';
        if (valA !== valB) {
          if (typeof valA === 'number' && typeof valB === 'number') {
            return order === 'asc' ? valA - valB : valB - valA;
          }
          const numA = Number(valA);
          const numB = Number(valB);
          if (!isNaN(numA) && !isNaN(numB) && valA !== '' && valB !== '') {
            return order === 'asc' ? numA - numB : numB - numA;
          }
          return order === 'asc'
            ? String(valA).localeCompare(String(valB))
            : String(valB).localeCompare(String(valA));
        }
      }
      return 0;
    });
  }

  return list;
});

/**
 * 🌟 核心：Excel 嵌套关系层级展开与对齐算法 (该合并的地方合并，1:N:N 树状明细精准对齐)
 * 能够处理：
 * 1. 根主实体 (学生)：整个主项跨全部展开行 (rowspan = totalSpan)
 * 2. 1:N 一级从实体 (如 选课 student_course)：每个从表实体跨其所有下级明细行 (rowspan = childSpan)
 * 3. 1:N:N 二级孙实体 (如 考核分项 student_course_score_item)：每个孙项独立单行展示 (rowspan = 1)
 * 4. 并列 1:N 从实体 (如 荣誉奖励 student_reward)：与对应的行对齐，其余行填 '-'
 */
interface ExcelSubRow {
  rowKey: string;
  mainRecord: any;
  mainIndex: number;
  subRowIndex: number;
  span: number;
  isFirstSubRow: boolean;
  isLastSubRow: boolean;
  cells: Record<string, {
    val: any;
    isMulti: boolean;
    rowSpan: number;
    shouldRender: boolean;
  }>;
}

const excelExpandedRows = computed<ExcelSubRow[]>(() => {
  const result: ExcelSubRow[] = [];
  const records = filteredRecords.value;
  const headers = visibleHeaders.value;

  records.forEach((record, mainIdx) => {
    // 动态发现此记录下的所有 1:N 业务从表分支
    // 数据模型解构公理：
    // record -> 子模块数组 (如 record['103'], record['104']) 或直接物理从表数组
    // 数组项 item 结构：
    //   主实体数据: item.student_course 或 item.student_award (若无物理表名包裹则直接是 item 本身)
    //   二级细项列表:
    //     - 同模块从表: 如 item.student_course_score_item
    //     - 三级孙模块: 如 item['106'] (其项为 { student_award_detail: {...} } 或平铺明细)
    interface DynamicSlot {
      entityObj: any;     // 一级从表物理实体对象 (如 { id: 8, course_name: 'xxx', ... })
      subTable: string;   // 二级细项物理表名 (如 'student_course_score_item' 或 'student_award_detail')
      subItems: any[];    // 二级细项列表
      slotSpan: number;   // 当前一级从表实体占用的展开跨度 = max(1, subItems.length)
    }

    interface DynamicBranch {
      modKey: string;      // 模块标识 (如 '103', '104')
      primaryTable: string;// 一级从表物理表名 (如 'student_course', 'student_award')
      subTable?: string;   // 二级细项物理表名
      slots: DynamicSlot[];// 一级从表项槽位列表
      totalSpan: number;   // 分支总展开跨度 = sum(slotSpan)
    }

    const branches: DynamicBranch[] = [];

    // 递归扫描 record 及其各级空间下的全部 1:N 从表分支
    function scanBranches(currentObj: any, currentModKey: string) {
      if (!currentObj || typeof currentObj !== 'object' || Array.isArray(currentObj)) return;

      for (const k of Object.keys(currentObj)) {
        const val = currentObj[k];
        if (Array.isArray(val) && val.length > 0 && typeof val[0] === 'object') {
          // 捕获到一个 1:N 数组分支！
          // 分析第一项，确定其主实体物理表名与内嵌二级细项
          let primaryTable = k;
          const slots: DynamicSlot[] = [];
          let detectedSubTable = '';

          val.forEach((rawItem: any) => {
            let entityObj: any = rawItem;
            let subItems: any[] = [];
            let itemSubTable = '';

            if (rawItem && typeof rawItem === 'object') {
              // 1. 查找是否存在物理表包装（例如 rawItem.student_course、rawItem.student_award）
              for (const itemKey of Object.keys(rawItem)) {
                const inner = rawItem[itemKey];
                // 物理实体对象（排除数字键子模块和细项数组）
                if (inner && typeof inner === 'object' && !Array.isArray(inner) && isNaN(Number(itemKey))) {
                  primaryTable = itemKey;
                  entityObj = inner;
                  break;
                }
              }

              // 2. 查找是否存在二级细项数组（同模块从表或数字键三级子模块）
              for (const itemKey of Object.keys(rawItem)) {
                const inner = rawItem[itemKey];
                if (Array.isArray(inner) && inner.length > 0) {
                  // A. 同模块从表数组 (如 rawItem.student_course_score_item)
                  if (isNaN(Number(itemKey))) {
                    itemSubTable = itemKey;
                    subItems = inner;
                    break;
                  }
                  // B. 数字键三级子模块 (如 rawItem['106'])
                  if (!isNaN(Number(itemKey))) {
                    // 解包数字键子模块下的第一项看其物理表名
                    const firstSub = inner[0];
                    if (firstSub && typeof firstSub === 'object') {
                      for (const subK of Object.keys(firstSub)) {
                        if (isNaN(Number(subK)) && typeof firstSub[subK] === 'object') {
                          itemSubTable = subK;
                          break;
                        }
                      }
                      if (!itemSubTable) itemSubTable = itemKey;
                    }
                    subItems = inner;
                    break;
                  }
                }
              }
            }

            if (itemSubTable) {
              detectedSubTable = itemSubTable;
            }

            const slotSpan = Math.max(1, subItems.length);
            slots.push({
              entityObj,
              subTable: itemSubTable,
              subItems,
              slotSpan
            });
          });

          const totalSpan = slots.reduce((sum, s) => sum + s.slotSpan, 0);
          branches.push({
            modKey: currentModKey || k,
            primaryTable,
            subTable: detectedSubTable,
            slots,
            totalSpan
          });
        } else if (val && typeof val === 'object' && !Array.isArray(val)) {
          // 深入子模块命名空间
          scanBranches(val, k);
        }
      }
    }

    scanBranches(record, '');

    // 主记录占用的总展开行数 = max(1, 各 1:N 分支总跨度)
    const maxBranchSpan = branches.length > 0 ? Math.max(...branches.map(b => b.totalSpan)) : 1;
    const totalSpan = Math.max(1, maxBranchSpan);

    // 为每个 subIdx 构建切片行
    for (let subIdx = 0; subIdx < totalSpan; subIdx++) {
      const isFirst = subIdx === 0;
      const isLast = subIdx === totalSpan - 1;
      const cells: ExcelSubRow['cells'] = {};

      // 遍历所有表头字段列
      headers.forEach(col => {
        const colKey = getColumnKey(col);
        const { table, field } = col;

        // 1. 检查当前列是否属于某个 1:N 一级从表 (如 student_course, student_award)
        const matchedPrimaryBranch = branches.find(b => b.primaryTable === table);
        if (matchedPrimaryBranch) {
          let activeSlot: DynamicSlot | null = null;
          let offsetInSlot = 0;
          let acc = 0;
          for (const s of matchedPrimaryBranch.slots) {
            if (subIdx >= acc && subIdx < acc + s.slotSpan) {
              activeSlot = s;
              offsetInSlot = subIdx - acc;
              break;
            }
            acc += s.slotSpan;
          }

          if (activeSlot) {
            const rawVal = activeSlot.entityObj ? activeSlot.entityObj[field] : undefined;
            cells[colKey] = {
              val: (rawVal !== undefined && rawVal !== null && rawVal !== '') ? rawVal : '-',
              isMulti: true,
              rowSpan: activeSlot.slotSpan,
              shouldRender: offsetInSlot === 0
            };
          } else {
            // 当前 subIdx 超出该分支数据行范围，补空行保证表格边框封闭
            cells[colKey] = {
              val: '-',
              isMulti: false,
              rowSpan: 1,
              shouldRender: true
            };
          }
          return;
        }

        // 2. 检查当前列是否属于某个 1:N:N 二级细项表 (如 student_course_score_item, student_award_detail)
        const matchedSubBranch = branches.find(b => b.subTable === table);
        if (matchedSubBranch) {
          let activeSlot: DynamicSlot | null = null;
          let offsetInSlot = 0;
          let acc = 0;
          for (const s of matchedSubBranch.slots) {
            if (subIdx >= acc && subIdx < acc + s.slotSpan) {
              activeSlot = s;
              offsetInSlot = subIdx - acc;
              break;
            }
            acc += s.slotSpan;
          }

          let val: any = '-';
          if (activeSlot && offsetInSlot < activeSlot.subItems.length) {
            const subRaw = activeSlot.subItems[offsetInSlot];
            if (subRaw) {
              if (subRaw[table] && typeof subRaw[table] === 'object') {
                val = subRaw[table][field] ?? '-';
              } else if (subRaw[field] !== undefined) {
                val = subRaw[field] ?? '-';
              }
            }
          }

          cells[colKey] = {
            val: (val !== undefined && val !== null && val !== '') ? val : '-',
            isMulti: true,
            rowSpan: 1,
            shouldRender: true
          };
          return;
        }

        // 3. 根主表或 1:1 / N:1 伴生表 (如 student, clazz, student_profile)
        // 纵向跨满主记录全部展开行，仅首行渲染 (rowSpan = totalSpan, shouldRender = isFirst)
        let cellVal: any = '-';
        if (record[table] && typeof record[table] === 'object' && record[table][field] !== undefined) {
          cellVal = record[table][field];
        } else {
          const vals = getCellValuesList(record, col);
          cellVal = (vals && vals.length > 0) ? vals[0] : '-';
        }

        cells[colKey] = {
          val: (cellVal !== undefined && cellVal !== null && cellVal !== '') ? cellVal : '-',
          isMulti: false,
          rowSpan: totalSpan,
          shouldRender: isFirst
        };
      });

      // 主键 id 安全提取
      const rowMainId = record.student?.id || record.id || (record['101']?.student?.id) || mainIdx;
      result.push({
        rowKey: `${rowMainId}_sub_${subIdx}`,
        mainRecord: record,
        mainIndex: mainIdx,
        subRowIndex: subIdx,
        span: totalSpan,
        isFirstSubRow: isFirst,
        isLastSubRow: isLast,
        cells
      });
    }
  });

  return result;
});

function getStatusBadgeClass(status: string) {
  if (status === '在读' || status === '已开课') return 'badge-emerald';
  if (status === '休学' || status === '计划中') return 'badge-amber';
  if (status === '退学') return 'badge-rose';
  if (status === '毕业' || status === '已结课') return 'badge-indigo';
  return 'badge-slate';
}
</script>

<template>
  <div class="dynamic-table-container glass-card">
    <!-- Header Title Bar -->
    <div class="table-header-bar">
      <div class="table-title-area">
        <h2 class="table-title">{{ title }}</h2>
        <span class="record-counter">共 {{ filteredRecords.length }} 主项 / {{ excelExpandedRows.length }} 行明细</span>
      </div>

      <div class="table-right-hint">
        <span class="role-hint badge badge-indigo">
          <Sparkles :size="12" />
          视角: {{ roleStore.currentRole.name }}
        </span>
      </div>
    </div>

    <!-- 活跃筛选与排序状态标签栏 -->
    <div v-if="activeFilterTags.length > 0 || sortStates.length > 0" class="active-queries-bar animate-fade-in">
      <div class="active-tags-list">
        <span class="active-queries-label">当前检索条件:</span>

        <!-- 排序标签列表 (支持多字段复合排序与优先级标注) -->
        <span
          v-for="(item, idx) in sortStates"
          :key="`sort-${item.key}`"
          class="query-tag query-tag-sort"
        >
          <ArrowUp v-if="item.order === 'asc'" :size="12" />
          <ArrowDown v-else :size="12" />
          <span class="tag-text">
            排序{{ sortStates.length > 1 ? ` ${idx + 1}` : '' }}: {{ item.col.name }} ({{ item.order === 'asc' ? '升序' : '降序' }})
          </span>
          <button class="tag-close-btn" title="清除此项排序" @click="removeSortState(item.key)">
            <X :size="12" />
          </button>
        </span>

        <!-- 过滤条件标签 -->
        <span
          v-for="tag in activeFilterTags"
          :key="tag.key"
          class="query-tag query-tag-filter"
        >
          <Filter :size="12" />
          <span class="tag-text">{{ tag.name }}: {{ tag.value }}</span>
          <button class="tag-close-btn" title="清除此项筛选" @click="removeFilterTag(tag.key)">
            <X :size="12" />
          </button>
        </span>
      </div>

      <button class="clear-all-queries-btn" title="清空全部筛选与排序" @click="clearAllFilters(); clearSort();">
        <RotateCcw :size="12" />
        <span>清空重置</span>
      </button>
    </div>

    <!-- Table Grid with In-Header Icons (上下箭头排序 + 漏斗搜索) -->
    <div class="table-wrapper">
      <table class="modern-table excel-table">
        <colgroup>
          <col class="col-index" style="width: 70px; min-width: 70px;" />
          <col
            v-for="col in visibleHeaders"
            :key="`col-${getColumnKey(col)}`"
            :style="{ width: col.width ? `${col.width}px` : 'auto', minWidth: col.width ? `${col.width}px` : '110px' }"
          />
          <col v-if="hasActionColumn" class="col-actions" style="width: 120px; min-width: 120px;" />
        </colgroup>

        <thead>
          <!-- 🌟 第一层表头：一级业务根模块大标题 (101 学生综合档案、102 课程排课中心) -->
          <tr v-if="hasModuleGroupHeader" class="th-module-group-row">
            <!-- 序号列跨 maxHeaderDepth 行 -->
            <th class="th-index text-center excel-group-th" :rowspan="maxHeaderDepth">序号</th>

            <!-- 一级根模块大标题组 -->
            <th
              v-for="grp in level1HeaderRow"
              :key="grp.key"
              :colspan="grp.colSpan"
              :rowspan="grp.rowSpan"
              class="th-module-title-cell text-center"
              :class="{ 'th-nested-parent': grp.rowSpan === 1 }"
            >
              <div class="module-group-title-wrapper">
                <span class="module-group-badge">{{ grp.badge }}</span>
                <span class="module-group-text">{{ grp.title }}</span>
                <span class="module-group-count">({{ grp.count }}列)</span>
              </div>
            </th>

            <!-- 操作列跨 maxHeaderDepth 行 -->
            <th v-if="hasActionColumn" class="th-actions text-center excel-group-th" :rowspan="maxHeaderDepth">
              <div class="th-actions-header">
                <span>操作</span>
                <Settings :size="13" class="settings-icon" title="列设置与配置" />
              </div>
            </th>
          </tr>

          <!-- 🌟 第二层表头：二级业务模块行 (101 下拆分为 105核心档案、103选课与成绩、104荣誉与奖惩) -->
          <tr v-if="maxHeaderDepth >= 3 && level2HeaderRow.length > 0" class="th-level2-group-row">
            <th
              v-for="sub in level2HeaderRow"
              :key="sub.key"
              :colspan="sub.colSpan"
              :rowspan="sub.rowSpan"
              class="th-level2-title-cell text-center"
              :class="{ 'th-nested-parent': sub.rowSpan === 1 }"
            >
              <div class="level2-title-wrapper">
                <span v-if="sub.badge" class="level2-badge">{{ sub.badge }}</span>
                <span class="level2-text">{{ sub.title }}</span>
                <span class="level2-count">({{ sub.count }}列)</span>
              </div>
            </th>
          </tr>

          <!-- 🌟 第三层表头：三级子模块行 (104 下拆分为 106佐证明细、104直属荣誉项；103 下拆分为成绩明细等) -->
          <tr v-if="maxHeaderDepth === 4 && level3HeaderRow.length > 0" class="th-submodule-group-row">
            <th
              v-for="sub in level3HeaderRow"
              :key="sub.key"
              :colspan="sub.colSpan"
              :rowspan="sub.rowSpan"
              class="th-submodule-title-cell text-center"
              :class="{ 'is-sub-module': sub.isSubModule, 'is-direct-group': sub.isDirectGroup }"
            >
              <div class="submodule-title-wrapper">
                <span v-if="sub.isSubModule" class="submodule-arrow">↳</span>
                <span v-if="sub.badge" class="submodule-badge">{{ sub.badge }}</span>
                <span class="submodule-text">{{ sub.title }}</span>
                <span class="submodule-count">({{ sub.count }}列)</span>
              </div>
            </th>
          </tr>

          <!-- 第三层（或单层）表头：具体字段列 -->
          <tr class="th-header-row">
            <!-- 无模块大标题时单独渲染序号列 -->
            <th v-if="!hasModuleGroupHeader" class="th-index text-center">序号</th>

            <th
              v-for="col in visibleHeaders"
              :key="getColumnKey(col)"
              class="th-column-cell"
            >
              <div class="th-content-wrapper">
                <span class="th-label-text">{{ col.name }}</span>

                <!-- 右侧图标组：排序箭头 + 漏斗搜索 -->
                <div v-if="col.sortable || col.searchType" class="th-icons-group">
                  <!-- 1. 排序上下箭头 (支持多列复合排序) -->
                  <button
                    v-if="col.sortable"
                    class="th-icon-btn th-sort-btn"
                    :class="{ active: getSortOrder(getColumnKey(col)) !== null }"
                    :title="`点击切换排序${getSortPriority(getColumnKey(col)) ? ` (当前排序优先级: ${getSortPriority(getColumnKey(col))})` : ''}`"
                    @click="toggleSort(col, $event)"
                  >
                    <ArrowUp
                      v-if="getSortOrder(getColumnKey(col)) === 'asc'"
                      :size="13"
                      class="sort-active-icon"
                    />
                    <ArrowDown
                      v-else-if="getSortOrder(getColumnKey(col)) === 'desc'"
                      :size="13"
                      class="sort-active-icon"
                    />
                    <ArrowUpDown
                      v-else
                      :size="13"
                    />
                    <span
                      v-if="getSortPriority(getColumnKey(col)) !== null"
                      class="sort-priority-badge"
                    >
                      {{ getSortPriority(getColumnKey(col)) }}
                    </span>
                  </button>

                  <!-- 2. 漏斗搜索图标 -->
                  <button
                    v-if="col.searchType"
                    class="th-icon-btn th-filter-btn"
                    :class="{ active: Boolean(columnFilters[getColumnKey(col)]), open: activePopoverKey === getColumnKey(col) }"
                    title="点击打开列筛选"
                    @click="toggleFilterPopover(col, $event)"
                  >
                    <Filter :size="13" />
                  </button>

                  <!-- 漏斗下拉弹窗 Popover -->
                  <div
                    v-if="activePopoverKey === getColumnKey(col)"
                    class="th-filter-popover animate-fade-in"
                    @click.stop
                  >
                    <div class="popover-header">
                      <span class="popover-title">筛选: {{ col.name }}</span>
                      <button class="popover-close-btn" @click="activePopoverKey = null">
                        <X :size="12" />
                      </button>
                    </div>

                    <div class="popover-body">
                      <!-- 搜索输入框 -->
                      <input
                        v-model="tempFilterValue"
                        type="text"
                        :placeholder="`输入${col.name}过滤或从下方选择...`"
                        class="popover-input"
                        autofocus
                        @keyup.enter="applyFilter(getColumnKey(col))"
                      />

                      <!-- 智能下拉候选列表 -->
                      <div class="popover-options-wrapper">
                        <div class="options-header-tip">
                          <span>快捷选择</span>
                          <span v-if="popoverLoading" class="loading-tag">加载中...</span>
                          <span v-else-if="popoverOptions.length > 0" class="options-count">{{ filteredPopoverOptions.length }}/{{ popoverOptions.length }} 项</span>
                        </div>
                        <div v-if="popoverLoading" class="popover-loading-box">
                          <span class="popover-spinner"></span>
                          <span>加载中...</span>
                        </div>
                        <div v-else-if="filteredPopoverOptions.length > 0" class="popover-options-list">
                          <button
                            v-for="(opt, oIdx) in filteredPopoverOptions"
                            :key="oIdx"
                            type="button"
                            class="popover-option-item"
                            :class="{ active: String(tempFilterValue).trim() === String(opt.value) }"
                            :title="String(opt.label)"
                            @click="selectOption(getColumnKey(col), opt.value)"
                          >
                            <span class="option-text">{{ opt.label }}</span>
                            <Check v-if="String(tempFilterValue).trim() === String(opt.value)" :size="11" class="check-icon" />
                          </button>
                        </div>
                        <div v-else class="popover-empty-tip">
                          无匹配候选项
                        </div>
                      </div>
                    </div>

                    <div class="popover-footer">
                      <button
                        class="popover-btn btn-reset"
                        @click="resetFilter(getColumnKey(col))"
                      >
                        <RotateCcw :size="11" />
                        <span>重置</span>
                      </button>
                      <button
                        class="popover-btn btn-confirm"
                        @click="applyFilter(getColumnKey(col))"
                      >
                        <Check :size="12" />
                        <span>确定</span>
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </th>

            <!-- 操作列（带设置齿轮图标），无大标题且有权限时单独渲染；有大标题时已在第一行通过 rowspan=2 渲染 -->
            <th v-if="hasActionColumn && !hasModuleGroupHeader" class="th-actions text-center">
              <div class="th-actions-header">
                <span>操作</span>
                <Settings :size="13" class="settings-icon" title="列设置与配置" />
              </div>
            </th>
          </tr>
        </thead>

        <tbody>
          <tr v-if="loading" class="loading-row">
            <td :colspan="visibleHeaders.length + (hasActionColumn ? 2 : 1)" class="text-center">
              <div class="loading-spinner"></div>
              <span>动态数据加载中...</span>
            </td>
          </tr>

          <tr v-else-if="excelExpandedRows.length === 0" class="empty-row">
            <td :colspan="visibleHeaders.length + (hasActionColumn ? 2 : 1)" class="text-center">
              <span class="empty-text">暂无符合条件的记录</span>
            </td>
          </tr>

          <!-- 🌟 Excel 明细渲染：主表列 rowspan 智能合并，1:N 从表不合并逐行展示 -->
          <tr
            v-for="row in excelExpandedRows"
            :key="row.rowKey"
            class="data-row excel-row"
            :class="{
              'excel-row-start': row.isFirstSubRow,
              'excel-row-end': row.isLastSubRow,
              'excel-group-alt': row.mainIndex % 2 === 1
            }"
          >
            <!-- 序号列：第一行合并展示主序号 (rowspan=row.span) -->
            <td
              v-if="row.isFirstSubRow"
              :rowspan="row.span"
              class="td-index text-center excel-merged-cell"
            >
              {{ row.mainIndex + 1 }}
            </td>

            <!-- 数据列：根据 shouldRender 和 rowSpan 精准输出 -->
            <template v-for="col in visibleHeaders" :key="getColumnKey(col)">
              <td
                v-if="row.cells[getColumnKey(col)]?.shouldRender"
                :rowspan="row.cells[getColumnKey(col)].rowSpan"
                class="td-column-cell"
                :class="{
                  'excel-merged-cell': row.cells[getColumnKey(col)].rowSpan > 1,
                  'excel-sub-cell': row.cells[getColumnKey(col)].isMulti
                }"
              >
                <div class="td-cell-wrapper">
                  <!-- 状态标签特殊渲染 -->
                  <span
                    v-if="col.field === 'status'"
                    class="badge status-badge"
                    :class="getStatusBadgeClass(row.cells[getColumnKey(col)].val)"
                  >
                    {{ row.cells[getColumnKey(col)].val }}
                  </span>

                  <!-- 普通文本渲染 -->
                  <span v-else class="cell-text">
                    {{ row.cells[getColumnKey(col)].val }}
                  </span>
                </div>
              </td>
            </template>

            <!-- 操作列：第一行合并展示对应主记录的操作按钮 (rowspan=row.span) -->
            <td
              v-if="hasActionColumn && row.isFirstSubRow"
              :rowspan="row.span"
              class="td-actions text-center excel-merged-cell"
            >
              <button
                v-if="hasEditPermission"
                class="action-btn btn-edit"
                title="编辑维护"
                @click="emit('view-detail', row.mainRecord)"
              >
                <Edit3 :size="13" />
                <span>编辑</span>
              </button>

              <button
                v-else-if="hasViewPermission"
                class="action-btn btn-view"
                title="查看详情"
                @click="emit('view-detail', row.mainRecord)"
              >
                <Eye :size="13" />
                <span>查看</span>
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.dynamic-table-container {
  background: var(--bg-surface);
  border-radius: var(--radius-lg);
  overflow: visible;
  border: 1px solid var(--border-subtle);
}

.table-header-bar {
  padding: 16px 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--border-subtle);
  background: var(--bg-subtle);
}

.table-title-area {
  display: flex;
  align-items: center;
  gap: 12px;
}

.table-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-main);
}

.record-counter {
  font-size: 12px;
  color: var(--text-muted);
}

.table-right-hint {
  display: flex;
  align-items: center;
}

.role-hint {
  font-size: 11px;
}

/* Active Queries & Filter Tags Bar */
.active-queries-bar {
  padding: 10px 20px;
  background: #f1f5f9;
  border-bottom: 1px solid var(--border-subtle);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.active-tags-list {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.active-queries-label {
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
  margin-right: 4px;
}

.query-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
  transition: var(--transition);
}

.query-tag-filter {
  background: #e0e7ff;
  color: #3730a3;
  border: 1px solid #c7d2fe;
}

.query-tag-sort {
  background: #ecfdf5;
  color: #065f46;
  border: 1px solid #a7f3d0;
}

.tag-text {
  line-height: 1;
}

.tag-close-btn {
  background: transparent;
  border: none;
  color: currentColor;
  opacity: 0.6;
  cursor: pointer;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: var(--transition);
}

.tag-close-btn:hover {
  opacity: 1;
  background: rgba(0, 0, 0, 0.1);
}

.clear-all-queries-btn {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  color: #64748b;
  font-size: 12px;
  font-weight: 500;
  padding: 4px 10px;
  border-radius: var(--radius-sm);
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  transition: var(--transition);
}

.clear-all-queries-btn:hover {
  background: #fee2e2;
  color: #b91c1c;
  border-color: #fca5a5;
}

/* Table Layout */
.table-wrapper {
  overflow-x: auto;
  width: 100%;
  border: 1px solid #cbd5e1;
  border-radius: var(--radius-md);
  background: #ffffff;
  box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.05);
}

.modern-table {
  width: 100%;
  border-collapse: collapse;
  border-spacing: 0;
  text-align: left;
  table-layout: auto;
}

.th-header-row th {
  background: #f8fafc;
  color: #64748b;
  font-size: 13px;
  font-weight: 600;
  padding: 13px 16px;
  border-bottom: 1px solid var(--border-subtle);
  white-space: nowrap;
  user-select: none;
  position: relative;
  text-align: left;
  vertical-align: middle;
  box-sizing: border-box;
}

.th-content-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 24px;
}

.th-label-text {
  color: #334155;
  font-weight: 600;
  white-space: nowrap;
}

.th-icons-group {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  position: relative;
  margin-left: auto;
}

.th-icon-btn {
  background: transparent;
  border: none;
  color: #94a3b8;
  cursor: pointer;
  padding: 3px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: var(--transition);
}

.th-icon-btn:hover {
  color: var(--primary-600);
  background: #e2e8f0;
}

.th-icon-btn.active {
  color: #2563eb;
  background: #dbeafe;
}

.th-icon-btn.open {
  color: #2563eb;
  background: #dbeafe;
}

.sort-active-icon {
  color: #2563eb;
}

.sort-priority-badge {
  font-size: 10px;
  font-weight: 700;
  line-height: 1;
  color: #2563eb;
  margin-left: 2px;
}

.th-actions-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 24px;
}

.settings-icon {
  color: #94a3b8;
  cursor: pointer;
}

.settings-icon:hover {
  color: #334155;
}

/* Filter Popover Floating Panel */
.th-filter-popover {
  position: absolute;
  top: 100%;
  right: 0;
  margin-top: 8px;
  width: 220px;
  background: #ffffff;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.12), 0 8px 10px -6px rgba(0, 0, 0, 0.08);
  z-index: 100;
  padding: 10px;
  font-weight: normal;
}

.popover-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  padding-bottom: 6px;
  border-bottom: 1px solid var(--border-subtle);
}

.popover-title {
  font-size: 11px;
  font-weight: 600;
  color: #475569;
}

.popover-close-btn {
  background: transparent;
  border: none;
  color: #94a3b8;
  cursor: pointer;
  padding: 1px;
}

.popover-body {
  margin-bottom: 10px;
}

.popover-input, .popover-select {
  width: 100%;
  padding: 6px 8px;
  font-size: 12px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  outline: none;
  background: #f8fafc;
  box-sizing: border-box;
}

.popover-input:focus, .popover-select:focus {
  border-color: #2563eb;
  background: #ffffff;
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.15);
}

.popover-options-wrapper {
  margin-top: 8px;
  border-top: 1px dashed #e2e8f0;
  padding-top: 6px;
}

.options-header-tip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 10px;
  color: #64748b;
  margin-bottom: 4px;
}

.options-count {
  color: #94a3b8;
  font-family: monospace;
}

.loading-tag {
  color: #3b82f6;
}

.popover-loading-box, .popover-empty-tip {
  padding: 10px 4px;
  text-align: center;
  font-size: 11px;
  color: #94a3b8;
}

.popover-spinner {
  width: 12px;
  height: 12px;
  border: 2px solid #cbd5e1;
  border-top-color: #2563eb;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  display: inline-block;
  vertical-align: middle;
  margin-right: 4px;
}

.popover-options-list {
  max-height: 140px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding-right: 2px;
}

.popover-option-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 4px 6px;
  font-size: 11px;
  color: #334155;
  background: #f8fafc;
  border: 1px solid transparent;
  border-radius: 4px;
  text-align: left;
  cursor: pointer;
  transition: all 0.15s ease;
}

.popover-option-item:hover {
  background: #eff6ff;
  color: #1d4ed8;
  border-color: #bfdbfe;
}

.popover-option-item.active {
  background: #dbeafe;
  color: #1d4ed8;
  font-weight: 600;
  border-color: #93c5fd;
}

.popover-option-item .option-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.popover-option-item .check-icon {
  color: #2563eb;
  flex-shrink: 0;
}

.popover-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.popover-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 4px 8px;
  font-size: 11px;
  font-weight: 500;
  border-radius: var(--radius-sm);
  cursor: pointer;
  border: 1px solid transparent;
  transition: var(--transition);
}

.btn-reset {
  background: #f1f5f9;
  color: #64748b;
}

.btn-reset:hover {
  background: #e2e8f0;
}

.btn-confirm {
  background: #2563eb;
  color: #ffffff;
}

.btn-confirm:hover {
  background: #1d4ed8;
}

/* Data Rows */
.modern-table td {
  padding: 10px 14px;
  font-size: 13px;
  color: var(--text-main);
  border-bottom: 1px solid #e2e8f0;
  border-right: 1px solid #f1f5f9;
  transition: var(--transition);
  vertical-align: middle;
  box-sizing: border-box;
  text-align: left;
}

/* Excel 风格专业网格 */
.excel-table {
  border-collapse: separate;
  border-spacing: 0;
  width: 100%;
  border: none;
}

.excel-table th {
  border-right: 1px solid #cbd5e1;
  border-bottom: 1px solid #cbd5e1;
  background: #f8fafc;
  box-sizing: border-box;
}

/* 🌟 第一层：一级模块层级分组大标题样式 */
.th-module-group-row th {
  background: #f1f5f9;
  border-bottom: 1px solid #cbd5e1;
  padding: 8px 12px;
}

.excel-group-th {
  background: #f1f5f9 !important;
  vertical-align: middle !important;
  font-weight: 600;
  color: #334155;
  border-bottom: 2px solid #94a3b8 !important;
}

.th-module-title-cell {
  background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
  border-bottom: 1px solid #cbd5e1 !important;
  border-right: 2px solid #94a3b8 !important; /* 根模块间加深垂直分割线 */
}

.th-nested-parent {
  background: linear-gradient(180deg, #f1f5f9 0%, #e2e8f0 100%) !important;
  border-bottom: 1px solid #cbd5e1 !important;
}

.module-group-title-wrapper {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
  letter-spacing: 0.01em;
}

.module-group-badge {
  display: inline-block;
  padding: 1px 6px;
  font-size: 10px;
  font-weight: 700;
  font-family: monospace;
  color: #1e40af;
  background: #dbeafe;
  border-radius: 4px;
}

.module-group-text {
  color: #0f172a;
}

.module-group-count {
  font-size: 11px;
  color: #64748b;
  font-weight: normal;
}

/* 🌟 第二层：二级业务模块 (105 核心档案、103 选课与成绩、104 荣誉与奖惩) */
.th-level2-group-row th {
  padding: 7px 12px;
  background: #f8fafc;
  border-bottom: 1px solid #cbd5e1;
  border-right: 1px solid #cbd5e1;
}

.th-level2-title-cell {
  background: #f8fafc !important;
  border-right: 1px solid #cbd5e1 !important;
}

.level2-title-wrapper {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: #1e293b;
}

.level2-badge {
  display: inline-block;
  padding: 1px 5px;
  font-size: 9px;
  font-weight: 700;
  font-family: monospace;
  color: #4338ca;
  background: #e0e7ff;
  border-radius: 4px;
}

.level2-text {
  color: #334155;
}

.level2-count {
  font-size: 11px;
  color: #64748b;
  font-weight: normal;
}

/* 🌟 第三层：三级子模块/明细组 (106 佐证明细等) */
.th-submodule-group-row th {
  padding: 6px 10px;
  border-bottom: 1px solid #cbd5e1;
  border-right: 1px solid #cbd5e1;
}

.th-submodule-title-cell.is-sub-module {
  background: #f0fdf4 !important; /* 浅绿底色，突显佐证明细从属子模块 */
  border-right: 2px solid #94a3b8 !important;
}

.th-submodule-title-cell.is-direct-group {
  background: #fafaf9 !important; /* 浅柔暖灰，直属字段组 */
  border-right: 1px dashed #cbd5e1 !important;
}

.submodule-title-wrapper {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  font-size: 12px;
  font-weight: 600;
  color: #334155;
}

.submodule-arrow {
  color: #16a34a;
  font-weight: 700;
  font-size: 13px;
}

.submodule-badge {
  display: inline-block;
  padding: 1px 5px;
  font-size: 9px;
  font-weight: 700;
  font-family: monospace;
  color: #15803d;
  background: #dcfce7;
  border-radius: 4px;
}

.submodule-text {
  color: #1e293b;
}

.submodule-count {
  font-size: 10px;
  color: #64748b;
  font-weight: normal;
}

/* 第三层：具体字段列底层加深底线，隔开表格内容 */
.th-header-row th {
  border-bottom: 2px solid #94a3b8 !important;
  background: #f8fafc;
}

.excel-table td {
  border-right: 1px solid #e2e8f0;
  border-bottom: 1px solid #e2e8f0;
  box-sizing: border-box;
}

/* 合并单元格突出展示（主表、序号、操作），加固底部隔离线，彻底防止尾部连线丢失 */
.excel-merged-cell {
  background: #ffffff;
  font-weight: 500;
  vertical-align: middle !important;
  border-bottom: 2px solid #cbd5e1 !important;
}

/* 1:N 细项子单元格背景微调 */
.excel-sub-cell {
  background: #fcfdfe;
}

/* 组交替背景色（不同学生/主记录呈现精致区分） */
.excel-group-alt .excel-merged-cell {
  background: #f8fafc;
}

.excel-group-alt .excel-sub-cell {
  background: #f8fafc;
}

/* 组边界线加深，形成类似 Excel 的记录块隔离感 */
.excel-row-end td {
  border-bottom: 2px solid #cbd5e1 !important;
}

.excel-row:hover td {
  background: #f1f5f9;
}

/* 右侧与底部封闭由 .table-wrapper 的 border 承担，单元格不清除边框，防止滚动时连线丢失 */

.td-cell-wrapper {
  display: flex;
  align-items: center;
  min-height: 24px;
}

.th-index, .td-index {
  width: 70px;
  text-align: center !important;
  color: #475569;
  font-size: 12px;
  font-family: monospace;
  font-weight: 600;
}

.th-actions, .td-actions {
  width: 120px;
  text-align: center !important;
}

.cell-text {
  font-variant-numeric: tabular-nums;
  line-height: 1.5;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

/* Actions Column */
.action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 4px 10px;
  font-size: 12px;
  font-weight: 500;
  border-radius: var(--radius-sm);
  border: 1px solid transparent;
  cursor: pointer;
  margin: 0 auto;
  transition: var(--transition);
}

.btn-view {
  background: var(--primary-50);
  color: var(--primary-600);
  border-color: var(--primary-100);
}

.btn-view:hover {
  background: var(--primary-100);
}

.btn-edit {
  background: #ecfdf5;
  color: #059669;
  border-color: #a7f3d0;
}

.btn-edit:hover {
  background: #d1fae5;
}

.loading-row td, .empty-row td {
  padding: 40px;
}

.loading-spinner {
  width: 20px;
  height: 20px;
  border: 2px solid var(--border-subtle);
  border-top-color: var(--primary-600);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  display: inline-block;
  vertical-align: middle;
  margin-right: 8px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.empty-text {
  color: var(--text-dim);
}
</style>
