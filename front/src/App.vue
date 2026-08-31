<script setup lang="ts">
import HeaderNavbar from './components/HeaderNavbar.vue';
</script>

<template>
  <div class="app-layout">
    <!-- Top Header & Role Switcher -->
    <HeaderNavbar />

    <!-- Main Workspace (Router View) -->
    <main class="main-content">
      <div class="content-container">
        <router-view />
      </div>
    </main>

    <!-- Footer -->
    <footer class="app-footer">
      <div class="footer-container">
        <span>JDEC Platform © 2026 低代码企业级动态数据引擎与模块拓扑平台</span>
        <span class="tech-stack-tag">Spring Boot 3.2.5 • jOOQ • MySQL 8 • Vue 3 + Vue Router</span>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.app-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.main-content {
  flex: 1;
  padding: 24px 0;
}

.content-container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 24px;
}

.app-footer {
  border-top: 1px solid var(--border-subtle);
  background: var(--bg-surface);
  padding: 16px 0;
  margin-top: auto;
}

.footer-container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: var(--text-muted);
}

.tech-stack-tag {
  color: var(--text-dim);
}
</style>
