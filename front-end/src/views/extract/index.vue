<template>
  <div class="files-container">
    <!-- ① 顶部状态栏 -->
    <div class="status-bar mb-12">
      <div class="status-bar-left">
        <h3 class="page-title">异常测段提取</h3>
        <template v-if="tabs.length">
          <span class="status-sep">|</span>
          <span class="status-item">已选 <strong>{{ tabs.length }}</strong> 个文件</span>
          <span class="status-sep">|</span>
          <span class="status-item">原始 <strong>{{ totalOriginalRows.toLocaleString() }}</strong> 行</span>
          <span class="status-sep">|</span>
          <span class="status-item">保留 <strong>{{ totalFilteredRows.toLocaleString() }}</strong> 行</span>
          <span class="status-sep">|</span>
          <span class="status-item">保留率 <strong :class="{ 'rate-warn': retentionRate < 50 }">{{ retentionRate }}%</strong></span>
          <template v-if="currentActiveFilterCount > 0">
            <span class="status-sep">|</span>
            <span class="status-item status-active condition-toggle" @click="conditionsPanelVisible = !conditionsPanelVisible">
              生效条件 <strong>{{ currentActiveFilterCount }}</strong> 条
              <el-icon class="condition-toggle-icon" :class="{ expanded: conditionsPanelVisible }"><ArrowDown /></el-icon>
            </span>
          </template>
        </template>
      </div>
    </div>

      <div v-if="conditionsPanelVisible && currentTab" class="conditions-panel">
        <template v-if="currentActiveConditions.length > 0">
          <el-tag
            v-for="cond in currentActiveConditions"
            :key="cond.col + cond.type + (cond.min || '') + (cond.max || '') + (cond.values ? cond.values.join(',') : '')"
            size="small"
            type="info"
            effect="plain"
            class="condition-tag"
          >
            <template v-if="cond.type === 'range'">
              {{ cond.col }}：{{ cond.min || '—' }} ~ {{ cond.max || '—' }}
            </template>
            <template v-else>
              {{ cond.col }}：{{ cond.values.join('、') }}
            </template>
          </el-tag>
        </template>
        <div v-else class="conditions-empty">当前文件暂无生效条件</div>
      </div>

    <!-- ② 工具栏 -->
    <div class="toolbar mb-16" v-if="tabs.length">
      <div class="toolbar-group">
        <el-button type="primary" icon="FolderOpened" @click="openFileSelector">选择文件</el-button>
        <el-button type="primary" plain icon="Filter" @click="filterDrawerVisible = true">
          条件筛选
          <el-badge v-if="activeFilterCount > 0" :value="activeFilterCount" class="filter-badge" />
        </el-button>
        <el-button icon="Refresh" plain @click="clearAllFilters" :disabled="activeFilterCount === 0">清除条件</el-button>
      </div>
      <div class="toolbar-group">
        <el-button type="success" icon="Document" plain @click="handleExportCurrent">导出当前</el-button>
        <el-button type="success" icon="Files" plain @click="handleBatchExport">导出全部</el-button>
        <el-button type="danger" icon="Delete" plain @click="closeAllTabs">清空已选</el-button>
      </div>
    </div>

    <!-- ③ 空状态 -->
    <div v-if="tabs.length === 0" class="empty-state-main">
      <el-empty description="暂无测井数据，请点击“选择文件”开始">
        <el-button type="primary" icon="FolderOpened" @click="openFileSelector">选择文件</el-button>
      </el-empty>
    </div>

    <!-- ④ 数据工作区 -->
    <el-card v-else shadow="always" class="tabs-card">
      <el-tabs v-model="activeTabName" type="border-card" closable @tab-remove="removeTab">
        <el-tab-pane v-for="item in tabs" :key="item.name" :label="item.title" :name="item.name">
          <div class="table-wrapper">
            <el-table
              :data="item.displayData"
              border
              stripe
              height="550"
              v-loading="item.loading"
              element-loading-text="正在加载数据，请稍候..."
            >
              <el-table-column type="index" label="测点序列" width="80" align="center" fixed />
              <el-table-column
                v-for="col in item.columns"
                :key="col"
                :prop="col"
                :label="col"
                min-width="140"
                align="center"
                sortable
              >
                <template #default="{ row }">
                  {{ getDisplayValue(row, col) }}
                </template>
              </el-table-column>
            </el-table>
          </div>

          <div class="pagination-container">
            <el-pagination
              v-model:current-page="item.currentPage"
              v-model:page-size="item.pageSize"
              :page-sizes="[100, 200, 500, 1000]"
              layout="total, sizes, prev, pager, next, jumper"
              :total="item.totalRows"
              @size-change="(size) => handleSizeChange(item, size)"
              @current-change="(page) => handleCurrentChange(item, page)"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- ⑤ 右侧筛选抽屉 -->
    <el-drawer v-model="filterDrawerVisible" title="条件筛选" direction="rtl" size="720px" :close-on-click-modal="true">
      <div class="drawer-body">
        <!-- 文本列配置 -->
        <div class="drawer-section">
          <div class="drawer-section-title" @click="drawerTextConfigVisible = !drawerTextConfigVisible">
            <el-icon><Setting /></el-icon>
            文本列配置
            <el-tag v-if="currentTab?.textColumns?.length" size="small" type="success" effect="plain">
              {{ currentTab?.textColumns?.length }} 个
            </el-tag>
            <el-icon class="expand-icon" :class="{ expanded: drawerTextConfigVisible }"><ArrowDown /></el-icon>
          </div>
          <div v-show="drawerTextConfigVisible" class="drawer-section-body">
            <p class="text-config-tip">选择包含分类文本的列（如岩性、井号），最多 10 个。其余列默认数值筛选。</p>
            <el-select v-model="currentTab.textColumns" multiple filterable collapse-tags collapse-tags-tooltip placeholder="选择文本列…" style="width: 100%;">
              <el-option v-for="col in currentTab?.columns || []" :key="col" :label="col" :value="col" />
            </el-select>
            <el-button type="primary" size="small" style="margin-top: 8px;" :loading="textColumnSaving" @click="handleTextColumnConfigSave(currentTab)">
              {{ textColumnSaving ? '正在回填数据...' : '保存并回填' }}
            </el-button>
          </div>
        </div>

        <!-- 筛选条件 -->
        <div class="drawer-section">
          <!-- 一体化范围切换 + 内容区 -->
          <div class="filter-scope-tabs">
            <div
              class="scope-tab"
              :class="{ active: filterScope === 'current' }"
              @click="filterScope = 'current'"
            >
              <el-icon><Document /></el-icon>
              当前文件
              <span v-if="currentTab" class="scope-tab-sub">{{ currentTab.title }}</span>
            </div>
            <div
              class="scope-tab"
              :class="{ active: filterScope === 'all' }"
              @click="filterScope = 'all'"
            >
              <el-icon><Files /></el-icon>
              所有文件
              <span class="scope-tab-sub">{{ tabs.length }} 个</span>
            </div>
          </div>

          <div class="drawer-section-body">
            <!-- 当前文件模式 -->
            <template v-if="filterScope === 'current' && currentTab">
              <div v-for="col in currentTab.columns" :key="col" class="drawer-filter-item">
                <template v-if="isTextColumn(currentTab, col)">
                  <div class="drawer-filter-label text-col-label">
                    {{ col }}
                    <el-tag size="small" type="info" effect="plain">文本</el-tag>
                  </div>
                  <el-select
                    v-model="currentTab.textFilterValues[col]"
                    multiple collapse-tags collapse-tags-tooltip filterable
                    placeholder="选择值…"
                    style="width: 100%;"
                  >
                    <el-option v-for="opt in (currentTab.textColumnOptions[col] || [])" :key="opt" :label="opt" :value="opt" />
                  </el-select>
                </template>
                <template v-else>
                  <div class="drawer-filter-label">{{ col }}</div>
                  <div class="drawer-range-row">
                    <el-input v-model="currentTab.filters[col].min" placeholder="最小值" clearable />
                    <span class="drawer-range-sep">—</span>
                    <el-input v-model="currentTab.filters[col].max" placeholder="最大值" clearable />
                  </div>
                </template>
              </div>
            </template>

            <!-- 所有文件模式 -->
            <template v-else-if="filterScope === 'all'">
              <div class="global-filter-header">
                <span class="global-filter-tip">筛选条件将应用于所有已选文件</span>
                <el-select
                  v-model="globalFilterColumns"
                  multiple filterable collapse-tags collapse-tags-tooltip
                  placeholder="+ 添加筛选列"
                  size="small"
                  style="width: 180px;"
                >
                  <el-option
                    v-for="col in allAvailableColumns"
                    :key="col.name"
                    :label="`${col.name} (${col.count}/${tabs.length})`"
                    :value="col.name"
                    :disabled="globalFilterColumns.includes(col.name)"
                  />
                </el-select>
              </div>

              <div v-for="col in globalFilterColumns" :key="col" class="drawer-filter-item">
                <!-- 文本列：多选下拉 -->
                <template v-if="isGlobalTextColumn(col)">
                  <div class="drawer-filter-label text-col-label">
                    {{ col }}
                    <el-tag size="small" type="info" effect="plain">文本</el-tag>
                    <el-icon class="remove-col-icon" @click="removeGlobalFilterCol(col)"><Close /></el-icon>
                  </div>
                  <el-select
                    v-model="globalTextFilterValues[col]"
                    multiple collapse-tags collapse-tags-tooltip filterable
                    placeholder="选择值…"
                    style="width: 100%;"
                  >
                    <el-option v-for="opt in (getGlobalTextOptions(col) || [])" :key="opt" :label="opt" :value="opt" />
                  </el-select>
                </template>
                <!-- 数值列：min-max -->
                <template v-else>
                  <div class="drawer-filter-label">
                    {{ col }}
                    <el-icon class="remove-col-icon" @click="removeGlobalFilterCol(col)"><Close /></el-icon>
                  </div>
                  <div class="drawer-range-row">
                    <el-input v-model="globalFilters[col].min" placeholder="最小值" clearable />
                    <span class="drawer-range-sep">—</span>
                    <el-input v-model="globalFilters[col].max" placeholder="最大值" clearable />
                  </div>
                </template>
              </div>

              <el-empty v-if="globalFilterColumns.length === 0" description="请点击右上角添加筛选列" :image-size="50" />
            </template>

            <el-empty v-else description="请先选择文件" :image-size="60" />
          </div>
        </div>
      </div>

      <!-- 抽屉底栏 -->
      <template #footer>
        <div class="drawer-footer">
          <el-button @click="filterDrawerVisible = false">取消</el-button>
          <el-button type="info" plain @click="clearCurrentTabFilters">清除条件</el-button>
          <el-button type="warning" plain @click="openPresetPicker">加载预设</el-button>
          <el-button type="success" plain @click="saveCurrentAsPreset">保存为预设</el-button>
          <el-button type="primary" icon="Filter" @click="applyDrawerFilters">应用筛选</el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="presetPickerVisible" title="选择预设" width="760px" append-to-body>
      <el-table :data="presetList" border height="360px" v-loading="presetLoading" row-key="id" @row-dblclick="loadPresetFromRow">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="preset-expand-detail">
              <div class="preset-expand-row">
                <span class="preset-expand-label">包含列：</span>
                <template v-if="parseJson(row.columnsJson, []).length">
                  <el-tag v-for="col in parseJson(row.columnsJson, [])" :key="col" size="small" style="margin: 2px 4px 2px 0;">{{ col }}</el-tag>
                </template>
                <span v-else style="color: #909399;">无</span>
              </div>
              <div class="preset-expand-row" style="margin-top: 8px;">
                <span class="preset-expand-label">筛选条件：</span>
                <template v-if="getFilterEntries(row.filtersJson).length">
                  <div v-for="entry in getFilterEntries(row.filtersJson)" :key="entry.col" class="preset-filter-item">
                    <span class="preset-filter-col">{{ entry.col }}</span>
                    <span class="preset-filter-range">{{ entry.min || '—' }} ~ {{ entry.max || '—' }}</span>
                  </div>
                </template>
                <span v-else style="color: #909399;">无</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="预设名称" min-width="140" />
        <el-table-column prop="scope" label="范围" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.scope === 'all' ? '' : 'warning'">{{ row.scope === 'all' ? '全局' : '当前' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ row }">
            <el-button type="primary" size="small" plain @click="loadPresetFromRow(row)">加载</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="presetPickerVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="fileDialogVisible" title="选择测井文件" width="70%" destroy-on-close>
      <div class="dialog-toolbar">
        <el-input
          v-model="filePageParams.fileName"
          class="dialog-search-input"
          placeholder="按文件名搜索"
          clearable
          @clear="handleFileSearch"
          @keyup.enter="handleFileSearch"
        />
        <el-button type="primary" @click="handleFileSearch">搜索</el-button>
      </div>

      <el-table
        ref="fileTableRef"
        :data="fileTableData"
        border
        row-key="id"
        height="400px"
        v-loading="fileTableLoading"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" />
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="fileName" label="文件名" show-overflow-tooltip />
        <el-table-column prop="totalRows" label="总行数" width="120" />
        <el-table-column prop="createTime" label="上传时间" width="180" />
      </el-table>

      <div class="dialog-pagination">
        <el-pagination
          v-model:current-page="filePageParams.current"
          v-model:page-size="filePageParams.size"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          :total="fileTotal"
          @size-change="handleFileSizeChange"
          @current-change="handleFileCurrentChange"
        />
      </div>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="fileDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="confirmFileSelection">确认选择（已选 {{ dialogSelectedRows.length }}）</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, Download, Setting, Filter, Document, Files, Close } from '@element-plus/icons-vue'
