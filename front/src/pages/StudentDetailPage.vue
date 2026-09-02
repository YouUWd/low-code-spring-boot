<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useRoleStore } from '../stores/roleStore';
import { engineApi, type HeaderMeta } from '../api/engineApi';
import {
  ArrowLeft,
  ArrowUpDown,
  ArrowUp,
  ArrowDown,
  Filter,
  Check,
  RotateCcw,
  X,
  Save,
  BookOpen,
  Award,
  User,
  Lock,
  Sparkles,
  Plus,
  Trash2,
  ChevronRight,
  ChevronDown,
  ShieldCheck,
  Settings
} from 'lucide-vue-next';

const route = useRoute();
const router = useRouter();
const roleStore = useRoleStore();

const studentId = Number(route.params.id) || 1001;
const loading = ref(false);

// 从 URL Query 中恢复 Tab 参数，默认 courses
const validTabs = ['courses', 'awards'];
const initialTab = typeof route.query.tab === 'string' && validTabs.includes(route.query.tab)
  ? (route.query.tab as 'courses' | 'awards')
  : 'courses';

const activeTab = ref<'courses' | 'awards'>(initialTab);
const isSaving = ref(false);
const saveSuccessMessage = ref('');

const coursesLoading = ref(false);
const awardsLoading = ref(false);

const formData = ref<any>({
  student: {},
  clazz: {},
  student_profile: {},
  student_course: [],
  student_award: []
});

// 选课子表表头配置（默认值与接口动态下发合并）
const courseHeaders = ref<HeaderMeta[]>([
  { table: 'student_course', field: 'course_name', name: '课程名称', searchType: 'input', sortable: false },
  { table: 'student_course', field: 'semester', name: '修读学期', searchType: 'singleSelect', sortable: false, width: 150 },
  { table: 'student_course', field: 'score', name: '考核成绩', searchType: 'none', sortable: true, width: 160 }
]);

// 荣誉子表表头配置（默认值与接口动态下发合并）
const awardHeaders = ref<HeaderMeta[]>([
  { table: 'student_award', field: 'award_name', name: '荣誉表彰名称', searchType: 'input', sortable: false },
  { table: 'student_award', field: 'level', name: '级别', searchType: 'select', sortable: false, width: 140 },
  { table: 'student_award', field: 'award_date', name: '获奖评定日期', searchType: 'none', sortable: true, width: 160 }
]);

// 各列筛选与排序状态
const columnFilters = ref<Record<string, string>>({});
const activePopoverKey = ref<string | null>(null);
const tempFilterValue = ref<string>('');
const sortKey = ref<string>('');
const sortOrder = ref<'asc' | 'desc' | null>(null);

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

function applyFilter(key: string) {
  if (tempFilterValue.value && tempFilterValue.value.trim()) {
    columnFilters.value[key] = tempFilterValue.value.trim();
  } else {
    delete columnFilters.value[key];
  }
  activePopoverKey.value = null;
}

function resetFilter(key: string) {
  tempFilterValue.value = '';
  delete columnFilters.value[key];
  activePopoverKey.value = null;
}

function getUniqueOptions(table: string, field: string): string[] {
  const dataset = activeTab.value === 'courses' ? formData.value.student_course : formData.value.student_award;
  const values = new Set<string>();
  dataset.forEach((r: any) => {
    const val = r[field];
    if (val !== undefined && val !== null && val !== '') values.add(String(val));
  });
  return Array.from(values);
}

function handleDocumentClick(e: MouseEvent) {
  const target = e.target as HTMLElement;
  if (!target.closest('.th-filter-popover') && !target.closest('.th-filter-btn')) {
    activePopoverKey.value = null;
  }
}

onMounted(() => {
  document.addEventListener('click', handleDocumentClick);
  // 若未指定 tab 或 tab 无效，默认激活第一个 Tab（courses）并同步更新路由链接
  if (!route.query.tab || !validTabs.includes(String(route.query.tab))) {
    activeTab.value = 'courses';
    router.replace({
      query: {
        ...route.query,
        tab: 'courses'
      }
    });
  }
  loadDetail();
});

onUnmounted(() => {
  document.removeEventListener('click', handleDocumentClick);
});

// 响应式前端筛选过滤与排序（选课列表）
const filteredCourses = computed(() => {
  let list = formData.value.student_course || [];

  Object.entries(columnFilters.value).forEach(([key, filterVal]) => {
    if (!filterVal || !filterVal.trim()) return;
    const [table, field] = key.split('.');
    if (table !== 'student_course') return;
    const searchStr = filterVal.trim().toLowerCase();

    list = list.filter((r: any) => {
      const cellVal = String(r[field] ?? '').toLowerCase();
      return cellVal.includes(searchStr);
    });
  });

  if (sortKey.value && sortOrder.value) {
    const [table, field] = sortKey.value.split('.');
    if (table === 'student_course') {
      list = [...list].sort((a: any, b: any) => {
        const valA = a[field];
        const valB = b[field];
        if (typeof valA === 'number' && typeof valB === 'number') {
          return sortOrder.value === 'asc' ? valA - valB : valB - valA;
        }
        return sortOrder.value === 'asc'
          ? String(valA ?? '').localeCompare(String(valB ?? ''))
          : String(valB ?? '').localeCompare(String(valA ?? ''));
      });
    }
  }

  return list;
});

