<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useRoleStore } from '../stores/roleStore';
import { engineApi } from '../api/engineApi';
import {
  ArrowLeft,
  ArrowUpDown,
  Save,
  BookOpen,
  Calendar,
  Users,
  UserCheck,
  Plus,
  Trash2,
  MapPin,
  Sparkles,
  ChevronRight,
  ShieldCheck,
  TrendingUp,
  Award,
  Search,
  CheckCircle2,
  AlertCircle
} from 'lucide-vue-next';

const route = useRoute();
const router = useRouter();
const roleStore = useRoleStore();

const courseId = Number(route.params.id) || 201;
const loading = ref(false);

// 从 URL Query 中恢复 Tab 参数，默认 syllabus
const validCourseTabs = ['syllabus', 'schedules', 'roster', 'teacher'];
const initialCourseTab = typeof route.query.tab === 'string' && validCourseTabs.includes(route.query.tab)
  ? (route.query.tab as 'syllabus' | 'schedules' | 'roster' | 'teacher')
  : 'syllabus';

const activeTab = ref<'syllabus' | 'schedules' | 'roster' | 'teacher'>(initialCourseTab);
const isSaving = ref(false);
const saveSuccessMessage = ref('');

function switchTab(tab: 'syllabus' | 'schedules' | 'roster' | 'teacher') {
  activeTab.value = tab;
  router.replace({
    query: {
      ...route.query,
      tab
    }
  });
}

// 监听 URL Query 参数变化，支持浏览器前进/后退
watch(
  () => route.query.tab,
  (newTab) => {
    if (typeof newTab === 'string' && validCourseTabs.includes(newTab)) {
      activeTab.value = newTab as 'syllabus' | 'schedules' | 'roster' | 'teacher';
    }
  }
);

// 监听当前角色切换，自动重新拉取当前角色的最新权限及课程详情数据
watch(
  () => roleStore.currentRoleId,
  () => {
    loadDetail();
  }
);

const formData = ref<any>({
  course: {},
  teacher: {},
  course_syllabus: {},
  course_schedule: [],
  enrolled_students: []
});

async function loadDetail() {
  loading.value = true;
  try {
    const data = await engineApi.getCourseById(courseId);
    if (data) {
      formData.value = data;
    }
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  if (!route.query.tab || !validCourseTabs.includes(String(route.query.tab))) {
    activeTab.value = 'syllabus';
    router.replace({
      query: {
        ...route.query,
        tab: 'syllabus'
      }
    });
  }
  loadDetail();
});

function goBack() {
  router.push('/courses');
}

function addSchedule() {
  formData.value.course_schedule.push({
    day_of_week: '周五',
    time_slot: '14:00 - 15:40',
    classroom: '公共教学楼 201',
    weeks: '1-16周'
  });
}

function removeSchedule(idx: number) {
  formData.value.course_schedule.splice(idx, 1);
}

// 花名册表头内联筛选与排序对象
const rosterFilters = ref<Record<string, string>>({
  student_no: '',
  name: '',
  clazz_name: '',
  grade: ''
});

const activeRosterFilterCol = ref<string | null>(null);
const tempRosterFilterVal = ref<string>('');

const rosterSortKey = ref<string>('');
const rosterSortOrder = ref<'asc' | 'desc' | null>(null);

function toggleRosterSort(colKey: string) {
  if (rosterSortKey.value !== colKey) {
    rosterSortKey.value = colKey;
    rosterSortOrder.value = 'asc';
  } else if (rosterSortOrder.value === 'asc') {
    rosterSortOrder.value = 'desc';
  } else {
    rosterSortKey.value = '';
    rosterSortOrder.value = null;
  }
}

function toggleRosterFilter(colKey: string) {
  if (activeRosterFilterCol.value === colKey) {
    activeRosterFilterCol.value = null;
  } else {
    activeRosterFilterCol.value = colKey;
    tempRosterFilterVal.value = rosterFilters.value[colKey] || '';
  }
}

function applyRosterFilter(colKey: string) {
  rosterFilters.value[colKey] = tempRosterFilterVal.value.trim();
  activeRosterFilterCol.value = null;
}