import { exportBatchZipStream, exportFilteredExcel, getFileList, getFilePage, getPageData, getFileLayers, getTextColumns, saveTextColumns, getDistinctValues } from '@/api/file'
import { getDefaultColumns, listPresets, savePreset } from '@/api/filterPreset'

const activeTabName = ref('')
const tabs = ref([])
const fileList = ref([])

const getDisplayValue = (row, col) => {
  if (!row) return '-'
  
  // 1. 直取
  if (row[col] !== undefined && row[col] !== null && row[col] !== '') {
    return row[col]
  }

  // 2. 修剪空格及忽略大小写匹配
  const normalizedCol = String(col).trim().toLowerCase()
  const matchKey = Object.keys(row).find(k => String(k).trim().toLowerCase() === normalizedCol)
  if (matchKey && row[matchKey] !== undefined && row[matchKey] !== null && row[matchKey] !== '') {
    return row[matchKey]
  }
  
  // 3. 防止强等于带来 0 的丢失
  if (row[col] === 0 || (matchKey && row[matchKey] === 0)) {
    return 0
  }

  return '-'
}
const selectedFiles = ref([])
const fileDialogVisible = ref(false)
const fileTableData = ref([])
const fileTotal = ref(0)
const fileTableLoading = ref(false)
const fileTableRef = ref(null)
const dialogSelectedRows = ref([])
const globalFilters = ref({})
const analysisResults = ref([])
const analysisResultsEmpty = ref(false)
const analysisLoading = ref(false)

