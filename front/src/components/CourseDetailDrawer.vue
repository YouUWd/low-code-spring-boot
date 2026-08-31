<script setup lang="ts">
import { ref, watch, computed } from 'vue';
import { useRoleStore } from '../stores/roleStore';
import { engineApi } from '../api/engineApi';
import {
  X,
  Save,
  BookOpen,
  Calendar,
  Users,
  UserCheck,
  Plus,
  Trash2,
  Clock,
  MapPin,
  Sparkles,
  Lock,
  GraduationCap
} from 'lucide-vue-next';

const props = defineProps<{
  visible: boolean;
  record: any;
}>();

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void;
  (e: 'saved'): void;
}>();

const roleStore = useRoleStore();
const activeTab = ref<'syllabus' | 'schedules' | 'roster' | 'teacher'>('syllabus');
const isSaving = ref(false);
const saveSuccessMessage = ref('');

// 本地可编辑数据副本 (Table-First 架构)
const formData = ref<any>({
  course: {},
  teacher: {},
  course_syllabus: {},
  course_schedule: [],
  enrolled_students: []
});

watch(
  () => props.record,
  (newVal) => {
    if (newVal) {
      formData.value = JSON.parse(JSON.stringify(newVal));
      if (!formData.value.course) formData.value.course = {};
      if (!formData.value.teacher) formData.value.teacher = {};
      if (!formData.value.course_syllabus) formData.value.course_syllabus = {};
      if (!formData.value.course_schedule) formData.value.course_schedule = [];
      if (!formData.value.enrolled_students) formData.value.enrolled_students = [];
    }
  },
  { immediate: true, deep: true }
);

function close() {
  emit('update:visible', false);
}

// 排课场景：添加排课时段
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

// 选课花名册场景：平时分与期末分变动时自动计算总评 (40% + 60%)
function updateStudentScore(student: any) {
  const reg = Number(student.regular_score) || 0;
  const fin = Number(student.final_score) || 0;
  student.total_score = Math.round((reg * 0.4 + fin * 0.6) * 10) / 10;
}

// 是否具备排课与成绩编辑权限
const canEditSchedule = computed(() => {
  return roleStore.currentRoleId === 1 || roleStore.currentRoleId === 2; // 管理员或教师
});

const canEditRosterScore = computed(() => {
  return roleStore.currentRoleId === 1 || roleStore.currentRoleId === 2; // 教师/管理员可录入成绩
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

    saveSuccessMessage.value = '课程排课与全景档案保存成功！';
    setTimeout(() => {
      saveSuccessMessage.value = '';
      emit('saved');
      close();
    }, 1000);
  } catch (err: any) {
    alert('保存失败: ' + err.message);
  } finally {
    isSaving.value = false;
  }
}
</script>

