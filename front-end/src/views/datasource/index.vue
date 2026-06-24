<template>
  <div class="datasource-container">
    <el-card class="datasource-card" shadow="hover">
      <template #header>
        <div class="page-header">
          <div>
            <div class="page-title">数据源管理中心</div>
            <p class="page-subtitle">统一管理上传文件、扫描目录、解析状态与预览结果。</p>
          </div>
          <div class="header-actions">
            <el-input
              v-model="pageParams.fileName"
              class="search-input"
              placeholder="按文件名搜索"
              clearable
              @clear="handleSearch"
              @keyup.enter="handleSearch"
            >
              <template #append>
                <el-button icon="Search" @click="handleSearch" />
              </template>
            </el-input>

            <el-button type="primary" icon="FolderOpened" @click="handleScanDialogOpen">服务器目录扫描</el-button>

            <el-upload
              class="upload-inline"
              action="#"
              :show-file-list="false"
              :http-request="customUpload"
              accept=".txt,.xlsx,.xls,.csv"
            >
              <el-button type="success" icon="Upload">上传测井文件</el-button>
            </el-upload>

            <el-button type="danger" icon="Delete" @click="handleClear">一键清空</el-button>
          </div>
        </div>
      </template>

      <div class="table-card">
        <el-table v-loading="loading" :data="tableData" stripe border>
          <el-table-column prop="id" label="文件 ID" width="100" />
          <el-table-column prop="fileName" label="文件名称" min-width="140" show-overflow-tooltip />
          <el-table-column prop="totalRows" label="数据量(行)" width="120" align="center" />
          <el-table-column label="解析列名预览" show-overflow-tooltip>
            <template #default="{ row }">
              {{ formatColumns(row.columnsJson) }}
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="上传时间" width="180" />
          <el-table-column label="状态" width="120" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.status === 1" type="success">可用</el-tag>
              <el-tag v-else-if="row.status === 0" type="primary" effect="light">
                <el-icon class="is-loading"><Loading /></el-icon>
                处理中
              </el-tag>
              <el-tag v-else-if="row.status === -1" type="danger">解析失败</el-tag>
              <el-tag v-else type="info">未知</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="380" fixed="right" align="center">
            <template #default="{ row }">
              <el-button size="small" type="success" link icon="DocumentChecked" :disabled="row.status !== 1" @click="handleParseReport(row)">解析报告</el-button>
              <el-button size="small" type="primary" link icon="View" :disabled="row.status !== 1" @click="handlePreview(row)">预览</el-button>
              <el-button size="small" type="warning" link icon="Menu" :disabled="row.status !== 1" @click="openLayerDialog(row)">分层</el-button>
              <el-button size="small" type="info" link icon="Edit"
                :disabled="row.status !== 1 || textColBackfilling[row.id]"
                :loading="textColBackfilling[row.id]"
                @click="openTextColumnDialog(row)">
                {{ textColBackfilling[row.id] ? '回填中' : '文本列' }}
              </el-button>
              <el-button size="small" type="danger" link icon="Delete" :disabled="row.status === 0" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-empty v-if="!loading && tableData.length === 0" class="table-empty" description="暂无数据源文件，请先上传文件或扫描目录" />
      </div>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pageParams.current"
          v-model:page-size="pageParams.size"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="scanDialogVisible" title="服务器目录扫描" width="500px">
      <el-form label-position="top">
        <el-form-item label="请输入服务器绝对路径（包含 .txt 测井记录）">
          <el-input v-model="scanPath" placeholder="例如: E:\logs\" clearable />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="scanDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="scanning" @click="confirmScan">开始扫描</el-button>
        </span>
      </template>
    </el-dialog>

    <el-dialog v-model="previewDialogVisible" title="数据预览（前 100 行）" width="80%" top="5vh">
      <el-table :data="previewTableData" border stripe height="60vh" v-loading="previewLoading">
        <el-table-column
          v-for="col in previewColumns"
          :key="col"
          :prop="col"
          :label="col"
          align="center"
          show-overflow-tooltip
        />
      </el-table>
    </el-dialog>

    <el-dialog v-model="parseReportVisible" title="文件解析报告" width="760px" top="7vh">
      <div v-loading="parseReportLoading" class="parse-report">
        <template v-if="parseReport">
          <div class="report-summary">
            <div class="summary-item">
              <span class="summary-label">文件名称</span>
              <strong>{{ parseReport.fileName || '-' }}</strong>
            </div>
            <div class="summary-item">
              <span class="summary-label">解析行数</span>
              <strong>{{ formatNumber(parseReport.totalRows) }} 行</strong>
            </div>
            <div class="summary-item">
              <span class="summary-label">识别字段</span>
              <strong>{{ formatNumber(parseReport.columnCount) }} 个</strong>
            </div>
            <div class="summary-item">
              <span class="summary-label">深度范围</span>
              <strong>{{ formatRange(parseReport.depthMin, parseReport.depthMax, 'm') }}</strong>
            </div>
          </div>

          <el-descriptions :column="2" border class="report-descriptions">
            <el-descriptions-item label="缺失/无效值数量">{{ formatNumber(parseReport.invalidValueCount) }} 个</el-descriptions-item>
            <el-descriptions-item label="隔离脏数据行">{{ formatNumber(parseReport.dirtyLineCount) }} 行</el-descriptions-item>
            <el-descriptions-item label="数据库记录数">{{ formatNumber(parseReport.recordCount) }} 行</el-descriptions-item>
            <el-descriptions-item label="解析状态">
              <el-tag :type="parseReport.status === 1 ? 'success' : 'info'">{{ parseReport.status === 1 ? '可用' : '非可用' }}</el-tag>
            </el-descriptions-item>
          </el-descriptions>
          <el-alert
            class="report-note"
            type="info"
            show-icon
            :closable="false"
            title="缺失/无效值指空值、非数字值、NaN、null，以及 -9999、-999.25、-999 等占位无效值；井号仅作为标识字段展示，不参与缺失/无效统计。"
          />

          <div class="report-section-title">识别字段</div>
          <div class="column-tags">
            <el-tag v-for="col in parseReport.columns" :key="col" effect="plain">{{ col }}</el-tag>
          </div>

          <div class="report-section-title">关键列范围</div>
          <el-table :data="parseReport.columnStats || []" border stripe max-height="320">
            <el-table-column prop="column" label="字段" min-width="130" show-overflow-tooltip />
            <el-table-column prop="validCount" label="有效值" width="100" align="center" />
            <el-table-column prop="invalidCount" label="无效/缺失" width="110" align="center" />
            <el-table-column label="最小值" min-width="120" align="center">
              <template #default="{ row }">{{ formatReportValue(row.min) }}</template>
            </el-table-column>
            <el-table-column label="最大值" min-width="120" align="center">
              <template #default="{ row }">{{ formatReportValue(row.max) }}</template>
            </el-table-column>
          </el-table>
        </template>
      </div>
    </el-dialog>

    <el-dialog v-model="sandboxVisible" title="数据预览与列映射沙盒" width="80%" top="5vh">
      <el-alert
        title="系统已从前 100 行样本中推测了部分字段。若错位，请手动调整；确认后无法匹配的数据将进入隔离区。"
        type="warning"
        show-icon
        class="sandbox-alert"
      />
      <div class="sandbox-file-title">文件：{{ previewSummary.originalFileName }}</div>
      <el-table :data="previewSummary.previewData" border stripe max-height="400">
        <el-table-column v-for="(header, index) in previewSummary.originalHeaders" :key="index" min-width="160">
          <template #header>
            <div class="mapping-header-origin">原列：{{ header }}</div>
            <el-select v-model="previewSummary.suggestedMapping[index]" placeholder="映射到" size="small" class="full-width">
              <el-option value="Ignore" label="[弃用] / 不入库" />
              <el-option
                v-for="mapCol in allMappingOptions"
                :key="mapCol.standardKey"
                :label="`${mapCol.standardName} (${mapCol.chineseMeaning})`"
                :value="mapCol.standardName"
              />
              <template #footer>
                <el-button text type="primary" icon="Plus" class="full-width"
                  @click="openAddDictDialog(index, header)">添加新的标准字段</el-button>
              </template>
            </el-select>
          </template>
          <template #default="scope">
            {{ scope.row[previewSummary.originalSuggestedMapping[index]] ?? scope.row[header] ?? scope.row[`ExtraCol${index + 1}`] }}
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="sandboxVisible = false">取消</el-button>
          <el-button type="primary" :loading="sandboxLoading" @click="confirmSandbox">确认映射并落库</el-button>
        </span>
      </template>
    </el-dialog>

    <el-dialog v-model="addDictDialogVisible" title="快捷新增标准字段" width="450px" append-to-body>
      <el-form :model="addDictForm" label-width="120px">
        <el-form-item label="原始列名参考">
          <el-input :model-value="addDictForm.originalHeader" disabled />
        </el-form-item>
        <el-form-item label="标准英文名" required>
          <el-input v-model="addDictForm.standardName" placeholder="例如: NewDepth" />
        </el-form-item>
        <el-form-item label="中文含义" required>
          <el-input v-model="addDictForm.chineseMeaning" placeholder="例如: 新测试深度" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="addDictDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="addDictLoading" @click="submitAddDict">保存并应用</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 文本列配置 -->
    <el-dialog v-model="textColDialogVisible" title="文本列配置" width="660px" top="8vh" class="textcol-dialog" @open="fetchTextColumnCandidates">
      <div class="textcol-subtitle">系统扫描了前 500 行，自动推荐以下文本类型列（多选筛选）：</div>
      <div v-loading="textColScanning" class="textcol-list">
        <div v-if="textColCandidates.length === 0 && !textColScanning" class="textcol-empty">
          暂未检测到候选文本列（所有列均为数值类型）
        </div>
        <el-checkbox-group v-model="textColSelected" class="textcol-grid">
          <div v-for="col in textColCandidates" :key="col.name" class="textcol-item"
            :class="{ 'textcol-item--warn': col.reason === '自动检测' && col.uniqueCount > 200 }">
            <el-checkbox :label="col.name" :value="col.name">
              <span class="textcol-name">{{ col.name }}</span>
            </el-checkbox>
            <div class="textcol-meta">
              <span>非数值 {{ col.nonNumericRate }}%</span>
              <span>{{ col.uniqueCount }} 种值</span>
              <el-tag v-if="col.reason" size="small" type="info" effect="plain" class="textcol-reason">{{ col.reason }}</el-tag>
            </div>
            <div class="textcol-samples">{{ col.samples?.join(' / ') || '-' }}</div>
          </div>
        </el-checkbox-group>
      </div>
      <div class="textcol-footer-info">已选 {{ textColSelected.length }} / 10 列</div>
      <template #footer>
        <el-button @click="textColDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="textColSaving" @click="saveTextColumnConfig">保存配置并回填数据</el-button>
      </template>
    </el-dialog>

    <!-- 地质分层配置 -->
    <el-dialog v-model="layerDialogVisible" title="地质分层配置" width="720px" top="4vh" class="layer-dialog">
      <div class="layer-dialog-header">
        <div class="layer-file-info">
          <el-icon :size="18" color="var(--el-color-primary)"><Document /></el-icon>
          <span class="layer-file-name">{{ layerFile.fileName }}</span>
          <el-tag size="small" type="info" effect="plain">{{ layerFile.totalRows?.toLocaleString() || 0 }} 行</el-tag>
        </div>
        <div class="layer-file-desc">配置该井的地层分层深度，导出时自动附加层位列。</div>
      </div>

      <div class="layer-table-wrapper">
        <el-table :data="layerRows" size="default" v-loading="layerLoading" :header-cell-style="{ background: '#f8fafc', fontWeight: 600 }">
          <el-table-column type="index" label="#" width="50" align="center" />
          <el-table-column label="层位名称" min-width="130">
            <template #default="{ row }">
              <el-input v-model="row.layerName" size="default" placeholder="如 盒5" />
            </template>
          </el-table-column>
          <el-table-column label="顶深 (m)" width="140">
            <template #default="{ row }">
              <el-input-number v-model="row.topDepth" size="default" :controls="false" :precision="3" placeholder="0" style="width: 100%;" />
            </template>
          </el-table-column>
          <el-table-column label="底深 (m)" width="140">
            <template #default="{ row }">
              <el-input-number v-model="row.bottomDepth" size="default" :controls="false" :precision="3" placeholder="0" style="width: 100%;" />
            </template>
          </el-table-column>
          <el-table-column label="备注" min-width="110">
            <template #default="{ row }">
              <el-input v-model="row.remark" size="default" placeholder="可选" />
            </template>
          </el-table-column>
          <el-table-column label="" width="50" align="center">
            <template #default="{ $index }">
              <el-button type="danger" link size="small" icon="Delete" @click="layerRows.splice($index, 1)" />
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="layer-actions-bar">
        <el-button type="primary" icon="Plus" @click="layerRows.push({ layerName: '', topDepth: null, bottomDepth: null, remark: '' })">
          添加分层
        </el-button>
        <el-upload action="#" :show-file-list="false" :http-request="handleLayerExcelImport" accept=".xlsx,.xls,.csv,.txt">
          <el-button type="success" icon="Upload">从文件导入</el-button>
        </el-upload>
        <el-button type="warning" icon="CopyDocument" :disabled="layerRows.length === 0" @click="openCopyLayerDialog">
          复制到其他文件
        </el-button>
        <el-button type="danger" plain icon="Delete" @click="handleClearLayers">
          清空分层
        </el-button>
      </div>

      <template #footer>
        <div class="layer-dialog-footer">
          <el-button size="large" @click="layerDialogVisible = false">取消</el-button>
          <el-button type="primary" size="large" :loading="layerSaving" icon="Check" @click="saveLayerConfig">保存分层配置</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 复制分层到其他文件 -->
    <el-dialog v-model="copyLayerDialogVisible" title="复制分层配置到其他文件" width="500px" append-to-body>
      <div style="margin-bottom: 12px; color: var(--el-text-color-secondary); font-size: 13px;">
        将 <strong style="color: var(--el-text-color-primary);">{{ layerFile.fileName }}</strong> 的分层配置复制到其他文件。
        <br>层名会被复制，深度值保持相同，复制后请根据各井实际情况调整深度。
      </div>

      <el-table
        ref="copyFileTableRef"
        :data="copyFileList"
        border
        size="small"
        height="300"
        @selection-change="handleCopySelectionChange"
      >
        <el-table-column type="selection" width="55" />
        <el-table-column prop="fileName" label="文件名称" show-overflow-tooltip />
        <el-table-column prop="totalRows" label="数据量" width="100" align="center" />
      </el-table>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="copyLayerDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="copyLayerLoading" :disabled="copySelectedFiles.length === 0" @click="confirmCopyLayers">
            复制到 {{ copySelectedFiles.length }} 个文件
          </el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 井名选择 Dialog -->
    <el-dialog v-model="wellSelectVisible" title="选择井名" width="400px" append-to-body>
      <div style="margin-bottom: 16px; color: var(--el-text-color-secondary); font-size: 13px;">
        Excel 中包含多口井的数据，请选择要导入的井名：
      </div>
      <el-select v-model="selectedWell" placeholder="请选择井名" style="width: 100%;" size="large">
        <el-option v-for="name in wellNames" :key="name" :label="name" :value="name" />
      </el-select>
      <template #footer>
        <el-button @click="wellSelectVisible = false">取消</el-button>
        <el-button type="primary" :loading="wellSelectLoading" @click="confirmWellSelect">确认导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { clearFiles, deleteFile, getFileParseReport, getFileLayers, saveFileLayers, deleteFileLayers, copyLayersToFiles, importLayersFromExcel } from '@/api/file'