// ==================== 状态栏 + 抽屉相关 ====================
const filterDrawerVisible = ref(false)
const drawerTextConfigVisible = ref(false)
const filterScope = ref('current')
const textColumnSaving = ref(false)
const globalFilterColumns = ref([])
const globalTextFilterValues = ref({})

// 全局筛选列变化时，自动初始化 filter 骨架，避免模板访问 undefined.min 崩溃
watch(globalFilterColumns, (newCols, oldCols) => {
  const added = newCols.filter(c => !(oldCols || []).includes(c))
  const removed = (oldCols || []).filter(c => !newCols.includes(c))
  added.forEach(col => {
    if (!globalFilters.value[col]) {
      globalFilters.value[col] = { min: '', max: '' }
    }
    if (!globalTextFilterValues.value[col]) {
      globalTextFilterValues.value[col] = []
    }
  })
  removed.forEach(col => {
    delete globalFilters.value[col]
    delete globalTextFilterValues.value[col]
  })
}, { deep: false })
const presetList = ref([])           // 后端预设模板列表（供加载下拉）
const presetSaving = ref(false)       // 保存预设按钮 loading
const presetLoading = ref(false)      // 预设列表表格 loading
const presetPickerVisible = ref(false)
const presetNameInput = ref('')
const conditionsPanelVisible = ref(false) // 状态栏条件面板展开状态

// 汇总所有文件中被标记为文本列的列名
const globalTextColumns = computed(() => {
  const textCols = new Set()
  tabs.value.forEach(tab => {
    (tab.textColumns || []).forEach(col => textCols.add(col))
  })
  return textCols
})

const isGlobalTextColumn = (col) => globalTextColumns.value.has(col)

// 全局文本列的去重选项缓存（跨文件聚合）
const globalTextColumnOptions = ref({})

// 获取全局文本列的聚合选项（所有已打开文件的值并集去重）
const getGlobalTextOptions = (col) => {
  // 如果缓存中没有该列的选项，聚合所有 tab 的已知选项
  if (!globalTextColumnOptions.value[col]) {
    const allVals = new Set()
    tabs.value.forEach(tab => {
      const opts = tab.textColumnOptions?.[col]
      if (Array.isArray(opts)) {
        opts.forEach(v => allVals.add(v))
      }
    })
    globalTextColumnOptions.value[col] = Array.from(allVals).sort()
  }
  // 如果仍为空，异步加载（跨所有文件取并集）
  if (!globalTextColumnOptions.value[col]?.length) {
    loadGlobalTextOptions(col)
  }
  return globalTextColumnOptions.value[col] || []
}

// 异步加载全局文本列的聚合选项（首次加载时调用后端取并集）
const loadGlobalTextOptions = async (col) => {
  const allVals = new Set()
  const promises = tabs.value.map(async (tab) => {
    if (!tab.columns?.includes(col)) return
    try {
      const values = await getDistinctValues(tab.fileId, col)
      if (Array.isArray(values)) {
        values.forEach(v => allVals.add(v))
      }
    } catch (e) {
      // 忽略单文件加载失败
    }
  })
  await Promise.all(promises)
  globalTextColumnOptions.value[col] = Array.from(allVals).sort()
}

const currentTab = computed(() => {
  if (!activeTabName.value) return null
  return tabs.value.find(t => t.name === activeTabName.value) || null
})

const totalOriginalRows = computed(() => {
  return tabs.value.reduce((sum, t) => sum + (t.originalRows || t.fileTotalRows || 0), 0)
})

const totalFilteredRows = computed(() => {
  return tabs.value.reduce((sum, t) => sum + (t.totalRows || 0), 0)
})

const retentionRate = computed(() => {
  const orig = totalOriginalRows.value
  if (!orig) return 100
  return Math.round((totalFilteredRows.value / orig) * 1000) / 10
})

const activeFilterCount = computed(() => {
  let count = 0
  tabs.value.forEach(tab => {
    if (tab.filters) {
      for (const range of Object.values(tab.filters)) {
        if ((range.min !== '' && range.min != null) || (range.max !== '' && range.max != null)) count++
      }
    }
    if (tab.textFilterValues) {
      for (const vals of Object.values(tab.textFilterValues)) {
        if (Array.isArray(vals) && vals.length > 0) count++
      }
    }
  })
  return count
})

// 当前 tab 实际生效的条件列表（供状态栏面板展示）
const currentActiveConditions = computed(() => {
  const tab = currentTab.value
  if (!tab) return []
  const conditions = []
  if (tab.filters) {
    Object.entries(tab.filters).forEach(([col, range]) => {
      if ((range.min !== '' && range.min != null) || (range.max !== '' && range.max != null)) {
        conditions.push({ col, type: 'range', min: range.min, max: range.max })
      }
    })
  }
  if (tab.textFilterValues) {
    Object.entries(tab.textFilterValues).forEach(([col, vals]) => {
      if (Array.isArray(vals) && vals.length > 0) {
        conditions.push({ col, type: 'text', values: vals })
      }
    })
  }
  return conditions
})

// 当前 tab 的生效条件计数
const currentActiveFilterCount = computed(() => currentActiveConditions.value.length)

// 全局筛选：汇总所有文件的列及其存在文件数
const allAvailableColumns = computed(() => {
  const colMap = new Map()
  tabs.value.forEach(tab => {
    (tab.columns || []).forEach(col => {
      if (colMap.has(col)) {
        colMap.get(col).count++
      } else {
        colMap.set(col, { name: col, count: 1 })
      }
    })
  })
  return Array.from(colMap.values()).sort((a, b) => b.count - a.count || a.name.localeCompare(b.name))
})

// 切换到“所有文件”时，默认添加核心数值列
// 切换到"所有文件"时，优先从后端配置加载默认列，无配置则动态猜测
watch(filterScope, async (val) => {
  if (val === 'all' && globalFilterColumns.value.length === 0) {
    try {
      const res = await getDefaultColumns()
      if (res && res.columnsJson) {
        // 配置优先：按配置加载，与文件实际存在列取交集
        const configured = JSON.parse(res.columnsJson)
        const available = allAvailableColumns.value.map(c => c.name)
        const valid = configured.filter(c => available.includes(c))
        globalFilterColumns.value = valid
        valid.forEach(col => {
          if (!globalFilters.value[col]) {
            globalFilters.value[col] = { min: '', max: '' }
          }
        })
        // 如果配置的列有些文件不存在，提示用户
        const missing = configured.filter(c => !available.includes(c))
        if (missing.length > 0) {
          ElMessage.info(`以下默认列在当前文件中不存在，已自动跳过：${missing.join('、')}`)
        }
        return
      }
    } catch (error) {
      console.error('加载默认筛选列配置失败，回退到动态猜测', error)
    }
    // 兼容兜底：动态猜测（所有文件都有的前6列）
    const defaultCols = allAvailableColumns.value
      .filter(c => c.count === tabs.value.length)
      .map(c => c.name)
      .slice(0, 6)
    globalFilterColumns.value = defaultCols
    defaultCols.forEach(col => {
      if (!globalFilters.value[col]) {
        globalFilters.value[col] = { min: '', max: '' }
      }
    })
  }
})

