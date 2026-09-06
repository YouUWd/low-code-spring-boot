<script setup lang="ts">
import { ref, watch } from 'vue';
import { useRoleStore } from '../stores/roleStore';
import { engineApi } from '../api/engineApi';
import { X, Save, BookOpen, Award, User, Lock, Sparkles, Plus, Trash2 } from 'lucide-vue-next';

const props = defineProps<{
  visible: boolean;
  record: any;
}>();

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void;
  (e: 'saved'): void;
}>();

const roleStore = useRoleStore();
const activeTab = ref<'courses' | 'awards'>('courses');
const isSaving = ref(false);
const saveSuccessMessage = ref('');

// 本地可编辑的同构数据副本 (Table-First 结构)
const formData = ref<any>({
  student: {},
  clazz: {},
  student_profile: {},
  student_course: [],
  student_award: []
});

watch(
  () => props.record,
  (newVal) => {
    if (newVal) {
      const copy = JSON.parse(JSON.stringify(newVal));
      // 兼容多模块纯对象嵌套结构（如形态 A: copy['101'].student, copy['103'].student_course 等）
      const student = copy['101']?.student || copy.student || {};
      const clazz = copy['101']?.clazz || copy.clazz || {};
      const student_profile = copy['101']?.student_profile || copy.student_profile || {};
      const student_course = copy['103']?.student_course || copy.student_course || [];
      const student_award = copy['104']?.student_award || copy.student_award || [];

      formData.value = {
        ...copy,
        student,
        clazz,
        student_profile,
        student_course: Array.isArray(student_course) ? student_course : [],
        student_award: Array.isArray(student_award) ? student_award : []
      };
    }
  },
  { immediate: true, deep: true }
);

function close() {
  emit('update:visible', false);
}

function addCourse() {
  formData.value.student_course.push({
    course_name: '新开课程',
    semester: '2026-秋',
    score: 80.0
  });
}

function removeCourse(idx: number) {
  formData.value.student_course.splice(idx, 1);
}

async function handleSave() {
  isSaving.value = true;
  saveSuccessMessage.value = '';

  try {
    const payload = {
      moduleId: 101,
      tables: {
        student: formData.value.student,
        student_profile: formData.value.student_profile,
        student_course: formData.value.student_course,
        student_award: formData.value.student_award
      }
    };

    await engineApi.save(payload);

    saveSuccessMessage.value = '全景主从档案保存成功！';
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
      <!-- Top Header -->
      <div class="drawer-header">
        <div class="header-left">
          <div class="avatar-badge">{{ roleStore.currentRole.avatar }}</div>
          <div>
            <h2 class="drawer-title">
              {{ formData.student?.name || '学生' }} - 全景综合档案
            </h2>
            <span class="drawer-subtitle">
              学号: {{ formData.student?.student_no }} | 所属: {{ formData.clazz?.clazz_name }}
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
            <span>{{ isSaving ? '保存中...' : '保存全景档案' }}</span>
          </button>
          <button class="icon-btn-close" @click="close">
            <X :size="18" />
          </button>
        </div>
      </div>

      <!-- Success Toast Notification -->
      <div v-if="saveSuccessMessage" class="toast-banner badge-emerald">
        <Sparkles :size="15" />
        <span>{{ saveSuccessMessage }}</span>
      </div>

      <!-- Body Content -->
      <div class="drawer-body">
        <!-- 1. Master Card: Student Basic + 1:1 Profile -->
        <div class="section-card">
          <div class="section-title-bar">
            <div class="sec-title">
              <User :size="16" class="sec-icon" />
              <span>基本档案与 1:1 伴生扩展信息</span>
            </div>
            <span class="role-badge badge badge-slate">
              当前编辑权限: {{ roleStore.currentRole.name }}
            </span>
          </div>

          <div class="form-grid">
            <!-- 姓名 (view/edit 权限判断) -->
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

            <!-- 学号 -->
            <div class="form-item">
              <label class="form-label">
                <span>学号</span>
                <Lock :size="11" class="lock-icon" />
              </label>
              <input
                v-model="formData.student.student_no"
                type="text"
                class="input-control"
                disabled
              />
            </div>

            <!-- 所属班级 -->
            <div class="form-item">
              <label class="form-label">所属班级 (N:1)</label>
              <input
                v-model="formData.clazz.clazz_name"
                type="text"
                class="input-control"
                disabled
              />
            </div>

            <!-- 学籍状态 -->
            <div class="form-item">
              <label class="form-label">
                <span>学籍状态</span>
                <Lock v-if="!roleStore.canEdit('student', 'status')" :size="11" class="lock-icon" />
              </label>
              <select
                v-model="formData.student.status"
                class="input-control"
                :disabled="!roleStore.canEdit('student', 'status')"
              >
                <option value="在读">在读</option>
                <option value="休学">休学</option>
                <option value="毕业">毕业</option>
              </select>
            </div>

            <!-- 身份证号 (1:1 伴生表, 隐私字段, 教师视角 view=0 自动脱敏隐藏) -->
            <div class="form-item">
              <label class="form-label">
                <span>身份证号 (1:1 伴生)</span>
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

            <!-- 紧急联系电话 (1:1 伴生表, 教师视角 view=0 自动脱敏隐藏) -->
            <div class="form-item">
              <label class="form-label">
                <span>紧急联系电话 (1:1 伴生)</span>
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

        <!-- 2. 1:N Detail Tabs (Courses & Awards) -->
        <div class="section-card">
          <div class="tabs-header">
            <div class="tabs-left">
              <button
                class="tab-btn"
                :class="{ active: activeTab === 'courses' }"
                @click="activeTab = 'courses'"
              >
                <BookOpen :size="15" />
                <span>选课修读与成绩明细 (1:N)</span>
                <span class="tab-badge">{{ formData.student_course?.length || 0 }}</span>
              </button>

              <button
                class="tab-btn"
                :class="{ active: activeTab === 'awards' }"
                @click="activeTab = 'awards'"
              >
                <Award :size="15" />
                <span>荣誉表彰与奖惩记录 (1:N)</span>
                <span class="tab-badge">{{ formData.student_award?.length || 0 }}</span>
              </button>
            </div>

            <button
              v-if="activeTab === 'courses' && roleStore.canEdit('student_course', 'score')"
              class="btn btn-outline btn-sm"
              @click="addCourse"
            >
              <Plus :size="13" />
              <span>加选课程</span>
            </button>
          </div>

          <!-- Tab Content: Courses Table -->
          <div v-if="activeTab === 'courses'" class="tab-pane">
            <table class="sub-table">
              <thead>
                <tr>
                  <th>课程名称</th>
                  <th>修读学期</th>
                  <th style="width: 140px;">考核成绩</th>
                  <th v-if="roleStore.canEdit('student_course', 'score')" style="width: 60px;">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(c, idx) in formData.student_course" :key="c.id || idx">
                  <td>
                    <input
                      v-model="c.course_name"
                      type="text"
                      class="sub-input"
                      :disabled="!roleStore.canEdit('student_course', 'score')"
                    />
                  </td>
                  <td>
                    <input
                      v-model="c.semester"
                      type="text"
                      class="sub-input"
                      :disabled="!roleStore.canEdit('student_course', 'score')"
                    />
                  </td>
                  <td>
                    <div class="score-input-wrapper">
                      <input
                        v-model.number="c.score"
                        type="number"
                        step="0.5"
                        min="0"
                        max="100"
                        class="sub-input score-input"
                        :class="{ 'score-high': c.score >= 90, 'score-low': c.score < 60 }"
                        :disabled="!roleStore.canEdit('student_course', 'score')"
                      />
                      <span class="score-unit">分</span>
                    </div>
                  </td>
                  <td v-if="roleStore.canEdit('student_course', 'score')">
                    <button class="delete-btn" @click="removeCourse(idx)">
                      <Trash2 :size="14" />
                    </button>
                  </td>
                </tr>
                <tr v-if="formData.student_course.length === 0">
                  <td colspan="4" class="text-center empty-sub">暂无选课记录</td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- Tab Content: Awards List -->
          <div v-if="activeTab === 'awards'" class="tab-pane">
            <div class="awards-grid">
              <div
                v-for="(award, idx) in formData.student_award"
                :key="award.id || idx"
                class="award-card"
              >
                <div class="award-header">
                  <Award :size="18" class="award-icon" />
                  <span class="badge badge-amber">{{ award.level || '校级' }}</span>
                </div>
                <h4 class="award-title">{{ award.award_name }}</h4>
                <span class="award-date">获奖时间: {{ award.award_date }}</span>
              </div>
              <div v-if="formData.student_award.length === 0" class="empty-sub text-center w-full">
                暂无荣誉获奖记录
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
  width: 760px;
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
  gap: 20px;
}

.section-card {
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  padding: 16px 20px;
}

.section-title-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border-subtle);
}

