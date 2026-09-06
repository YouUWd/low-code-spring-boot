<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { 
  configApi, type SysModuleMetaComplete, type ModuleInfo, 
  type TableRelationItem, type ModuleHeaderItem, type ModuleFieldItem 
} from '../api/configApi';
import { 
  Plus, Save, Trash2, Settings, RefreshCw, 
  GitFork, Table, ListChecks, CheckSquare, MoveUp, MoveDown, 
  FileCode, Check, Copy, AlertCircle, Info, Sparkles
} from 'lucide-vue-next';

const route = useRoute();
const router = useRouter();

const loading = ref(false);
const saving = ref(false);
const isDirty = ref(false); // 是否有未保存修改
const modules = ref<ModuleInfo[]>([]);
const activeModuleId = ref<number>(Number(route.params.moduleId) || 101);

// 当前正在编辑的模块完整配置模型
const form = ref<SysModuleMetaComplete>({
  module: {
    moduleCode: '',
    moduleName: '',
    primaryTable: '',
    parentId: 0,
    moduleType: 'LIST'
  },
  fields: [],
  tableRelations: [],
  moduleHeaders: []
});

// 当前设计标签页，严格按照用户指定的五大选项卡布局顺序：
// 基本信息 -> 字段配置 -> 表头配置 -> 关系拓扑（推断） -> 配置JSON
const activeTab = ref<'base' | 'fields' | 'headers' | 'relations' | 'json'>('base');

// 复制 JSON 状态
const copied = ref(false);

// 搜索类型选项
const searchTypeOptions = [
  { value: 'text', label: '🔤 文本输入 (LIKE)' },
  { value: 'singleFuzzySelect', label: '🔍 模糊下拉单选 (LIKE)' },
  { value: 'singleSelect', label: '📌 精确单选 (=)' },
  { value: 'multipleSelect', label: '📑 多选集合 (IN)' },
  { value: 'between', label: '📊 数值区间 (BETWEEN)' },
  { value: 'dateRange', label: '📅 日期范围 (BETWEEN)' },
  { value: 'none', label: '❌ 不参与搜索' }
];

// 加载所有模块清单
async function loadModuleList() {
  try {
    const list = await configApi.listModules();
    modules.value = list || [];
  } catch (err: any) {
    console.error('加载模块列表失败:', err);
  }
}

// 加载指定模块的完整元数据
async function loadModuleDetail(moduleId: number) {
  if (!moduleId) return;
  activeModuleId.value = moduleId;
  loading.value = true;
  try {
    const completeData = await configApi.getModuleComplete(moduleId);
    if (completeData) {
      form.value = {
        module: completeData.module || { moduleCode: '', moduleName: '', primaryTable: '', parentId: 0 },
        fields: (completeData.fields || []).map((f, idx) => ({
          ...f,
          sortOrder: f.sortOrder ?? idx + 1
        })),
        tableRelations: completeData.tableRelations || [],
        moduleHeaders: (completeData.moduleHeaders || []).map((h, idx) => ({
          ...h,
          sortOrder: h.sortOrder ?? idx + 1,
          searchType: h.searchType || 'text',
          fixed: h.fixed || 'none',
          ellipsis: h.ellipsis ?? true,
          sortable: h.sortable ?? true
        }))
      };
      isDirty.value = false;
    }
  } catch (err: any) {
    console.error('加载模块元数据失败:', err);
  } finally {
    loading.value = false;
  }
}

// 切换模块
function switchModule(id: number) {
  if (isDirty.value) {
    if (!confirm('当前模块有未保存的修改，切换将放弃修改，确定继续吗？')) {
      return;
    }
  }
  router.push(`/modules/designer/${id}`);
  loadModuleDetail(id);
}

// 新建模块草稿
function handleCreateNewModule() {
  activeModuleId.value = 0;
  form.value = {
    module: {
      moduleCode: 'MOD-NEW-' + Date.now().toString().slice(-4),
      moduleName: '新建动态模块',
      primaryTable: '',
      parentId: 0,
      moduleType: 'LIST'
    },
    fields: [],
    tableRelations: [],
    moduleHeaders: []
  };
  activeTab.value = 'base';
  isDirty.value = true;
}

