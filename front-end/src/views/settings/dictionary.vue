<template>
  <div class="dictionary-container">
    <el-card shadow="hover" class="dictionary-card">
      <template #header>
        <div class="page-header">
          <div>
            <div class="page-title">字典映射参数</div>
            <p class="page-subtitle">维护标准键名、显示名、中文含义与别名映射规则。</p>
          </div>
        </div>
      </template>

      <div class="filter-container">
        <el-input
          v-model="listQuery.keyword"
          class="search-input"
          placeholder="搜索列键、名称或含义"
          clearable
          @keyup.enter="handleFilter"
        />
        <el-button type="primary" icon="Search" @click="handleFilter">搜索</el-button>
        <el-button type="success" icon="Plus" @click="handleCreate">新增映射</el-button>
        <el-button type="danger" icon="Delete" :disabled="selection.length === 0" @click="handleBatchDelete">批量删除</el-button>
      </div>

      <div class="table-area">
        <el-table
          v-loading="listLoading"
          :data="list"
          border
          fit
          highlight-current-row
          @selection-change="handleSelectionChange"
        >
          <el-table-column type="selection" align="center" width="55" :selectable="canSelect" />
          <el-table-column label="ID" prop="id" sortable="custom" align="center" width="80" />
          <el-table-column label="标准键名" prop="standardKey" min-width="120">
            <template #default="{ row }">
              <el-tag>{{ row.standardKey }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="标准显示名" prop="standardName" min-width="120" />
          <el-table-column label="中文含义" prop="chineseMeaning" min-width="120" />
          <el-table-column label="别名列表（逗号分隔）" prop="aliasList" min-width="200">
            <template #default="{ row }">
              <el-tooltip v-if="row.aliasList && row.aliasList.length > 20" effect="dark" :content="row.aliasList" placement="top">
                <span>{{ row.aliasList.substring(0, 20) }}...</span>
              </el-tooltip>
              <span v-else>{{ row.aliasList }}</span>
            </template>
          </el-table-column>
          <el-table-column label="是否核心" prop="isCore" align="center" width="100">
            <template #default="{ row }">
              <el-tag :type="row.isCore === 1 ? 'danger' : 'success'">
                {{ row.isCore === 1 ? '核心列' : '普通列' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" align="center" width="230">
            <template #default="{ row }">
              <el-button type="primary" size="small" icon="Edit" @click="handleUpdate(row)">编辑</el-button>
              <el-button v-if="row.isCore !== 1" size="small" type="danger" icon="Delete" @click="handleDelete(row)">删除</el-button>
              <el-tooltip v-else content="核心列禁止删除" placement="top">
                <el-button size="small" type="info" icon="Delete" disabled>删除</el-button>
              </el-tooltip>
            </template>
          </el-table-column>
        </el-table>

        <el-empty v-if="!listLoading && list.length === 0" class="table-empty" description="暂无映射数据，可通过上方按钮新增映射规则" />
      </div>

      <div class="pagination-wrap">
        <el-pagination
          background
          :current-page="listQuery.pageNum"
          :page-sizes="[10, 20, 30, 50]"
          :page-size="listQuery.pageSize"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <el-dialog :title="textMap[dialogStatus]" v-model="dialogFormVisible" width="500px">
      <el-form ref="dataForm" :rules="rules" :model="temp" label-position="right" label-width="100px" class="mapping-form">
        <el-form-item label="标准键名" prop="standardKey">
          <el-input v-model="temp.standardKey" placeholder="如: depth" :disabled="dialogStatus === 'update' && temp.isCore === 1" />
        </el-form-item>
        <el-form-item label="标准显示名" prop="standardName">
          <el-input v-model="temp.standardName" placeholder="如: DEPTH" />
        </el-form-item>
        <el-form-item label="中文含义" prop="chineseMeaning">
          <el-input v-model="temp.chineseMeaning" placeholder="如: 测量深度" />
        </el-form-item>
        <el-form-item label="别名列表" prop="aliasList">
          <el-input v-model="temp.aliasList" type="textarea" :rows="3" placeholder="英文字符且逗号分隔，如: dept,tvd,md,测深" />
          <div class="alias-tip">用于解析 TXT 文件时识别列。</div>
        </el-form-item>
        <el-form-item label="列属性">
          <el-switch
            v-model="temp.isCore"
            :active-value="1"
            :inactive-value="0"
            active-text="核心列"
            inactive-text="普通"
            :disabled="dialogStatus === 'update' && temp.isCore === 1"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogFormVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitLoading" @click="dialogStatus === 'create' ? createData() : updateData()">确认</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addColumnMapping,
  batchDeleteColumnMapping,
  deleteColumnMapping,
  getColumnMappingPage,
  updateColumnMapping
} from '@/api/columnMapping'

const list = ref([])
const total = ref(0)
const listLoading = ref(true)
const submitLoading = ref(false)
const selection = ref([])

const listQuery = reactive({
  pageNum: 1,
  pageSize: 10,
  keyword: undefined
})

const temp = reactive({
  id: undefined,
  standardKey: '',
  standardName: '',
  chineseMeaning: '',
  aliasList: '',
  isCore: 0
})

const dialogFormVisible = ref(false)
const dialogStatus = ref('')
const textMap = reactive({
  update: '编辑列映射',
  create: '新增列映射'
})

const dataForm = ref(null)
const rules = reactive({
  standardKey: [{ required: true, message: '标准键名必填', trigger: 'blur' }],
  standardName: [{ required: true, message: '标准显示名必填', trigger: 'blur' }],
  chineseMeaning: [{ required: true, message: '中文含义必填', trigger: 'blur' }]
})

const getList = () => {
  listLoading.value = true
  getColumnMappingPage(listQuery).then(res => {
    list.value = res.records || []
    total.value = res.total || 0
  }).catch(() => {
    list.value = []
    total.value = 0
  }).finally(() => {
    listLoading.value = false
  })
}

const handleFilter = () => {
  listQuery.pageNum = 1
  getList()
}

const handleSizeChange = (val) => {
  listQuery.pageSize = val
  getList()
}

const handleCurrentChange = (val) => {
  listQuery.pageNum = val
  getList()
}

const canSelect = (row) => row.isCore !== 1

const handleSelectionChange = (val) => {
  selection.value = val
}

const resetTemp = () => {
  temp.id = undefined
  temp.standardKey = ''
  temp.standardName = ''
  temp.chineseMeaning = ''
  temp.aliasList = ''
  temp.isCore = 0
}

const handleCreate = () => {
  resetTemp()
  dialogStatus.value = 'create'
  dialogFormVisible.value = true
  nextTick(() => dataForm.value?.clearValidate())
}

const createData = () => {
  dataForm.value?.validate((valid) => {
    if (!valid) return
    submitLoading.value = true
    addColumnMapping(temp).then(() => {
      getList()
      dialogFormVisible.value = false
      ElMessage.success('创建成功')
    }).finally(() => {
      submitLoading.value = false
    })
  })
}

const handleUpdate = (row) => {
  Object.assign(temp, row)
  dialogStatus.value = 'update'
  dialogFormVisible.value = true
  nextTick(() => dataForm.value?.clearValidate())
}

const updateData = () => {
  dataForm.value?.validate((valid) => {
    if (!valid) return
    submitLoading.value = true
    updateColumnMapping(temp).then(() => {
      getList()
      dialogFormVisible.value = false
      ElMessage.success('更新成功')
    }).finally(() => {
      submitLoading.value = false
    })
  })
}

const handleDelete = (row) => {
  ElMessageBox.confirm('确认删除该映射记录吗?', '提示', { type: 'warning' }).then(() => {
    deleteColumnMapping(row.id).then(() => {
      getList()
      ElMessage.success('删除成功')
    })
  })
}

const handleBatchDelete = () => {
  if (selection.value.length === 0) return
  ElMessageBox.confirm(`确认删除选中的 ${selection.value.length} 条记录吗?`, '提示', { type: 'warning' }).then(() => {
    const ids = selection.value.map(item => item.id)
    batchDeleteColumnMapping(ids).then(() => {
      getList()
      ElMessage.success('批量删除成功')
    })
  })
}

onMounted(() => {
  getList()
})
</script>

<style scoped>
.dictionary-container {
  height: 100%;
}

.dictionary-card {
  border-radius: 10px;
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

.filter-container {
  margin-bottom: 20px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.search-input {
  width: 250px;
}

.table-area {
  min-height: 0;
}

.table-empty {
  padding: 32px 0 12px;
}

.pagination-wrap {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.mapping-form {
  margin: 0 20px;
}

.alias-tip {
  margin-top: 5px;
  font-size: 12px;
  color: #999;
}

@media (max-width: 768px) {
  .filter-container {
    flex-direction: column;
  }

  .search-input {
    width: 100%;
  }
}
</style>
