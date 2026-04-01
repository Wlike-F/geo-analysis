<template>
  <div class="files-container">
    <!-- 顶部步骤条-->
    <el-card shadow="never" class="mb-20 step-card">
      <el-steps :active="activeStep" align-center finish-status="success">
        <el-step title="第一步：数据选择" description="从数据源选择文件" /> 
        <el-step title="第二步：阈值筛选" description="多文件或单文件条件过滤" />
        <el-step title="第三步：多表导出" description="一键提取并下载Excel" />  
      </el-steps>
    </el-card>


    <!-- 顶部操作区-->
    <el-card shadow="hover" class="action-card mb-20">
      <el-row :gutter="20" align="middle">
        <el-col :xs="24" :sm="24" :md="6" class="mb-xs">
          <div class="action-title">数据选择</div>
          <p class="action-desc">从基础数据源中心选择已挂载的测井文件</p>
        </el-col>

        <el-col :xs="24" :sm="24" :md="18" style="display: flex; gap: 10px; align-items: center;">
          <el-button type="primary" icon="FolderOpened" @click="openFileSelector">选择文件</el-button>
          <el-button type="danger" icon="Delete" plain @click="closeAllTabs" :disabled="!tabs.length">清除看板</el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- 全局多文件批量操作区 -->
    <el-card shadow="never" class="elegant-filters mb-20" v-if="tabs.length > 0">
      <div class="filter-header">
        <span class="title">多文件统一筛选</span>
        <div class="actions">
          <el-button type="primary" icon="Filter" @click="applyGlobalFilters">统一提取全部</el-button>
          <el-dropdown @command="handleExportCommand">
            <el-button color="var(--el-color-success)" plain>
              <el-icon style="margin-right: 5px;"><Download /></el-icon> 批量打包导出<el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="sheet">多Sheet表格 (.xlsx)</el-dropdown-item>
                <el-dropdown-item command="zip">多文件压缩包 (.zip)</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
      <div class="filter-grid">
        <div class="f-group" v-for="key in Object.keys(globalFilters)" :key="key">
          <span class="f-label">{{ key }}</span>
          <el-input class="f-input" v-model="globalFilters[key].min" placeholder="Min" clearable />
          <span class="f-sep">-</span>
          <el-input class="f-input" v-model="globalFilters[key].max" placeholder="Max" clearable />
        </div>
      </div>
    </el-card>

    <!-- 数据与高级分析区 (左右布局: 左侧Tabs, 右侧高级分析) -->
    <div style="display: flex; gap: 20px;" class="split-layout">
      <!-- 左侧: 表格展示 -->
      <el-card shadow="always" class="tabs-card" style="min-height: 500px; flex-grow: 1; min-width: 0; width: calc(100% - 350px);">
      <div v-if="tabs.length === 0" class="empty-state">
        <el-empty description="暂无测井数据，请在上方加载文件" />
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
          <!-- 阈值筛选操作区 -->
          <div class="filter-bar mb-20 p-15 bg-gray">
            <el-row :gutter="20" align="middle" style="width: 100%;">
              <!-- 左侧 2x2 网格条件筛选-->
              <el-col :xs="24" :lg="16">
                <!-- 动态列筛选-->
                <el-row :gutter="20">
                  <el-col :xs="24" :sm="12" :md="12" style="margin-bottom: 12px;" v-for="col in item.columns" :key="col">
                    <div class="flex-center">
                      <span class="filter-label" style="width: 60px; font-weight: bold; color: #606266;">{{ col }}</span>
                      <el-input v-model="item.filters[col].min" placeholder="最小值" clearable style="width: 120px;" />
                      <span class="separator" style="margin: 0 10px; color: #909399;">-</span>
                      <el-input v-model="item.filters[col].max" placeholder="最大值" clearable style="width: 120px;" />
                    </div>
                  </el-col>
                </el-row>
              </el-col>

              <el-col :span="8" style="display: flex; flex-direction: column; align-items: flex-end; justify-content: center; border-left: 1px solid #EBEEF5; padding-left: 20px;">
                <div style="margin-bottom: 12px; display: flex; gap: 10px;">
                  <el-button type="primary" icon="Filter" @click="applyFilters(item)">提取数据</el-button>
                  <el-button type="info" icon="Refresh" @click="clearFilters(item)" plain>清除筛选</el-button>
                  <el-button type="success" icon="Download" @click="exportToExcel(item)" plain>生成 Excel</el-button>
                </div>
                <div class="count-tip" style="font-size: 13px; color: #606266;">
                  筛选总记录数: <strong class="text-primary" style="font-size: 16px;">{{ item.totalRows }}</strong> 条
                </div>
              </el-col>
            </el-row>
          </div>

          <!-- 第一版表格：前端截取前 300 行展示数据 (或使用过滤后的 displayData) -->
          <div class="table-wrapper">
            <el-table
              :data="item.displayData"
              border 
              stripe 
              style="width: 100%"
              height="550"
              v-loading="item.loading"
              element-loading-text="正在极速解析测井数据中..."
              :header-cell-style="{ background: '#f5f7fa', color: '#303133', fontWeight: 'bold' }"
            >
              <!-- 增加自增序号体现测点 -->
              <el-table-column type="index" label="测点序列" width="80" align="center" fixed />
                <el-table-column 
                  v-for="col in item.columns" 
                  :key="col" 
                  :prop="col" 
                  :label="col" 
                  min-width="140"
                  align="center"
                />
              </el-table>
          </div>

          <!-- 分页组件 -->
          <div class="pagination-container" style="margin-top: 15px; display: flex; justify-content: flex-end;">
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

    <!-- 右侧: 连续测段异常厚度分析 -->
    <el-card shadow="always" class="analysis-card" style="width: 320px; min-height: 500px; flex-shrink: 0;" v-if="tabs.length > 0">
      <div style="font-size: 16px; font-weight: bold; margin-bottom: 20px;">连续异常测段提取</div>

      <el-form label-position="top" size="small">
        <el-form-item label="目标字段">
          <el-select v-model="analysisParams.field" placeholder="选择字段" style="width: 100%">
              <el-option v-for="col in activeTabColumns" :key="col" :label="col" :value="col" />
            </el-select>
        </el-form-item>

        <el-form-item label="判断条件">
          <div style="display: flex; gap: 10px;">
            <el-select v-model="analysisParams.operator" style="width: 100px;"> 
              <el-option label="大于 (>=)" value=">=" />
              <el-option label="小于 (<=)" value="<=" />
            </el-select>
            <el-input v-model="analysisParams.threshold" type="number" placeholder="阈值" />
          </div>
        </el-form-item>

        <el-form-item label="最大值小值短连续测点数 (去除噪点)">
          <el-input-number v-model="analysisParams.minPoints" :min="1" :max="1000" style="width: 100%" />
        </el-form-item>

        <el-button type="primary" style="width: 100%; margin-top: 10px;" @click="extractContinuousSegments" icon="Location">
          执行智能提取
        </el-button>
      </el-form>

      <div v-if="analysisResults.length > 0" style="margin-top: 25px; border-top: 1px dashed #dcdfe6; padding-top: 15px;">
        <div style="font-size: 14px; color: #606266; margin-bottom: 15px; display: flex; justify-content: space-between; align-items: center;">     
          <span>检测出 <strong style="color: #f56c6c;">{{ analysisResults.length }}</strong> 段异常地层</span>
          <el-button type="success" size="small" plain @click="downloadAnalysisExcel" icon="Download">导出Excel</el-button>
        </div>
        <div style="max-height: 480px; overflow-y: auto; padding-right: 5px; margin-left:1px" class="custom-scrollbar">
          <el-timeline>
            <el-timeline-item v-for="(seg, index) in analysisResults" :key="index" type="primary" :hollow="true">
              <div style="font-weight: bold; color: #303133; margin-bottom: 4px;">
                厚度: {{ seg.thickness.toFixed(4) }} m
              </div>
              <div style="font-size: 12px; color: #909399;">顶深: {{ seg.startDepth.toFixed(4) }} m</div>
              <div style="font-size: 12px; color: #909399;">底深: {{ seg.endDepth.toFixed(4) }} m</div>
              <div style="font-size: 12px; color: #e6a23c; margin-top: 4px;">   
                点数: {{ seg.pointCount }} | 均值: {{ seg.avgValue.toFixed(4) }} 
              </div>
            </el-timeline-item>
          </el-timeline>
        </div>
      </div>
      <el-empty v-else-if="analysisResultsEmpty" description="未发现满足要求的层段" :image-size="60" style="margin-top: 20px;"></el-empty>
    </el-card>
  </div>
  </div>
  
    <!-- 选择文件弹窗 -->
    <el-dialog v-model="fileDialogVisible" title="选择测井文件" width="70%" destroy-on-close>
      <div style="margin-bottom: 15px; display: flex; gap: 10px;">
        <el-input v-model="filePageParams.fileName" placeholder="按文件名搜索" clearable @clear="handleFileSearch" @keyup.enter="handleFileSearch" style="width: 250px" />
        <el-button type="primary" @click="handleFileSearch">搜索</el-button>
      </div>
      <el-table
        ref="fileTableRef"
        :data="fileTableData"
        border
        row-key="id"
        @selection-change="handleSelectionChange"
        height="400px"
        v-loading="fileTableLoading"
      >
        <el-table-column type="selection" width="55" :reserve-selection="true"></el-table-column>
        <el-table-column prop="id" label="ID" width="80"></el-table-column>
        <el-table-column prop="fileName" label="文件名" show-overflow-tooltip></el-table-column>
        <el-table-column prop="totalRows" label="总行数" width="120"></el-table-column>
        <el-table-column prop="createTime" label="上传时间" :formatter="formatDate" width="180"></el-table-column>
      </el-table>
      <div style="margin-top: 15px; display: flex; justify-content: flex-end;">
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
          <el-button type="primary" @click="confirmFileSelection">确认选择 (已'{{ dialogSelectedRows.length }})</el-button>
        </span>
      </template>
    </el-dialog>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, reactive, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, ArrowDown } from '@element-plus/icons-vue'
