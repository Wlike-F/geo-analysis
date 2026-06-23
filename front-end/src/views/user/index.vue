<template>
  <div class="user-container">
    <el-card class="user-card" shadow="hover">
      <template #header>
        <div class="page-header">
          <div>
            <div class="page-title">系统用户管理</div>
            <p class="page-subtitle">统一维护系统账号、角色权限与基础资料。</p>
          </div>
          <el-button type="primary" icon="Plus" @click="handleAdd">新增用户</el-button>
        </div>
      </template>

      <div class="table-area">
        <el-table v-loading="loading" :data="tableData" border stripe>
          <el-table-column type="index" label="序号" width="60" align="center" />
          <el-table-column prop="username" label="系统账号" min-width="120" />
          <el-table-column prop="realName" label="真实姓名" min-width="120" />
          <el-table-column prop="email" label="邮箱" min-width="180" />
          <el-table-column prop="role" label="角色" width="120" align="center">
            <template #default="scope">
              <el-tag :type="scope.row.role === 'admin' ? 'danger' : 'info'">
                {{ scope.row.role === 'admin' ? '管理员' : '普通用户' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="注册时间" min-width="160" />
          <el-table-column label="操作" width="180" align="center" fixed="right">
            <template #default="scope">
              <el-button type="primary" link icon="Edit" @click="handleEdit(scope.row)">编辑</el-button>
              <el-button type="danger" link icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-empty v-if="!loading && tableData.length === 0" class="table-empty" description="暂无用户数据，可通过右上角按钮新增用户" />
      </div>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pageParam.current"
          v-model:page-size="pageParam.size"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="fetchData"
          @current-change="fetchData"
        />
      </div>
    </el-card>

    <el-dialog :title="dialogType === 'add' ? '新增用户' : '编辑用户'" v-model="dialogVisible" width="500px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item v-if="dialogType === 'add'" label="系统账号" prop="username">
          <el-input v-model="form.username" placeholder="请输入系统登录账号" />
        </el-form-item>
        <el-form-item label="真实姓名" prop="realName">
          <el-input v-model="form.realName" placeholder="请输入真实姓名" />
        </el-form-item>
        <el-form-item label="电子邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入联系邮箱" />
        </el-form-item>
        <el-form-item label="系统角色" prop="role">
          <el-radio-group v-model="form.role">
            <el-radio label="user">普通用户</el-radio>
            <el-radio label="admin">管理员</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="个人简介">
          <el-input v-model="form.introduction" type="textarea" :rows="3" placeholder="选填，个人简介" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitLoading" @click="submitForm">确定保存</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { addUser, deleteUser, getUserPage, updateUser } from '@/api/user'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const pageParam = reactive({
  current: 1,
  size: 10
})

const dialogVisible = ref(false)
const dialogType = ref('add')
const submitLoading = ref(false)
const formRef = ref(null)

const form = reactive({
  id: null,
  username: '',
  realName: '',
  email: '',
  role: 'user',
  introduction: ''
})

const rules = reactive({
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  email: [{ type: 'email', message: '请输入有效的邮箱地址', trigger: 'blur' }]
})

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getUserPage(pageParam)
    tableData.value = res?.records || []
    total.value = res?.total || 0
  } catch (error) {
    console.error(error)
    ElMessage.error('获取用户列表失败')
  } finally {
    loading.value = false
  }
}

const resetForm = () => {
  form.id = null
  form.username = ''
  form.realName = ''
  form.email = ''
  form.role = 'user'
  form.introduction = ''
  formRef.value?.clearValidate()
}

const handleAdd = () => {
  resetForm()
  dialogType.value = 'add'
  dialogVisible.value = true
}

const handleEdit = (row) => {
  resetForm()
  dialogType.value = 'edit'
  form.id = row.id
  form.username = row.username
  form.realName = row.realName
  form.email = row.email || ''
  form.role = row.role
  form.introduction = row.introduction || ''
  dialogVisible.value = true
}

const submitForm = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return // 校验未通过
  }
  submitLoading.value = true
  try {
    if (dialogType.value === 'add') {
      await addUser(form)
      ElMessage.success('新增成功，初始默认密码为 123456')
    } else {
      await updateUser(form)
      ElMessage.success('信息更新成功')
    }
    dialogVisible.value = false
    fetchData()
  } catch (error) {
    console.error(error)
    ElMessage.error(dialogType.value === 'add' ? '新增用户失败' : '更新用户失败')
  } finally {
    submitLoading.value = false
  }
}

const handleDelete = (row) => {
  if (row.id === userStore.userInfo.id) {
    ElMessage.warning('不能删除自己的账号')
    return
  }
  ElMessageBox.confirm(`确定要永久删除用户 "${row.username}" 吗？`, '警告', {
    confirmButtonText: '确定删除',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await deleteUser(row.id)
      ElMessage.success('删除成功')
      fetchData()
    } catch (error) {
      console.error(error)
      ElMessage.error('删除用户失败')
    }
  }).catch(() => {})
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.user-container {
  height: 100%;
}

.user-card {
  min-height: calc(100vh - 120px);
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

@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
  }
}
</style>