function resetRosterFilter(colKey: string) {
  tempRosterFilterVal.value = '';
  rosterFilters.value[colKey] = '';
  activeRosterFilterCol.value = null;
}

function updateStudentScore(student: any) {
  const reg = Number(student.regular_score) || 0;
  const fin = Number(student.final_score) || 0;
  student.total_score = Math.round((reg * 0.4 + fin * 0.6) * 10) / 10;
}

// 统计指标
const rosterStats = computed(() => {
  const list = formData.value.enrolled_students || [];
  if (list.length === 0) return { total: 0, avg: 0, max: 0, min: 0, excellentRate: 0, passRate: 100 };
  const scores = list.map((s: any) => Number(s.total_score) || 0);
  const total = list.length;
  const sum = scores.reduce((a: number, b: number) => a + b, 0);
  const avg = Math.round((sum / total) * 10) / 10;
  const max = Math.max(...scores);
  const min = Math.min(...scores);
  const excellent = scores.filter((s: number) => s >= 90).length;
  const pass = scores.filter((s: number) => s >= 60).length;
  return {
    total,
    avg,
    max,
    min,
    excellentRate: Math.round((excellent / total) * 100),
    passRate: Math.round((pass / total) * 100)
  };
});

// 根据表头筛选与排序过滤后的选课列表
const filteredStudents = computed(() => {
  let list = formData.value.enrolled_students || [];
  const filters = rosterFilters.value;

  if (filters.student_no && filters.student_no.trim()) {
    const kw = filters.student_no.trim().toLowerCase();
    list = list.filter((s: any) => s.student_no && s.student_no.toLowerCase().includes(kw));
  }
  if (filters.name && filters.name.trim()) {
    const kw = filters.name.trim().toLowerCase();
    list = list.filter((s: any) => s.name && s.name.toLowerCase().includes(kw));
  }
  if (filters.clazz_name && filters.clazz_name.trim()) {
    const kw = filters.clazz_name.trim().toLowerCase();
    list = list.filter((s: any) => s.clazz_name && s.clazz_name.toLowerCase().includes(kw));
  }
  if (filters.grade) {
    list = list.filter((s: any) => {
      const gradeObj = getScoreGrade(s.total_score);
      return gradeObj.gradeCode === filters.grade;
    });
  }

  // 排序
  if (rosterSortKey.value && rosterSortOrder.value) {
    const key = rosterSortKey.value;
    list = [...list].sort((a, b) => {
      const valA = Number(a[key]) || 0;
      const valB = Number(b[key]) || 0;
      return rosterSortOrder.value === 'asc' ? valA - valB : valB - valA;
    });
  }

  return list;
});

function getScoreGrade(score: number) {
  if (score >= 90) return { label: '优秀 (A)', gradeCode: 'A', cls: 'badge-emerald' };
  if (score >= 80) return { label: '良好 (B)', gradeCode: 'B', cls: 'badge-indigo' };
  if (score >= 70) return { label: '中等 (C)', gradeCode: 'C', cls: 'badge-sky' };
  if (score >= 60) return { label: '及格 (D)', gradeCode: 'D', cls: 'badge-amber' };
  return { label: '不及格 (F)', gradeCode: 'F', cls: 'badge-rose' };
}

const canEditSchedule = computed(() => {
  return roleStore.currentRoleId === 1 || roleStore.currentRoleId === 2;
});

const canEditRosterScore = computed(() => {
  return roleStore.currentRoleId === 1 || roleStore.currentRoleId === 2;
});

