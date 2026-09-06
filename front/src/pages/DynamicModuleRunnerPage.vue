<script setup lang="ts">
import { ref, watch, onMounted, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { configApi, type ModuleInfo, type SysModuleMetaComplete } from '../api/configApi';
import { 
  FileCode, Layers, Settings, Table, GitFork, ListChecks, 
  Check, Copy, Sparkles, RefreshCw, ArrowUpRight
} from 'lucide-vue-next';

const route = useRoute();
const router = useRouter();

const moduleId = computed(() => Number(route.params.moduleId) || 101);
const moduleList = ref<ModuleInfo[]>([]);

const loading = ref(false);
const configData = ref<SysModuleMetaComplete | null>(null);
const activeConfigTab = ref<'headers' | 'relations' | 'fields' | 'json'>('headers');
const copied = ref(false);

async function loadModules() {
  try {
    moduleList.value = await configApi.listModules();
  } catch (_) {}
}

async function loadConfig() {
  loading.value = true;
  try {
    const res = await configApi.getModuleComplete(moduleId.value);
    configData.value = res;
  } catch (err: any) {
    console.error('加载模块配置失败:', err);
  } finally {
    loading.value = false;
  }
}

function handleSwitchModule(newId: number) {
  router.push(`/modules/run/${newId}`);
}

function goToDesigner() {
  router.push('/modules/designer');
}

function copyJson() {
  if (!configData.value) return;
  navigator.clipboard.writeText(JSON.stringify(configData.value, null, 2)).then(() => {
    copied.value = true;
    setTimeout(() => {
      copied.value = false;
    }, 2000);
  });
}

function getSearchTypeBadge(searchType?: string) {
  switch (searchType) {
    case 'text':
      return { text: '文本模糊 (LIKE)', class: 'badge-indigo' };
    case 'singleFuzzySelect':
      return { text: '下拉单选模糊 (LIKE)', class: 'badge-emerald' };
    case 'singleSelect':
      return { text: '精确单选 (=)', class: 'badge-slate' };
    case 'multipleSelect':
      return { text: '多选集合 (IN)', class: 'badge-purple' };
    case 'between':
      return { text: '数值区间 (BETWEEN)', class: 'badge-amber' };
    case 'dateRange':
      return { text: '日期范围 (BETWEEN)', class: 'badge-cyan' };
    default:
      return { text: searchType || '不参与搜索', class: 'badge-slate' };
  }
}

watch(
  () => moduleId.value,
  () => {
    loadConfig();
  }
);

onMounted(() => {
  loadModules();
  loadConfig();
});
</script>

<template>
  <div class="module-config-page animate-fade-in">
    <!-- Top Feature Banner -->
    <div class="feature-banner glass-card">
      <div class="banner-left">
        <div class="banner-icon-box">
          <FileCode :size="26" />
        </div>
        <div>
          <div class="banner-title-row">
            <h2 class="banner-title">{{ configData?.module?.moduleName || '模块配置展示' }}</h2>
            <span class="badge badge-indigo">{{ configData?.module?.moduleCode || `MOD-${moduleId}` }}</span>
            <span class="badge badge-slate">ID: {{ moduleId }}</span>
          </div>
          <p class="banner-desc">
            模块物理主表: <code>{{ configData?.module?.primaryTable || '未配置' }}</code> • 
            父级模块 ID: <code>{{ configData?.module?.parentId ?? 0 }}</code> • 
            仅展示该模块已注册的动态元数据与系统配置
          </p>
        </div>
      </div>

      <div class="banner-right">
        <!-- Quick Module Switcher -->
        <div class="module-quick-switch">
          <span class="switch-label">查看其它模块配置:</span>
          <select
            class="form-select module-select"
            :value="moduleId"
            @change="handleSwitchModule(Number(($event.target as HTMLSelectElement).value))"
          >
            <option v-for="m in moduleList" :key="m.id" :value="m.id">
              {{ m.moduleName }} (ID: {{ m.id }})
            </option>
          </select>
        </div>

        <button class="btn btn-outline btn-sm" title="刷新配置" :disabled="loading" @click="loadConfig">
          <RefreshCw :size="14" :class="{ 'spin-icon': loading }" />
          <span>刷新</span>
        </button>

        <button class="btn btn-primary btn-sm" @click="goToDesigner">
          <Settings :size="14" />
          <span>前往设计器编辑</span>
        </button>
      </div>
    </div>

    <!-- Main Config Details Container -->
    <div class="config-container glass-card">
      <!-- Nav Tabs -->
      <div class="config-tabs-bar">
        <button
          class="cfg-tab"
          :class="{ active: activeConfigTab === 'headers' }"
          @click="activeConfigTab = 'headers'"
        >
          <Table :size="15" />
          <span>表头配置 (sys_module_header)</span>
          <span class="cfg-badge">{{ configData?.moduleHeaders?.length || 0 }}</span>
        </button>

        <button
          class="cfg-tab"
          :class="{ active: activeConfigTab === 'relations' }"
          @click="activeConfigTab = 'relations'"
        >
          <GitFork :size="15" />
          <span>主从关联拓扑 (tableRelations)</span>
          <span class="cfg-badge">{{ configData?.tableRelations?.length || 0 }}</span>
        </button>

        <button
          class="cfg-tab"
          :class="{ active: activeConfigTab === 'fields' }"
          @click="activeConfigTab = 'fields'"
        >
          <ListChecks :size="15" />
          <span>物理字段清单 (fields)</span>
          <span class="cfg-badge">{{ configData?.fields?.length || 0 }}</span>
        </button>

        <button
          class="cfg-tab"
          :class="{ active: activeConfigTab === 'json' }"
          @click="activeConfigTab = 'json'"
        >
          <FileCode :size="15" />
          <span>原始配置 JSON</span>
        </button>
      </div>

      <!-- Tab 1: 表头配置展示 (sys_module_header) -->
      <div v-if="activeConfigTab === 'headers'" class="tab-panel">
        <div class="panel-header">
          <div>
            <h3 class="panel-title">表头列及动态搜索方案配置</h3>
            <p class="panel-desc">对应 <code>sys_module_header</code> 记录，由数据引擎自动解析并映射前端列与复合搜索谓词。</p>
          </div>
          <span class="counter-text">共配置 <strong>{{ configData?.moduleHeaders?.length || 0 }}</strong> 个表头列</span>
        </div>

        <div class="table-responsive">
          <table class="meta-table">
            <thead>
              <tr>
                <th style="width: 60px;" class="text-center">序号</th>
                <th style="width: 160px;">列显示名称 (name)</th>
                <th style="width: 150px;">归属物理表 (table)</th>
                <th style="width: 150px;">物理字段名 (field)</th>
                <th style="width: 180px;">搜索推导类型 (searchType)</th>
                <th style="width: 90px;" class="text-center">列宽 (px)</th>
                <th style="width: 90px;" class="text-center">对齐固定</th>
                <th style="width: 70px;" class="text-center">可排序</th>
                <th style="width: 70px;" class="text-center">省略</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!configData?.moduleHeaders || configData.moduleHeaders.length === 0">
                <td colspan="9" class="text-center empty-cell">此模块尚未配置表头数据</td>
              </tr>
              <tr v-for="(h, idx) in configData?.moduleHeaders" :key="`h-${idx}`">
                <td class="text-center font-mono font-bold">{{ h.sortOrder ?? idx + 1 }}</td>
                <td class="font-semibold text-main">{{ h.name }}</td>
                <td><code class="code-pill">{{ h.table }}</code></td>
                <td><code class="code-pill text-indigo">{{ h.field }}</code></td>
                <td>
                  <span class="badge" :class="getSearchTypeBadge(h.searchType).class">
                    {{ getSearchTypeBadge(h.searchType).text }}
                  </span>
                </td>
                <td class="text-center font-mono">{{ h.width || '-' }}</td>
                <td class="text-center">
                  <span v-if="h.fixed === 'left'" class="badge badge-emerald">左固定</span>
                  <span v-else-if="h.fixed === 'right'" class="badge badge-amber">右固定</span>
                  <span v-else class="text-muted text-xs">默认</span>
                </td>
                <td class="text-center">
                  <span v-if="h.sortable" class="check-mark">✓</span>
                  <span v-else class="cross-mark">-</span>
                </td>
                <td class="text-center">
                  <span v-if="h.ellipsis" class="check-mark">✓</span>
                  <span v-else class="cross-mark">-</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Tab 2: 主从关联拓扑 (tableRelations) -->
      <div v-if="activeConfigTab === 'relations'" class="tab-panel">
        <div class="panel-header">
          <div>
            <h3 class="panel-title">物理表关系拓扑配置</h3>
            <p class="panel-desc">定义模块内主表与伴生表、多层 1:N 级联从表的图关联路径。</p>
          </div>
          <span class="counter-text">共配置 <strong>{{ configData?.tableRelations?.length || 0 }}</strong> 条关联规则</span>
        </div>

        <div class="table-responsive">
          <table class="meta-table">
            <thead>
              <tr>
                <th style="width: 60px;" class="text-center">序号</th>
                <th style="width: 140px;">主方物理表</th>
                <th style="width: 140px;">主方连接列</th>
                <th style="width: 130px;" class="text-center">关系类型</th>
                <th style="width: 140px;">从方物理表</th>
                <th style="width: 140px;">从方连接列</th>
                <th>业务关联描述</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!configData?.tableRelations || configData.tableRelations.length === 0">
                <td colspan="7" class="text-center empty-cell">暂无关联拓扑，当前为单表独立模块</td>
              </tr>
              <tr v-for="(rel, idx) in configData?.tableRelations" :key="`rel-${idx}`">
                <td class="text-center font-mono">{{ idx + 1 }}</td>
                <td><code class="code-pill">{{ rel.mainTable }}</code></td>
                <td><code class="code-pill text-indigo">{{ rel.mainField }}</code></td>
                <td class="text-center">
                  <span class="badge" :class="rel.relationType === '1:N' ? 'badge-amber' : (rel.relationType === 'N:1' ? 'badge-cyan' : 'badge-emerald')">
                    {{ rel.relationType }}
                  </span>
                </td>
                <td><code class="code-pill">{{ rel.joinTable }}</code></td>
                <td><code class="code-pill text-indigo">{{ rel.joinField }}</code></td>
                <td class="text-main">{{ rel.description || '-' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Tab 3: 物理字段清单 (fields) -->
      <div v-if="activeConfigTab === 'fields'" class="tab-panel">
        <div class="panel-header">
          <div>
            <h3 class="panel-title">注册物理字段清单</h3>
            <p class="panel-desc">模块所拥有的全部物理字段，参与动态投影列构建与权限校验。</p>
          </div>
          <span class="counter-text">共配置 <strong>{{ configData?.fields?.length || 0 }}</strong> 个字段</span>
        </div>

        <div class="table-responsive">
          <table class="meta-table">
            <thead>
              <tr>
                <th style="width: 60px;" class="text-center">序号</th>
                <th style="width: 160px;">物理表名</th>
                <th style="width: 160px;">物理字段名</th>
                <th>字段显示名称</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!configData?.fields || configData.fields.length === 0">
                <td colspan="4" class="text-center empty-cell">未登记物理字段</td>
              </tr>
              <tr v-for="(f, idx) in configData?.fields" :key="`f-${idx}`">
                <td class="text-center font-mono">{{ idx + 1 }}</td>
                <td><code class="code-pill">{{ f.tableName }}</code></td>
                <td><code class="code-pill text-indigo">{{ f.columnName }}</code></td>
                <td class="font-semibold">{{ f.displayName || '-' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Tab 4: 原始 JSON -->
      <div v-if="activeConfigTab === 'json'" class="tab-panel">
        <div class="panel-header">
          <div>
            <h3 class="panel-title">完整配置 JSON 镜像</h3>
            <p class="panel-desc">供调试、备份或后端接口核对直接复制使用。</p>
          </div>
          <button class="btn btn-sm btn-outline" @click="copyJson">
            <Check v-if="copied" :size="13" class="text-emerald" />
            <Copy v-else :size="13" />
            <span>{{ copied ? '已复制到剪贴板' : '复制 JSON' }}</span>
          </button>
        </div>

        <pre class="json-code-viewer"><code>{{ JSON.stringify(configData, null, 2) }}</code></pre>
      </div>
    </div>
  </div>
</template>

<style scoped>
.module-config-page {
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
}

.banner-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.banner-icon-box {
  width: 50px;
  height: 50px;
  border-radius: var(--radius-lg);
  background: linear-gradient(135deg, var(--primary-600), var(--primary-800));
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
  font-size: 12px;
  color: var(--text-muted);
}

.banner-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.module-quick-switch {
  display: flex;
  align-items: center;
  gap: 8px;
}

.switch-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-muted);
}

.module-select {
  padding: 6px 12px;
  font-size: 13px;
  font-weight: 600;
  color: var(--primary-700);
  border-radius: var(--radius-md);
  background: #ffffff;
  border: 1px solid var(--border-subtle);
}

/* Config Container */
.config-container {
  padding: 20px 24px;
  min-height: 550px;
}

.config-tabs-bar {
  display: flex;
  gap: 8px;
  border-bottom: 1px solid var(--border-subtle);
  padding-bottom: 12px;
  margin-bottom: 20px;
}

.cfg-tab {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  border-radius: var(--radius-md);
  font-size: 13px;
  font-weight: 600;
  color: var(--text-muted);
  background: transparent;
  border: 1px solid transparent;
  cursor: pointer;
  transition: var(--transition);
}

.cfg-tab:hover {
  color: var(--text-main);
  background: var(--bg-muted);
}

.cfg-tab.active {
  color: var(--primary-600);
  background: #ffffff;
  border-color: var(--primary-200);
  box-shadow: var(--shadow-sm);
}

.cfg-badge {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: var(--radius-full);
  background: var(--primary-100);
  color: var(--primary-700);
  font-weight: 700;
}

/* Tab Panel */
.tab-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.panel-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-main);
  margin-bottom: 2px;
}

.panel-desc {
  font-size: 12px;
  color: var(--text-muted);
}

.counter-text {
  font-size: 12px;
  color: var(--text-muted);
}

.counter-text strong {
  color: var(--primary-600);
}

/* Meta Table */
.meta-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.meta-table th {
  background: var(--bg-muted);
  padding: 10px 14px;
  font-weight: 600;
  color: var(--text-muted);
  border: 1px solid var(--border-subtle);
  text-align: left;
}

.meta-table td {
  padding: 10px 14px;
  border: 1px solid var(--border-subtle);
  vertical-align: middle;
}

.code-pill {
  font-family: var(--font-mono);
  font-size: 12px;
  padding: 2px 6px;
  background: var(--bg-muted);
  border-radius: var(--radius-sm);
}

.check-mark {
  color: #10b981;
  font-weight: 700;
}

.cross-mark {
  color: var(--text-dim);
}

.empty-cell {
  padding: 40px;
  color: var(--text-muted);
  font-style: italic;
}

/* JSON Viewer */
.json-code-viewer {
  background: #0f172a;
  color: #e2e8f0;
  padding: 16px 20px;
  border-radius: var(--radius-md);
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1.5;
  max-height: 600px;
  overflow: auto;
}

.spin-icon {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
