<script setup lang="ts">
import { ref, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useRoleStore } from '../stores/roleStore';
import { Layers, ShieldCheck, ChevronDown, Check, GraduationCap, Users, Settings, FileCode } from 'lucide-vue-next';

const route = useRoute();
const router = useRouter();
const roleStore = useRoleStore();
const isDropdownOpen = ref(false);

const isStudentModule = computed(() => route.path.startsWith('/students') || route.path === '/');
const isCourseModule = computed(() => route.path.startsWith('/courses'));
const isDesignerModule = computed(() => route.path.startsWith('/modules/designer') || route.path.startsWith('/modules/run'));

function goToStudents() {
  router.push('/students');
}

function goToCourses() {
  router.push('/courses');
}

function goToDesigner() {
  router.push('/modules/designer/101');
}

function handleSelectRole(roleId: number) {
  roleStore.switchRole(roleId);
  isDropdownOpen.value = false;
}
</script>

<template>
  <header class="header-nav">
    <div class="header-container">
      <!-- Left: Logo & Brand -->
      <div class="brand-section" style="cursor: pointer;" @click="goToStudents">
        <div class="logo-box">
          <Layers class="logo-icon" :size="22" />
        </div>
        <div class="brand-text">
          <h1 class="brand-title">JDEC Platform</h1>
          <span class="brand-subtitle">低代码动态数据引擎</span>
        </div>
      </div>

      <!-- Center: Module Nav Switcher -->
      <nav class="module-nav">
        <button
          class="nav-tab"
          :class="{ active: isStudentModule }"
          @click="goToStudents"
        >
          <GraduationCap :size="16" />
          <span>学生综合档案</span>
        </button>
        <button
          class="nav-tab"
          :class="{ active: isCourseModule }"
          @click="goToCourses"
        >
          <Users :size="16" />
          <span>课程排课中心</span>
        </button>
        <button
          class="nav-tab nav-tab-highlight"
          :class="{ active: isDesignerModule }"
          @click="goToDesigner"
        >
          <Settings :size="16" />
          <span>🛠️ 模块配置中心</span>
        </button>
      </nav>

      <!-- Right: Role Switcher (Highlighted) -->
      <div class="role-switcher-container">
        <div class="role-pill" @click="isDropdownOpen = !isDropdownOpen">
          <div class="role-avatar-badge">{{ roleStore.currentRole.avatar }}</div>
          <div class="role-info">
            <span class="role-label">当前角色</span>
            <span class="role-name">{{ roleStore.currentRole.name }}</span>
          </div>
          <ChevronDown :size="14" class="arrow-icon" :class="{ rotated: isDropdownOpen }" />
        </div>

        <!-- Role Dropdown Menu -->
        <transition name="dropdown">
          <div v-if="isDropdownOpen" class="role-dropdown-menu">
            <div class="menu-header">
              <ShieldCheck :size="15" class="header-icon" />
              <span>切换权限身份体验差异</span>
            </div>
            
            <div class="role-list">
              <div
                v-for="role in roleStore.availableRoles"
                :key="role.id"
                class="role-option"
                :class="{ selected: role.id === roleStore.currentRoleId }"
                @click="handleSelectRole(role.id)"
              >
                <div class="role-opt-left">
                  <span class="opt-avatar">{{ role.avatar }}</span>
                  <div class="opt-text">
                    <div class="opt-title">
                      <strong>{{ role.name }}</strong>
                      <span class="opt-tag">{{ role.tag }}</span>
                    </div>
                    <p class="opt-desc">{{ role.desc }}</p>
                  </div>
                </div>
                <Check v-if="role.id === roleStore.currentRoleId" :size="16" class="check-icon" />
              </div>
            </div>
          </div>
        </transition>
      </div>
    </div>
  </header>
</template>

<style scoped>
.header-nav {
  position: sticky;
  top: 0;
  z-index: 100;
  height: var(--header-height);
  background: var(--bg-surface-glass);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-bottom: 1px solid var(--border-subtle);
}

.header-container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 24px;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.brand-section {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-box {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, var(--primary-600), var(--primary-500));
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  box-shadow: 0 4px 12px var(--primary-glow);
}

.brand-title {
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 700;
  color: var(--text-main);
  line-height: 1.1;
  letter-spacing: -0.02em;
}

.brand-subtitle {
  font-size: 11px;
  font-weight: 500;
  color: var(--text-muted);
}

/* Nav Tabs */
.module-nav {
  display: flex;
  gap: 6px;
  background: var(--bg-muted);
  padding: 4px;
  border-radius: var(--radius-full);
  border: 1px solid var(--border-subtle);
}

.nav-tab {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 16px;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-muted);
  border-radius: var(--radius-full);
  background: transparent;
  border: none;
  cursor: pointer;
  transition: var(--transition);
}

.nav-tab:hover {
  color: var(--text-main);
}

.nav-tab.active {
  background: #ffffff;
  color: #2563eb;
  font-weight: 700;
  box-shadow: 0 2px 10px rgba(37, 99, 235, 0.12), 0 1px 3px rgba(0, 0, 0, 0.05);
}

.nav-tab-highlight {
  color: #4f46e5;
}

.nav-tab-highlight.active {
  color: #4338ca;
}

/* Right Role Switcher */
.role-switcher-container {
  position: relative;
}

.role-pill {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 14px;
  border-radius: var(--radius-full);
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  cursor: pointer;
  box-shadow: var(--shadow-sm);
  transition: var(--transition);
}

.role-pill:hover {
  border-color: var(--primary-500);
  box-shadow: 0 0 0 3px var(--primary-glow);
}

.role-avatar-badge {
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.role-info {
  display: flex;
  flex-direction: column;
  text-align: left;
}

.role-label {
  font-size: 10px;
  color: var(--text-dim);
  text-transform: uppercase;
  font-weight: 600;
}

.role-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--primary-600);
}

.arrow-icon {
  color: var(--text-muted);
  transition: transform 0.2s ease;
}

.arrow-icon.rotated {
  transform: rotate(180deg);
}

/* Dropdown */
.role-dropdown-menu {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  width: 360px;
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  padding: 8px;
  z-index: 200;
}

.menu-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-muted);
  border-bottom: 1px solid var(--border-subtle);
  margin-bottom: 4px;
}

.header-icon {
  color: var(--primary-500);
}

.role-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.role-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: var(--transition);
}

.role-option:hover {
  background: var(--primary-50);
}

.role-option.selected {
  background: var(--primary-50);
  border: 1px solid var(--primary-100);
}

.role-opt-left {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.opt-avatar {
  font-size: 20px;
  margin-top: 2px;
}

.opt-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.opt-title strong {
  font-size: 13px;
  color: var(--text-main);
}

.opt-tag {
  font-size: 11px;
  color: var(--primary-600);
  font-weight: 500;
}

.opt-desc {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 2px;
  line-height: 1.4;
}

.check-icon {
  color: var(--primary-600);
}

/* Animations */
.dropdown-enter-active, .dropdown-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.dropdown-enter-from, .dropdown-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