async function handleSave() {
  isSaving.value = true;
  saveSuccessMessage.value = '';

  try {
    const payload = {
      moduleId: 102,
      tables: {
        course: formData.value.course,
        course_syllabus: formData.value.course_syllabus,
        course_schedule: formData.value.course_schedule
      }
    };

    await engineApi.save(payload);

    saveSuccessMessage.value = '课程排课与全景教学档案保存成功！';
    setTimeout(() => {
      saveSuccessMessage.value = '';
    }, 2500);
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
          <span class="crumb-link" @click="goBack">开课计划与排课列表</span>
          <ChevronRight :size="13" class="crumb-sep" />
          <span class="crumb-current">{{ formData.course?.course_name }} ({{ formData.course?.course_code }})</span>
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
          <span>{{ isSaving ? '正在保存...' : '保存排课全景' }}</span>
        </button>
      </div>
    </div>

    <!-- Success Toast -->
    <div v-if="saveSuccessMessage" class="toast-banner badge-emerald animate-fade-in">
      <Sparkles :size="16" />
      <span>{{ saveSuccessMessage }}</span>
    </div>

    <!-- Multi-Scenario Big Screen Tabs with Topology Relation Tags -->
    <div class="scenario-tabs-card glass-card">
      <div class="tabs-header-bar">
        <button
          class="tab-nav-btn"
          :class="{ active: activeTab === 'syllabus' }"
          @click="switchTab('syllabus')"
        >
          <BookOpen :size="16" />
          <span>1. 基础信息与教学大纲</span>
          <span class="relation-tag tag-1-1">1:1</span>
        </button>

        <button
          class="tab-nav-btn"
          :class="{ active: activeTab === 'schedules' }"
          @click="switchTab('schedules')"
        >
          <Calendar :size="16" />
          <span>2. 排课日程与调度</span>
          <span class="relation-tag tag-1-n">1:N</span>
          <span class="tab-count">{{ formData.course_schedule?.length || 0 }} 节</span>
        </button>

        <button
          class="tab-nav-btn"
          :class="{ active: activeTab === 'roster' }"
          @click="switchTab('roster')"
        >
          <Users :size="16" />
          <span>3. 选课名单与成绩管理册</span>
          <span class="relation-tag tag-1-n">1:N</span>
          <span class="tab-count">{{ formData.enrolled_students?.length || 0 }} 人</span>
        </button>

        <button
          class="tab-nav-btn"
          :class="{ active: activeTab === 'teacher' }"
          @click="switchTab('teacher')"
        >
          <UserCheck :size="16" />
          <span>4. 授课教师团队</span>
          <span class="relation-tag tag-n-1">N:1</span>
        </button>
      </div>
    </div>

    <!-- Scenario 1: Course Info & Syllabus -->
    <div v-if="activeTab === 'syllabus'" class="scenario-pane animate-fade-in">
      <div class="section-card glass-card">
        <h3 class="sec-heading">课程核心基础属性</h3>
        <div class="form-grid">
          <div class="form-item">
            <label class="form-label">课程代码</label>
            <input v-model="formData.course.course_code" type="text" class="input-control" disabled />
          </div>
          <div class="form-item">
            <label class="form-label">课程名称</label>
            <input
              v-model="formData.course.course_name"
              type="text"
              class="input-control"
              :disabled="roleStore.currentRoleId === 3"
            />
          </div>
          <div class="form-item">
            <label class="form-label">课程学分</label>
            <input
              v-model.number="formData.course.credit"
              type="number"
              step="0.5"
              class="input-control"
              :disabled="roleStore.currentRoleId === 3"
            />
          </div>
          <div class="form-item">
            <label class="form-label">计划学时</label>
            <input
              v-model.number="formData.course.hours"
              type="number"
              class="input-control"
              :disabled="roleStore.currentRoleId === 3"
            />
          </div>
        </div>
      </div>

      <div class="section-card glass-card">
        <h3 class="sec-heading">教学大纲与考核评价标准</h3>
        <div class="form-column">
          <div class="form-item">
            <label class="form-label">先修课程与知识要求</label>
            <input
              v-model="formData.course_syllabus.prerequisite"
              type="text"
              class="input-control"
              :disabled="roleStore.currentRoleId === 3"
            />
          </div>
          <div class="form-item">
            <label class="form-label">课程培养目标</label>
            <textarea
              v-model="formData.course_syllabus.target"
              rows="2"
              class="input-control textarea-ctrl"
              :disabled="roleStore.currentRoleId === 3"
            ></textarea>
          </div>
          <div class="form-item">
            <label class="form-label">教学大纲主要内容</label>
            <textarea
              v-model="formData.course_syllabus.syllabus_content"
              rows="4"
              class="input-control textarea-ctrl"
              :disabled="roleStore.currentRoleId === 3"
            ></textarea>
          </div>
          <div class="form-item">
            <label class="form-label">考核与评分标准构成</label>
            <input
              v-model="formData.course_syllabus.exam_mode"
              type="text"
              class="input-control"
              :disabled="roleStore.currentRoleId === 3"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- Scenario 2: Schedule Timetable -->
    <div v-if="activeTab === 'schedules'" class="scenario-pane animate-fade-in">
      <div class="section-card glass-card table-card-flush">
        <div class="pane-header-row">
          <h3 class="sec-heading">多节次排课日程调度</h3>
          <button
            v-if="canEditSchedule"
            class="btn btn-outline btn-sm"
            @click="addSchedule"
          >
            <Plus :size="14" />
            <span>新增排课时段</span>
          </button>
        </div>

        <table class="modern-table schedule-grid-table">
          <thead>
            <tr class="th-header-row">
              <th style="width: 50px;" class="text-center">序号</th>
              <th style="width: 140px;" class="text-center">上课星期</th>
              <th style="width: 180px;" class="text-center">上课节次时间</th>
              <th style="width: 150px;" class="text-center">教学周次</th>
              <th class="text-left">授课教室 / 实验室</th>
              <th v-if="canEditSchedule" style="width: 80px;" class="text-center">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(sch, sIdx) in formData.course_schedule" :key="sch.id || sIdx">
              <td class="text-center text-dim font-mono text-xs">{{ sIdx + 1 }}</td>
              <td class="text-center">
                <select
                  v-model="sch.day_of_week"
                  class="schedule-select-aligned"
                  :disabled="!canEditSchedule"
                >
                  <option value="周一">周一</option>
                  <option value="周二">周二</option>
                  <option value="周三">周三</option>
                  <option value="周四">周四</option>
                  <option value="周五">周五</option>
                  <option value="周六">周六</option>
                  <option value="周日">周日</option>
                </select>
              </td>
              <td class="text-center">
                <input
                  v-model="sch.time_slot"
                  type="text"
                  placeholder="如: 14:00 - 15:40"
                  class="schedule-input-aligned"
                  :disabled="!canEditSchedule"
                />
              </td>
              <td class="text-center">
                <input
                  v-model="sch.weeks"
                  type="text"
                  placeholder="如: 1-16周"
                  class="schedule-input-aligned"
                  :disabled="!canEditSchedule"
                />
              </td>
              <td class="text-left">
                <div class="room-input-box">
                  <MapPin :size="14" class="room-icon" />
                  <input
                    v-model="sch.classroom"
                    type="text"
                    placeholder="如: 计算机实验大楼 302"
                    class="schedule-room-input"
                    :disabled="!canEditSchedule"
                  />
                </div>
              </td>
              <td v-if="canEditSchedule" class="text-center">
                <button class="delete-btn" title="删除排课时段" @click="removeSchedule(sIdx)">
                  <Trash2 :size="14" />
                </button>
              </td>
            </tr>
            <tr v-if="formData.course_schedule.length === 0">
              <td :colspan="canEditSchedule ? 6 : 5" class="text-center empty-sub">
                暂未安排上课时间与教室
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Scenario 3: Enrolled Students Roster & Score Entry -->
    <div v-if="activeTab === 'roster'" class="scenario-pane animate-fade-in">
      <div class="section-card glass-card table-card-flush">
        <div class="pane-header-row">
          <h3 class="sec-heading">选课学生成绩管理册</h3>
        </div>

        <!-- Aligned Student Roster Table with In-Header Sorters & Filter Popover -->
        <table class="modern-table roster-table">
          <thead>
            <tr class="th-header-row">
              <th style="width: 50px;" class="text-center">序号</th>

              <!-- 学号 -->
              <th style="width: 140px;" class="text-left">
                <div class="th-content-wrapper">
                  <span>学号</span>
                  <div class="th-icons-group">
                    <button
                      class="th-icon-btn"
                      :class="{ active: Boolean(rosterFilters.student_no), open: activeRosterFilterCol === 'student_no' }"
                      title="按学号筛选"
                      @click.stop="toggleRosterFilter('student_no')"
                    >
                      <Filter :size="13" />
                    </button>
                    <!-- Popover -->
                    <div v-if="activeRosterFilterCol === 'student_no'" class="th-filter-popover" @click.stop>
                      <div class="popover-header">
                        <span class="popover-title">筛选学号</span>
                        <button class="popover-close-btn" @click="activeRosterFilterCol = null"><X :size="12" /></button>
                      </div>
                      <div class="popover-body">
                        <input v-model="tempRosterFilterVal" type="text" placeholder="输入学号..." class="popover-input" autofocus />
                      </div>
                      <div class="popover-footer">
                        <button class="popover-btn btn-reset" @click="resetRosterFilter('student_no')">重置</button>
                        <button class="popover-btn btn-confirm" @click="applyRosterFilter('student_no')">确定</button>
                      </div>
                    </div>
                  </div>
                </div>
              </th>

              <!-- 姓名 -->
              <th style="width: 130px;" class="text-left">
                <div class="th-content-wrapper">
                  <span>学生姓名</span>
                  <div class="th-icons-group">
                    <button
                      class="th-icon-btn"
                      :class="{ active: Boolean(rosterFilters.name), open: activeRosterFilterCol === 'name' }"
                      title="按姓名筛选"
                      @click.stop="toggleRosterFilter('name')"
                    >
                      <Filter :size="13" />
                    </button>
                    <!-- Popover -->
                    <div v-if="activeRosterFilterCol === 'name'" class="th-filter-popover" @click.stop>
                      <div class="popover-header">
                        <span class="popover-title">筛选姓名</span>
                        <button class="popover-close-btn" @click="activeRosterFilterCol = null"><X :size="12" /></button>
                      </div>
                      <div class="popover-body">
                        <input v-model="tempRosterFilterVal" type="text" placeholder="输入姓名..." class="popover-input" autofocus />
                      </div>
                      <div class="popover-footer">
                        <button class="popover-btn btn-reset" @click="resetRosterFilter('name')">重置</button>
                        <button class="popover-btn btn-confirm" @click="applyRosterFilter('name')">确定</button>
                      </div>
                    </div>
                  </div>
                </div>
              </th>

              <!-- 班级 -->
              <th class="text-left">
                <div class="th-content-wrapper">
                  <span>所属学籍班级</span>
                  <div class="th-icons-group">
                    <button
                      class="th-icon-btn"
                      :class="{ active: Boolean(rosterFilters.clazz_name), open: activeRosterFilterCol === 'clazz_name' }"
                      title="按班级筛选"
                      @click.stop="toggleRosterFilter('clazz_name')"
                    >
                      <Filter :size="13" />
                    </button>
                    <!-- Popover -->
                    <div v-if="activeRosterFilterCol === 'clazz_name'" class="th-filter-popover" @click.stop>
                      <div class="popover-header">
                        <span class="popover-title">筛选班级</span>
                        <button class="popover-close-btn" @click="activeRosterFilterCol = null"><X :size="12" /></button>
                      </div>
                      <div class="popover-body">
                        <input v-model="tempRosterFilterVal" type="text" placeholder="输入班级..." class="popover-input" autofocus />
                      </div>
                      <div class="popover-footer">
                        <button class="popover-btn btn-reset" @click="resetRosterFilter('clazz_name')">重置</button>
                        <button class="popover-btn btn-confirm" @click="applyRosterFilter('clazz_name')">确定</button>
                      </div>
                    </div>
                  </div>
                </div>
              </th>

              <!-- 平时考核 -->
              <th style="width: 140px;" class="text-center">
                <div class="th-content-wrapper justify-center">
                  <span>平时考核 (40%)</span>
                  <button class="th-icon-btn" title="按平时成绩排序" @click="toggleRosterSort('regular_score')">
                    <ArrowUpDown :size="13" />
                  </button>
                </div>
              </th>

              <!-- 期末考核 -->
              <th style="width: 140px;" class="text-center">
                <div class="th-content-wrapper justify-center">
                  <span>期末考核 (60%)</span>
                  <button class="th-icon-btn" title="按期末成绩排序" @click="toggleRosterSort('final_score')">
                    <ArrowUpDown :size="13" />
                  </button>
                </div>
              </th>

              <!-- 综合总评 -->
              <th style="width: 110px;" class="text-center">
                <div class="th-content-wrapper justify-center">
                  <span>综合总评</span>
                  <button class="th-icon-btn" title="按总评分数排序" @click="toggleRosterSort('total_score')">
                    <ArrowUpDown :size="13" />
                  </button>
                </div>
              </th>

              <!-- 考核等级 -->
              <th style="width: 130px;" class="text-center">
                <div class="th-content-wrapper justify-center">
                  <span>考核等级</span>
                  <div class="th-icons-group">
                    <button
                      class="th-icon-btn"
                      :class="{ active: Boolean(rosterFilters.grade), open: activeRosterFilterCol === 'grade' }"
                      title="按等级筛选"
                      @click.stop="toggleRosterFilter('grade')"
                    >
                      <Filter :size="13" />
                    </button>
                    <!-- Popover -->
                    <div v-if="activeRosterFilterCol === 'grade'" class="th-filter-popover" @click.stop>
                      <div class="popover-header">
                        <span class="popover-title">筛选考核等级</span>
                        <button class="popover-close-btn" @click="activeRosterFilterCol = null"><X :size="12" /></button>
                      </div>
                      <div class="popover-body">
                        <select v-model="tempRosterFilterVal" class="popover-select">
                          <option value="">全部等级</option>
                          <option value="A">优秀 (A)</option>
                          <option value="B">良好 (B)</option>
                          <option value="C">中等 (C)</option>
                          <option value="D">及格 (D)</option>
                          <option value="F">不及格 (F)</option>
                        </select>
                      </div>
                      <div class="popover-footer">
                        <button class="popover-btn btn-reset" @click="resetRosterFilter('grade')">重置</button>
                        <button class="popover-btn btn-confirm" @click="applyRosterFilter('grade')">确定</button>
                      </div>
                    </div>
                  </div>
                </div>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(st, sIdx) in filteredStudents"
              :key="st.student_id"
              class="roster-data-row"
            >
              <td class="text-center text-dim font-mono text-xs">{{ sIdx + 1 }}</td>
              <td class="text-left">
                <span class="student-no-code">
                  {{ st.student_no }}
                </span>
              </td>
              <td class="text-left">
                <div class="student-name-box">
                  <span class="student-avatar-dot">👨‍🎓</span>
                  <strong>{{ st.name }}</strong>
                </div>
              </td>
              <td class="text-left">
                <span class="badge badge-slate">{{ st.clazz_name }}</span>
              </td>
              <td class="text-center">
                <div class="score-input-container">
                  <input
                    v-model.number="st.regular_score"
                    type="number"
                    min="0"
                    max="100"
                    step="0.5"
                    class="score-input-aligned"
                    :class="{ 'input-disabled': !canEditRosterScore }"
                    :disabled="!canEditRosterScore"
                    @input="updateStudentScore(st)"
                  />
                  <span class="score-unit-label">分</span>
                </div>
              </td>
              <td class="text-center">
                <div class="score-input-container">
                  <input
                    v-model.number="st.final_score"
                    type="number"
                    min="0"
                    max="100"
                    step="0.5"
                    class="score-input-aligned"
                    :class="{ 'input-disabled': !canEditRosterScore }"
                    :disabled="!canEditRosterScore"
                    @input="updateStudentScore(st)"
                  />
                  <span class="score-unit-label">分</span>
                </div>
              </td>
              <td class="text-center">
                <div class="total-score-container">
                  <span
                    class="total-score-number"
                    :class="st.total_score >= 90 ? 'score-excellent' : (st.total_score < 60 ? 'score-fail' : 'score-normal')"
                  >
                    {{ st.total_score }}
                  </span>
                  <span class="score-unit-dim">分</span>
                </div>
              </td>
              <td class="text-center">
                <span class="badge" :class="getScoreGrade(st.total_score).cls">
                  {{ getScoreGrade(st.total_score).label }}
                </span>
              </td>
            </tr>
            <tr v-if="filteredStudents.length === 0">
              <td colspan="8" class="text-center empty-sub">
                <div class="empty-roster-box">
                  <Users :size="24" class="empty-icon" />
                  <span>未找到符合条件的选课学生记录</span>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Scenario 4: N:1 Teacher Profile -->
    <div v-if="activeTab === 'teacher'" class="scenario-pane animate-fade-in">
      <div class="section-card glass-card teacher-card">
        <div class="teacher-header-box">
          <div class="teacher-avatar">👨‍🏫</div>
          <div>
            <div class="teacher-title-row">
              <h3 class="teacher-name-big">{{ formData.teacher.teacher_name }}</h3>
              <span class="badge badge-emerald">{{ formData.teacher.title }}</span>
            </div>
            <p class="teacher-dept">所属学院：计算机与人工智能学院</p>
          </div>
        </div>

        <div class="teacher-details-grid">
          <div class="t-detail-item">
            <span class="t-label">电子邮箱</span>
            <span class="t-val">{{ formData.teacher.email || 'chen@university.edu.cn' }}</span>
          </div>
          <div class="t-detail-item">
            <span class="t-label">办公地点</span>
            <span class="t-val">{{ formData.teacher.office || '理学实验楼 408' }}</span>
          </div>
          <div class="t-detail-item">
            <span class="t-label">每周学生答疑时间 (Office Hours)</span>
            <span class="t-val">每周二、周四 16:00 - 17:30</span>
          </div>
          <div class="t-detail-item">
            <span class="t-label">主要研究与讲授方向</span>
            <span class="t-val">算法复杂度、高等微积分与离散结构</span>
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

/* Tabs Bar */
.scenario-tabs-card {
  padding: 8px;
}

.tabs-header-bar {
  display: flex;
  gap: 8px;
  background: var(--bg-subtle);
  padding: 4px;
  border-radius: var(--radius-lg);
}

.tab-nav-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 11px 16px;
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

.tab-nav-btn:hover {
  color: #1e293b;
  background: rgba(255, 255, 255, 0.6);
}

.tab-nav-btn.active {
  background: #ffffff;
  color: #2563eb;
  font-weight: 700;
  border-color: rgba(191, 219, 254, 0.8);
  box-shadow: 0 4px 14px rgba(37, 99, 235, 0.12), 0 1px 3px rgba(0, 0, 0, 0.05);
}

.tab-nav-btn.active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 20px;
  right: 20px;
  height: 3px;
  background: linear-gradient(90deg, #2563eb, #3b82f6);
  border-radius: 3px 3px 0 0;
}

.tab-count {
  background: #eff6ff;
  color: #2563eb;
  font-size: 11px;
  font-weight: 600;
  padding: 1px 7px;
  border-radius: var(--radius-full);
  border: 1px solid #dbeafe;
}

.scenario-pane {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.section-card {
  padding: 20px;
  background: var(--bg-surface);
}

.pane-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.sec-heading {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-main);
  margin-bottom: 4px;
}

.sec-sub {
  font-size: 12px;
  color: var(--text-muted);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  margin-top: 12px;
}

.form-column {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 12px;
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
}

.textarea-ctrl {
  resize: vertical;
}

.schedule-grid-table {
  width: 100%;
  border-collapse: collapse;
}

.schedule-grid-table th {
  font-size: 13px;
  font-weight: 600;
  color: #64748b;
  padding: 12px 16px;
  background: #f8fafc;
  border-bottom: 1px solid var(--border-subtle);
}

.schedule-grid-table td {
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-subtle);
  vertical-align: middle;
}

.schedule-select-aligned {
  width: 110px;
  padding: 6px 10px;
  font-size: 13px;
  font-weight: 500;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  background: var(--bg-surface);
  color: var(--text-main);
  outline: none;
  text-align: center;
}

.schedule-input-aligned {
  width: 140px;
  padding: 6px 10px;
  font-size: 13px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  background: var(--bg-surface);
  color: var(--text-main);
  outline: none;
  text-align: center;
}

.room-input-box {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.schedule-room-input {
  flex: 1;
  padding: 6px 10px;
  font-size: 13px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  background: var(--bg-surface);
  color: var(--text-main);
  outline: none;
}

.room-icon {
  color: #10b981;
}

.delete-btn {
  background: transparent;
  border: none;
  color: #94a3b8;
  cursor: pointer;
  padding: 4px;
}

.delete-btn:hover {
  color: #e11d48;
}

.score-box {
  font-weight: 700;
  text-align: center;
}

.empty-sub {
  padding: 30px;
  color: var(--text-dim);
}

.teacher-header-box {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border-subtle);
  margin-bottom: 16px;
}

.teacher-avatar {
  font-size: 40px;
}

.teacher-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.teacher-name-big {
  font-size: 18px;
  font-weight: 700;
}

.teacher-dept {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 2px;
}

.teacher-details-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.t-detail-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.t-label {
  font-size: 11px;
  color: var(--text-dim);
  text-transform: uppercase;
  font-weight: 600;
}

.t-val {
  font-size: 13px;
  color: var(--text-main);
  font-weight: 500;
}

/* ==========================================================================
   Roster & Score Entry Advanced Aesthetics
   ========================================================================== */

.roster-main-card {
  padding: 0;
  overflow: hidden;
}

.roster-toolbar-row {
  padding: 18px 22px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--border-subtle);
  background: var(--bg-subtle);
}