// 响应式前端筛选过滤与排序（荣誉表彰列表）
const filteredAwards = computed(() => {
  let list = formData.value.student_award || [];

  Object.entries(columnFilters.value).forEach(([key, filterVal]) => {
    if (!filterVal || !filterVal.trim()) return;
    const [table, field] = key.split('.');
    if (table !== 'student_award') return;
    const searchStr = filterVal.trim().toLowerCase();

    list = list.filter((r: any) => {
      const cellVal = String(r[field] ?? '').toLowerCase();
      return cellVal.includes(searchStr);
    });
  });

  if (sortKey.value && sortOrder.value) {
    const [table, field] = sortKey.value.split('.');
    if (table === 'student_award') {
      list = [...list].sort((a: any, b: any) => {
        const valA = a[field];
        const valB = b[field];
        if (typeof valA === 'number' && typeof valB === 'number') {
          return sortOrder.value === 'asc' ? valA - valB : valB - valA;
        }
        return sortOrder.value === 'asc'
          ? String(valA ?? '').localeCompare(String(valB ?? ''))
          : String(valB ?? '').localeCompare(String(valA ?? ''));
      });
    }
  }

  return list;
});

/** 每次切换 Tab 时按需发起独立请求加载对应模块的最新数据 (含并发互斥保护) */
async function loadTabData(tab: 'courses' | 'awards') {
  if (tab === 'courses') {
    if (coursesLoading.value) return;
    coursesLoading.value = true;
    try {
      const res = await engineApi.queryStudentCourses(studentId);
      if (res.headers && res.headers.length > 0) {
        const courseOnly = res.headers.filter(h => h.table === 'student_course');
        if (courseOnly.length > 0) {
          courseHeaders.value = courseOnly;
        }
      }
      formData.value.student_course = res.records || [];
    } finally {
      coursesLoading.value = false;
    }
  } else if (tab === 'awards') {
    if (awardsLoading.value) return;
    awardsLoading.value = true;
    try {
      const res = await engineApi.queryStudentAwards(studentId);
      if (res.headers && res.headers.length > 0) {
        const awardOnly = res.headers.filter(h => h.table === 'student_award');
        if (awardOnly.length > 0) {
          awardHeaders.value = awardOnly;
        }
      }
      formData.value.student_award = (res.records || []).map((a: any) => {
        if (a && a.award_date) {
          const dateStr = String(a.award_date).trim();
          if (dateStr.length === 7 && /^\d{4}-\d{2}$/.test(dateStr)) {
            a.award_date = `${dateStr}-01`;
          } else if (dateStr.includes('T')) {
            a.award_date = dateStr.split('T')[0];
          } else if (dateStr.includes(' ')) {
            a.award_date = dateStr.split(' ')[0];
          }
        }
        return a;
      });
    } finally {
      awardsLoading.value = false;
    }
  }
}

async function loadDetail() {
  loading.value = true;
  try {
    const dataPromise = engineApi.getStudentById(studentId);
    const tabPromise = loadTabData(activeTab.value);

    const [detailData] = await Promise.all([dataPromise, tabPromise]);
    if (detailData) {
      formData.value.student = detailData.student || {};
      formData.value.clazz = detailData.clazz || {};
      formData.value.student_profile = detailData.student_profile || {};
    }
  } finally {
    loading.value = false;
  }
}

/** 切换 Tab 时仅需同步路由，由统一的 watch(route.query.tab) 驱动按需加载 */
function switchTab(tab: 'courses' | 'awards') {
  if (activeTab.value === tab) return;
  activeTab.value = tab;
  router.replace({
    query: {
      ...route.query,
      tab
    }
  });
}

// 统一由 URL Query 变化驱动 Tab 数据懒加载
watch(
  () => route.query.tab,
  async (newTab) => {
    if (typeof newTab === 'string' && validTabs.includes(newTab)) {
      activeTab.value = newTab as 'courses' | 'awards';
      await loadTabData(activeTab.value);
    }
  }
);

// 监听当前角色切换，自动重新拉取当前角色的最新权限、脱敏字段及子模块数据
watch(
  () => roleStore.currentRoleId,
  () => {
    loadDetail();
  }
);

function goBack() {
  router.push('/students');
}

function addCourse() {
  formData.value.student_course.push({
    course_name: '新选课程',
    semester: '2026-秋',
    score: 85.0,
    _expanded: true,
    score_items: [
      { item_name: '期中考试', weight: 30.0, score: 85.0, remark: '' },
      { item_name: '平时作业', weight: 30.0, score: 90.0, remark: '' },
      { item_name: '期末大考', weight: 40.0, score: 85.0, remark: '' }
    ]
  });
}

function removeCourseItem(item: any) {
  const idx = formData.value.student_course.indexOf(item);
  if (idx !== -1) {
    formData.value.student_course.splice(idx, 1);
  }
}

function addScoreItem(course: any) {
  if (!course.score_items) {
    course.score_items = [];
  }
  course.score_items.push({
    item_name: '新考核分项',
    weight: 20.0,
    score: 85.0,
    remark: ''
  });
  course._expanded = true;
}

const deletedScoreItemIds = ref<number[]>([]);

function removeScoreItem(course: any, itemIdx: number) {
  if (course.score_items && course.score_items[itemIdx]) {
    const item = course.score_items[itemIdx];
    if (item.id) {
      deletedScoreItemIds.value.push(item.id);
    }
    course.score_items.splice(itemIdx, 1);
  }
}