const removeGlobalFilterCol = (col) => {
  globalFilterColumns.value = globalFilterColumns.value.filter(c => c !== col)
  if (globalFilters.value[col]) {
    globalFilters.value[col] = { min: '', max: '' }
  }
}

const clearAllFilters = () => {
  tabs.value.forEach(tab => {
    if (tab.filters) {
      Object.keys(tab.filters).forEach(col => { tab.filters[col] = { min: '', max: '' } })
    }
    if (tab.textFilterValues) {
      Object.keys(tab.textFilterValues).forEach(col => { tab.textFilterValues[col] = [] })
    }
    tab.hasFiltered = false
    tab.currentPage = 1
  })
  fetchPageData(tabs.value.find(t => t.name === activeTabName.value) || tabs.value[0])
  ElMessage.success('已清除所有筛选条件')
}

const clearCurrentTabFilters = () => {
  const tab = currentTab.value
  if (!tab) return
  if (tab.filters) {
    Object.keys(tab.filters).forEach(col => { tab.filters[col] = { min: '', max: '' } })
  }
  if (tab.textFilterValues) {
    Object.keys(tab.textFilterValues).forEach(col => { tab.textFilterValues[col] = [] })
  }
  tab.hasFiltered = false
  tab.currentPage = 1
  ElMessage.success('已清除当前文件筛选条件')
}

const applyDrawerFilters = () => {
  if (filterScope.value === 'all') {
    // 全局模式：合并到所有文件，保留未在筛选列中的列的原值
    const mergedFilters = JSON.parse(JSON.stringify(globalFilters.value))
    const mergedTextFilters = JSON.parse(JSON.stringify(globalTextFilterValues.value))
    tabs.value.forEach(tab => {
      ;(tab.columns || []).forEach(col => {
        if (!tab.filters[col]) tab.filters[col] = { min: '', max: '' }
        if (!tab.textFilterValues[col]) tab.textFilterValues[col] = []
      })
      Object.entries(mergedFilters).forEach(([col, range]) => {
        tab.filters[col] = { min: range.min || '', max: range.max || '' }
      })
      Object.entries(mergedTextFilters).forEach(([col, vals]) => {
        tab.textFilterValues[col] = vals || []
      })
      tab.currentPage = 1
      tab.hasFiltered = true
      fetchPageData(tab)
    })
    ElMessage.success(`已应用全局筛选到 ${tabs.value.length} 个文件`)
  } else {
    // 单文件模式
    const tab = currentTab.value
    if (!tab) return
    applyFilters(tab)
  }
  filterDrawerVisible.value = false
}

const openPresetPicker = async () => {
  await loadPresets()
  presetPickerVisible.value = true
}

const loadPresets = async () => {
  presetLoading.value = true
  try {
    const res = await listPresets()
    presetList.value = Array.isArray(res) ? res : []
  } catch (error) {
    console.error('加载预设失败', error)
    presetList.value = []
  } finally {
    presetLoading.value = false
  }
}

const parseJson = (value, fallback) => {
  if (!value) return fallback
  try {
    return JSON.parse(value)
  } catch {
    return fallback
  }
}

const getFilterEntries = (filtersJson) => {
  const filters = parseJson(filtersJson, {})
  return Object.entries(filters)
    .filter(([, v]) => v && (v.min || v.max))
    .map(([col, v]) => ({ col, min: v.min || '', max: v.max || '' }))
}

const saveCurrentAsPreset = () => {
  const tab = currentTab.value
  if (!tab) {
    ElMessage.warning('请先选择一个文件')
    return
  }
  presetNameInput.value = tab.title || '未命名预设'
  ElMessageBox.prompt('请输入预设名称', '保存为预设', {
    confirmButtonText: '保存',
    cancelButtonText: '取消',
    inputValue: presetNameInput.value,
    inputPlaceholder: '如：高GR异常'
  }).then(async ({ value }) => {
    const name = (value || '').trim()
    if (!name) {
      ElMessage.warning('预设名称不能为空')
      return
    }
    presetSaving.value = true
    try {
      const payload = {
        name,
        scope: filterScope.value,
        columnsJson: JSON.stringify(filterScope.value === 'all' ? globalFilterColumns.value : (tab.columns || [])),
        filtersJson: JSON.stringify(filterScope.value === 'all' ? globalFilters.value : (tab.filters || {})),
        textFiltersJson: JSON.stringify(filterScope.value === 'all' ? globalTextFilterValues.value : (tab.textFilterValues || {}))
      }
      await savePreset(payload)
      ElMessage.success('预设保存成功')
      await loadPresets()
    } catch (error) {
      console.error(error)
      ElMessage.error('预设保存失败')
    } finally {
      presetSaving.value = false
    }
  }).catch(() => {})
}

const applyPresetToCurrentTab = (row) => {
  const tab = currentTab.value
  if (!tab || !row) return
  const columns = parseJson(row.columnsJson, [])
  const filters = parseJson(row.filtersJson, {})
  const textFilters = parseJson(row.textFiltersJson, {})

  if (row.scope === 'all') {
    // 先设置数据，再切换 scope，避免 watcher 异步回填空值覆盖预设数据
    globalFilterColumns.value = columns
    globalFilters.value = filters
    globalTextFilterValues.value = textFilters
    filterScope.value = 'all'
  } else {
    // 当前文件：merge 而非替换，保留所有列结构
    ;(tab.columns || []).forEach(col => {
      if (!tab.filters[col]) tab.filters[col] = { min: '', max: '' }
      if (!tab.textFilterValues[col]) tab.textFilterValues[col] = []
    })
    Object.entries(filters).forEach(([col, range]) => {
      tab.filters[col] = { min: range.min || '', max: range.max || '' }
    })
    Object.entries(textFilters).forEach(([col, vals]) => {
      tab.textFilterValues[col] = vals || []
    })
    filterScope.value = 'current'
  }
  presetPickerVisible.value = false
  ElMessage.success('预设已加载，可直接点击应用筛选')
}

const loadPresetFromRow = (row) => {
  applyPresetToCurrentTab(row)
}

const filePageParams = reactive({
  current: 1,
  size: 10,
  fileName: ''
})

const analysisParams = ref({
  field: 'GR',
  operator: '>=',
  threshold: 150,
  minPoints: 3
})

let tabIndex = 0

const activeStep = computed(() => {
  if (tabs.value.length === 0) return 0
  const hasExtracted = tabs.value.some(tab => tab.displayData?.length > 0 && tab.hasFiltered)
  return hasExtracted ? 2 : 1
})

const activeTabColumns = computed(() => {
  if (!activeTabName.value) return []
  const currentTab = tabs.value.find(tab => tab.name === activeTabName.value)
  return currentTab ? currentTab.columns : []
})