import { getAllColumnMappings, addColumnMapping } from '@/api/columnMapping'
import { Delete, DocumentChecked, FolderOpened, Loading, Plus, Search, Upload, View, Menu } from '@element-plus/icons-vue'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const pageParams = reactive({
  current: 1,
  size: 10,
  fileName: ''
})

let pollingTimer = null
let pollingAbortController = null

const scanDialogVisible = ref(false)
const scanPath = ref('')
const scanning = ref(false)

const sandboxVisible = ref(false)
const sandboxLoading = ref(false)

// ========== 文本列配置 ==========
const textColDialogVisible = ref(false)
const textColScanning = ref(false)
const textColSaving = ref(false)
const textColCandidates = ref([])
const textColSelected = ref([])
const textColCurrentFileId = ref(null)
const textColBackfilling = reactive({})

const openTextColumnDialog = async (row) => {
  textColCurrentFileId.value = row.id
  textColSelected.value = []
  textColDialogVisible.value = true
}

const fetchTextColumnCandidates = async () => {
  if (!textColCurrentFileId.value) return
  textColScanning.value = true
  try {
    const res = await request.get(`/data/${textColCurrentFileId.value}/text-columns/candidates`)
    textColCandidates.value = res || []
    // 自动勾选推荐的
    textColSelected.value = (res || []).filter(c => c.suggested).map(c => c.name).slice(0, 10)
  } catch {
    textColCandidates.value = []
    ElMessage.error('扫描候选列失败')
  } finally {
    textColScanning.value = false
  }
}