import { getFileList, getPageData, exportFilteredExcel, getFilePage, exportBatchZipStream } from '@/api/file'

const visibleRowsLimit = ref(100)
const fileList = ref([])

const formatDate = (row, column, cellValue) => {
  if (!cellValue) return '';
  const date = new Date(cellValue);
  const pad = (n) => (n < 10 ? '0' + n : n);
  return date.getFullYear() + '-' +
    pad(date.getMonth() + 1) + '-' +
    pad(date.getDate()) + ' ' +
    pad(date.getHours()) + ':' +
    pad(date.getMinutes()) + ':' +
    pad(date.getSeconds());
};

const selectedFiles = ref([])

// --- 文件选择弹窗逻辑 ---
const fileDialogVisible = ref(false)
const fileTableData = ref([])
const fileTotal = ref(0)
const fileTableLoading = ref(false)
const filePageParams = reactive({
  current: 1,
  size: 10,
  fileName: ''
})
const fileTableRef = ref(null)
const dialogSelectedRows = ref([])

const openFileSelector = () => {
  fileDialogVisible.value = true
  fetchFilePage() // 每次打开刷新当前列表
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
    if (!fileList.value.some(f => f.id === row.id)) {
      fileList.value.push(row)
    }
  })
  selectedFiles.value = dialogSelectedRows.value.map(r => r.id)
  handleFileSelect(selectedFiles.value)
  fileDialogVisible.value = false
}
// --- 结束 ---

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