/** 计算单门课程的各分项权重合计 */
function getCourseTotalWeight(c: any): number {
  if (!c.score_items || c.score_items.length === 0) return 100;
  return c.score_items.reduce((acc: number, it: any) => acc + (Number(it.weight) || 0), 0);
}

/** 实时计算课程的加权总评成绩 */
function calculateCourseScore(c: any): number {
  if (!c.score_items || c.score_items.length === 0) {
    return Number(c.score) || 0;
  }
  const totalWeight = getCourseTotalWeight(c);
  if (totalWeight <= 0) {
    return Number(c.score) || 0;
  }
  const weightedSum = c.score_items.reduce((acc: number, it: any) => {
    return acc + ((Number(it.score) || 0) * (Number(it.weight) || 0));
  }, 0);
  const total = Math.round((weightedSum / totalWeight) * 10) / 10;
  c.score = total;
  return total;
}

function addAward() {
  formData.value.student_award.push({
    award_name: '校级三好学生荣誉',
    award_date: '2026-06-30',
    level: '校级'
  });
}

function removeAwardItem(item: any) {
  const idx = formData.value.student_award.indexOf(item);
  if (idx !== -1) {
    formData.value.student_award.splice(idx, 1);
  }
}

async function handleSave() {
  isSaving.value = true;
  saveSuccessMessage.value = '';

  try {
    // 1. 组装选课列表 (以读写对称的树状结构，每门课程天然内嵌其所属的考核分项)
    const coursesToSave = (formData.value.student_course || []).map((c: any) => ({
      id: c.id,
      student_id: studentId,
      course_name: c.course_name,
      semester: c.semester,
      score: c.score,
      student_course_score_item: (c.score_items || []).map((it: any) => ({
        id: it.id,
        item_name: it.item_name,
        weight: it.weight,
        score: it.score,
        remark: it.remark
      }))
    }));

    // 软删除记录
    const deletedItemsToSave = deletedScoreItemIds.value.map((delId) => ({
      id: delId,
      deleted: 1
    }));
    deletedScoreItemIds.value = [];

    // 2. 构造干净、职责单一的 3 个独立业务模块保存列表 (与查询端 105/104/103 读写对齐)
    const modulesToSave: any[] = [];

    // 模块 105: 学生核心基本档案模块 (包含 student 与 1:1 伴生 student_profile)
    modulesToSave.push({
      moduleId: 105,
      record: {
        student: formData.value.student,
        student_profile: formData.value.student_profile
      }
    });

    // 模块 104: 荣誉与奖项管理模块 (包含多条 student_award 记录)
    const awardsList = (formData.value.student_award || []).map((a: any) => ({
      student_award: {
        id: a.id,
        student_id: a.student_id || studentId,
        award_name: a.award_name,
        award_date: a.award_date,
        level: a.level
      }
    }));
    if (awardsList.length > 0) {
      modulesToSave.push({
        moduleId: 104,
        records: awardsList
      });
    }

    // 模块 103: 选课与考核明细管理模块 (包含多条选课及内嵌 student_course_score_item 分项)
    if (coursesToSave.length > 0) {
      modulesToSave.push({
        moduleId: 103,
        records: coursesToSave.map((c) => {
          const sc: any = {
            id: c.id,
            student_id: c.student_id || studentId,
            semester: c.semester,
            score: c.score
          };
          if (c.course_id) {
            sc.course_id = c.course_id;
          }
          return {
            student_course: sc,
            student_course_score_item: c.student_course_score_item
          };
        }).concat(
          deletedItemsToSave.length > 0
            ? [{ student_course: { id: coursesToSave[0]?.id || 1 }, student_course_score_item: deletedItemsToSave }]
            : []
        )
      });
    }

    // 单次原子 HTTP 请求发起多模块批量保存 (由后端物理 DAG 拓扑调度与单事务强一致性保障)
    await engineApi.batchSave({
      modules: modulesToSave
    });

    saveSuccessMessage.value = '学生全景综合档案及课程考核构成明细已成功原子持久化保存！';
    setTimeout(() => {
      saveSuccessMessage.value = '';
    }, 2500);

    // 重新加载一次当前 Tab 数据以刷新真实物理主键
    await loadTabData(activeTab.value);
  } catch (err: any) {
    alert('保存失败: ' + err.message);
  } finally {
    isSaving.value = false;
  }
}
</script>