const openFileSelector = () => {
  fileDialogVisible.value = true
  fetchFilePage()
}

const handleFileSearch = () => {
  filePageParams.current = 1
  fetchFilePage()
}

const fetchFilePage = async () => {
  fileTableLoading.value = true
  try {
    const res = await getFilePage(filePageParams)
    fileTableData.value = res.records || []
    fileTotal.value = res.total || 0
  } catch (error) {
    ElMessage.error('获取文件列表失败')
  } finally {
    fileTableLoading.value = false
  }
}

const handleFileSizeChange = (val) => {
  filePageParams.size = val
  fetchFilePage()
}

const handleFileCurrentChange = (val) => {
  filePageParams.current = val
  fetchFilePage()
}

const handleSelectionChange = (rows) => {
  dialogSelectedRows.value = rows
}

const confirmFileSelection = () => {
  dialogSelectedRows.value.forEach(row => {
    if (!fileList.value.some(file => file.id === row.id)) {
      fileList.value.push(row)
    }
  })
  selectedFiles.value = dialogSelectedRows.value.map(row => row.id)
  handleFileSelect()
  fileDialogVisible.value = false
}

const loadFileList = async () => {
  try {
    const res = await getFileList()
    fileList.value = res || []
  } catch (error) {
    ElMessage.error('获取挂载文件列表失败')
  }
}

onMounted(() => {
  loadFileList()
})

const updateGlobalFilters = () => {
  const keys = new Set()
  tabs.value.forEach(tab => tab.columns.forEach(col => keys.add(col)))

  keys.forEach(key => {
    if (!globalFilters.value[key]) {
      globalFilters.value[key] = { min: '', max: '' }
    }
  })

  tabs.value.forEach(tab => {
    tab.columns.forEach(col => {
      if (!tab.filters[col]) {
        tab.filters[col] = { min: '', max: '' }
      }
    })
  })
}

const buildFiltersParam = (tabObj) => {
  const params = {}
  if (tabObj.filters) {
    for (const [col, range] of Object.entries(tabObj.filters)) {
      // 文本列：传 values
      if (tabObj.textColumns && tabObj.textColumns.includes(col)) {
        const values = tabObj.textFilterValues?.[col]
        if (values && values.length > 0) {
          params[col] = { values }
        }
        continue
      }
      // 定量列：传 min/max
      if ((range.min !== '' && range.min !== null) || (range.max !== '' && range.max !== null)) {
        params[col] = {
          min: range.min !== '' ? Number(range.min) : null,
          max: range.max !== '' ? Number(range.max) : null
        }
      }
    }
  }
  return Object.keys(params).length > 0 ? params : null
}

const fetchPageData = async (tabObj) => {
  tabObj.loading = true
  try {
    const payload = {
      fileId: tabObj.fileId,
      current: tabObj.currentPage,
      size: tabObj.pageSize,
      filters: buildFiltersParam(tabObj)
    }
    const res = await getPageData(payload)
    tabObj.displayData = res.records || []
    tabObj.totalRows = res.total || 0
  } catch (error) {
    console.error(error)
    tabObj.displayData = []
    ElMessage.error('获取数据失败，请重试')
  } finally {
    tabObj.loading = false
  }
}

const handleSizeChange = (tabObj, size) => {
  tabObj.pageSize = size
  tabObj.currentPage = 1
  fetchPageData(tabObj)
}

const handleCurrentChange = (tabObj, page) => {
  tabObj.currentPage = page
  fetchPageData(tabObj)
}

const handleFileSelect = () => {
  const selectedIds = selectedFiles.value

  tabs.value = tabs.value.filter(tab => selectedIds.includes(tab.fileId))

  selectedIds.forEach(id => {
    if (!tabs.value.some(tab => tab.fileId === id)) {
      const fileInfo = fileList.value.find(file => file.id === id)
      if (!fileInfo) return

      let parsedColumns = []
      try {
        parsedColumns = JSON.parse(fileInfo.columnsJson || '[]')
      } catch (error) {
        console.error('Failed to parse columnsJson', error)
      }

      const newTab = {
        title: fileInfo.fileName,
        name: `tab_${++tabIndex}`,
        fileId: fileInfo.id,
        columns: parsedColumns,
        displayData: [],
        filters: Object.fromEntries(parsedColumns.map(col => [col, { min: '', max: '' }])),
        textColumns: [],
        textFilterValues: {},
        textColumnOptions: {},
        textConfigVisible: false,
        currentPage: 1,
        pageSize: 100,
        totalRows: fileInfo.totalRows || 0,
        originalRows: fileInfo.totalRows || 0,
        fileTotalRows: fileInfo.totalRows || 0,
        loading: false,
        hasFiltered: false
      }

      tabs.value.push(newTab)
      // Vue 3 响应式：必须传递加入了 ref 数组后的代理对象，否则内部 loading 状态不更新
      fetchPageData(tabs.value[tabs.value.length - 1])
      loadTextColumns(tabs.value[tabs.value.length - 1])
    }
  })

  // 清除全局文本列选项缓存（tab 变化后需重新聚合）
  globalTextColumnOptions.value = {}

  if (tabs.value.length > 0 && !tabs.value.some(tab => tab.name === activeTabName.value)) {
    activeTabName.value = tabs.value[tabs.value.length - 1].name
  } else if (tabs.value.length === 0) {
    activeTabName.value = ''
    analysisResults.value = []
    analysisResultsEmpty.value = false
  }

  updateGlobalFilters()
}

const closeAllTabs = () => {
  tabs.value = []
  activeTabName.value = ''
  selectedFiles.value = []
  dialogSelectedRows.value = []
  globalFilters.value = {}
  analysisResults.value = []
  analysisResultsEmpty.value = false
  if (fileTableRef.value) {
    fileTableRef.value.clearSelection()
  }
}

const removeTab = (targetName) => {
  const currentTabs = tabs.value
  let nextActiveName = activeTabName.value
  const targetTab = currentTabs.find(tab => tab.name === targetName)

  if (targetTab) {
    selectedFiles.value = selectedFiles.value.filter(id => id !== targetTab.fileId)
    dialogSelectedRows.value = dialogSelectedRows.value.filter(row => row.id !== targetTab.fileId)
    if (fileTableRef.value) {
      const rowToUncheck = fileTableData.value.find(row => row.id === targetTab.fileId) || { id: targetTab.fileId }
      fileTableRef.value.toggleRowSelection(rowToUncheck, false)
    }
  }

  if (nextActiveName === targetName) {
    currentTabs.forEach((tab, index) => {
      if (tab.name === targetName) {
        const nextTab = currentTabs[index + 1] || currentTabs[index - 1]
        if (nextTab) {
          nextActiveName = nextTab.name
        }
      }
    })
  }

  activeTabName.value = nextActiveName
  tabs.value = currentTabs.filter(tab => tab.name !== targetName)

  // 清除全局文本列选项缓存
  globalTextColumnOptions.value = {}

  if (tabs.value.length === 0) {
    analysisResults.value = []
    analysisResultsEmpty.value = false
  }

  updateGlobalFilters()
}