const analysisParams = ref({
  field: 'GR',
  operator: '>=',
  threshold: 150,
  minPoints: 3
})
const analysisResults = ref([])
const analysisResultsEmpty = ref(false)

const activeTabColumns = computed(() => {
  if (!activeTabName.value) return []
  const currentTab = tabs.value.find(t => t.name === activeTabName.value)
  return currentTab ? currentTab.columns : []
})


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

  ElMessage.success('结果成果表导出完成！系统已利用Excel/CSV 格式进行保存')
}


const extractContinuousSegments = async () => {
  if (!activeTabName.value) {
    ElMessage.warning('请先选择一个数据页签进行分析')
    return
  }

  const currentTab = tabs.value.find(t => t.name === activeTabName.value)
  if (!currentTab) {
    ElMessage.warning('当前页签无数据')
    return
  }

  const field = analysisParams.value.field
  const op = analysisParams.value.operator
  const threshold = Number(analysisParams.value.threshold)
  const minPoints = analysisParams.value.minPoints

  if (isNaN(threshold)) {
    ElMessage.warning('请输入有效的阈值')
    return
  }

  ElMessage.info('正在极速拉取后端全量日志序列，请稍候..');
  let targetData = [];
  try {
     const res = await getPageData({ fileId: currentTab.fileId, current: 1, size: 500000 });
     targetData = res.records || [];
  } catch(e) {
     ElMessage.error('拉取全量数据失败');
     return;
  }

  let segments = []
  let currentSegment = null

  for (let i = 0; i < targetData.length; i++) {
    const row = targetData[i]
    if (row[field] == null || isNaN(row[field])) {
      if (currentSegment) {
        if (currentSegment.points.length >= minPoints) {
          segments.push(currentSegment)
        }
        currentSegment = null
      }
      continue
    }

    const depthCol = currentTab.columns.find(c => ['DEPTH','深度','TVD'].includes(c.toUpperCase()) || c.includes('深')) || currentTab.columns[0];
    const val = Number(row[field])
    const depth = Number(row[depthCol])

    let isMatch = false
    if (op === '>=') isMatch = val >= threshold
    if (op === '<=') isMatch = val <= threshold

    if (isMatch) {
      if (!currentSegment) {
        currentSegment = {
          startDepth: depth,
          endDepth: depth,
          points: [val],
          pointCount: 1,
          sumValue: val
        }
      } else {
        currentSegment.endDepth = Math.max(currentSegment.endDepth, depth)
        currentSegment.points.push(val)
        currentSegment.pointCount++
        currentSegment.sumValue += val
      }
    } else {
      if (currentSegment) {
        if (currentSegment.points.length >= minPoints) {
          segments.push(currentSegment)
        }
        currentSegment = null
      }
    }
  }

  if (currentSegment && currentSegment.points.length >= minPoints) {
    segments.push(currentSegment)
  }

  analysisResults.value = segments.map(seg => ({
    startDepth: seg.startDepth,
    endDepth: seg.endDepth,
    thickness: Math.abs(seg.endDepth - seg.startDepth),
    pointCount: seg.pointCount,
    avgValue: seg.sumValue / seg.pointCount
  }))

  analysisResultsEmpty.value = segments.length === 0
  if (!analysisResultsEmpty.value) {
    ElMessage.success(`分析完成，共提取了${segments.length} 段连续地层异常`)
  }
}
 