// === 字段配置增删 ===
function addFieldRow() {
  const currentTable = form.value.module.primaryTable || 'main_table';
  form.value.fields.push({
    tableName: currentTable,
    columnName: 'new_column',
    displayName: '新字段展示名',
    sortOrder: form.value.fields.length + 1
  });
  markDirty();
}

function removeFieldRow(index: number) {
  form.value.fields.splice(index, 1);
  markDirty();
}

// === 表头配置增删与操作 ===
function addHeaderRow() {
  const currentTable = form.value.module.primaryTable || 'main_table';
  const newHeader: ModuleHeaderItem = {
    name: '新字段',
    table: currentTable,
    field: 'new_column',
    width: 150,
    sortOrder: (form.value.moduleHeaders.length + 1),
    searchType: 'singleFuzzySelect',
    fixed: 'none',
    ellipsis: true,
    sortable: true
  };
  form.value.moduleHeaders.push(newHeader);
  markDirty();
}

function removeHeaderRow(index: number) {
  form.value.moduleHeaders.splice(index, 1);
  form.value.moduleHeaders.forEach((h, idx) => {
    h.sortOrder = idx + 1;
  });
  markDirty();
}

function moveHeaderUp(index: number) {
  if (index <= 0) return;
  const temp = form.value.moduleHeaders[index];
  form.value.moduleHeaders[index] = form.value.moduleHeaders[index - 1];
  form.value.moduleHeaders[index - 1] = temp;
  form.value.moduleHeaders.forEach((h, idx) => {
    h.sortOrder = idx + 1;
  });
  markDirty();
}

function moveHeaderDown(index: number) {
  if (index >= form.value.moduleHeaders.length - 1) return;
  const temp = form.value.moduleHeaders[index];
  form.value.moduleHeaders[index] = form.value.moduleHeaders[index + 1];
  form.value.moduleHeaders[index + 1] = temp;
  form.value.moduleHeaders.forEach((h, idx) => {
    h.sortOrder = idx + 1;
  });
  markDirty();
}

// 从物理字段配置中一键同步至表头
function syncHeadersFromFields() {
  if (!form.value.fields || form.value.fields.length === 0) {
    alert('当前模块未在【字段配置】中登记字段');
    return;
  }
  const existingKeys = new Set(form.value.moduleHeaders.map(h => `${h.table}.${h.field}`));
  let addedCount = 0;
  form.value.fields.forEach(f => {
    const key = `${f.tableName}.${f.columnName}`;
    if (!existingKeys.has(key)) {
      form.value.moduleHeaders.push({
        name: f.displayName || f.columnName,
        table: f.tableName,
        field: f.columnName,
        width: 150,
        sortOrder: form.value.moduleHeaders.length + 1,
        searchType: 'singleFuzzySelect',
        fixed: 'none',
        ellipsis: true,
        sortable: true
      });
      addedCount++;
    }
  });
  if (addedCount > 0) {
    markDirty();
    alert(`成功同步并追加了 ${addedCount} 个字段至表头配置！`);
  } else {
    alert('所有物理字段均已在表头配置中存在，无需重复同步。');
  }
}

// === 关系拓扑增删 ===
function addRelationRow() {
  const newRel: TableRelationItem = {
    mainTable: form.value.module.primaryTable || '',
    mainField: 'id',
    joinTable: '',
    joinField: '',
    relationType: '1:N',
    description: ''
  };
  form.value.tableRelations.push(newRel);
  markDirty();
}

function removeRelationRow(index: number) {
  form.value.tableRelations.splice(index, 1);
  markDirty();
}

function markDirty() {
  isDirty.value = true;
}

// 保存完整模块配置
async function handleSave() {
  if (!form.value.module.moduleCode || !form.value.module.moduleName) {
    alert('请完善模块编码与模块名称');
    return;
  }
  saving.value = true;
  try {
    const savedId = await configApi.saveModuleComplete(form.value);
    isDirty.value = false;
    alert('✅ 模块配置已成功保存并立即生效！');
    await loadModuleList();
    if (savedId) {
      loadModuleDetail(savedId);
    }
  } catch (err: any) {
    alert('❌ 保存失败: ' + err.message);
  } finally {
    saving.value = false;
  }
}

// 复制 JSON
function copyJson() {
  if (!form.value) return;
  navigator.clipboard.writeText(JSON.stringify(form.value, null, 2)).then(() => {
    copied.value = true;
    setTimeout(() => {
      copied.value = false;
    }, 2000);
  });
}

