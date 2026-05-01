<template>
  <div class="files-container">
    <el-card shadow="never" class="mb-20 step-card">
      <el-steps :active="activeStep" align-center finish-status="success">
        <el-step title="第一步：数据选择" description="从数据源选择文件" />
        <el-step title="第二步：阈值筛选" description="多文件或单文件条件过滤" />
        <el-step title="第三步：结果导出" description="筛选结果与异常测段导出" />
      </el-steps>
    </el-card>

    <el-card shadow="hover" class="action-card mb-20">
      <el-row :gutter="20" align="middle">
        <el-col :xs="24" :sm="24" :md="6" class="mb-xs">
          <div class="action-title">数据选择</div>
          <p class="action-desc">从基础数据源中心选择已挂载的测井文件。</p>
        </el-col>

        <el-col :xs="24" :sm="24" :md="18" class="action-buttons-col">
          <el-button type="primary" icon="FolderOpened" @click="openFileSelector">选择文件</el-button>
          <el-button type="danger" icon="Delete" plain @click="closeAllTabs" :disabled="!tabs.length">清空已选文件</el-button>
        </el-col>
      </el-row>
    </el-card>

    <el-card v-if="tabs.length > 0" shadow="never" class="elegant-filters mb-20">
      <div class="filter-header">
        <span class="title">多文件统一筛选</span>
        <div class="actions">
          <el-button type="primary" icon="Filter" @click="applyGlobalFilters">统一提取全部</el-button>
          <el-button color="var(--el-color-success)" icon="Download" plain @click="handleBatchExport">批量导出</el-button>
        </div>
      </div>

      <div class="filter-grid">
        <div v-for="key in Object.keys(globalFilters)" :key="key" class="f-group">
          <span class="f-label">{{ key }}</span>
          <el-input v-model="globalFilters[key].min" class="f-input" placeholder="Min" clearable />
          <span class="f-sep">-</span>
          <el-input v-model="globalFilters[key].max" class="f-input" placeholder="Max" clearable />
        </div>
      </div>
    </el-card>

    <div class="split-layout">
      <el-card shadow="always" class="tabs-card">
        <div v-if="tabs.length === 0" class="empty-state">
          <el-empty description="暂无测井数据，请先在上方选择文件" />
        </div>

        <el-tabs
          v-else
          v-model="activeTabName"
          type="border-card"
          closable
          @tab-remove="removeTab"
        >
          <el-tab-pane
            v-for="item in tabs"
            :key="item.name"
            :label="item.title"
            :name="item.name"
          >
            <div class="filter-bar mb-20 p-15 bg-gray">
              <el-row :gutter="20" class="filter-row" align="middle">
                <el-col :xs="24" :lg="16">
                  <el-row :gutter="20">
                    <el-col
                      v-for="col in item.columns"
                      :key="col"
                      :xs="24"
                      :sm="12"
                      :md="12"
                      class="filter-item-col"
                    >
                      <div class="flex-center">
                        <span class="filter-label filter-label-fixed">{{ col }}</span>
                        <el-input v-model="item.filters[col].min" class="range-input" placeholder="最小值" clearable />
                        <span class="separator">-</span>
                        <el-input v-model="item.filters[col].max" class="range-input" placeholder="最大值" clearable />
                      </div>
                    </el-col>
                  </el-row>
                </el-col>

                <el-col :xs="24" :lg="8" class="filter-actions-col">
                  <div class="filter-actions">
                    <el-button type="primary" icon="Filter" @click="applyFilters(item)">提取数据</el-button>
                    <el-button type="info" icon="Refresh" plain @click="clearFilters(item)">清除筛选</el-button>
                    <el-button type="success" icon="Download" plain @click="exportToExcel(item)">导出 Excel</el-button>
                  </div>
                  <div class="count-tip">
                    筛选总记录数：<strong class="count-value">{{ item.totalRows }}</strong> 行
                  </div>
                </el-col>
              </el-row>
            </div>

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
    </div>

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
        <el-table-column type="selection" width="55" :reserve-selection="true" />
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
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowDown, Download } from '@element-plus/icons-vue'
import { exportBatchZipStream, exportFilteredExcel, getFileList, getFilePage, getPageData } from '@/api/file'

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
        currentPage: 1,
        pageSize: 100,
        totalRows: fileInfo.totalRows || 0,
        loading: false,
        hasFiltered: false
      }

      tabs.value.push(newTab)
      // Vue 3 响应式：必须传递加入了 ref 数组后的代理对象，否则内部 loading 状态不更新
      fetchPageData(tabs.value[tabs.value.length - 1])
    }
  })

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
  tabObj.hasFiltered = false
  tabObj.currentPage = 1
  fetchPageData(tabObj)
  ElMessage.success('过滤条件已重置')
}