const applyGlobalFilters = () => {
  if (tabs.value.length === 0) {
    ElMessage.warning('目前没有任何文件可以提取')
    return
  }

  tabs.value.forEach(tab => {
    tab.filters = JSON.parse(JSON.stringify(globalFilters.value))
    applyFilters(tab)
  })
  ElMessage.success(`已统一套用筛选条件至全部 ${tabs.value.length} 个文件`)
}

const applyFilters = (tabObj) => {
  tabObj.currentPage = 1
  tabObj.hasFiltered = true
  fetchPageData(tabObj)
  ElMessage.success('已应用新的阈值筛选条件，正在拉取大数据...')
}

const clearFilters = (tabObj) => {
  ;(tabObj.columns || []).forEach(col => {
    tabObj.filters[col] = { min: '', max: '' }
  })
  if (tabObj.textFilterValues) {
    Object.keys(tabObj.textFilterValues).forEach(col => {
      tabObj.textFilterValues[col] = []
    })
  }
  tabObj.hasFiltered = false
  tabObj.currentPage = 1
  fetchPageData(tabObj)
  ElMessage.success('过滤条件已重置')
}

const clearGlobalFilters = () => {
  // 清空全局筛选条件
  Object.keys(globalFilters.value).forEach(key => {
    globalFilters.value[key] = { min: '', max: '' }
  })
  // 同步清空所有 tab 的筛选条件并重新加载
  tabs.value.forEach(tab => {
    ;(tab.columns || []).forEach(col => {
      tab.filters[col] = { min: '', max: '' }
    })
    tab.hasFiltered = false
    tab.currentPage = 1
    fetchPageData(tab)
  })
  ElMessage.success('所有筛选条件已清除')
}

const exportToExcel = async (tabObj) => {
  if (!tabObj.fileId) {
    ElMessage.warning('当前没有任何数据归档信息可以导出')
    return
  }

  try {
    ElMessage.info('正在生成当前文件筛选结果，这可能需要几十秒，请稍候...')
    const blob = await exportFilteredExcel(tabObj.fileId, { filters: buildFiltersParam(tabObj) })
    const url = window.URL.createObjectURL(new Blob([blob]))
    const link = document.createElement('a')
    link.style.display = 'none'
    link.href = url
    link.setAttribute('download', `${tabObj.title}_大数据归档报表.xlsx`)
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)

    ElMessage.success('大数据归档报表导出成功，浏览器即将开始下载')
  } catch (error) {
    console.error(error)
    ElMessage.error('报表导出失败，请检查网络设置或查看后台日志')
  }
}

// 导出当前激活页签的筛选结果（单文件 Excel）
const handleExportCurrent = () => {
  const tab = currentTab.value
  if (!tab) {
    ElMessage.warning('请先选择一个数据页签再导出')
    return
  }
  exportToExcel(tab)
}

const handleBatchExport = async () => {
  if (tabs.value.length === 0) {
    ElMessage.warning('没有可导出的数据文件')
    return
  }

  try {
    ElMessage.info('后端正流式组装并压缩导出数据，请耐心等待...')
    const queries = tabs.value.map(tab => ({
      fileId: tab.fileId,
      filters: buildFiltersParam(tab)
    }))
    const blob = await exportBatchZipStream(queries)
    const url = window.URL.createObjectURL(new Blob([blob]))
    const link = document.createElement('a')
    link.style.display = 'none'
    link.href = url
    link.setAttribute('download', '批量档案导出.zip')
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    ElMessage.success('批量压缩包导出成功')
  } catch (error) {
    console.error(error)
    ElMessage.error('批量压缩导出失败，请重试')
  }
}

const downloadAnalysisExcel = () => {
  if (analysisResults.value.length === 0) {
    ElMessage.warning('没有可导出的提取结果')
    return
  }

  const hasLayer = analysisResults.value.some(seg => seg.layerName)
  let csvHeader = hasLayer
    ? '\uFEFF提取序号,顶深(m),底深(m),厚度(m),层位,数据点数,平均值\n'
    : '\uFEFF提取序号,顶深(m),底深(m),厚度(m),数据点数,平均值\n'

  let csvContent = csvHeader
  analysisResults.value.forEach((seg, index) => {
    if (hasLayer) {
      csvContent += `${index + 1},${seg.startDepth.toFixed(4)},${seg.endDepth.toFixed(4)},${seg.thickness.toFixed(4)},${seg.layerName || ''},${seg.pointCount},${seg.avgValue.toFixed(4)}\n`
    } else {
      csvContent += `${index + 1},${seg.startDepth.toFixed(4)},${seg.endDepth.toFixed(4)},${seg.thickness.toFixed(4)},${seg.pointCount},${seg.avgValue.toFixed(4)}\n`
    }
  })

  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.style.display = 'none'
  link.href = url
  link.setAttribute('download', `异常地层提取结果_${analysisParams.value.field}.csv`)
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)

  ElMessage.success('异常测段结果已导出')
}

const extractContinuousSegments = async () => {
  if (analysisLoading.value) {
    return
  }

  if (!activeTabName.value) {
    ElMessage.warning('请先选择一个数据页签进行分析')
    return
  }

  const currentTab = tabs.value.find(tab => tab.name === activeTabName.value)
  if (!currentTab) {
    ElMessage.warning('当前页签无数据')
    return
  }

  const field = analysisParams.value.field
  const op = analysisParams.value.operator
  const threshold = Number(analysisParams.value.threshold)
  const minPoints = analysisParams.value.minPoints

  if (!field) {
    ElMessage.warning('请选择目标字段')
    return
  }

  if (Number.isNaN(threshold)) {
    ElMessage.warning('请输入有效的阈值')
    return
  }

  // 加载分层配置
  let layers = []
  try {
    layers = (await getFileLayers(currentTab.fileId)) || []
  } catch (e) {
    layers = []
  }
  layers.sort((a, b) => Number(a.topDepth) - Number(b.topDepth))

  const matchLayer = (depth) => {
    for (const l of layers) {
      if (depth >= Number(l.topDepth) && depth < Number(l.bottomDepth)) return l.layerName
    }
    return ''
  }

  const depthCol = currentTab.columns.find(col => ['DEPTH', '深度', 'TVD'].includes(col.toUpperCase()) || col.includes('深')) || currentTab.columns[0]
  const pageSize = 5000
  let currentPage = 1
  let hasMore = true
  let safetyGuard = 0
  let currentSegment = null
  const segments = []

  analysisLoading.value = true
  analysisResults.value = []
  analysisResultsEmpty.value = false
  ElMessage.info(layers.length > 0 ? '已加载分层配置，正在分批读取并切割跨层段...' : '正在分批读取全量序列，请稍候...')

  try {
    while (hasMore && safetyGuard < 1000) {
      safetyGuard += 1
      const res = await getPageData({
        fileId: currentTab.fileId,
        current: currentPage,
        size: pageSize
      })
      const records = res.records || []

      for (let i = 0; i < records.length; i += 1) {
        const row = records[i]
        const rawValue = Number(row[field])
        const depth = Number(row[depthCol])

        if (row[field] == null || Number.isNaN(rawValue) || Number.isNaN(depth)) {
          if (currentSegment && currentSegment.pointCount >= minPoints) {
            segments.push(currentSegment)
          }
          currentSegment = null
          continue
        }

        const isMatch = op === '>=' ? rawValue >= threshold : rawValue <= threshold

        if (isMatch) {
          const currentLayer = layers.length > 0 ? matchLayer(depth) : ''

          if (!currentSegment) {
            currentSegment = {
              startDepth: depth,
              endDepth: depth,
              pointCount: 1,
              sumValue: rawValue,
              layerName: currentLayer
            }
          } else {
            // 检查是否跨越分层边界
            if (layers.length > 0 && currentSegment.layerName !== currentLayer) {
              // 关闭当前段
              if (currentSegment.pointCount >= minPoints) {
                segments.push(currentSegment)
              }
              // 开启新段
              currentSegment = {
                startDepth: depth,
                endDepth: depth,
                pointCount: 1,
                sumValue: rawValue,
                layerName: currentLayer
              }
            } else {
              currentSegment.endDepth = Math.max(currentSegment.endDepth, depth)
              currentSegment.pointCount += 1
              currentSegment.sumValue += rawValue
            }
          }
        } else if (currentSegment) {
          if (currentSegment.pointCount >= minPoints) {
            segments.push(currentSegment)
          }
          currentSegment = null
        }
      }

      const total = Number(res.total || 0)
      hasMore = records.length === pageSize && currentPage * pageSize < total
      currentPage += 1
    }

    if (currentSegment && currentSegment.pointCount >= minPoints) {
      segments.push(currentSegment)
    }

    analysisResults.value = segments.map(seg => ({
      startDepth: seg.startDepth,
      endDepth: seg.endDepth,
      thickness: Math.abs(seg.endDepth - seg.startDepth),
      pointCount: seg.pointCount,
      avgValue: seg.sumValue / seg.pointCount,
      layerName: seg.layerName || ''
    }))

    analysisResultsEmpty.value = analysisResults.value.length === 0
    if (analysisResultsEmpty.value) {
      ElMessage.warning('未发现满足要求的连续异常测段')
    } else {
      const layerInfo = layers.length > 0 ? `，跨层段已自动切割` : ''
      ElMessage.success(`分析完成，共提取 ${analysisResults.value.length} 段连续异常地层${layerInfo}`)
    }
  } catch (error) {
    console.error(error)
    ElMessage.error('异常测段提取失败，请稍后重试')
  } finally {
    analysisLoading.value = false
  }
}

