<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { useRoleStore } from '../stores/roleStore';
import type { HeaderMeta } from '../api/engineApi';
import { Eye, Edit3, ArrowUpDown, ArrowUp, ArrowDown, Filter, Settings, Sparkles, Check, RotateCcw, X } from 'lucide-vue-next';

const props = defineProps<{
  title: string;
  headers: HeaderMeta[];
  records: any[];
  loading?: boolean;
}>();

const emit = defineEmits<{
  (e: 'view-detail', record: any): void;
}>();

const roleStore = useRoleStore();

// 存储各个列在表头内配置的筛选值 (key 为 `${col.table}.${col.field}`)
const columnFilters = ref<Record<string, string>>({});

// 当前打开的表头筛选 Popover (key 为 `${col.table}.${col.field}`，为空表示关闭)
const activePopoverKey = ref<string | null>(null);

// 暂存当前正在编辑的筛选值（点击确定后生效）
const tempFilterValue = ref<string>('');

// 当前排序列与方向
const sortKey = ref<string>('');
const sortOrder = ref<'asc' | 'desc' | null>(null);

// 根据当前角色权限过滤出可见的表头列 (view 权限判定)
const visibleHeaders = computed(() => {
  if (!props.headers) return [];
  return props.headers.filter(h => roleStore.canView(h.table, h.field));
});