<template>
  <div v-if="visible" class="drawer-overlay" @click.self="close">
    <div class="drawer-container animate-fade-in">
      <!-- Header -->
      <div class="drawer-header">
        <div class="header-left">
          <div class="avatar-badge">📚</div>
          <div>
            <div class="title-row">
              <h2 class="drawer-title">
                {{ formData.course?.course_name || '课程' }} ({{ formData.course?.course_code }})
              </h2>
              <span class="badge badge-emerald">{{ formData.course?.credit }} 学分 / {{ formData.course?.hours || 48 }} 学时</span>
            </div>
            <span class="drawer-subtitle">
              开课学期: {{ formData.course?.semester }} | 主讲教师: {{ formData.teacher?.teacher_name }} ({{ formData.teacher?.title }})
            </span>
          </div>
        </div>

        <div class="header-right">
          <button
            v-if="roleStore.currentRoleId !== 3"
            class="btn btn-primary"
            :disabled="isSaving"
            @click="handleSave"
          >
            <Save :size="15" />
            <span>{{ isSaving ? '保存中...' : '保存排课全景' }}</span>
          </button>
          <button class="icon-btn-close" @click="close">
            <X :size="18" />
          </button>
        </div>
      </div>

      <!-- Toast -->
      <div v-if="saveSuccessMessage" class="toast-banner badge-emerald">
        <Sparkles :size="15" />
        <span>{{ saveSuccessMessage }}</span>
      </div>

      <!-- Body Content -->
      <div class="drawer-body">
        <!-- Scenario Tabs Bar -->
        <div class="tabs-nav-bar glass-card">
          <button
            class="tab-nav-btn"
            :class="{ active: activeTab === 'syllabus' }"
            @click="activeTab = 'syllabus'"
          >
            <BookOpen :size="15" />
            <span>1. 基础信息与大纲 (1:1)</span>
          </button>

          <button
            class="tab-nav-btn"
            :class="{ active: activeTab === 'schedules' }"
            @click="activeTab = 'schedules'"
          >
            <Calendar :size="15" />
            <span>2. 排课日程与调度 (1:N)</span>
            <span class="tab-count">{{ formData.course_schedule?.length || 0 }}</span>
          </button>

          <button
            class="tab-nav-btn"
            :class="{ active: activeTab === 'roster' }"
            @click="activeTab = 'roster'"
          >
            <Users :size="15" />
            <span>3. 选课名单与成绩册 (1:N)</span>
            <span class="tab-count">{{ formData.enrolled_students?.length || 0 }}</span>
          </button>

          <button
            class="tab-nav-btn"
            :class="{ active: activeTab === 'teacher' }"
            @click="activeTab = 'teacher'"
          >
            <UserCheck :size="15" />
            <span>4. 授课教师团队 (N:1)</span>
          </button>
        </div>

        <!-- Scenario 1: Course Info & 1:1 Syllabus -->
        <div v-if="activeTab === 'syllabus'" class="scenario-pane animate-fade-in">
          <div class="section-card">
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

          <div class="section-card">
            <h3 class="sec-heading">1:1 伴生教学大纲与考核评价标准</h3>
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
                  rows="3"
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

        <!-- Scenario 2: 1:N Schedule Timetable & Adjustments -->
        <div v-if="activeTab === 'schedules'" class="scenario-pane animate-fade-in">
          <div class="section-card">
            <div class="pane-header-row">
              <div>
                <h3 class="sec-heading">多节次排课日程明细 (1:N 拓扑)</h3>
                <p class="sec-sub">支持按星期、节次与教学周次进行排课与教室冲突检测。</p>
              </div>
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
                <tr>
                  <th style="width: 110px;">上课星期</th>
                  <th style="width: 160px;">上课节次时间</th>
                  <th style="width: 140px;">教学周次</th>
                  <th>授课教室 / 实验室</th>
                  <th v-if="canEditSchedule" style="width: 60px;">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(sch, sIdx) in formData.course_schedule" :key="sch.id || sIdx">
                  <td>
                    <select
                      v-model="sch.day_of_week"
                      class="sub-input"
                      :disabled="!canEditSchedule"
                    >
                      <option value="周一">周一</option>
                      <option value="周二">周二</option>
                      <option value="周三">周三</option>
                      <option value="周四">周四</option>
                      <option value="周五">周五</option>
                      <option value="周六">周六</option>
                    </select>
                  </td>
                  <td>
                    <input
                      v-model="sch.time_slot"
                      type="text"
                      class="sub-input"
                      :disabled="!canEditSchedule"
                    />
                  </td>
                  <td>
                    <input
                      v-model="sch.weeks"
                      type="text"
                      class="sub-input"
                      :disabled="!canEditSchedule"
                    />
                  </td>
                  <td>
                    <div class="room-input-box">
                      <MapPin :size="13" class="room-icon" />
                      <input
                        v-model="sch.classroom"
                        type="text"
                        class="sub-input"
                        :disabled="!canEditSchedule"
                      />
                    </div>
                  </td>
                  <td v-if="canEditSchedule">
                    <button class="delete-btn" @click="removeSchedule(sIdx)">
                      <Trash2 :size="14" />
                    </button>
                  </td>
                </tr>
                <tr v-if="formData.course_schedule.length === 0">
                  <td colspan="5" class="text-center empty-sub">暂未安排上课时间与教室</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- Scenario 3: 1:N Enrolled Students Roster & Score Entry -->
        <div v-if="activeTab === 'roster'" class="scenario-pane animate-fade-in">
          <div class="section-card">
            <div class="pane-header-row">
              <div>
                <h3 class="sec-heading">选课学生花名册与成绩综合录入 (1:N 关联)</h3>
                <p class="sec-sub">
                  <template v-if="roleStore.currentRoleId === 2">
                    🎓 <strong>教师独家权限</strong>：直接录入【平时分】与【期末分】，系统实时合成总评成绩。
                  </template>
                  <template v-else-if="roleStore.currentRoleId === 1">
                    👑 <strong>管理员权限</strong>：全览与调分。
                  </template>
                  <template v-else>
                    👤 <strong>学生视角</strong>：选课名册已脱敏。
                  </template>
                </p>
              </div>
            </div>

            <!-- Student Roster Table -->
            <table class="modern-table">
              <thead>
                <tr>
                  <th style="width: 120px;">学号</th>
                  <th style="width: 100px;">姓名</th>
                  <th>所属班级</th>
                  <th style="width: 110px;">平时成绩(40%)</th>
                  <th style="width: 110px;">期末成绩(60%)</th>
                  <th style="width: 120px;">综合总评</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="st in formData.enrolled_students" :key="st.student_id">
                  <td><code>{{ st.student_no }}</code></td>
                  <td><strong>{{ st.name }}</strong></td>
                  <td>{{ st.clazz_name }}</td>
                  <td>
                    <input
                      v-model.number="st.regular_score"
                      type="number"
                      min="0"
                      max="100"
                      class="sub-input score-box"
                      :disabled="!canEditRosterScore"
                      @input="updateStudentScore(st)"
                    />
                  </td>
                  <td>
                    <input
                      v-model.number="st.final_score"
                      type="number"
                      min="0"
                      max="100"
                      class="sub-input score-box"
                      :disabled="!canEditRosterScore"
                      @input="updateStudentScore(st)"
                    />
                  </td>
                  <td>
                    <span
                      class="badge font-bold"
                      :class="st.total_score >= 90 ? 'badge-emerald' : (st.total_score < 60 ? 'badge-rose' : 'badge-indigo')"
                    >
                      {{ st.total_score }} 分
                    </span>
                  </td>
                </tr>
                <tr v-if="formData.enrolled_students.length === 0">
                  <td colspan="6" class="text-center empty-sub">暂无选课学生记录</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- Scenario 4: N:1 Teacher Profile -->
        <div v-if="activeTab === 'teacher'" class="scenario-pane animate-fade-in">
          <div class="section-card teacher-card">
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
    </div>
  </div>