const activeTabName = ref('')
const tabs = ref([])
let tabIndex = 0

const activeStep = computed(() => {
  if (tabs.value.length === 0) return 0;
  const hasExtracted = tabs.value.some(tab => tab.displayData && tab.displayData.length > 0 && tab.hasFiltered);
  if (hasExtracted) return 2;
  return 1;
})

const globalFilters = ref({})
const updateGlobalFilters = () => {
  const keys = new Set();
  tabs.value.forEach(t => t.columns.forEach(c => keys.add(c)));
  keys.forEach(k => {
    if (!globalFilters.value[k]) {
      globalFilters.value[k] = { min: '', max: '' }
    }
  });
  tabs.value.forEach(t => {
    t.columns.forEach(c => {
      if(!t.filters[c]) t.filters[c] = { min: '', max: '' }
    })
  });
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
  ElMessage.success('已统一套用筛选条件至全部 ' + tabs.value.length + ' 个文件！')
}

// 提取当前tab对象的过滤参数格式
const buildFiltersParam = (tabObj) => {
  let params = {};
  if (tabObj.filters) {
    for (const [col, range] of Object.entries(tabObj.filters)) {
      if ((range.min !== '' && range.min !== null) || (range.max !== '' && range.max !== null)) {
        params[col] = {
           min: range.min !== '' ? Number(range.min) : null,
           max: range.max !== '' ? Number(range.max) : null
        };
      }
    }
  }
  return Object.keys(params).length > 0 ? params : null;
}