<template>
  <div class="detail-page-container animate-fade-in">
    <!-- Top Breadcrumb & Action Bar -->
    <div class="top-nav-bar glass-card">
      <div class="nav-left">
        <button class="btn btn-outline btn-sm back-btn" @click="goBack">
          <ArrowLeft :size="14" />
          <span>返回列表</span>
        </button>

        <div class="breadcrumb-trail">
          <span class="crumb-link" @click="goBack">学生名册列表</span>
          <ChevronRight :size="13" class="crumb-sep" />
          <span class="crumb-current">{{ formData.student?.name || '学生' }} 的全景档案 (ID: {{ studentId }})</span>
        </div>
      </div>

      <div class="nav-right">
        <div class="role-badge-pill">
          <ShieldCheck :size="14" class="shield-icon" />
          <span>当前权限: {{ roleStore.currentRole.name }}</span>
        </div>

        <button
          v-if="roleStore.currentRoleId !== 3"
          class="btn btn-primary"
          :disabled="isSaving"
          @click="handleSave"
        >
          <Save :size="15" />
          <span>{{ isSaving ? '正在保存...' : '保存全景档案' }}</span>
        </button>
      </div>
    </div>

    <!-- Success Toast Notification -->
    <div v-if="saveSuccessMessage" class="toast-banner badge-emerald animate-fade-in">
      <Sparkles :size="16" />
      <span>{{ saveSuccessMessage }}</span>
    </div>

    <!-- Main Detail Content (Master-Detail Grid) -->
    <div class="detail-content-grid">
      <!-- Left Column: Master Card & 1:1 Profile -->
      <div class="master-card-column">
        <div class="section-card glass-card">
          <div class="profile-header-box">
            <div class="student-avatar-big">👨‍🎓</div>
            <div>
              <div class="name-row">
                <h2 class="student-name-title">{{ formData.student?.name }}</h2>
                <span class="badge badge-indigo">学号: {{ formData.student?.student_no }}</span>
              </div>
              <span v-if="formData.clazz?.clazz_name" class="student-clazz-text">
                {{ formData.clazz.clazz_name }}<span v-if="formData.clazz?.grade" class="text-dim text-xs"> ({{ formData.clazz.grade }})</span>
              </span>
              <span v-else class="student-clazz-text text-dim text-xs">未分班级</span>
            </div>
          </div>

          <div class="form-vertical-list">
            <div class="form-item">
              <label class="form-label">
                <span>学生姓名</span>
                <Lock v-if="!roleStore.canEdit('student', 'name')" :size="11" class="lock-icon" />
              </label>
              <input
                v-model="formData.student.name"
                type="text"
                class="input-control"
                :disabled="!roleStore.canEdit('student', 'name')"
              />
            </div>

            <div class="form-item">
              <label class="form-label">所属班级 (N:1)</label>
              <input
                v-model="formData.clazz.clazz_name"
                type="text"
                class="input-control"
                disabled
              />
            </div>

            <div class="form-item">
              <label class="form-label">学籍状态</label>
              <select
                v-model="formData.student.status"
                class="input-control"
                :disabled="!roleStore.canEdit('student', 'status')"
              >
                <option value="在读">在读</option>
                <option value="休学">休学</option>
                <option value="退学">退学</option>
                <option value="毕业">毕业</option>
              </select>
            </div>

            <!-- 隐私档案字段 (教师视角 view=0 自动脱敏) -->
            <div class="form-item">
              <label class="form-label">
                <span>身份证号</span>
                <span v-if="!roleStore.canView('student_profile', 'id_card')" class="badge badge-rose">
                  🚫 教师不可见已脱敏
                </span>
              </label>
              <input
                v-if="roleStore.canView('student_profile', 'id_card')"
                v-model="formData.student_profile.id_card"
                type="text"
                class="input-control"
                :disabled="!roleStore.canEdit('student_profile', 'id_card')"
              />
              <input
                v-else
                type="text"
                value="****************** (受权限保护已隐藏)"
                class="input-control"
                disabled
              />
            </div>

            <div class="form-item">
              <label class="form-label">
                <span>紧急联系电话</span>
                <span v-if="!roleStore.canView('student_profile', 'emergency_phone')" class="badge badge-rose">
                  🚫 隐私保护
                </span>
              </label>
              <input
                v-if="roleStore.canView('student_profile', 'emergency_phone')"
                v-model="formData.student_profile.emergency_phone"
                type="text"
                class="input-control"
                :disabled="!roleStore.canEdit('student_profile', 'emergency_phone')"
              />
              <input
                v-else
                type="text"
                value="*********** (已隐藏)"
                class="input-control"
                disabled
              />
            </div>
          </div>
        </div>
      </div>

      <!-- Right Column: Sub Tables Tabs (Courses & Awards) -->
      <div class="sub-tabs-column">
        <div class="section-card glass-card sub-tabs-card-flush">
          <div class="tabs-header-bar">
            <div class="tabs-left">
              <button
                class="tab-btn"
                :class="{ active: activeTab === 'courses' }"
                @click="switchTab('courses')"
              >
                <BookOpen :size="16" />
                <span>选课修读与各科成绩</span>
              </button>

              <button
                class="tab-btn"
                :class="{ active: activeTab === 'awards' }"
                @click="switchTab('awards')"
              >
                <Award :size="16" />
                <span>荣誉表彰与获奖记录</span>
              </button>
            </div>

            <div class="tabs-actions-right">
              <button
                v-if="activeTab === 'courses' && roleStore.canEdit('student_course', 'score')"
                class="btn btn-outline btn-sm"
                @click="addCourse"
              >
                <Plus :size="14" />
                <span>加选课程</span>
              </button>

              <button
                v-if="activeTab === 'awards' && roleStore.canEdit('student_award', 'award_name')"
                class="btn btn-outline btn-sm"
                @click="addAward"
              >
                <Plus :size="14" />
                <span>新增荣誉表彰</span>
              </button>
            </div>
          </div>

          <!-- Tab 1: Courses (1:N) -->
          <div v-if="activeTab === 'courses'" class="tab-content-area">
            <table class="modern-table sub-detail-table">
              <colgroup>
                <col style="width: 50px; min-width: 50px;" />
                <col
                  v-for="col in courseHeaders"
                  :key="`course-col-${col.field}`"
                  :style="{ width: col.width ? `${col.width}px` : 'auto', minWidth: col.width ? `${col.width}px` : '120px' }"
                />
                <col v-if="roleStore.canEdit('student_course', 'score')" style="width: 80px; min-width: 80px;" />
              </colgroup>
              <thead>
                <tr class="th-header-row">
                  <th class="th-index text-center">#</th>
                  <th
                    v-for="col in courseHeaders"
                    :key="`${col.table}.${col.field}`"
                    class="th-column-cell"
                  >
                    <div class="th-content-wrapper">
                      <span class="th-label-text">{{ col.name }}</span>

                      <!-- 右侧图标组：排序箭头 + 漏斗搜索 -->
                      <div v-if="col.sortable || (col.searchType && col.searchType !== 'none')" class="th-icons-group">
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
                          v-if="col.searchType && col.searchType !== 'none'"
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
                              v-if="col.searchType === 'select' || col.searchType === 'singleSelect' || col.searchType === 'multipleSelect'"
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
                  <th v-if="roleStore.canEdit('student_course', 'score')" style="width: 80px;" class="th-actions text-center">操作</th>
                </tr>
              </thead>
              <tbody>
                <template v-for="(c, idx) in filteredCourses" :key="c.id || idx">
                  <!-- 1. 课程主行 -->
                  <tr class="master-course-row" :class="{ 'row-expanded': c._expanded !== false }">
                    <td class="text-center">
                      <div class="expand-index-cell">
                        <button
                          class="btn-toggle-expand"
                          :title="c._expanded === false ? '展开考核分项' : '收起考核分项'"
                          @click="c._expanded = !c._expanded"
                        >
                          <ChevronDown :size="13" :class="{ 'icon-collapsed': c._expanded === false }" />
                        </button>
                        <span class="index-num">{{ Number(idx) + 1 }}</span>
                      </div>
                    </td>
                    <td v-for="col in courseHeaders" :key="col.field">
                      <input
                        v-if="col.field === 'course_name'"
                        v-model="c.course_name"
                        type="text"
                        class="sub-input font-medium"
                        :disabled="!roleStore.canEdit('student_course', 'score')"
                      />
                      <input
                        v-else-if="col.field === 'semester'"
                        v-model="c.semester"
                        type="text"
                        class="sub-input"
                        :disabled="!roleStore.canEdit('student_course', 'score')"
                      />
                      <div v-else-if="col.field === 'score'" class="score-input-wrapper justify-center">
                        <div class="composite-score-badge" :class="{ 'score-high': calculateCourseScore(c) >= 90, 'score-low': calculateCourseScore(c) < 60 }">
                          <span class="score-val">{{ calculateCourseScore(c) }}</span>
                          <span class="score-unit">分</span>
                          <span v-if="c.score_items && c.score_items.length > 0" class="badge-auto-calc" title="由下方分项权重实时自动折算">加权</span>
                        </div>
                      </div>
                      <span v-else class="cell-text">{{ c[col.field] }}</span>
                    </td>
                    <td v-if="roleStore.canEdit('student_course', 'score')" class="text-center">
                      <button class="delete-btn" title="删除课程记录" @click="removeCourseItem(c)">
                        <Trash2 :size="14" />
                      </button>
                    </td>
                  </tr>

                  <!-- 2. 嵌套子表格行（直接展开模式） -->
                  <tr v-if="c._expanded !== false" class="nested-sub-row">
                    <td :colspan="courseHeaders.length + (roleStore.canEdit('student_course', 'score') ? 2 : 1)" class="nested-sub-container">
                      <div class="score-items-card">
                        <div class="score-items-header">
                          <div class="score-items-title">
                            <span class="title-arrow">↳</span>
                            <span class="title-text">成绩考核构成明细</span>
                            <span class="items-count-pill">{{ c.score_items?.length || 0 }} 项考核</span>
                          </div>

                          <div class="score-items-actions">
                            <span class="weight-total-hint" :class="{ 'weight-warn': getCourseTotalWeight(c) !== 100 }">
                              权重合计: {{ getCourseTotalWeight(c) }}%
                            </span>
                            <button
                              v-if="roleStore.canEdit('student_course', 'score')"
                              class="btn-add-item"
                              @click="addScoreItem(c)"
                            >
                              <Plus :size="12" />
                              <span>添加考核分项</span>
                            </button>
                          </div>
                        </div>

                        <!-- 嵌套微型子表 -->
                        <table class="mini-score-table">
                          <thead>
                            <tr>
                              <th style="width: 36px;" class="text-center">#</th>
                              <th>考核分项</th>
                              <th style="width: 110px;" class="text-center">权重占比(%)</th>
                              <th style="width: 120px;" class="text-center">分项得分(分)</th>
                              <th style="width: 100px;" class="text-center">折合贡献</th>
                              <th>评语 / 备注说明</th>
                              <th v-if="roleStore.canEdit('student_course', 'score')" style="width: 50px;" class="text-center">操作</th>
                            </tr>
                          </thead>
                          <tbody>
                            <tr v-for="(item, iIdx) in (c.score_items || [])" :key="item.id || iIdx">
                              <td class="text-center font-mono text-dim text-xs">{{ Number(iIdx) + 1 }}</td>
                              <td>
                                <input
                                  v-model="item.item_name"
                                  type="text"
                                  class="mini-input"
                                  placeholder="如: 期中考试、课后作业..."
                                  :disabled="!roleStore.canEdit('student_course', 'score')"
                                />
                              </td>
                              <td class="text-center">
                                <div class="mini-weight-box">
                                  <input
                                    v-model.number="item.weight"
                                    type="number"
                                    min="0"
                                    max="100"
                                    step="5"
                                    class="mini-input text-center"
                                    :disabled="!roleStore.canEdit('student_course', 'score')"
                                  />
                                  <span class="unit">%</span>
                                </div>
                              </td>
                              <td class="text-center">
                                <div class="mini-score-box">
                                  <input
                                    v-model.number="item.score"
                                    type="number"
                                    min="0"
                                    max="100"
                                    step="0.5"
                                    class="mini-input text-center font-bold"
                                    :class="{ 'score-high': item.score >= 90, 'score-low': item.score < 60 }"
                                    :disabled="!roleStore.canEdit('student_course', 'score')"
                                  />
                                  <span class="unit">分</span>
                                </div>
                              </td>
                              <td class="text-center font-mono text-xs text-dim">
                                <span class="contribution-tag">
                                  +{{ ((Number(item.score) || 0) * (Number(item.weight) || 0) / 100).toFixed(1) }}分
                                </span>
                              </td>
                              <td>
                                <input
                                  v-model="item.remark"
                                  type="text"
                                  class="mini-input"
                                  placeholder="可选评语说明..."
                                  :disabled="!roleStore.canEdit('student_course', 'score')"
                                />
                              </td>
                              <td v-if="roleStore.canEdit('student_course', 'score')" class="text-center">
                                <button class="delete-btn-xs" title="删除该分项" @click="removeScoreItem(c, iIdx)">
                                  <Trash2 :size="12" />
                                </button>
                              </td>
                            </tr>
                            <tr v-if="!c.score_items || c.score_items.length === 0">
                              <td :colspan="roleStore.canEdit('student_course', 'score') ? 7 : 6" class="text-center empty-mini">
                                暂无考核分项明细，可直接在上方录入成绩或点击“添加考核分项”拆分构成
                              </td>
                            </tr>
                          </tbody>
                        </table>
                      </div>
                    </td>
                  </tr>
                </template>
                <tr v-if="filteredCourses.length === 0">
                  <td :colspan="courseHeaders.length + (roleStore.canEdit('student_course', 'score') ? 2 : 1)" class="text-center empty-sub">
                    暂无选课修读记录
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- Tab 2: Awards (1:N) -->
          <div v-if="activeTab === 'awards'" class="tab-content-area">
            <table class="modern-table sub-detail-table">
              <colgroup>
                <col style="width: 50px; min-width: 50px;" />
                <col
                  v-for="col in awardHeaders"
                  :key="`award-col-${col.field}`"
                  :style="{ width: col.width ? `${col.width}px` : 'auto', minWidth: col.width ? `${col.width}px` : '120px' }"
                />
                <col v-if="roleStore.canEdit('student_award', 'award_name')" style="width: 80px; min-width: 80px;" />
              </colgroup>
              <thead>
                <tr class="th-header-row">
                  <th class="th-index text-center">#</th>
                  <th
                    v-for="col in awardHeaders"
                    :key="`${col.table}.${col.field}`"
                    class="th-column-cell"
                  >
                    <div class="th-content-wrapper">
                      <span class="th-label-text">{{ col.name }}</span>

                      <!-- 右侧图标组：排序箭头 + 漏斗搜索 -->
                      <div v-if="col.sortable || (col.searchType && col.searchType !== 'none')" class="th-icons-group">
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
                          v-if="col.searchType && col.searchType !== 'none'"
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
                              v-if="col.searchType === 'select' || col.searchType === 'singleSelect' || col.searchType === 'multipleSelect'"
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
                  <th v-if="roleStore.canEdit('student_award', 'award_name')" style="width: 80px;" class="th-actions text-center">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(aw, aIdx) in filteredAwards" :key="aw.id || aIdx">
                  <td class="text-center text-dim font-mono text-xs">{{ Number(aIdx) + 1 }}</td>
                  <td v-for="col in awardHeaders" :key="col.field">
                    <template v-if="col.field === 'award_name'">
                      <input
                        v-if="roleStore.canEdit('student_award', 'award_name')"
                        v-model="aw.award_name"
                        type="text"
                        class="sub-input"
                        placeholder="输入荣誉奖项名称..."
                      />
                      <span v-else class="font-medium">{{ aw.award_name }}</span>
                    </template>
                    <template v-else-if="col.field === 'level'">
                      <select
                        v-if="roleStore.canEdit('student_award', 'award_name')"
                        v-model="aw.level"
                        class="sub-input text-center"
                      >
                        <option value="国家级">国家级</option>
                        <option value="省部级">省部级</option>
                        <option value="校级">校级</option>
                        <option value="院系级">院系级</option>
                      </select>
                      <span
                        v-else
                        class="badge"
                        :class="aw.level === '国家级' ? 'badge-rose' : (aw.level === '省部级' ? 'badge-indigo' : 'badge-amber')"
                      >
                        {{ aw.level }}
                      </span>
                    </template>
                    <template v-else-if="col.field === 'award_date'">
                      <input
                        v-if="roleStore.canEdit('student_award', 'award_name')"
                        v-model="aw.award_date"
                        type="date"
                        class="sub-input text-center"
                      />
                      <span v-else class="text-dim text-xs">{{ aw.award_date }}</span>
                    </template>
                    <span v-else class="cell-text">{{ aw[col.field] }}</span>
                  </td>
                  <td v-if="roleStore.canEdit('student_award', 'award_name')" class="text-center">
                    <button class="delete-btn" title="删除荣誉记录" @click="removeAwardItem(aw)">
                      <Trash2 :size="14" />
                    </button>
                  </td>
                </tr>
                <tr v-if="filteredAwards.length === 0">
                  <td :colspan="awardHeaders.length + (roleStore.canEdit('student_award', 'award_name') ? 2 : 1)" class="text-center empty-sub">
                    暂无荣誉表彰记录
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.detail-page-container {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.top-nav-bar {
  padding: 14px 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.nav-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 6px;
}

.breadcrumb-trail {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.crumb-link {
  color: var(--text-muted);
  cursor: pointer;
  transition: var(--transition);
}

.crumb-link:hover {
  color: var(--primary-600);
}

.crumb-sep {
  color: var(--text-dim);
}

.crumb-current {
  font-weight: 600;
  color: var(--text-main);
}

.nav-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.role-badge-pill {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 500;
  color: var(--primary-600);
  background: var(--primary-50);
  padding: 5px 12px;
  border-radius: var(--radius-full);
  border: 1px solid var(--primary-100);
}

.shield-icon {
  color: var(--primary-600);
}

.toast-banner {
  padding: 10px 20px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  font-weight: 500;
}

/* Master-Detail Layout */
.detail-content-grid {
  display: grid;
  grid-template-columns: 360px 1fr;
  gap: 20px;
}

.master-card-column {
  display: flex;
  flex-direction: column;
}

.section-card {
  padding: 20px;
  background: var(--bg-surface);
}

.profile-header-box {
  display: flex;
  align-items: center;
  gap: 14px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border-subtle);
  margin-bottom: 16px;
}

.student-avatar-big {
  font-size: 36px;
}

.name-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.student-name-title {
  font-size: 18px;
  font-weight: 700;
}

.student-clazz-text {
  font-size: 12px;
  color: var(--text-muted);
}

.form-vertical-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-muted);
  display: flex;
  align-items: center;
  gap: 6px;
}