const saveTextColumnConfig = async () => {
  const fileId = textColCurrentFileId.value
  const cols = [...textColSelected.value]
  textColDialogVisible.value = false
  if (!fileId || cols.length === 0) return
  textColBackfilling[fileId] = true
  try {
    await request.post(`/data/${fileId}/text-columns`, { columns: cols })
    ElMessage.success('文本列配置已保存，数据回填完成')
  } catch (error) {
    ElMessage.error(error?.message || '保存失败')
  } finally {
    textColBackfilling[fileId] = false
  }
}
const allMappingOptions = ref([])
const previewSummary = ref({
  tempFilePath: '',
  originalFileName: '',
  originalHeaders: [],
  suggestedMapping: [],
  previewData: [],
  originalSuggestedMapping: []
})

const previewDialogVisible = ref(false)
const previewLoading = ref(false)
const previewTableData = ref([])
const previewColumns = ref([])

const parseReportVisible = ref(false)
const parseReportLoading = ref(false)
const parseReport = ref(null)

const addDictDialogVisible = ref(false)
const addDictLoading = ref(false)
const currentEditIndex = ref(-1)
const addDictForm = reactive({
  originalHeader: '',
  standardName: '',
  chineseMeaning: ''
})

const openAddDictDialog = (index, originalHeader) => {
  currentEditIndex.value = index
  addDictForm.originalHeader = originalHeader
  addDictForm.standardName = originalHeader.replace(/[^a-zA-Z0-9_]/g, '') || `col_${index + 1}`
  addDictForm.chineseMeaning = ''
  addDictDialogVisible.value = true
}

