<script setup lang="ts">
import { ref, onMounted, watch } from 'vue';
import { useRoleStore } from '../stores/roleStore';
import {
  engineApi,
  type HeaderMeta,
  type DynamicFilterItem,
  type DynamicSortItem
} from '../api/engineApi';
import DynamicTable from '../components/DynamicTable.vue';
import DynamicDetailDrawer from '../components/DynamicDetailDrawer.vue';
import { GraduationCap, ShieldAlert, Sparkles } from 'lucide-vue-next';

const roleStore = useRoleStore();

const loading = ref(false);
const headers = ref<HeaderMeta[]>([]);
const moduleMeta = ref<any>(null);
const records = ref<any[]>([]);
const activeRecord = ref<any>(null);
const isDrawerVisible = ref(false);
const currentFilters = ref<DynamicFilterItem[]>([]);
const currentSorts = ref<DynamicSortItem[]>([]);

async function loadHeaders() {
  try {
    const res = await engineApi.getHeader({ moduleId: 101 });
    if (res?.fields) {
      headers.value = res.fields.map(f => ({
        fieldId: f.id || f.fieldId,
        table: f.tableName,
        field: f.columnName,
        name: f.displayName,
        sortOrder: f.sortOrder
      }));
    }
  } catch (err) {
    console.error('Failed to load headers', err);
  }
}

async function loadData(filters: DynamicFilterItem[] = currentFilters.value, sorts: DynamicSortItem[] = currentSorts.value) {
  loading.value = true;
  currentFilters.value = filters;
  currentSorts.value = sorts;
  try {
    const res = await engineApi.query({
      moduleId: 101,
      filters: currentFilters.value,
      sorts: currentSorts.value
    });
    records.value = res?.records || [];
  } finally {
    loading.value = false;
  }
}

function handleQueryChange(payload: { filters: DynamicFilterItem[]; sorts: DynamicSortItem[] }) {
  loadData(payload.filters, payload.sorts);
}

// 监听角色切换，即时刷新元数据与数据
watch(
  () => roleStore.currentRoleId,
  async () => {
    await loadHeaders();
    await loadData();
  }
);

onMounted(async () => {
  await loadHeaders();
  await loadData();
});

function handleViewDetail(row: any) {
  activeRecord.value = row;
  isDrawerVisible.value = true;
}
</script>

<template>
  <div class="module-view animate-fade-in">
    <!-- Top Feature Banner -->
    <div class="feature-banner glass-card">
      <div class="banner-left">
        <div class="banner-icon-box">
          <GraduationCap :size="28" />
        </div>
        <div>
          <div class="banner-title-row">
            <h2 class="banner-title">学生综合全景档案</h2>
            <span class="badge badge-indigo">MOD-SCHOOL-STUDENT (101)</span>
          </div>
          <p class="banner-desc">
            主表 <code>student</code> 联动 1:1 伴生档案 <code>student_profile</code>、N:1 班级 <code>clazz</code> 及 1:N 选课 <code>student_course</code> 与荣誉 <code>student_award</code>。
          </p>
        </div>
      </div>

      <!-- Live Role Status Indicator -->
      <div class="role-status-card">
        <div class="status-top">
          <Sparkles :size="15" class="sparkle-icon" />
          <span class="status-heading">权限引擎实时生效中</span>
        </div>
        <div class="status-content">
          <span class="current-role-badge badge" :class="roleStore.currentRoleId === 1 ? 'badge-indigo' : (roleStore.currentRoleId === 2 ? 'badge-emerald' : 'badge-amber')">
            {{ roleStore.currentRole.avatar }} {{ roleStore.currentRole.name }}
          </span>
          <p class="status-summary">
            <template v-if="roleStore.currentRoleId === 1">
              ✅ 全字段明文可见，拥有最高申请、查看与编辑权限。
            </template>
            <template v-else-if="roleStore.currentRoleId === 2">
              🛡️ 身份证/电话自动脱敏隐藏；学生档案只读；开放选课成绩打分。
            </template>
            <template v-else>
              🔒 仅开放个人数据浏览，写权限全部拦截关闭。
            </template>
          </p>
        </div>
      </div>
    </div>

    <!-- Dynamic Table -->
    <DynamicTable
      title="学生名册列表 (viewMode: LIST)"
      :headers="headers"
      :meta="moduleMeta"
      :records="records"
      :loading="loading"
      @view-detail="handleViewDetail"
      @query-change="handleQueryChange"
      @search="loadData()"
    />

    <!-- Detail Drawer -->
    <DynamicDetailDrawer
      v-model:visible="isDrawerVisible"
      :record="activeRecord"
      @saved="loadData"
    />
  </div>
</template>

<style scoped>
.module-view {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.feature-banner {
  padding: 20px 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.9), rgba(241, 245, 249, 0.7));
}

.banner-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.banner-icon-box {
  width: 52px;
  height: 52px;
  border-radius: var(--radius-lg);
  background: linear-gradient(135deg, var(--primary-500), var(--primary-700));
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  box-shadow: 0 4px 14px var(--primary-glow);
}

.banner-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 4px;
}

.banner-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-main);
}

.banner-desc {
  font-size: 13px;
  color: var(--text-muted);
}

.banner-desc code {
  background: var(--bg-muted);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: monospace;
  color: var(--primary-600);
}

.role-status-card {
  padding: 12px 16px;
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  min-width: 320px;
  box-shadow: var(--shadow-sm);
}

.status-top {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 600;
  color: var(--text-dim);
  text-transform: uppercase;
  margin-bottom: 6px;
}

.sparkle-icon {
  color: var(--primary-500);
}

.current-role-badge {
  font-size: 12px;
  margin-bottom: 4px;
}

.status-summary {
  font-size: 11px;
  color: var(--text-muted);
  line-height: 1.4;
}
</style>