.lock-icon {
  color: var(--text-dim);
}

.sub-tabs-card-flush {
  padding: 0 !important;
  overflow: hidden;
}

.sub-tabs-card-flush .tabs-header-bar {
  padding: 14px 20px;
  background: var(--bg-subtle);
  border-bottom: 1px solid var(--border-subtle);
  margin-bottom: 0;
}

.tabs-header-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.tabs-left {
  display: flex;
  gap: 8px;
}

.tab-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  font-size: 13px;
  font-weight: 500;
  color: #64748b;
  border-radius: var(--radius-md);
  background: transparent;
  border: 1px solid transparent;
  cursor: pointer;
  transition: all 0.2s ease;
  position: relative;
}

.tab-btn:hover {
  color: #1e293b;
  background: rgba(255, 255, 255, 0.6);
}

.tab-btn.active {
  background: #ffffff;
  color: #2563eb;
  font-weight: 700;
  border-color: rgba(191, 219, 254, 0.8);
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.12), 0 1px 3px rgba(0, 0, 0, 0.05);
}

.tab-btn.active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 14px;
  right: 14px;
  height: 3px;
  background: linear-gradient(90deg, #2563eb, #3b82f6);
  border-radius: 3px 3px 0 0;
}

.tab-badge {
  background: #eff6ff;
  color: #2563eb;
  font-size: 11px;
  font-weight: 600;
  padding: 1px 7px;
  border-radius: var(--radius-full);
  border: 1px solid #dbeafe;
}

