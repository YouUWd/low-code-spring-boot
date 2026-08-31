<script setup lang="ts">
import { ref, onMounted, watch } from 'vue';
import { useRoleStore } from '../stores/roleStore';
import { engineApi, type HeaderMeta } from '../api/engineApi';
import DynamicTable from '../components/DynamicTable.vue';
import CourseDetailDrawer from '../components/CourseDetailDrawer.vue';
import { BookOpen, Sparkles } from 'lucide-vue-next';

const roleStore = useRoleStore();

const loading = ref(false);
const headers = ref<HeaderMeta[]>([]);
const records = ref<any[]>([]);
const activeRecord = ref<any>(null);
const isDrawerVisible = ref(false);

async function loadData(keyword = '') {
  loading.value = true;
  try {
    const res = await engineApi.query({
      moduleId: 102,
      viewMode: 'LIST',
      filters: { keyword }
    });
    if (res.meta.headers) {
      headers.value = res.meta.headers;
    }
    records.value = res.data.records;
  } finally {
    loading.value = false;
  }
}

// 监听角色切换，即时刷新
watch(
  () => roleStore.currentRoleId,
  () => {
    loadData();
  }
);

onMounted(() => {
  loadData();
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
          <BookOpen :size="28" />
        </div>
        <div>
          <div class="banner-title-row">
            <h2 class="banner-title">课程排课中心</h2>
            <span class="badge badge-emerald">MOD-SCHOOL-COURSE (102)</span>
          </div>
          <p class="banner-desc">
            主表 <code>course</code> 直连 N:1 教师 <code>teacher</code>、1:1 教学大纲 <code>course_syllabus</code> 与 1:N 排课日程 <code>course_schedule</code> 及选课花名册。
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
          <span
            class="current-role-badge badge"
            :class="roleStore.currentRoleId === 1 ? 'badge-indigo' : (roleStore.currentRoleId === 2 ? 'badge-emerald' : 'badge-amber')"
          >
            {{ roleStore.currentRole.avatar }} {{ roleStore.currentRole.name }}
          </span>
          <p class="status-summary">
            <template v-if="roleStore.currentRoleId === 1">
              ✅ 拥有排课调度、教学大纲审定与全校选课成绩终审权限。
            </template>
            <template v-else-if="roleStore.currentRoleId === 2">
              🎓 教师权限：支持调整排课教室与节次，开放选课学生成绩批量打分录入。
            </template>
            <template v-else>
              🔒 学生权限：仅查看课程信息、大纲与上课时间表。
            </template>
          </p>
        </div>
      </div>
    </div>

    <!-- Dynamic Table (viewMode = LIST) -->
    <DynamicTable
      title="开课计划与排课列表 (viewMode: LIST)"
      :headers="headers"
      :records="records"
      :loading="loading"
      @view-detail="handleViewDetail"
      @search="loadData"
    />

    <!-- Multi-Scenario Detail Drawer -->
    <CourseDetailDrawer
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
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.9), rgba(240, 253, 244, 0.7));
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
  background: linear-gradient(135deg, #10b981, #059669);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  box-shadow: 0 4px 14px rgba(16, 185, 129, 0.25);
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
  color: #059669;
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
  color: #10b981;
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