// 分页拉取服务端数据
const fetchPageData = async (tabObj) => {
  tabObj.loading = true
  try {
    const filters = buildFiltersParam(tabObj)
    const payload = {
       fileId: tabObj.fileId,
       current: tabObj.currentPage,
       size: tabObj.pageSize,
       filters: filters
    }
    const res = await getPageData(payload)
    tabObj.displayData = res.records || []
    tabObj.totalRows = res.total || 0
  } catch (err) {
    console.error(err)
  } finally {
    tabObj.loading = false
  }
}

const handleSizeChange = (tabObj, size) => {
  tabObj.pageSize = size;
  tabObj.currentPage = 1;
  fetchPageData(tabObj);
}

const handleCurrentChange = (tabObj, page) => {
  tabObj.currentPage = page;
  fetchPageData(tabObj);
}

const handleFileSelect = (val) => {
  // val is an array of selected fileIds
  
  // Find tabs to remove (those whose fileId is no longer in selectedFiles)
  const tabsToRemove = tabs.value.filter(tab => !val.includes(tab.fileId));
  tabsToRemove.forEach(tab => removeTab(tab.name));

  // Find newly selected files and create tabs for them
  val.forEach(fileId => {
    if (!tabs.value.some(tab => tab.fileId === fileId)) {
      const fileMeta = fileList.value.find(f => f.id === fileId);
      if (fileMeta) {
        const newTabName = 'tab_' + (++tabIndex);
                  let parsedColumns = []
          try {
            parsedColumns = JSON.parse(fileMeta.columnsJson || '[]')
          } catch (e) {
            console.error('Failed to parse columnsJson', e)
          }
          const newTab = {
            title: fileMeta.fileName,
            name: newTabName,
            fileId: fileId,
            columns: parsedColumns,
          displayData: [],
          filters: {},
          currentPage: 1,
          pageSize: 100,
          totalRows: 0,
          loading: true,
          hasFiltered: false
        };
        newTab.columns.forEach(c => {
          newTab.filters[c] = { min: "", max: "" };
        });
        
        tabs.value.push(newTab);
        activeTabName.value = newTabName;
        updateGlobalFilters();
        
        // Fetch initial paginated data using the proxied object
        const latestTab = tabs.value[tabs.value.length - 1];
        fetchPageData(latestTab);
      }
    }
  });
}

const closeAllTabs = () => {
  tabs.value = [];
  activeTabName.value = '';
  selectedFiles.value = []; // Sync back to the selector
  dialogSelectedRows.value = [];
  if (fileTableRef.value) {
    fileTableRef.value.clearSelection();
  }
};

const removeTab = (targetName) => {
  const tbs = tabs.value;
  const targetTab = tbs.find(t => t.name === targetName);
  
  // Also remove from selectedFiles array to keep UI in sync
  if (targetTab) {
    selectedFiles.value = selectedFiles.value.filter(id => id !== targetTab.fileId);
    // Synchronize the table selection to remove the tab's file
    if (fileTableRef.value) {
      const rowToUncheck = fileTableData.value.find(r => r.id === targetTab.fileId) || { id: targetTab.fileId }
      fileTableRef.value.toggleRowSelection(rowToUncheck, false)
    }
    // Also remove from dialogSelectedRows
    dialogSelectedRows.value = dialogSelectedRows.value.filter(r => r.id !== targetTab.fileId)
  }

  let activeName = activeTabName.value;
  if (activeName === targetName) {
    tbs.forEach((tab, index) => {
      if (tab.name === targetName) {
        const nextTab = tbs[index + 1] || tbs[index - 1];
        if (nextTab) {
          activeName = nextTab.name;
        }
      }
    });
  }
  activeTabName.value = activeName;
  tabs.value = tbs.filter((tab) => tab.name !== targetName);
}

const applyFilters = (tabObj) => {
  tabObj.currentPage = 1;
  tabObj.hasFiltered = true;
  fetchPageData(tabObj);
  ElMessage.success('已应用新的阈值筛选条件，正在拉取大数据..');
}