.sub-tabs-card-flush .sub-detail-table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
}

.sub-tabs-card-flush .sub-detail-table th {
  font-size: 13px;
  font-weight: 600;
  color: #475569;
  padding: 14px 18px;
  background: #f8fafc;
  border-bottom: 1px solid var(--border-subtle);
  vertical-align: middle;
  text-align: left;
}

.sub-tabs-card-flush .sub-detail-table td {
  padding: 12px 18px;
  border-bottom: 1px solid var(--border-subtle);
  vertical-align: middle;
  font-size: 13px;
  color: #1e293b;
}

.sub-tabs-card-flush .sub-detail-table tbody tr:last-child td {
  border-bottom: none;
}

.sub-tabs-card-flush .sub-detail-table tbody tr:hover {
  background: rgba(248, 250, 252, 0.7);
}

.sub-input {
  width: 100%;
  height: 36px;
  padding: 6px 12px;
  font-size: 13px;
  font-family: inherit;
  color: #1e293b;
  background-color: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: var(--radius-sm);
  outline: none;
  transition: all 0.2s ease;
  box-sizing: border-box;
}

.sub-input:focus {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15);
}

.sub-input:disabled {
  background-color: #f1f5f9;
  color: #64748b;
  cursor: not-allowed;
  border-color: #e2e8f0;
}