// ==================== 文本列筛选 ====================

const loadTextColumns = async (tabObj) => {
  try {
    const mapping = await getTextColumns(tabObj.fileId)
    if (mapping && Object.keys(mapping).length > 0) {
      tabObj.textColumns = Object.keys(mapping)
      tabObj.textColumns.forEach(col => {
        if (!tabObj.textFilterValues[col]) tabObj.textFilterValues[col] = []
      })
      await Promise.all(tabObj.textColumns.map(col => loadTextColumnOptions(tabObj, col)))
    }
  } catch (error) {
    // 没有配置过文本列，忽略
  }
}

const loadTextColumnOptions = async (tabObj, colName) => {
  try {
    const values = await getDistinctValues(tabObj.fileId, colName)
    tabObj.textColumnOptions[colName] = values || []
  } catch (error) {
    tabObj.textColumnOptions[colName] = []
  }
}

const handleTextColumnConfigSave = async (tabObj) => {
  if (!tabObj) return
  if (!tabObj.textColumns || tabObj.textColumns.length === 0) {
    ElMessage.warning('请至少选择一个文本列')
    return
  }
  if (tabObj.textColumns.length > 10) {
    ElMessage.warning('文本列最多支持 10 个')
    return
  }
  textColumnSaving.value = true
  try {
    const mapping = {}
    tabObj.textColumns.forEach((col, idx) => { mapping[col] = `text_col_${idx + 1}` })
    const res = await saveTextColumns(tabObj.fileId, mapping)
    tabObj.textColumns.forEach(col => {
      if (!tabObj.textFilterValues[col]) tabObj.textFilterValues[col] = []
    })
    for (const col of tabObj.textColumns) {
      await loadTextColumnOptions(tabObj, col)
    }
    ElMessage.success(res || '文本列配置已保存，数据回填完成')
    // 清除全局文本列选项缓存，因为配置变更了
    globalTextColumnOptions.value = {}
  } catch (error) {
    ElMessage.error(error.message || '文本列配置保存失败')
  } finally {
    textColumnSaving.value = false
  }
}

const isTextColumn = (tabObj, colName) => {
  return tabObj.textColumns && tabObj.textColumns.includes(colName)
}
</script>

<style scoped>
.files-container {
  height: 100%;
}

.mb-20 {
  margin-bottom: 20px;
}

/* ① 紧凑标题栏 */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
}

.page-header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.page-header-right {
  display: flex;
  gap: 10px;
}

.split-layout {
  display: flex;
  gap: 20px;
}

.tabs-card {
  min-height: 500px;
  flex: 1;
  min-width: 0;
}

.empty-state {
  padding: 60px 0;
}