const clearFilters = (tabObj) => {
  (tabObj.columns || []).forEach(col => {
    tabObj.filters[col] = { min: '', max: '' }
  })
  tabObj.hasFiltered = false;
  tabObj.currentPage = 1;
  fetchPageData(tabObj);
  ElMessage.success('过滤条件已重');
}

const exportToExcel = async (tabObj) => {
  if (!tabObj.fileId) {
    ElMessage.warning('当前没有任何数据归档信息可以导出')
    return
  }
  
  try {
    ElMessage.info('正在请求后端生成大数据过滤归档Excel，这可能需要几十秒，请稍候..')
    const filters = buildFiltersParam(tabObj)
    const blob = await exportFilteredExcel(tabObj.fileId, { filters: filters })
    const url = window.URL.createObjectURL(new Blob([blob]))
    const link = document.createElement('a')
    link.style.display = 'none'
    link.href = url
    link.setAttribute('download', tabObj.title + '_大数据归档报告.xlsx')
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    
    ElMessage.success('大数据归档报表导出成功，浏览器即将开始下')
  } catch (err) {
    console.error(err)
    ElMessage.error('报表导出失败，请检查网络设置或查看后台日志')
  }
}

const handleExportCommand = async (command) => {
  if (tabs.value.length === 0) {
    ElMessage.warning('没有可导出的数据文件！')
    return
  }
  if (command === 'zip') {
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
      ElMessage.success('批量压缩包导出成功！')
    } catch (err) {
      console.error(err)
      ElMessage.error('批量压缩导出失败，请重试')
    }
  } else {
    ElMessage.warning('为防止浏览器内存溢出，直接多Sheet导出已拦截，请选择【多文件压缩包 (.zip)】通过服务侧流式构建下载！')
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
  font-weight: bold;
  color: #1f2d3d;
}
.action-desc {
  font-size: 13px;
  color: #909399;
  margin-top: 5px;
}
.empty-state {
  padding: 60px 0;
}
.filter-bar {
  display: flex;
  align-items: center;
}
.tabs-card {
  border-radius: 12px;
}
.table-wrapper {
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 12px 0 rgba(0,0,0,0.05);
}
.p-15 { padding: 15px 20px; }
.mr-15 { margin-right: 15px; }
.ml-15 { margin-left: 15px; }
.bg-gray {
  background-color: #f8f9fc;
  border-radius: 6px;
  border: 1px solid #ebeef5;
}
.flex-center {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
}
.filter-label {
  font-size: 14px;
  font-weight: bold;
  color: #606266;
  white-space: nowrap;
}
.separator {
  margin: 0 8px;
  color: #909399;
}
.text-right {
  text-align: right;
  display: flex;
  justify-content: flex-end;
  align-items: center;
}
.count-tip {
  font-size: 13px;
  color: #909399;
}
.text-primary {
  color: #1890ff;
}
:deep(.el-input-group__prepend) {
  background-color: #fff;
}

<style scoped>
.modern-header th {
  background-color: var(--el-bg-color-page) !important;
  color: var(--el-text-color-regular);
  font-weight: 500;
  border-bottom: 2px solid var(--el-border-color-lighter);
}
.modern-row td {
  font-family: 'JetBrains Mono', 'Fira Code', Consolas, monospace;
  letter-spacing: -0.5px;
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
  gap: 16px;
  flex-wrap: wrap;
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
  padding: 0 10px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-color-primary);
  min-width: 50px;
}
.f-input {
  width: 80px;
}
.f-input :deep(.el-input__wrapper) {
  box-shadow: none !important;
  background: white;
  border-radius: 4px;
}
.f-sep {
  margin: 0 8px;
  color: var(--el-text-color-placeholder);
}

.analysis-panel {
  background: var(--el-bg-color);
  border-radius: 8px;
  border: 1px solid var(--el-border-color-light);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.analysis-header {
  background: var(--bg-gradient-blue);
  color: white;
  padding: 16px;
}
.analysis-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 500;
}
.analysis-body {
  padding: 16px;
  background: var(--el-bg-color-page);
}
/* Right side enhancements */
.result-card {
  border: 1px solid var(--el-border-color-light) !important;
  box-shadow: 0 2px 8px rgba(0,0,0,0.02) !important;
  transition: all 0.3s ease;
  border-left: 3px solid var(--el-color-danger) !important;
}
.result-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0,0,0,0.05) !important;
}