const submitAddDict = async () => {
  if (!addDictForm.standardName || !addDictForm.chineseMeaning) {
    ElMessage.warning('标准英文名和中文含义不能为空')
    return
  }
  
  addDictLoading.value = true
  try {
    await addColumnMapping({
      standardName: addDictForm.standardName,
      chineseMeaning: addDictForm.chineseMeaning,
      standardKey: addDictForm.standardName.toUpperCase()
    })
    
    await fetchMappingOptions()
    
    if (currentEditIndex.value !== -1) {
      if (!previewSummary.value.suggestedMapping) {
        previewSummary.value.suggestedMapping = []
      }
      previewSummary.value.suggestedMapping[currentEditIndex.value] = addDictForm.standardName
    }
    
    ElMessage.success('新增标准字段成功并已自动应用')
    addDictDialogVisible.value = false
  } catch (error) {
    ElMessage.error(error.message || '新增字典失败')
  } finally {
    addDictLoading.value = false
  }
}

const checkPolling = () => {
  const isParsing = tableData.value.some(item => item.status === 0)
  if (isParsing && !pollingTimer) {
    pollingTimer = setInterval(() => {
      fetchTableDataSilently()
    }, 3000)
  } else if (!isParsing && pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
}

const fetchTableDataSilently = async () => {
  try {
    // 取消上一次未完成的轮询请求
    if (pollingAbortController) {
      pollingAbortController.abort()
    }
    pollingAbortController = new AbortController()
    const res = await request.get('/file/page', {
      params: pageParams,
      signal: pollingAbortController.signal
    })
    tableData.value = res.records || []
    total.value = res.total || 0
    checkPolling()
  } catch (error) {
    if (error?.name !== 'AbortError' && error?.code !== 'ERR_CANCELED') {
      console.error('轮询查询失败', error)
    }
  }
}

const handleSearch = () => {
  pageParams.current = 1
  getTableData()
}

const getTableData = async () => {
  loading.value = true
  try {
    const res = await request.get('/file/page', { params: pageParams })
    tableData.value = res.records || []
    total.value = res.total || 0
    checkPolling()
  } catch (error) {
    ElMessage.error(error.message || '获取列表失败')
  } finally {
    loading.value = false
  }
}

const fetchMappingOptions = async () => {
  try {
    const res = await getAllColumnMappings()
    allMappingOptions.value = res || []
  } catch (error) {
    console.error('获取映射选项失败', error)
  }
}

const handleSizeChange = (val) => {
  pageParams.size = val
  getTableData()
}

const handleCurrentChange = (val) => {
  pageParams.current = val
  getTableData()
}

const formatColumns = (jsonStr) => {
  if (!jsonStr) return '-'
  try {
    const cols = JSON.parse(jsonStr)
    return cols.join(', ')
  } catch {
    return jsonStr
  }
}

const formatNumber = (value) => {
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? numberValue.toLocaleString() : '0'
}

const formatReportValue = (value) => {
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? Number(numberValue.toFixed(4)).toString() : '-'
}

const formatRange = (min, max, unit = '') => {
  const minText = formatReportValue(min)
  const maxText = formatReportValue(max)
  if (minText === '-' || maxText === '-') return '-'
  return `${minText} - ${maxText}${unit ? ` ${unit}` : ''}`
}

// ==================== 地质分层配置 ====================
const layerDialogVisible = ref(false)
const layerLoading = ref(false)
const layerSaving = ref(false)
const layerFile = ref({})
const layerRows = ref([])

const openLayerDialog = async (row) => {
  layerFile.value = row
  layerDialogVisible.value = true
  layerLoading.value = true
  try {
    const data = await getFileLayers(row.id)
    layerRows.value = (data || []).map(l => ({
      layerName: l.layerName,
      topDepth: l.topDepth,
      bottomDepth: l.bottomDepth,
      remark: l.remark || ''
    }))
    if (layerRows.value.length === 0) {
      layerRows.value.push({ layerName: '', topDepth: null, bottomDepth: null, remark: '' })
    }
  } catch (error) {
    ElMessage.error('获取分层配置失败')
    layerRows.value = [{ layerName: '', topDepth: null, bottomDepth: null, remark: '' }]
  } finally {
    layerLoading.value = false
  }
}

const saveLayerConfig = async () => {
  const validRows = layerRows.value.filter(r => r.layerName && r.topDepth != null && r.bottomDepth != null)
  if (validRows.length === 0 && layerRows.value.length > 0) {
    ElMessage.warning('请至少填写一条完整的分层记录（层名 + 顶深 + 底深）')
    return
  }
  layerSaving.value = true
  try {
    await saveFileLayers(layerFile.value.id, validRows)
    ElMessage.success(`分层配置保存成功，共 ${validRows.length} 层`)
    layerDialogVisible.value = false
  } catch (error) {
    ElMessage.error(error.message || '保存分层配置失败')
  } finally {
    layerSaving.value = false
  }
}

const handleClearLayers = async () => {
  try {
    await ElMessageBox.confirm('确定要清空当前文件的所有分层配置吗？此操作不可恢复。', '清空分层', {
      confirmButtonText: '确认清空',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteFileLayers(layerFile.value.id)
    layerRows.value = [{ layerName: '', topDepth: null, bottomDepth: null, remark: '' }]
    ElMessage.success('分层配置已清空')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '清空失败')
  }
}

// ==================== P1: 复制分层到其他文件 ====================
const copyLayerDialogVisible = ref(false)
const copyLayerLoading = ref(false)
const copyFileList = ref([])
const copySelectedFiles = ref([])
const copyFileTableRef = ref(null)

const openCopyLayerDialog = async () => {
  // 获取当前用户所有可用文件（排除当前文件）
  try {
    const res = await request.get('/file/page', { params: { current: 1, size: 1000 } })
    const allFiles = res.records || []
    copyFileList.value = allFiles.filter(f => f.id !== layerFile.value.id && f.status === 1)
    copySelectedFiles.value = []
    copyLayerDialogVisible.value = true
  } catch (error) {
    ElMessage.error('获取文件列表失败')
  }
}

const handleCopySelectionChange = (rows) => {
  copySelectedFiles.value = rows
}

const confirmCopyLayers = async () => {
  if (copySelectedFiles.value.length === 0) return
  copyLayerLoading.value = true
  try {
    const targetIds = copySelectedFiles.value.map(f => f.id)
    await copyLayersToFiles(layerFile.value.id, targetIds)
    ElMessage.success(`已复制分层配置到 ${targetIds.length} 个文件`)
    copyLayerDialogVisible.value = false
  } catch (error) {
    ElMessage.error(error.message || '复制分层失败')
  } finally {
    copyLayerLoading.value = false
  }
}

// ==================== P2: Excel 导入分层 ====================
const layerImportFile = ref(null) // 缓存上传的文件
const wellSelectVisible = ref(false)
const wellSelectLoading = ref(false)
const wellNames = ref([])
const selectedWell = ref('')

const handleLayerExcelImport = async (options) => {
  layerImportFile.value = options.file
  const formData = new FormData()
  formData.append('file', options.file)
  try {
    layerLoading.value = true
    const res = await importLayersFromExcel(layerFile.value.id, formData)

    // 检查是否需要选择井名
    if (res && res.needSelectWell) {
      wellNames.value = res.wellNames || []
      selectedWell.value = wellNames.value[0] || ''
      wellSelectVisible.value = true
      return
    }

    ElMessage.success(res || '导入成功')
    await reloadLayerRows()
  } catch (error) {
    ElMessage.error(error.message || 'Excel 导入失败，请确认文件格式')
  } finally {
    layerLoading.value = false
  }
}

const confirmWellSelect = async () => {
  if (!selectedWell.value) {
    ElMessage.warning('请选择井名')
    return
  }
  wellSelectLoading.value = true
  try {
    const formData = new FormData()
    if (!layerImportFile.value) {
      ElMessage.error('文件丢失，请重新选择')
      wellSelectLoading.value = false
      return
    }
    formData.append('file', layerImportFile.value)
    const res = await importLayersFromExcel(layerFile.value.id, formData, selectedWell.value)
    ElMessage.success(res || '导入成功')
    wellSelectVisible.value = false
    await reloadLayerRows()
  } catch (error) {
    ElMessage.error(error.message || '导入失败')
  } finally {
    wellSelectLoading.value = false
  }
}

const reloadLayerRows = async () => {
  const data = await getFileLayers(layerFile.value.id)
  layerRows.value = (data || []).map(l => ({
    layerName: l.layerName,
    topDepth: l.topDepth,
    bottomDepth: l.bottomDepth,
    remark: l.remark || ''
  }))
  if (layerRows.value.length === 0) {
    layerRows.value.push({ layerName: '', topDepth: null, bottomDepth: null, remark: '' })
  }
}

const customUpload = async (options) => {
  const file = options.file
  if (!file) {
    ElMessage.error('未选择文件')
    return
  }
  if (file.size === 0) {
    ElMessage.error('文件为空，请重新选择')
    return
  }
  if (file.size > 50 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过 50MB')
    return
  }
  const formData = new FormData()
  formData.append('file', file)
  try {
    loading.value = true
    const res = await request.post('/file/preview', formData)
    res.originalSuggestedMapping = [...(res.suggestedMapping || [])]
    previewSummary.value = res
    sandboxVisible.value = true
  } catch (error) {
    ElMessage.error(error.message || '预览生成失败')
  } finally {
    loading.value = false
  }
}

const confirmSandbox = async () => {
  sandboxLoading.value = true
  try {
    await request.post('/file/confirm', {
      tempFilePath: previewSummary.value.tempFilePath,
      originalFileName: previewSummary.value.originalFileName,
      confirmedMapping: previewSummary.value.suggestedMapping
    })
    ElMessage.success('映射确认成功，开始入库')
    sandboxVisible.value = false
    getTableData()
  } catch (error) {
    ElMessage.error(error.message || '确认失败')
  } finally {
    sandboxLoading.value = false
  }
}

const handleScanDialogOpen = () => {
  scanDialogVisible.value = true
}

const confirmScan = async () => {
  if (!scanPath.value) {
    ElMessage.warning('请输入路径')
    return
  }
  scanning.value = true
  try {
    await request.get('/file/scan', { params: { path: scanPath.value } })
    ElMessage.success('扫描完成')
    scanDialogVisible.value = false
    pageParams.current = 1
    getTableData()
  } catch (error) {
    ElMessage.error(error.message || '扫描失败')
  } finally {
    scanning.value = false
  }
}

const handleParseReport = async (row) => {
  parseReportVisible.value = true
  parseReportLoading.value = true
  parseReport.value = null
  try {
    parseReport.value = await getFileParseReport(row.id)
  } catch (error) {
    ElMessage.error(error.message || '获取文件解析报告失败')
  } finally {
    parseReportLoading.value = false
  }
}

const handleDelete = (row) => {
  ElMessageBox.confirm(`确定要删除文件 ${row.fileName} 吗？`, '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await deleteFile(row.id)
      ElMessage.success('删除成功')
      getTableData()
    } catch (error) {
      ElMessage.error(error.message || '删除失败')
    }
  }).catch(() => {})
}