// 是否存在任意字段配置了可编辑权限
const hasEditPermission = computed(() => {
  // 1. 优先根据当前上下文动态权限字典判定
  const dynamicList = Object.values(roleStore.dynamicPermissions);
  if (dynamicList.length > 0) {
    return dynamicList.some(p => p.edit === 1);
  }
  // 2. 备用：检查表头中的列是否有编辑权限
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

function getCellValue(record: any, table: string, field: string) {
  const tableData = record[table];
  if (!tableData) return '-';
  const val = tableData[field];
  return val !== undefined && val !== null ? val : '-';
}

// 切换排序：asc -> desc -> 恢复不排序
function toggleSort(col: HeaderMeta, e: Event) {
  e.stopPropagation();
  if (!col.sortable) return;
  const key = `${col.table}.${col.field}`;
  if (sortKey.value !== key) {
    sortKey.value = key;
    sortOrder.value = 'asc';
  } else if (sortOrder.value === 'asc') {
    sortOrder.value = 'desc';
  } else {
    sortKey.value = '';
    sortOrder.value = null;
  }
}

// 切换打开/关闭漏斗筛选浮层
function toggleFilterPopover(col: HeaderMeta, e: Event) {
  e.stopPropagation();
  const key = `${col.table}.${col.field}`;
  if (activePopoverKey.value === key) {
    activePopoverKey.value = null;
  } else {
    activePopoverKey.value = key;
    tempFilterValue.value = columnFilters.value[key] || '';
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
}

// 重置当前列筛选
function resetFilter(key: string) {
  tempFilterValue.value = '';
  delete columnFilters.value[key];
  activePopoverKey.value = null;
}

// 获取某一列在当前数据集中的所有唯一值（用于 select 下拉筛选）
function getUniqueOptions(table: string, field: string): string[] {
  const values = new Set<string>();
  props.records.forEach(r => {
    const val = getCellValue(r, table, field);
    if (val && val !== '-') values.add(String(val));
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
    const [table, field] = key.split('.');
    const searchStr = filterVal.trim().toLowerCase();

    list = list.filter(r => {
      const cellVal = String(getCellValue(r, table, field)).toLowerCase();
      return cellVal.includes(searchStr);
    });
  });

  // 2. 排序
  if (sortKey.value && sortOrder.value) {
    const [table, field] = sortKey.value.split('.');
    list = [...list].sort((a, b) => {
      const valA = getCellValue(a, table, field);
      const valB = getCellValue(b, table, field);
      if (typeof valA === 'number' && typeof valB === 'number') {
        return sortOrder.value === 'asc' ? valA - valB : valB - valA;
      }
      return sortOrder.value === 'asc'
        ? String(valA).localeCompare(String(valB))
        : String(valB).localeCompare(String(valA));
    });
  }

  return list;
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
        <span class="record-counter">共 {{ filteredRecords.length }} / {{ records.length }} 条数据</span>
      </div>

      <div class="table-right-hint">
        <span class="role-hint badge badge-indigo">
          <Sparkles :size="12" />
          视角: {{ roleStore.currentRole.name }}
        </span>
      </div>
    </div>

    <!-- Table Grid with In-Header Icons (上下箭头排序 + 漏斗搜索) -->
    <div class="table-wrapper">
      <table class="modern-table">
        <colgroup>
          <col class="col-index" style="width: 70px; min-width: 70px;" />
          <col
            v-for="col in visibleHeaders"
            :key="`col-${col.table}.${col.field}`"
            :style="{ width: col.width ? `${col.width}px` : 'auto', minWidth: col.width ? `${col.width}px` : '110px' }"
          />
          <col v-if="hasActionColumn" class="col-actions" style="width: 120px; min-width: 120px;" />
        </colgroup>

        <thead>
          <tr class="th-header-row">
            <th class="th-index text-center">序号</th>

            <th
              v-for="col in visibleHeaders"
              :key="`${col.table}.${col.field}`"
              class="th-column-cell"
            >
              <div class="th-content-wrapper">
                <span class="th-label-text">{{ col.name }}</span>

                <!-- 右侧图标组：排序箭头 + 漏斗搜索 -->
                <div v-if="col.sortable || col.searchType" class="th-icons-group">
                  <!-- 1. 排序上下箭头 -->
                  <button
                    v-if="col.sortable"
                    class="th-icon-btn th-sort-btn"
                    :class="{ active: sortKey === `${col.table}.${col.field}` }"
                    title="点击切换排序 (升序/降序/默认)"
                    @click="toggleSort(col, $event)"
                  >
                    <ArrowUp
                      v-if="sortKey === `${col.table}.${col.field}` && sortOrder === 'asc'"
                      :size="13"
                      class="sort-active-icon"
                    />
                    <ArrowDown
                      v-else-if="sortKey === `${col.table}.${col.field}` && sortOrder === 'desc'"
                      :size="13"
                      class="sort-active-icon"
                    />
                    <ArrowUpDown
                      v-else
                      :size="13"
                    />
                  </button>

                  <!-- 2. 漏斗搜索图标 -->
                  <button
                    v-if="col.searchType"
                    class="th-icon-btn th-filter-btn"
                    :class="{ active: Boolean(columnFilters[`${col.table}.${col.field}`]), open: activePopoverKey === `${col.table}.${col.field}` }"
                    title="点击打开列筛选"
                    @click="toggleFilterPopover(col, $event)"
                  >
                    <Filter :size="13" />
                  </button>

                  <!-- 漏斗下拉弹窗 Popover -->
                  <div
                    v-if="activePopoverKey === `${col.table}.${col.field}`"
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
                      <!-- 下拉枚举筛选 -->
                      <select
                        v-if="col.searchType === 'select'"
                        v-model="tempFilterValue"
                        class="popover-select"
                      >
                        <option value="">全部</option>
                        <option
                          v-for="opt in getUniqueOptions(col.table, col.field)"
                          :key="opt"
                          :value="opt"
                        >
                          {{ opt }}
                        </option>
                      </select>

                      <!-- 文本模糊输入筛选 -->
                      <input
                        v-else
                        v-model="tempFilterValue"
                        type="text"
                        :placeholder="`请输入${col.name}...`"
                        class="popover-input"
                        autofocus
                        @keyup.enter="applyFilter(`${col.table}.${col.field}`)"
                      />
                    </div>

                    <div class="popover-footer">
                      <button
                        class="popover-btn btn-reset"
                        @click="resetFilter(`${col.table}.${col.field}`)"
                      >
                        <RotateCcw :size="11" />
                        <span>重置</span>
                      </button>
                      <button
                        class="popover-btn btn-confirm"
                        @click="applyFilter(`${col.table}.${col.field}`)"
                      >
                        <Check :size="12" />
                        <span>确定</span>
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </th>

            <!-- 操作列（带设置齿轮图标），无权限时不渲染 -->
            <th v-if="hasActionColumn" class="th-actions text-center">
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

          <tr v-else-if="filteredRecords.length === 0" class="empty-row">
            <td :colspan="visibleHeaders.length + (hasActionColumn ? 2 : 1)" class="text-center">
              <span class="empty-text">暂无符合条件的记录</span>
            </td>
          </tr>

          <tr
            v-for="(row, idx) in filteredRecords"
            :key="row.student?.id || row.course?.id || idx"
            class="data-row"
          >
            <td class="td-index text-center">{{ idx + 1 }}</td>

            <td
              v-for="col in visibleHeaders"
              :key="`${col.table}.${col.field}`"
              class="td-column-cell"
            >
              <div class="td-cell-wrapper">
                <!-- 状态标签特殊渲染 -->
                <span
                  v-if="col.field === 'status'"
                  class="badge status-badge"
                  :class="getStatusBadgeClass(getCellValue(row, col.table, col.field))"
                >
                  {{ getCellValue(row, col.table, col.field) }}
                </span>

                <!-- 普通文本渲染 -->
                <span v-else class="cell-text">
                  {{ getCellValue(row, col.table, col.field) }}
                </span>
              </div>
            </td>

            <!-- 操作列按钮：只要有可编辑权限显示编辑，只有只读权限显示查看，无权限不展示操作区域 -->
            <td v-if="hasActionColumn" class="td-actions text-center">
              <!-- 有可编辑权限：显示编辑按钮 -->
              <button
                v-if="hasEditPermission"
                class="action-btn btn-edit"
                title="编辑维护"
                @click="emit('view-detail', row)"
              >
                <Edit3 :size="13" />
                <span>编辑</span>
              </button>

              <!-- 仅有只读权限：显示查看按钮 -->
              <button
                v-else-if="hasViewPermission"
                class="action-btn btn-view"
                title="查看详情"
                @click="emit('view-detail', row)"
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

/* Table Layout */
.table-wrapper {
  overflow-x: auto;
  width: 100%;
}

.modern-table {
  width: 100%;
  border-collapse: collapse;
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
  width: 200px;
  background: #ffffff;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1);
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
}

.popover-input:focus, .popover-select:focus {
  border-color: #2563eb;
  background: #ffffff;
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.15);
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
  padding: 13px 16px;
  font-size: 13px;
  color: var(--text-main);
  border-bottom: 1px solid var(--border-subtle);
  transition: var(--transition);
  vertical-align: middle;
  box-sizing: border-box;
  text-align: left;
}

.td-cell-wrapper {
  display: flex;
  align-items: center;
  min-height: 24px;
}

.data-row:hover td {
  background: rgba(99, 102, 241, 0.04);
}

.th-index, .td-index {
  width: 70px;
  text-align: center !important;
  color: var(--text-dim);
  font-size: 12px;
  font-family: monospace;
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
