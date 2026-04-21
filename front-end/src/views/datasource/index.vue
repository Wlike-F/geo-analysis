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
              accept=".txt"
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
          <el-table-column prop="fileName" label="文件名称" show-overflow-tooltip />
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
          <el-table-column label="操作" width="200" fixed="right" align="center">
            <template #default="{ row }">
              <el-button size="small" type="primary" link icon="View" :disabled="row.status !== 1" @click="handlePreview(row)">预览</el-button>
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
            </el-select>
          </template>
          <template #default="scope">
            {{ scope.row[previewSummary.originalSuggestedMapping[index]] || scope.row[header] || scope.row[`ExtraCol${index + 1}`] }}
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
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { clearFiles, deleteFile } from '@/api/file'
import { getAllColumnMappings } from '@/api/columnMapping'
import { Delete, FolderOpened, Loading, Search, Upload, View } from '@element-plus/icons-vue'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const pageParams = reactive({
  current: 1,
  size: 10,
  fileName: ''
})

let pollingTimer = null

const scanDialogVisible = ref(false)
const scanPath = ref('')
const scanning = ref(false)

const sandboxVisible = ref(false)
const sandboxLoading = ref(false)
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
    const res = await request.get('/file/page', { params: pageParams })
    tableData.value = res.records || []
    total.value = res.total || 0
    checkPolling()
  } catch (error) {
    console.error('轮询查询失败', error)
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

const customUpload = async (options) => {
  const formData = new FormData()
  formData.append('file', options.file)
  try {
    loading.value = true
    const res = await request.post('/file/preview', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    res.originalSuggestedMapping = [...res.suggestedMapping]
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
    previewTableData.value = res.records || res.data?.records || res || []
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
}

.table-empty {
  padding: 32px 0 12px;
}

.pagination-container {
  padding: 15px 0;
  display: flex;
  justify-content: flex-end;
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
}
</style>