const handleClear = () => {
  ElMessageBox.confirm('此操作将清空当前所有测井数据，是否继续？', '严重警告', {
    confirmButtonText: '确定清空',
    cancelButtonText: '取消',
    type: 'error'
  }).then(async () => {
    try {
      await clearFiles()
      ElMessage.success('清空成功')
      getTableData()
    } catch (error) {
      ElMessage.error(error.message || '清空失败')
    }
  }).catch(() => {})
}

const handlePreview = async (row) => {
  previewDialogVisible.value = true
  previewLoading.value = true
  previewTableData.value = []
  previewColumns.value = []

  if (row.columnsJson) {
    try {
      previewColumns.value = JSON.parse(row.columnsJson)
    } catch (error) {
      console.error(error)
    }
  }

  try {
    const res = await request.post('/data/page', {
      fileId: row.id,
      current: 1,
      size: 100
    })
    previewTableData.value = res.records || []
  } catch (error) {
    ElMessage.error(error.message || '获取预览数据失败')
  } finally {
    previewLoading.value = false
  }
}

onMounted(() => {
  getTableData()
  fetchMappingOptions()
})

onUnmounted(() => {
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
  if (pollingAbortController) {
    pollingAbortController.abort()
    pollingAbortController = null
  }
})
</script>

<style scoped>
.datasource-container {
  height: 100%;
}