.sec-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-main);
}

.sec-icon {
  color: var(--primary-600);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
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

/* Tabs */
.tabs-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--border-subtle);
  padding-bottom: 10px;
  margin-bottom: 14px;
}

.tabs-left {
  display: flex;
  gap: 8px;
}

.tab-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  font-size: 13px;
  font-weight: 500;
  border-radius: var(--radius-md);
  background: transparent;
  border: 1px solid transparent;
  cursor: pointer;
  color: var(--text-muted);
  transition: var(--transition);
}

.tab-btn.active {
  background: var(--primary-50);
  color: var(--primary-600);
  border-color: var(--primary-100);
  font-weight: 600;
}

.tab-badge {
  background: var(--bg-muted);
  font-size: 11px;
  padding: 1px 6px;
  border-radius: var(--radius-full);
}

.sub-table {
  width: 100%;
  border-collapse: collapse;
}

.sub-table th {
  font-size: 12px;
  color: var(--text-muted);
  padding: 8px 12px;
  background: var(--bg-muted);
  border-radius: var(--radius-sm);
  text-align: left;
}

.sub-table td {
  padding: 8px 12px;
  border-bottom: 1px solid var(--border-subtle);
}

.sub-input {
  width: 100%;
  padding: 6px 10px;
  font-size: 13px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  outline: none;
}

.score-input-wrapper {
  display: flex;
  align-items: center;
  gap: 4px;
}

.score-input {
  font-weight: 700;
  text-align: center;
}

.score-high {
  color: #059669;
}

.score-low {
  color: #e11d48;
}

.delete-btn {
  background: transparent;
  border: none;
  color: #94a3b8;
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  transition: var(--transition);
}

.delete-btn:hover {
  color: #e11d48;
  background: #ffe4e6;
}

.empty-sub {
  padding: 24px;
  color: var(--text-dim);
  font-size: 13px;
}

/* Awards */
.awards-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.award-card {
  padding: 12px 14px;
  background: var(--bg-subtle);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.award-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.award-icon {
  color: #f59e0b;
}

.award-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-main);
}

.award-date {
  font-size: 11px;
  color: var(--text-dim);
}
</style>