// 键盘快捷键监听 Ctrl+S 保存
function handleKeyDown(e: KeyboardEvent) {
  if ((e.ctrlKey || e.metaKey) && e.key === 's') {
    e.preventDefault();
    handleSave();
  }
}

watch(
  () => route.params.moduleId,
  (newId) => {
    if (newId) {
      loadModuleDetail(Number(newId));
    }
  }
);

onMounted(async () => {
  window.addEventListener('keydown', handleKeyDown);
  await loadModuleList();
  const initId = Number(route.params.moduleId) || activeModuleId.value || 101;
  loadModuleDetail(initId);
});
</script>

<template>
  <div class="designer-page animate-fade-in">
    <!-- Top Header Toolbar -->
    <div class="designer-toolbar glass-card">
      <div class="toolbar-left">
        <div class="toolbar-icon">
          <Settings :size="22" />
        </div>
        <div>
          <div class="title-row">
            <h2 class="toolbar-title">{{ form.module.moduleName || '模块配置中心' }}</h2>
            <span class="badge badge-indigo">{{ form.module.moduleCode || `MOD-${activeModuleId}` }}</span>
            <span v-if="activeModuleId" class="badge badge-slate">ID: {{ activeModuleId }}</span>
            <span v-if="isDirty" class="badge badge-amber dirty-badge">
              <AlertCircle :size="12" /> 未保存修改
            </span>
          </div>
          <span class="toolbar-desc">
            主表: <code>{{ form.module.primaryTable || '未指定' }}</code> • 
            直接就地编辑修改，点击右上角保存即可落库并热加载生效
          </span>
        </div>
      </div>

      <div class="toolbar-actions">
        <!-- 快速选择切换模块 -->
        <div class="module-selector-box">
          <select
            class="clean-select module-select"
            :value="activeModuleId"
            @change="switchModule(Number(($event.target as HTMLSelectElement).value))"
          >
            <option v-for="m in modules" :key="m.id" :value="m.id">
              {{ m.moduleName }} ({{ m.moduleCode }})
            </option>
          </select>
        </div>

        <button class="btn btn-outline btn-sm" title="新建模块" @click="handleCreateNewModule">
          <Plus :size="14" />
          <span>新建模块</span>
        </button>

        <button class="btn btn-outline btn-sm" title="刷新最新配置" :disabled="loading" @click="loadModuleDetail(activeModuleId)">
          <RefreshCw :size="14" :class="{ 'spin-icon': loading }" />
          <span>刷新</span>
        </button>

        <button class="btn btn-primary btn-sm save-btn" :disabled="saving" @click="handleSave">
          <Save :size="14" />
          <span>{{ saving ? '正在保存...' : '保存配置 (Ctrl+S)' }}</span>
        </button>
      </div>
    </div>

    <!-- Main Clean Container -->
    <div class="content-panel glass-card">
      <!-- 选项卡导航栏：严格按照用户要求的布局顺序排列 -->
      <div class="tab-nav-bar">
        <!-- 1. 基本信息 -->
        <button
          class="tab-btn"
          :class="{ active: activeTab === 'base' }"
          @click="activeTab = 'base'"
        >
          <Info :size="15" />
          <span>基本信息</span>
        </button>

        <!-- 2. 字段配置 -->
        <button
          class="tab-btn"
          :class="{ active: activeTab === 'fields' }"
          @click="activeTab = 'fields'"
        >
          <ListChecks :size="15" />
          <span>字段配置</span>
          <span class="tab-count">{{ form.fields.length }}</span>
        </button>

        <!-- 3. 表头配置 -->
        <button
          class="tab-btn"
          :class="{ active: activeTab === 'headers' }"
          @click="activeTab = 'headers'"
        >
          <Table :size="15" />
          <span>表头配置</span>
          <span class="tab-count">{{ form.moduleHeaders.length }}</span>
        </button>

        <!-- 4. 关系拓扑（推断） -->
        <button
          class="tab-btn"
          :class="{ active: activeTab === 'relations' }"
          @click="activeTab = 'relations'"
        >
          <GitFork :size="15" />
          <span>关系拓扑（推断）</span>
          <span class="tab-count">{{ form.tableRelations.length }}</span>
        </button>

        <!-- 5. 配置JSON -->
        <button
          class="tab-btn"
          :class="{ active: activeTab === 'json' }"
          @click="activeTab = 'json'"
        >
          <FileCode :size="15" />
          <span>配置JSON</span>
        </button>
      </div>

      <!-- Tab 1: 基本信息 -->
      <div v-if="activeTab === 'base'" class="tab-body">
        <div class="tab-action-bar">
          <span class="tip-text">配置模块基础标识、编码、主表与父子隶属层级关系。</span>
        </div>

        <div class="form-clean-grid">
          <div class="form-item">
            <label class="item-label">模块编码 (moduleCode)</label>
            <input v-model="form.module.moduleCode" class="clean-input-box" placeholder="如 MOD-SCHOOL-STUDENT" @input="markDirty" />
          </div>

          <div class="form-item">
            <label class="item-label">模块名称 (moduleName)</label>
            <input v-model="form.module.moduleName" class="clean-input-box font-bold" placeholder="如 学生综合全景档案" @input="markDirty" />
          </div>

          <div class="form-item">
            <label class="item-label">物理主表 (primaryTable)</label>
            <input v-model="form.module.primaryTable" class="clean-input-box font-mono" placeholder="如 student" @input="markDirty" />
          </div>

          <div class="form-item">
            <label class="item-label">父级模块 ID (parentId)</label>
            <input v-model.number="form.module.parentId" type="number" class="clean-input-box font-mono" placeholder="0 表示顶级模块" @input="markDirty" />
          </div>

          <div class="form-item full-width">
            <label class="item-label">模块业务定位与描述 (moduleDesc)</label>
            <textarea v-model="form.module.moduleDesc" rows="3" class="clean-input-box" placeholder="描述该模块的业务定位与核心主从表" @input="markDirty"></textarea>
          </div>
        </div>
      </div>

      <!-- Tab 2: 字段配置 (fields) -->
      <div v-if="activeTab === 'fields'" class="tab-body">
        <div class="tab-action-bar">
          <div class="bar-left">
            <span class="tip-text">管理模块在底层登记的物理表及物理列清单，点击文字即可就地编辑修改。</span>
          </div>
          <div class="bar-right">
            <button class="btn btn-sm btn-primary" @click="addFieldRow">
              <Plus :size="13" />
              <span>添加字段</span>
            </button>
          </div>
        </div>

        <div class="table-responsive">
          <table class="clean-edit-table">
            <thead>
              <tr>
                <th style="width: 50px;" class="text-center">#</th>
                <th style="width: 200px;">物理表名 (tableName)</th>
                <th style="width: 200px;">物理列名 (columnName)</th>
                <th>字段显示名称 (displayName)</th>
                <th style="width: 100px;" class="text-center">排序号</th>
                <th style="width: 70px;" class="text-center">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="form.fields.length === 0">
                <td colspan="6" class="text-center empty-cell">
                  暂无物理字段登记，请点击右上角“添加字段”
                </td>
              </tr>
              <tr 
                v-for="(f, idx) in form.fields" 
                :key="`f-${idx}`"
                class="hover-row"
              >
                <td class="text-center font-mono text-muted text-xs">{{ idx + 1 }}</td>
                <td>
                  <input
                    v-model="f.tableName"
                    class="clean-inline-input font-mono text-indigo"
                    placeholder="如 student"
                    @input="markDirty"
                  />
                </td>
                <td>
                  <input
                    v-model="f.columnName"
                    class="clean-inline-input font-mono text-emerald font-semibold"
                    placeholder="如 student_no"
                    @input="markDirty"
                  />
                </td>
                <td>
                  <input
                    v-model="f.displayName"
                    class="clean-inline-input font-medium text-main"
                    placeholder="如 学号"
                    @input="markDirty"
                  />
                </td>
                <td class="text-center">
                  <input
                    v-model.number="f.sortOrder"
                    type="number"
                    class="clean-inline-input text-center font-mono"
                    placeholder="1"
                    @input="markDirty"
                  />
                </td>
                <td class="text-center">
                  <button
                    class="act-btn act-btn-danger"
                    title="删除字段"
                    @click="removeFieldRow(idx)"
                  >
                    <Trash2 :size="12" />
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Tab 3: 表头配置 (headers) -->
      <div v-if="activeTab === 'headers'" class="tab-body">
        <div class="tab-action-bar">
          <div class="bar-left">
            <span class="tip-text">配置 <code>sys_module_header</code> 列表展示与各列搜索推导方案，点击文字即可直接编辑。</span>
          </div>
          <div class="bar-right">
            <button class="btn btn-sm btn-outline" @click="syncHeadersFromFields">
              <CheckSquare :size="13" />
              <span>从字段配置一键同步</span>
            </button>
            <button class="btn btn-sm btn-primary" @click="addHeaderRow">
              <Plus :size="13" />
              <span>添加表头列</span>
            </button>
          </div>
        </div>

        <div class="table-responsive">
          <table class="clean-edit-table">
            <thead>
              <tr>
                <th style="width: 50px;" class="text-center">#</th>
                <th style="width: 170px;">列显示名称 (name)</th>
                <th style="width: 160px;">物理表名 (table)</th>
                <th style="width: 160px;">物理列名 (field)</th>
                <th style="width: 220px;">搜索方案 (searchType)</th>
                <th style="width: 90px;" class="text-center">列宽 (px)</th>
                <th style="width: 100px;" class="text-center">固定方式</th>
                <th style="width: 70px;" class="text-center">排序</th>
                <th style="width: 70px;" class="text-center">省略</th>
                <th style="width: 110px;" class="text-center">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="form.moduleHeaders.length === 0">
                <td colspan="10" class="text-center empty-cell">
                  暂未配置表头，请点击右上角“添加表头列”或“从字段配置一键同步”
                </td>
              </tr>
              <tr 
                v-for="(h, idx) in form.moduleHeaders" 
                :key="`h-${idx}`"
                class="hover-row"
              >
                <!-- 序号 -->
                <td class="text-center font-mono text-muted text-xs">{{ idx + 1 }}</td>

                <!-- 列显示名称 -->
                <td>
                  <input
                    v-model="h.name"
                    class="clean-inline-input font-semibold text-main"
                    placeholder="如 学号"
                    @input="markDirty"
                  />
                </td>

                <!-- 物理表名 -->
                <td>
                  <input
                    v-model="h.table"
                    class="clean-inline-input font-mono text-indigo"
                    placeholder="如 student"
                    @input="markDirty"
                  />
                </td>

                <!-- 物理字段名 -->
                <td>
                  <input
                    v-model="h.field"
                    class="clean-inline-input font-mono text-emerald"
                    placeholder="如 student_no"
                    @input="markDirty"
                  />
                </td>

                <!-- 搜索类型 -->
                <td>
                  <select
                    v-model="h.searchType"
                    class="clean-inline-select font-medium"
                    @change="markDirty"
                  >
                    <option v-for="opt in searchTypeOptions" :key="opt.value" :value="opt.value">
                      {{ opt.label }}
                    </option>
                  </select>
                </td>

                <!-- 列宽 -->
                <td class="text-center">
                  <input
                    v-model.number="h.width"
                    type="number"
                    class="clean-inline-input text-center font-mono"
                    placeholder="150"
                    @input="markDirty"
                  />
                </td>

                <!-- 固定方式 -->
                <td class="text-center">
                  <select
                    v-model="h.fixed"
                    class="clean-inline-select text-center text-xs"
                    @change="markDirty"
                  >
                    <option value="none">默认</option>
                    <option value="left">左固定</option>
                    <option value="right">右固定</option>
                  </select>
                </td>

                <!-- 可排序 -->
                <td class="text-center">
                  <input
                    v-model="h.sortable"
                    type="checkbox"
                    class="clean-checkbox"
                    @change="markDirty"
                  />
                </td>

                <!-- 省略 -->
                <td class="text-center">
                  <input
                    v-model="h.ellipsis"
                    type="checkbox"
                    class="clean-checkbox"
                    @change="markDirty"
                  />
                </td>

                <!-- 行内操作按钮 -->
                <td class="text-center">
                  <div class="row-actions">
                    <button
                      class="act-btn"
                      title="上移"
                      :disabled="idx === 0"
                      @click="moveHeaderUp(idx)"
                    >
                      <MoveUp :size="12" />
                    </button>
                    <button
                      class="act-btn"
                      title="下移"
                      :disabled="idx === form.moduleHeaders.length - 1"
                      @click="moveHeaderDown(idx)"
                    >
                      <MoveDown :size="12" />
                    </button>
                    <button
                      class="act-btn act-btn-danger"
                      title="删除此列"
                      @click="removeHeaderRow(idx)"
                    >
                      <Trash2 :size="12" />
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Tab 4: 关系拓扑（推断） (tableRelations) -->
      <div v-if="activeTab === 'relations'" class="tab-body">
        <div class="tab-action-bar">
          <div class="bar-left">
            <span class="tip-text">由系统全局推断并透传的主从表拓扑关联网，支持在此直接增补或微调主外键路径。</span>
          </div>
          <div class="bar-right">
            <button class="btn btn-sm btn-primary" @click="addRelationRow">
              <Plus :size="13" />
              <span>添加拓扑关联</span>
            </button>
          </div>
        </div>

        <div class="table-responsive">
          <table class="clean-edit-table">
            <thead>
              <tr>
                <th style="width: 50px;" class="text-center">#</th>
                <th style="width: 160px;">主方物理表</th>
                <th style="width: 160px;">主方连接列</th>
                <th style="width: 140px;" class="text-center">关系类型</th>
                <th style="width: 160px;">从方物理表</th>
                <th style="width: 160px;">从方连接列</th>
                <th>业务关联描述</th>
                <th style="width: 70px;" class="text-center">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="form.tableRelations.length === 0">
                <td colspan="8" class="text-center empty-cell">暂无关联拓扑，当前为单表独立模块</td>
              </tr>
              <tr v-for="(rel, idx) in form.tableRelations" :key="`rel-${idx}`" class="hover-row">
                <td class="text-center font-mono text-muted text-xs">{{ idx + 1 }}</td>
                <td>
                  <input v-model="rel.mainTable" class="clean-inline-input font-mono" placeholder="如 student" @input="markDirty" />
                </td>
                <td>
                  <input v-model="rel.mainField" class="clean-inline-input font-mono text-indigo" placeholder="如 id" @input="markDirty" />
                </td>
                <td class="text-center">
                  <select v-model="rel.relationType" class="clean-inline-select font-semibold text-center" @change="markDirty">
                    <option value="1:1">1:1 (单行伴生)</option>
                    <option value="N:1">N:1 (主持外键维表)</option>
                    <option value="1:N">1:N (多行级联从表)</option>
                  </select>
                </td>
                <td>
                  <input v-model="rel.joinTable" class="clean-inline-input font-mono" placeholder="如 student_course" @input="markDirty" />
                </td>
                <td>
                  <input v-model="rel.joinField" class="clean-inline-input font-mono text-indigo" placeholder="如 student_id" @input="markDirty" />
                </td>
                <td>
                  <input v-model="rel.description" class="clean-inline-input" placeholder="如 选课与成绩管理" @input="markDirty" />
                </td>
                <td class="text-center">
                  <button class="act-btn act-btn-danger" title="删除" @click="removeRelationRow(idx)">
                    <Trash2 :size="12" />
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Tab 5: 配置JSON -->
      <div v-if="activeTab === 'json'" class="tab-body">
        <div class="tab-action-bar">
          <span class="tip-text">当前内存中完整模块配置（包含基本信息、字段、表头、推断拓扑）的 JSON 镜像。</span>
          <button class="btn btn-sm btn-outline" @click="copyJson">
            <Check v-if="copied" :size="13" class="text-emerald" />
            <Copy v-else :size="13" />
            <span>{{ copied ? '已复制到剪贴板' : '复制 JSON' }}</span>
          </button>
        </div>

        <pre class="clean-json-box"><code>{{ JSON.stringify(form, null, 2) }}</code></pre>
      </div>
    </div>
  </div>