.datasource-card {
  height: 100%;
  display: flex;
  flex-direction: column;
  border-radius: 10px;
}

:deep(.datasource-card > .el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding-bottom: 0;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.page-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.page-subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.header-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  justify-content: flex-end;
}

.search-input {
  width: 220px;
}

.upload-inline {
  display: inline-block;
}

.table-card {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.table-card :deep(.el-table) {
  height: 100%;
}

.table-empty {
  padding: 32px 0 12px;
}

.pagination-container {
  padding: 15px 0;
  display: flex;
  justify-content: flex-end;
  flex-shrink: 0;
}

.sandbox-alert {
  margin-bottom: 14px;
}

.sandbox-file-title {
  margin: 15px 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.mapping-header-origin {
  margin-bottom: 5px;
  font-size: 12px;
  color: #909399;
}

.full-width {
  width: 100%;
}

.parse-report {
  min-height: 220px;
}

.report-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.summary-item {
  padding: 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color-page);
}

.summary-label {
  display: block;
  margin-bottom: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.summary-item strong {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 15px;
  color: var(--el-text-color-primary);
}

.report-descriptions {
  margin-bottom: 16px;
}

.report-note {
  margin-bottom: 16px;
}

.report-section-title {
  margin: 16px 0 10px;
  font-size: 14px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.column-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

@media (max-width: 992px) {
  .page-header {
    flex-direction: column;
  }

  .header-actions {
    width: 100%;
    justify-content: flex-start;
  }
}

@media (max-width: 768px) {
  .header-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .search-input {
    width: 100%;
  }

  .report-summary {
    grid-template-columns: 1fr;
  }
}

/* ==================== 地质分层 Dialog ==================== */
.layer-dialog :deep(.el-dialog__header) {
  padding: 20px 24px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  margin-right: 0;
}

.layer-dialog :deep(.el-dialog__body) {
  padding: 0;
}

.layer-dialog :deep(.el-dialog__footer) {
  padding: 16px 24px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.layer-dialog-header {
  padding: 20px 24px 16px;
  border-bottom: 1px solid var(--el-border-color-extra-light);
  background: linear-gradient(135deg, var(--el-color-primary-light-9) 0%, var(--el-fill-color-lighter) 100%);
}

.layer-file-info {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.layer-file-name {
  font-size: 16px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.layer-file-desc {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.layer-table-wrapper {
  padding: 16px 24px;
}

.layer-actions-bar {
  display: flex;
  gap: 12px;
  padding: 12px 24px;
  border-top: 1px solid var(--el-border-color-extra-light);
  background: var(--el-fill-color-lighter);
}

.layer-dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

/* ===== 文本列配置弹窗 ===== */
.textcol-subtitle {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 16px;
  line-height: 1.6;
}
.textcol-list {
  min-height: 80px;
}
.textcol-grid {
  display: grid !important;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.textcol-empty {
  text-align: center;
  color: var(--el-text-color-placeholder);
  padding: 30px 0;
}
.textcol-item {
  padding: 10px 12px;
  background: var(--el-fill-color-lighter);
  border-radius: 8px;
  border: 1px solid transparent;
}
.textcol-item--warn {
  background: #fefce8;
  border-color: #fde68a;
}
.textcol-name {
  font-weight: 600;
  font-size: 14px;
  color: var(--el-text-color-primary);
}
.textcol-meta {
  margin-top: 4px;
  margin-left: 24px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.textcol-samples {
  margin-left: 24px;
  font-size: 11px;
  color: var(--el-text-color-placeholder);
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.textcol-reason {
  font-size: 11px;
}
.textcol-footer-info {
  margin-top: 12px;
  font-size: 13px;
  color: var(--el-color-primary);
  font-weight: 500;
  text-align: right;
}
</style>