</template>

<style scoped>
.drawer-overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  backdrop-filter: blur(4px);
  z-index: 500;
  display: flex;
  justify-content: flex-end;
}

.drawer-container {
  width: 820px;
  max-width: 95vw;
  height: 100vh;
  background: var(--bg-surface);
  box-shadow: -10px 0 30px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
}

.drawer-header {
  padding: 16px 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--border-subtle);
  background: var(--bg-subtle);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.avatar-badge {
  font-size: 24px;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.drawer-title {
  font-size: 17px;
  font-weight: 700;
  color: var(--text-main);
}

.drawer-subtitle {
  font-size: 12px;
  color: var(--text-muted);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.icon-btn-close {
  background: transparent;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  padding: 6px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: var(--transition);
}

.icon-btn-close:hover {
  background: var(--bg-muted);
  color: var(--text-main);
}

.toast-banner {
  padding: 8px 24px;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.drawer-body {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* Tabs Nav Bar */
.tabs-nav-bar {
  display: flex;
  gap: 4px;
  padding: 6px;
  background: var(--bg-muted);
  border-radius: var(--radius-md);
  border: 1px solid var(--border-subtle);
}

.tab-nav-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 10px;
  font-size: 12px;
  font-weight: 500;
  color: var(--text-muted);
  border-radius: var(--radius-sm);
  background: transparent;
  border: none;
  cursor: pointer;
  transition: var(--transition);
}

.tab-nav-btn:hover {
  color: var(--text-main);
}

.tab-nav-btn.active {
  background: var(--bg-surface);
  color: var(--primary-600);
  font-weight: 600;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.05);
}

.tab-count {
  background: var(--primary-50);
  color: var(--primary-600);
  font-size: 11px;
  padding: 1px 6px;
  border-radius: var(--radius-full);
}

/* Panes */
.scenario-pane {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.section-card {
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  padding: 16px 20px;
}

.pane-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.sec-heading {
  font-size: 14px;
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
  gap: 14px;
  margin-top: 10px;
}

.form-column {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 10px;
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

/* Schedule Table */
.schedule-grid-table th {
  font-size: 12px;
  padding: 8px 12px;
  background: var(--bg-muted);
}

.schedule-grid-table td {
  padding: 8px 12px;
}

.sub-input {
  width: 100%;
  padding: 6px 10px;
  font-size: 13px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  outline: none;
}

.room-input-box {
  display: flex;
  align-items: center;
  gap: 6px;
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
  border-radius: 4px;
}

.delete-btn:hover {
  color: #e11d48;
  background: #ffe4e6;
}

.score-box {
  font-weight: 700;
  text-align: center;
}

.empty-sub {
  padding: 24px;
  color: var(--text-dim);
  font-size: 13px;
}

/* Teacher Profile */
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
</style>