select.sub-input {
  appearance: none;
  background-image: url("data:image/svg+xml;charset=UTF-8,%3csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%2364748b' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3e%3cpolyline points='6 9 12 15 18 9'%3e%3c/polyline%3e%3c/svg%3e");
  background-repeat: no-repeat;
  background-position: right 10px center;
  background-size: 14px;
  padding-right: 30px;
  cursor: pointer;
}

input[type="date"].sub-input {
  cursor: pointer;
}

.score-input-wrapper {
  display: flex;
  align-items: center;
  gap: 6px;
}

.score-input {
  font-weight: 700;
  text-align: center;
  width: 80px;
}

.score-high {
  color: #059669;
}

.score-low {
  color: #e11d48;
}

.score-unit {
  font-size: 12px;
  color: #64748b;
  font-weight: 500;
}

.delete-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: var(--radius-sm);
  background: transparent;
  border: 1px solid transparent;
  color: #94a3b8;
  cursor: pointer;
  transition: all 0.2s ease;
}

.delete-btn:hover {
  background: #fee2e2;
  color: #ef4444;
  border-color: #fecaca;
}

.empty-sub {
  padding: 40px 20px;
  color: var(--text-dim);
  font-size: 13px;
}

/* Sub-table Header Sort & Popover Filter Styles */
.th-header-row th {
  position: relative;
  user-select: none;
}