.filter-bar {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.filter-row {
  width: 100%;
}

.filter-item-col {
  margin-bottom: 12px;
}

/* ⑤ 筛选操作区布局 */
.filter-actions-col {
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.filter-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.filter-actions :deep(.el-button) {
  display: block;
  width: 100%;
  margin-left: 0;
  box-sizing: border-box;
}

.filter-stats-bar {
  display: flex;
  align-items: baseline;
  gap: 6px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}

.count-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.count-value {
  font-size: 20px;
  font-family: var(--font-mono);
  color: var(--el-color-primary);
}

.count-unit {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

/* ④ 列名自适应宽度 */
.filter-field {
  display: flex;
  align-items: center;
  width: 100%;
  gap: 6px;
}

.filter-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-regular);
  white-space: nowrap;
  min-width: 40px;
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  flex-shrink: 0;
}

.range-input {
  width: 120px;
}

.table-wrapper {
  overflow: hidden;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
}

.pagination-container {
  margin-top: 15px;
  display: flex;
  justify-content: flex-end;
}

.dialog-toolbar {
  margin-bottom: 15px;
  display: flex;
  gap: 10px;
}

.dialog-search-input {
  width: 250px;
}

.dialog-pagination {
  margin-top: 15px;
  display: flex;
  justify-content: flex-end;
}

.p-15 {
  padding: 15px 20px;
}

/* ③ 全局筛选 vs 单文件筛选视觉区分 */
.global-filters-card {
  border: 2px dashed var(--el-border-color);
  border-radius: 10px;
  background: var(--el-fill-color-lighter);
}

.global-filters-card :deep(.el-card__body) {
  padding: 16px 20px;
}

.per-file-filter {
  background: var(--el-bg-color-page);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
}

.filter-label {
  font-size: 14px;
  font-weight: 700;
  color: #606266;
  white-space: nowrap;
}

.separator {
  margin: 0 4px;
  color: var(--el-text-color-placeholder);
}

:deep(.el-input-group__prepend) {
  background-color: #fff;
}

.filter-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.filter-header .title {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.filter-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.f-group {
  display: flex;
  align-items: center;
  background: var(--el-bg-color-page);
  padding: 4px;
  border-radius: 6px;
  border: 1px solid var(--el-border-color-lighter);
}

.f-label {
  min-width: 50px;
  padding: 0 10px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-color-primary);
}

.f-input {
  width: 80px;
}

.f-input :deep(.el-input__wrapper) {
  box-shadow: none !important;
  background: #fff;
  border-radius: 4px;
}

.f-sep {
  margin: 0 8px;
  color: var(--el-text-color-placeholder);
}

.button-icon {
  margin-right: 5px;
}

.analysis-card {
  width: 320px;
  min-height: 500px;
  flex-shrink: 0;
}

.analysis-card__header {
  margin-bottom: 20px;
}

.analysis-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.analysis-subtitle {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.analysis-card__body {
  display: flex;
  flex-direction: column;
}

.analysis-operator-row {
  display: flex;
  gap: 10px;
}

.operator-select {
  width: 100px;
}

.full-width {
  width: 100%;
}

.analysis-submit {
  width: 100%;
  margin-top: 10px;
}

.analysis-results {
  margin-top: 25px;
  padding-top: 15px;
  border-top: 1px dashed #dcdfe6;
}

.analysis-results__header {
  margin-bottom: 15px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  color: #606266;
}

.analysis-results__list {
  max-height: 480px;
  overflow-y: auto;
  padding-right: 5px;
}

.danger-text {
  color: #f56c6c;
}

.segment-title {
  margin-bottom: 4px;
  font-weight: 700;
  color: #303133;
}

.segment-meta {
  font-size: 12px;
  color: #909399;
}

.segment-highlight {
  margin-top: 4px;
  font-size: 12px;
  color: #e6a23c;
}

.analysis-empty {
  margin-top: 20px;
}

.context-menu {
  margin: 0;
  padding: 5px 0;
  position: fixed;
  z-index: 3000;
  list-style-type: none;
  background: #fff;
  color: #333;
  font-size: 13px;
  border-radius: 4px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

.context-menu li {
  margin: 0;
  padding: 7px 16px;
  cursor: pointer;
}

.context-menu li:hover {
  background: #f0f2f5;
  color: #409eff;
}

@media (max-width: 992px) {
  .filter-actions-col {
    align-items: flex-start;
    margin-top: 15px;
  }

  .filter-actions {
    flex-direction: row;
    justify-content: flex-start;
  }
}

@media (max-width: 768px) {
  .mb-xs {
    margin-bottom: 15px;
  }

  .page-header {
    flex-direction: column;
    gap: 12px;
    align-items: flex-start;
  }

  .page-header-right {
    width: 100%;
  }

  .split-layout {
    flex-direction: column;
  }

  .dialog-toolbar,
  .analysis-results__header,
  .analysis-operator-row {
    flex-direction: column;
    align-items: stretch;
  }

  .analysis-card,
  .dialog-search-input,
  .range-input,
  .operator-select {
    width: 100%;
  }

  .f-group {
    width: 100%;
    justify-content: space-between;
  }

  .filter-field {
    flex-wrap: wrap;
    gap: 6px;
  }

  .filter-label {
    min-width: 100%;
  }

  .dialog-search-input,
  .range-input,
  .operator-select {
    width: 100%;
  }
}

/* ==================== 文本列配置 ==================== */
.text-config-section {
  border: 1px dashed var(--el-border-color-light);
  border-radius: 8px;
  overflow: hidden;
}

.text-config-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  cursor: pointer;
  background: var(--el-fill-color-lighter);
  transition: background 0.2s;
}

.text-config-header:hover {
  background: var(--el-fill-color);
}

.text-config-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.expand-icon {
  margin-left: auto;
  transition: transform 0.3s;
}

.expand-icon.expanded {
  transform: rotate(180deg);
}

.text-col-label {
  color: var(--el-color-primary) !important;
  font-weight: 600;
}

/* ==================== 状态栏 ==================== */
.status-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: var(--el-fill-color-lighter);
  border-radius: 8px;
}

.status-bar-left {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.page-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.status-sep {
  color: var(--el-border-color);
  margin: 0 2px;
}

.status-item {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.status-item strong {
  color: var(--el-text-color-primary);
  font-size: 14px;
}

.status-active strong {
  color: var(--el-color-primary);
}

.rate-warn {
  color: var(--el-color-warning) !important;
}

/* ==================== 工具栏 ==================== */
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  gap: 12px;
  flex-wrap: wrap;
}

.toolbar-group {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.filter-badge {
  margin-left: 4px;
}

.filter-badge :deep(.el-badge__content) {
  background: var(--el-color-primary);
}

/* ==================== 空状态 ==================== */
.empty-state-main {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400px;
}

/* ==================== 抽屉 ==================== */
.drawer-body {
  padding: 0;
  height: 100%;
  overflow-y: auto;
}

.drawer-section {
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.drawer-section:last-child {
  border-bottom: none;
}

.drawer-section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px 20px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  cursor: pointer;
  background: var(--el-fill-color-lighter);
  transition: background 0.2s;
}

.drawer-section-title:hover {
  background: var(--el-fill-color);
}

.drawer-section-body {
  padding: 20px;
}

/* 筛选范围栏 */
.filter-scope-tabs {
  display: flex;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.scope-tab {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 16px 12px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  border-bottom: 2px solid transparent;
  transition: all 0.2s;
  background: var(--el-fill-color-lighter);
}

.scope-tab:hover {
  color: var(--el-text-color-primary);
  background: var(--el-fill-color);
}

.scope-tab.active {
  color: var(--el-color-primary);
  border-bottom-color: var(--el-color-primary);
  background: var(--el-bg-color);
}

.scope-tab-sub {
  font-size: 12px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.scope-tab.active .scope-tab-sub {
  color: var(--el-color-primary-light-5);
}

/* 全局筛选头部 */
.global-filter-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 20px;
  padding: 10px 14px;
  background: var(--el-color-info-light-9);
  border-radius: 8px;
  border-left: 3px solid var(--el-color-info);
}

.global-filter-tip {
  font-size: 13px;
  color: var(--el-color-info);
}

.remove-col-icon {
  cursor: pointer;
  color: var(--el-text-color-secondary);
  font-size: 14px;
  margin-left: 4px;
  transition: color 0.2s;
}

.remove-col-icon:hover {
  color: var(--el-color-danger);
}

.drawer-filter-item {
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}

.drawer-filter-item:last-child {
  margin-bottom: 0;
  padding-bottom: 0;
  border-bottom: none;
}

.drawer-filter-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-regular);
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.drawer-range-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.drawer-range-sep {
  color: var(--el-text-color-secondary);
  flex-shrink: 0;
  font-size: 12px;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 0 4px;
}

.condition-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
}

.condition-toggle-icon {
  transition: transform 0.2s;
}

.condition-toggle-icon.expanded {
  transform: rotate(180deg);
}

.conditions-panel {
  margin: 8px 0 16px;
  padding: 12px 14px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.condition-tag {
  max-width: 100%;
}

.conditions-empty {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.preset-picker-table .el-table__row {
  cursor: pointer;
}

/* ==================== 预设展开详情 ==================== */
.preset-expand-detail {
  padding: 6px 16px 8px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.preset-expand-row {
  display: flex;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 4px;
}

.preset-expand-label {
  flex-shrink: 0;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  line-height: 24px;
}

.preset-filter-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-right: 12px;
  padding: 2px 8px;
  background: var(--el-fill-color-lighter);
  border-radius: 4px;
  font-size: 12px;
  line-height: 22px;
}

.preset-filter-col {
  font-weight: 600;
  color: var(--el-color-primary);
}

.preset-filter-range {
  color: var(--el-text-color-regular);
}

</style>