@media (max-width: 992px) {
  .global-actions-col {
    border-left: none !important;
    padding-left: 0 !important;
    margin-top: 15px;
    justify-content: flex-start !important;
  }
}
@media (max-width: 768px) {
  .mb-xs { margin-bottom: 15px; }
  .split-layout { flex-direction: column; }
  .f-group { width: 100%; justify-content: space-between; }
}


.context-menu {
  margin: 0;
  background: white;
  z-index: 3000;
  position: fixed;
  list-style-type: none;
  padding: 5px 0;
  border-radius: 4px;
  font-size: 13px;
  color: #333;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, .1);
}
.context-menu li {
  margin: 0;
  padding: 7px 16px;
  cursor: pointer;
}
.context-menu li:hover {
  background: #f0f2f5;
  color: #409EFF;
}

</style>
\n<style scoped>
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
  font-weight: bold;
  color: #1f2d3d;
}
.action-desc {
  font-size: 13px;
  color: #909399;
  margin-top: 5px;
}
.empty-state {
  padding: 60px 0;
}
.filter-bar {
  display: flex;
  align-items: center;
}
.tabs-card {
  border-radius: 12px;
}
.table-wrapper {
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 12px 0 rgba(0,0,0,0.05);
}
.p-15 { padding: 15px 20px; }
.mr-15 { margin-right: 15px; }
.ml-15 { margin-left: 15px; }
.bg-gray {
  background-color: #f8f9fc;
  border-radius: 6px;
  border: 1px solid #ebeef5;
}
.flex-center {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
}
.filter-label {
  font-size: 14px;
  font-weight: bold;
  color: #606266;
  white-space: nowrap;
}
.separator {
  margin: 0 8px;
  color: #909399;
}
.text-right {
  text-align: right;
  display: flex;
  justify-content: flex-end;
  align-items: center;
}
.count-tip {
  font-size: 13px;
  color: #909399;
}
.text-primary {
  color: #1890ff;
}
:deep(.el-input-group__prepend) {
  background-color: #fff;
}

<style scoped>
.modern-header th {
  background-color: var(--el-bg-color-page) !important;
  color: var(--el-text-color-regular);
  font-weight: 500;
  border-bottom: 2px solid var(--el-border-color-lighter);
}
.modern-row td {
  font-family: 'JetBrains Mono', 'Fira Code', Consolas, monospace;
  letter-spacing: -0.5px;
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
  gap: 16px;
  flex-wrap: wrap;
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
  padding: 0 10px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-color-primary);
  min-width: 50px;
}
.f-input {
  width: 80px;
}
.f-input :deep(.el-input__wrapper) {
  box-shadow: none !important;
  background: white;
  border-radius: 4px;
}
.f-sep {
  margin: 0 8px;
  color: var(--el-text-color-placeholder);
}

.analysis-panel {
  background: var(--el-bg-color);
  border-radius: 8px;
  border: 1px solid var(--el-border-color-light);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.analysis-header {
  background: var(--bg-gradient-blue);
  color: white;
  padding: 16px;
}
.analysis-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 500;
}
.analysis-body {
  padding: 16px;
  background: var(--el-bg-color-page);
}
/* Right side enhancements */
.result-card {
  border: 1px solid var(--el-border-color-light) !important;
  box-shadow: 0 2px 8px rgba(0,0,0,0.02) !important;
  transition: all 0.3s ease;
  border-left: 3px solid var(--el-color-danger) !important;
}
.result-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0,0,0,0.05) !important;
}

@media (max-width: 992px) {
  .global-actions-col {
    border-left: none !important;
    padding-left: 0 !important;
    margin-top: 15px;
    justify-content: flex-start !important;
  }
}
@media (max-width: 768px) {
  .mb-xs { margin-bottom: 15px; }
  .split-layout { flex-direction: column; }
  .f-group { width: 100%; justify-content: space-between; }
  .analysis-panel { width: 100% !important; margin-top: 20px; }
}

</style>