.table-card-flush {
  padding: 0 !important;
  overflow: visible;
}

.table-card-flush .pane-header-row {
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-subtle);
  background: var(--bg-subtle);
  margin-bottom: 0;
}

.table-card-flush .schedule-grid-table th {
  padding: 12px 18px;
  background: #f8fafc;
  font-size: 13px;
  color: #64748b;
  font-weight: 600;
  border-bottom: 1px solid var(--border-subtle);
}

.table-card-flush .schedule-grid-table td {
  padding: 12px 18px;
  border-bottom: 1px solid var(--border-subtle);
}

.roster-table {
  width: 100%;
  border-collapse: collapse;
  margin-top: 0;
}

.roster-table .th-header-row th {
  padding: 12px 14px;
  background: #f8fafc;
  font-size: 13px;
  color: #64748b;
  font-weight: 600;
  border-bottom: 1px solid var(--border-subtle);
  position: relative;
}

.th-content-wrapper {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.th-content-wrapper.justify-center {
  justify-content: center;
}

.th-icons-group {
  display: flex;
  align-items: center;
  gap: 4px;
  position: relative;
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
  color: #2563eb;
  background: #e2e8f0;
}

.th-icon-btn.active, .th-icon-btn.open {
  color: #2563eb;
  background: #dbeafe;
}

.th-filter-popover {
  position: absolute;
  top: 100%;
  right: 0;
  margin-top: 8px;
  width: 190px;
  background: #ffffff;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1);
  z-index: 100;
  padding: 10px;
  font-weight: normal;
  text-align: left;
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

.roster-table td {
  padding: 12px 14px;
  border-bottom: 1px solid var(--border-subtle);
  vertical-align: middle;
}

.roster-data-row:hover td {
  background: rgba(99, 102, 241, 0.04);
}

.student-no-code {
  font-family: monospace;
  font-size: 12px;
  background: var(--bg-muted);
  color: var(--primary-600);
  padding: 3px 8px;
  border-radius: 4px;
  font-weight: 600;
  display: inline-block;
}

.student-name-box {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.student-avatar-dot {
  font-size: 15px;
}

.score-input-container {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.score-input-aligned {
  width: 76px;
  padding: 6px 8px;
  font-size: 13px;
  font-weight: 700;
  text-align: center;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  background: var(--bg-surface);
  color: var(--text-main);
  outline: none;
  transition: var(--transition);
}

.score-input-aligned:focus {
  border-color: var(--primary-500);
  box-shadow: 0 0 0 2px var(--primary-glow);
}

.score-input-aligned.input-disabled {
  background: var(--bg-muted);
  color: var(--text-dim);
  border-color: transparent;
  cursor: not-allowed;
}

.score-unit-label {
  font-size: 12px;
  color: var(--text-dim);
}

.total-score-container {
  display: inline-flex;
  align-items: baseline;
  justify-content: center;
  gap: 2px;
}

.total-score-number {
  font-size: 15px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.score-excellent {
  color: #059669;
}

.score-normal {
  color: #4f46e5;
}

.score-fail {
  color: #e11d48;
}

.score-unit-dim {
  font-size: 11px;
  color: var(--text-dim);
}

.empty-roster-box {
  padding: 36px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  color: var(--text-dim);
}

.empty-icon {
  color: #cbd5e1;
}
</style>