const exportToExcel = async (tabObj) => {
  if (!tabObj.fileId) {
    ElMessage.warning('当前没有任何数据归档信息可以导出')
    return
  }

  try {
    ElMessage.info('正在请求后端生成大数据过滤归档 Excel，这可能需要几十秒，请稍候...')
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

  let csvContent = '\uFEFF提取序号,顶深(m),底深(m),厚度(m),数据点数,平均值\n'
  analysisResults.value.forEach((seg, index) => {
    csvContent += `${index + 1},${seg.startDepth.toFixed(4)},${seg.endDepth.toFixed(4)},${seg.thickness.toFixed(4)},${seg.pointCount},${seg.avgValue.toFixed(4)}\n`
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
  ElMessage.info('正在分批读取全量序列，请稍候...')

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
          if (!currentSegment) {
            currentSegment = {
              startDepth: depth,
              endDepth: depth,
              pointCount: 1,
              sumValue: rawValue
            }
          } else {
            currentSegment.endDepth = Math.max(currentSegment.endDepth, depth)
            currentSegment.pointCount += 1
            currentSegment.sumValue += rawValue
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
      avgValue: seg.sumValue / seg.pointCount
    }))

    analysisResultsEmpty.value = analysisResults.value.length === 0
    if (analysisResultsEmpty.value) {
      ElMessage.warning('未发现满足要求的连续异常测段')
    } else {
      ElMessage.success(`分析完成，共提取 ${analysisResults.value.length} 段连续异常地层`)
    }
  } catch (error) {
    console.error(error)
    ElMessage.error('异常测段提取失败，请稍后重试')
  } finally {
    analysisLoading.value = false
  }
}
</script>

<style scoped>
.files-container {
  height: 100%;
}

.mb-20 {
  margin-bottom: 20px;
}

.action-card {
  border-radius: 12px;
  background: linear-gradient(145deg, #ffffff 0%, #f8fbfd 100%);
}

.action-title {
  font-size: 18px;
  font-weight: 700;
  color: #1f2d3d;
}

.action-desc {
  margin-top: 5px;
  font-size: 13px;
  color: #909399;
}

.action-buttons-col {
  display: flex;
  gap: 10px;
  align-items: center;
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
  align-items: center;
}

.filter-row {
  width: 100%;
}

.filter-item-col {
  margin-bottom: 12px;
}

.filter-actions-col {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: center;
  border-left: 1px solid #ebeef5;
  padding-left: 20px;
}

.filter-actions {
  margin-bottom: 12px;
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.filter-label-fixed {
  width: 60px;
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

.bg-gray {
  background-color: #f8f9fc;
  border: 1px solid #ebeef5;
  border-radius: 6px;
}

.flex-center {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
}

.filter-label {
  font-size: 14px;
  font-weight: 700;
  color: #606266;
  white-space: nowrap;
}

.separator {
  margin: 0 10px;
  color: #909399;
}

.count-tip {
  font-size: 13px;
  color: #606266;
}

.count-value {
  font-size: 16px;
  color: #1890ff;
}

:deep(.el-input-group__prepend) {
  background-color: #fff;
}

.elegant-filters {
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--spacing-2);
  background: var(--el-bg-color);
}

.elegant-filters :deep(.el-card__body) {
  padding: 16px 20px;
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
    border-left: none;
    padding-left: 0;
    margin-top: 15px;
  }

  .filter-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 768px) {
  .mb-xs {
    margin-bottom: 15px;
  }

  .action-buttons-col,
  .dialog-toolbar,
  .analysis-results__header,
  .analysis-operator-row {
    flex-direction: column;
    align-items: stretch;
  }

  .split-layout {
    flex-direction: column;
  }

  .f-group {
    width: 100%;
    justify-content: space-between;
  }

  .analysis-card,
  .dialog-search-input,
  .range-input,
  .operator-select {
    width: 100%;
  }

  .filter-item-col .flex-center {
    flex-wrap: wrap;
    justify-content: flex-start;
    gap: 8px;
  }

  .filter-label-fixed {
    width: 100%;
  }
}
</style>