.th-content-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 24px;
}

.th-label-text {
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

/* ===== 1:N:N 嵌套考核小项展开行样式 ===== */
.expand-index-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.btn-toggle-expand {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: var(--radius-sm);
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  color: #64748b;
  cursor: pointer;
  padding: 0;
  transition: all 0.2s ease;
}

.btn-toggle-expand:hover {
  background: #dbeafe;
  color: #2563eb;
  border-color: #bfdbfe;
}

.icon-collapsed {
  transform: rotate(-90deg);
}

.index-num {
  font-family: monospace;
  font-size: 11px;
  color: #94a3b8;
}

.master-course-row.row-expanded {
  background: rgba(241, 245, 249, 0.4);
}

.master-course-row.row-expanded td {
  border-bottom-color: #e2e8f0;
}

.composite-score-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: var(--radius-sm);
  font-family: inherit;
}

.composite-score-badge.score-high {
  background: #f0fdf4;
  border-color: #bbf7d0;
  color: #166534;
}

.composite-score-badge.score-low {
  background: #fff1f2;
  border-color: #fecdd3;
  color: #9f1239;
}

.score-val {
  font-size: 14px;
  font-weight: 700;
}

.badge-auto-calc {
  font-size: 10px;
  font-weight: 600;
  background: #eff6ff;
  color: #2563eb;
  padding: 1px 4px;
  border-radius: 3px;
  border: 1px solid #bfdbfe;
}

/* 嵌套子表格外壳卡片 */
.nested-sub-row {
  background: #fafafa;
}

.nested-sub-container {
  padding: 8px 18px 16px 44px !important;
  border-bottom: 1px solid #e2e8f0;
}

.score-items-card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: var(--radius-md);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  overflow: hidden;
}

.score-items-header {
  padding: 10px 14px;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.score-items-title {
  display: flex;
  align-items: center;
  gap: 6px;
}

.title-arrow {
  color: #3b82f6;
  font-weight: 700;
  font-size: 14px;
}

.title-text {
  font-size: 12px;
  font-weight: 600;
  color: #334155;
}

.items-count-pill {
  font-size: 11px;
  font-weight: 500;
  color: #64748b;
  background: #e2e8f0;
  padding: 1px 6px;
  border-radius: 10px;
}

.score-items-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.weight-total-hint {
  font-size: 11px;
  font-weight: 600;
  color: #059669;
}

.weight-total-hint.weight-warn {
  color: #d97706;
}

.btn-add-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  font-size: 11px;
  font-weight: 500;
  color: #2563eb;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-add-item:hover {
  background: #2563eb;
  color: #ffffff;
  border-color: #2563eb;
}

/* 微型考核表格 */
.mini-score-table {
  width: 100%;
  border-collapse: collapse;
}

.mini-score-table th {
  font-size: 11px;
  font-weight: 600;
  color: #64748b;
  padding: 8px 10px !important;
  background: #ffffff !important;
  border-bottom: 1px solid #f1f5f9 !important;
  text-align: left;
}

.mini-score-table td {
  padding: 6px 10px !important;
  border-bottom: 1px solid #f1f5f9 !important;
  vertical-align: middle;
  font-size: 12px;
}

.mini-score-table tbody tr:last-child td {
  border-bottom: none !important;
}

.mini-score-table tbody tr:hover {
  background: #f8fafc;
}

.mini-input {
  width: 100%;
  height: 28px;
  padding: 3px 8px;
  font-size: 12px;
  border: 1px solid #e2e8f0;
  border-radius: var(--radius-sm);
  outline: none;
  background: #ffffff;
  color: #1e293b;
  box-sizing: border-box;
  transition: all 0.2s ease;
}

.mini-input:focus {
  border-color: #3b82f6;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.12);
}

.mini-input:disabled {
  background-color: #f8fafc;
  color: #64748b;
  cursor: not-allowed;
  border-color: #f1f5f9;
}

.mini-weight-box, .mini-score-box {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.mini-weight-box .mini-input, .mini-score-box .mini-input {
  width: 60px;
}

.mini-weight-box .unit, .mini-score-box .unit {
  font-size: 11px;
  color: #94a3b8;
}

.contribution-tag {
  display: inline-block;
  padding: 1px 6px;
  background: #f1f5f9;
  border-radius: 3px;
  color: #475569;
  font-weight: 600;
}

.delete-btn-xs {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: var(--radius-sm);
  background: transparent;
  border: none;
  color: #94a3b8;
  cursor: pointer;
  transition: all 0.2s ease;
}

.delete-btn-xs:hover {
  background: #fee2e2;
  color: #ef4444;
}

.empty-mini {
  padding: 16px !important;
  color: #94a3b8;
  font-size: 12px;
}
</style>