</template>

<style scoped>
.designer-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* Header Toolbar */
.designer-toolbar {
  padding: 16px 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.toolbar-icon {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, var(--primary-600), var(--primary-800));
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  box-shadow: 0 4px 12px var(--primary-glow);
}

.title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 2px;
}

.toolbar-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-main);
}

.toolbar-desc {
  font-size: 12px;
  color: var(--text-muted);
}

.dirty-badge {
  display: flex;
  align-items: center;
  gap: 4px;
  animation: pulse 2s infinite;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.module-selector-box {
  display: flex;
  align-items: center;
}

.clean-select.module-select {
  padding: 6px 12px;
  font-size: 13px;
  font-weight: 600;
  color: var(--primary-700);
  background: #ffffff;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  outline: none;
}

.save-btn {
  font-weight: 700;
  box-shadow: 0 2px 10px var(--primary-glow);
}

/* Content Panel */
.content-panel {
  padding: 20px 24px;
  min-height: 600px;
}

.tab-nav-bar {
  display: flex;
  gap: 8px;
  border-bottom: 1px solid var(--border-subtle);
  padding-bottom: 12px;
  margin-bottom: 16px;
}

.tab-btn {
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

.tab-btn:hover {
  color: var(--text-main);
  background: var(--bg-muted);
}

.tab-btn.active {
  color: var(--primary-600);
  background: #ffffff;
  border-color: var(--primary-200);
  box-shadow: var(--shadow-sm);
}

.tab-count {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: var(--radius-full);
  background: var(--primary-100);
  color: var(--primary-700);
}

.tab-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.tab-action-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 0;
}

.tip-text {
  font-size: 12px;
  color: var(--text-muted);
}

.bar-right {
  display: flex;
  gap: 10px;
}

/* Clean In-Place Edit Table */
.clean-edit-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.clean-edit-table th {
  background: var(--bg-muted);
  padding: 10px 12px;
  font-weight: 600;
  color: var(--text-muted);
  border-bottom: 1px solid var(--border-subtle);
  text-align: left;
}

.clean-edit-table td {
  padding: 4px 6px;
  border-bottom: 1px solid var(--border-subtle);
  vertical-align: middle;
}

.hover-row:hover {
  background: rgba(248, 250, 252, 0.8);
}

/* 无边框极简行内输入框 */
.clean-inline-input {
  width: 100%;
  padding: 6px 8px;
  border: 1px solid transparent;
  background: transparent;
  border-radius: var(--radius-sm);
  font-size: 13px;
  color: var(--text-main);
  outline: none;
  transition: all 0.15s ease;
}

.clean-inline-input:hover {
  background: #ffffff;
  border-color: var(--border-subtle);
}

.clean-inline-input:focus {
  background: #ffffff;
  border-color: var(--primary-500);
  box-shadow: 0 0 0 2px var(--primary-glow);
}

/* 极简下拉选择 */
.clean-inline-select {
  width: 100%;
  padding: 6px 8px;
  border: 1px solid transparent;
  background: transparent;
  border-radius: var(--radius-sm);
  font-size: 13px;
  color: var(--primary-700);
  outline: none;
  cursor: pointer;
  transition: all 0.15s ease;
}

.clean-inline-select:hover {
  background: #ffffff;
  border-color: var(--border-subtle);
}

.clean-inline-select:focus {
  background: #ffffff;
  border-color: var(--primary-500);
}

.clean-checkbox {
  width: 15px;
  height: 15px;
  cursor: pointer;
  accent-color: var(--primary-600);
}

/* Row Action Buttons */
.row-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  opacity: 0.6;
  transition: opacity 0.2s;
}

.hover-row:hover .row-actions {
  opacity: 1;
}

.act-btn {
  padding: 4px;
  border-radius: var(--radius-sm);
  border: 1px solid transparent;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
}

.act-btn:hover:not(:disabled) {
  background: var(--bg-muted);
  color: var(--primary-600);
  border-color: var(--border-subtle);
}

.act-btn-danger:hover {
  background: #fef2f2;
  color: #ef4444;
  border-color: #fecaca;
}

.empty-cell {
  padding: 50px;
  color: var(--text-muted);
  font-style: italic;
}

/* Form Clean Grid */
.form-clean-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  padding: 10px 0;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-item.full-width {
  grid-column: span 2;
}

.item-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-muted);
}

.clean-input-box {
  padding: 10px 14px;
  border-radius: var(--radius-md);
  border: 1px solid var(--border-subtle);
  background: #ffffff;
  font-size: 13px;
  color: var(--text-main);
  outline: none;
  transition: var(--transition);
}

.clean-input-box:focus {
  border-color: var(--primary-500);
  box-shadow: 0 0 0 3px var(--primary-glow);
}

/* Clean JSON Box */
.clean-json-box {
  background: #0f172a;
  color: #e2e8f0;
  padding: 18px 22px;
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

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}
</style>
