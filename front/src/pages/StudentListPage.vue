<script setup lang="ts">
import { ref, onMounted, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useRoleStore } from '../stores/roleStore';
import {
  engineApi,
  flattenHeaderTree,
  type HeaderMeta,
  type EngineModuleMeta,
  type DynamicFilterItem,
  type DynamicSortItem
} from '../api/engineApi';
import DynamicTable from '../components/DynamicTable.vue';
import { GraduationCap, Sparkles } from 'lucide-vue-next';

const router = useRouter();
const roleStore = useRoleStore();

const loading = ref(false);
const meta = ref<EngineModuleMeta | undefined>(undefined);
const headers = ref<HeaderMeta[]>([]);
const records = ref<any[]>([]);
const currentFilters = ref<DynamicFilterItem[]>([]);
const currentSorts = ref<DynamicSortItem[]>([]);

// 全量完整字段集（101 及其所有子孙模块所有字段，共 40 个）
const STUDENT_PAGE_FIELD_IDS = [
  // 101 学生综合档案 (student: 5个)
  1, 2, 3, 4, 5,
  // 103 选课与成绩管理 (student_course: 6个, student_course_score_item: 6个)
  28, 29, 30, 31, 32, 33, 57, 58, 59, 60, 61, 62,
  // 104 荣誉与奖惩管理 (student_award: 5个)
  37, 38, 39, 40, 56,
  // 105 学生核心基本档案 (student: 5个, student_profile: 4个, clazz: 3个)
  43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54,
  // 106 荣誉材料与佐证明细 (student_award_detail: 6个)
  74, 75, 76, 77, 78, 79
];

async function loadData(filters: DynamicFilterItem[] = currentFilters.value, sorts: DynamicSortItem[] = currentSorts.value) {
  loading.value = true;
  currentFilters.value = filters;
  currentSorts.value = sorts;
  try {
    const res = await engineApi.query({
      moduleId: 101,
      fields: STUDENT_PAGE_FIELD_IDS,
      withHeader: true,
      filters: currentFilters.value,
      sorts: currentSorts.value
    });
    if (res?.header) {
      headers.value = flattenHeaderTree(res.header);
    }
    records.value = res?.records || [];
  } catch (err) {
    console.error('Failed to load student data', err);
  } finally {
    loading.value = false;
  }
}

function handleQueryChange(payload: { filters: DynamicFilterItem[]; sorts: DynamicSortItem[] }) {
  loadData(payload.filters, payload.sorts);
}

watch(
  () => roleStore.currentRoleId,
  async () => {
    await loadData();
  }
);

onMounted(async () => {
  await loadData();
});

// 点击“全景详情”，平滑路由跳转到独立详情页并默认激活 courses Tab
function handleViewDetail(row: any) {
  const studentId = row['101']?.student?.id || row.student?.id || row.id || 1;
  router.push({
    path: `/students/${studentId}`,
    query: { tab: 'courses' }
  });
}
</script>

<template>
  <div class="page-container animate-fade-in">
    <!-- Feature Banner -->
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
            主表 <code>student</code> 联动 1:1 伴生档案 <code>student_profile</code>、N:1 班级 <code>clazz</code> 及 1:N 选课与荣誉。
          </p>
        </div>
      </div>

      <div class="role-status-card">
        <div class="status-top">
          <Sparkles :size="15" class="sparkle-icon" />
          <span class="status-heading">权限引擎实时生效中</span>
        </div>
        <div class="status-content">
          <span
            class="current-role-badge badge"
            :class="roleStore.currentRoleId === 1 ? 'badge-indigo' : (roleStore.currentRoleId === 2 ? 'badge-emerald' : 'badge-amber')"
          >
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

    <!-- Table -->
    <DynamicTable
      title="学生名册列表 (viewMode: LIST)"
      :meta="meta"
      :headers="headers"
      :records="records"
      :loading="loading"
      @view-detail="handleViewDetail"
      @query-change="handleQueryChange"
      @search="loadData()"
    />
  </div>
</template>

<style scoped>
.page-container {
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
