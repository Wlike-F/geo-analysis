<template>
  <div class="settings-container">
    <el-card shadow="hover" class="settings-card">
      <el-tabs v-model="activeTab" tab-position="left" class="settings-tabs">
        <el-tab-pane label="个人信息" name="profile" icon="User">
          <div class="pane-content">
            <div class="pane-header">
              <h3 class="pane-title">个人信息设置</h3>
              <p class="pane-subtitle">维护账号基本资料、头像地址与个人简介。</p>
            </div>
            <el-divider />

            <el-row :gutter="40" class="profile-row">
              <el-col :xs="24" :lg="14">
                <el-form ref="profileFormRef" label-position="top" :model="userInfo" :rules="profileRules" size="large">
                  <el-form-item label="系统账号">
                    <el-input v-model="userInfo.username" disabled />
                  </el-form-item>
                  <el-form-item label="真实姓名">
                    <el-input v-model="userInfo.realName" placeholder="请输入你的姓名" />
                  </el-form-item>
                  <el-form-item label="绑定邮箱">
                    <el-input v-model="userInfo.email" placeholder="example@domain.com" />
                  </el-form-item>
                  <el-form-item label="个人简介">
                    <el-input v-model="userInfo.introduction" type="textarea" rows="3" placeholder="地质测井数据分析员..." />
                  </el-form-item>
                  <el-form-item>
                    <el-button type="primary" icon="Check" :loading="loading" @click="saveInfo">保存个人信息</el-button>
                  </el-form-item>
                </el-form>
              </el-col>

              <el-col :xs="24" :lg="10" class="avatar-col">
                <div class="avatar-wrapper">
                  <el-avatar :size="120" :src="userInfo.avatar || 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'" />
                  <el-input v-model="userInfo.avatar" class="avatar-input" placeholder="输入头像外链 URL" />
                  <p class="avatar-tip">支持外链图片地址</p>
                </div>
              </el-col>
            </el-row>
          </div>
        </el-tab-pane>

        <el-tab-pane label="筛选偏好" name="filterPref" icon="Filter">
          <div class="pane-content">
            <div class="pane-header">
              <h3 class="pane-title">筛选偏好设置</h3>
              <p class="pane-subtitle">配置全局筛选默认列、管理常用条件预设模板。</p>
            </div>
            <el-divider />

            <!-- 区块1：默认全局筛选列 -->
            <div class="security-list" style="max-width: 720px;">
              <div class="pref-section-title">默认全局筛选列</div>
              <p class="pref-section-desc">进入"异常测段提取 → 所有文件"模式时，自动加载以下列作为筛选条件候选。从字段映射表中选择。</p>
              <el-select
                v-model="defaultFilterColumns"
                multiple filterable collapse-tags collapse-tags-tooltip
                placeholder="点击选择默认筛选列…"
                style="width: 100%; margin-bottom: 12px;"
                :loading="columnMappingLoading"
              >
                <el-option
                  v-for="col in columnMappingOptions"
                  :key="col.standardName"
                  :label="col.chineseMeaning ? `${col.standardName}（${col.chineseMeaning}）` : col.standardName"
                  :value="col.standardName"
                />
              </el-select>
              <el-button type="primary" size="default" @click="handleSaveDefaultColumns" :loading="defaultColumnsSaving">保存默认列</el-button>
            </div>

            <el-divider border-style="dashed" />

            <!-- 区块2：条件预设模板 -->
            <div>
              <div class="pref-section-title">条件预设模板</div>
              <p class="pref-section-desc">保存常用的筛选条件组合，在异常测段提取页面一键加载。</p>

              <div style="margin-bottom: 12px; display: flex; gap: 8px;">
                <el-button type="primary" size="default" @click="openPresetDialog()">新建预设</el-button>
                <el-button icon="Refresh" size="default" @click="loadPresets" :loading="presetLoading">刷新</el-button>
              </div>

              <el-table :data="presetList" v-loading="presetLoading" border style="width: 80%;" size="default">
                <el-table-column type="index" label="序号" width="60" align="center" />
                <el-table-column prop="name" label="预设名称" min-width="60" align = "center" />
                <el-table-column prop="scope" label="适用范围" width="100" align="center">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.scope === 'all' ? '' : 'warning'">{{ row.scope === 'all' ? '全局' : '当前' }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="包含列" min-width="80">
                  <template #default="{ row }">
                    <template v-if="row.columnsJson">
                      <el-tag v-for="col in parseJson(row.columnsJson, [])" :key="col" size="small" style="margin: 2px 4px 2px 0;">{{ col }}</el-tag>
                    </template>
                    <span v-else style="color: var(--el-text-color-placeholder);">—</span>
                  </template>
                </el-table-column>
                <el-table-column prop="updateTime" label="更新时间" width="180" align="center" />
                <el-table-column label="操作" width="120" align="center">
                  <template #default="{ row }">
                    <el-button type="primary" link size="small" @click="openPresetDialog(row)">编辑</el-button>
                    <el-button type="danger" link size="small" @click="handleDeletePreset(row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="安全隐私" name="security" icon="Lock">
          <div class="pane-content">
            <div class="pane-header">
              <h3 class="pane-title">账号安全</h3>
              <p class="pane-subtitle">维护密码、邮箱与近期登录记录。</p>
            </div>
            <el-divider />

            <div class="security-list">
              <div class="security-item">
                <div class="item-info">
                  <div class="item-title">账户密码</div>
                  <div class="item-desc">建议定期修改密码以保证账号安全。</div>
                </div>
                <el-button type="primary" plain @click="pwdDialogVisible = true">修改密码</el-button>
              </div>
              <el-divider border-style="dashed" />
              <div class="security-item">
                <div class="item-info">
                  <div class="item-title">绑定邮箱</div>
                  <div class="item-desc">已绑定邮箱：{{ userStore.userInfo.email || '未绑定' }}</div>
                </div>
                <el-button type="primary" plain @click="goToProfile">修改邮箱</el-button>
              </div>
              <el-divider border-style="dashed" />
              <div class="security-item">
                <div class="item-info">
                  <div class="item-title">登录日志</div>
                  <div class="item-desc">查看最近登录系统的记录和活动时间。</div>
                </div>
                <el-button type="info" plain @click="showLoginLogDialog">查看详情</el-button>
              </div>
              <el-divider border-style="dashed" />
              <div class="security-item">
                <div class="item-info">
                  <div class="item-title">存储用量</div>
                  <div class="item-desc">当前账号的数据占用概览。</div>
                </div>
                <el-button type="info" plain @click="showStorageDialog" :loading="storageLoading">查看用量</el-button>
              </div>
              <el-divider border-style="dashed" />
              <div class="security-item">
                <div class="item-info">
                  <div class="item-title">清除缓存</div>
                  <div class="item-desc">清理已删除文件的残留数据、脏数据和过期日志。</div>
                </div>
                <el-button type="danger" plain @click="handleCleanup" :loading="cleanupLoading">清除缓存</el-button>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="操作日志" name="oplog" icon="Document">
          <div class="pane-content">
            <div class="pane-header">
              <h3 class="pane-title">操作日志</h3>
              <p class="pane-subtitle">记录所有文件上传、解析、导出、删除等操作历史。</p>
            </div>
            <el-divider />

            <el-table :data="opLogData" v-loading="opLogLoading" align="center" border style="width: 100%">
              <el-table-column type="index" label="序号" width="60" align="center" />
              <el-table-column prop="module" label="操作模块" width="120" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="getModuleTagType(row.module)">{{ row.module }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="description" label="操作详情" min-width="200" />
              <el-table-column prop="fileCount" label="文件数" width="80" align="center" />
              <el-table-column prop="lineCount" label="行数" width="100" align="center">
                <template #default="{ row }">
                  {{ row.lineCount > 0 ? row.lineCount.toLocaleString() : '-' }}
                </template>
              </el-table-column>
              <el-table-column prop="createTime" label="操作时间" width="180" align="center" />
            </el-table>
            <div class="log-pagination">
              <el-pagination
                v-model:current-page="opLogPage.current"
                v-model:page-size="opLogPage.size"
                :total="opLogPage.total"
                layout="total, prev, pager, next"
                @current-change="fetchOpLogs"
                @size-change="fetchOpLogs"
              />
            </div>
          </div>
        </el-tab-pane>
        <el-tab-pane label="运行日志" name="runtime" icon="Monitor">
          <div class="pane-content">
            <div class="pane-header">
              <h3 class="pane-title">运行日志</h3>
              <p class="pane-subtitle">实时查看后端服务运行状态和错误信息，每 3 秒自动刷新。</p>
            </div>
            <el-divider />

            <div class="runtime-toolbar">
              <el-select v-model="runtimeLevel" style="width: 140px;" @change="fetchRuntimeLogs">
                <el-option label="全部" value="ALL" />
                <el-option label="INFO" value="INFO" />
                <el-option label="WARN" value="WARN" />
                <el-option label="ERROR" value="ERROR" />
              </el-select>
              <el-button type="primary" icon="Refresh" @click="fetchRuntimeLogs" :loading="runtimeLoading">刷新日志</el-button>
              <el-button type="danger" icon="Delete" plain @click="runtimeLogs = []">清空显示</el-button>
              <span class="runtime-count">共 {{ runtimeLogs.length }} 条</span>
            </div>

            <div class="runtime-terminal" ref="runtimeTerminalRef">
              <div v-if="runtimeLogs.length === 0" class="runtime-empty">暂无运行日志...</div>
              <div
                v-for="(log, idx) in runtimeLogs"
                :key="idx"
                class="runtime-line"
                :class="'level-' + (log.level || '').toLowerCase()"
              >
                <span class="runtime-time">{{ log.timestamp }}</span>
                <span class="runtime-level">[{{ log.level }}]</span>
                <span class="runtime-logger">{{ log.logger }}</span>
                <span class="runtime-msg">{{ log.message }}</span>
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog title="修改密码" v-model="pwdDialogVisible" width="450px" @close="resetPwdForm">
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="100px">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入当前密码" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="请输入新密码（至少 6 位）" />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="pwdDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="pwdLoading" @click="submitPwdChange">立即修改</el-button>
        </span>
      </template>
    </el-dialog>

    <el-dialog title="登录日志" v-model="logDialogVisible" width="600px" append-to-body>
      <el-table :data="loginLogData" v-loading="logLoading" align="center" border>
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="description" label="活动详情" min-width="200" />
        <el-table-column prop="createTime" label="发生时间" width="180" align="center" />
      </el-table>
      <div class="log-pagination">
        <el-pagination
          v-model:current-page="logPage.current"
          v-model:page-size="logPage.size"
          :total="logPage.total"
          layout="total, prev, pager, next"
          @current-change="fetchLoginLogs"
          @size-change="fetchLoginLogs"
        />
      </div>
      <template #footer>
        <el-button @click="logDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog title="存储用量" v-model="storageDialogVisible" width="500px" append-to-body>
      <div class="storage-grid" v-loading="storageLoading">
        <div class="storage-item">
          <div class="storage-label">已上传文件</div>
          <div class="storage-value">{{ storageStats.fileCount ?? '-' }} <span class="storage-unit">个</span></div>
        </div>
        <div class="storage-item">
          <div class="storage-label">解析总行数</div>
          <div class="storage-value">{{ formatNumber(storageStats.totalLines) }} <span class="storage-unit">行</span></div>
        </div>
        <div class="storage-item">
          <div class="storage-label">脏数据行数</div>
          <div class="storage-value">{{ formatNumber(storageStats.dirtyLines) }} <span class="storage-unit">行</span></div>
        </div>
        <div class="storage-item">
          <div class="storage-label">操作日志</div>
          <div class="storage-value">{{ formatNumber(storageStats.logCount) }} <span class="storage-unit">条</span></div>
        </div>
      </div>
      <template #footer>
        <el-button @click="storageDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 预设编辑弹窗 -->
    <el-dialog v-model="presetDialogVisible" :title="presetForm.id ? '编辑预设' : '新建预设'" width="520px" destroy-on-close @close="resetPresetForm">
      <el-form :model="presetForm" label-width="90px" size="default">
        <el-form-item label="预设名称" required>
          <el-input v-model="presetForm.name" placeholder="如：高GR异常" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="适用范围">
          <el-radio-group v-model="presetForm.scope">
            <el-radio value="all">全局（所有文件）</el-radio>
            <el-radio value="current">当前文件</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="包含列">
          <el-select
            v-model="presetColumnList"
            multiple filterable collapse-tags collapse-tags-tooltip
            placeholder="选择筛选列…"
            style="width: 100%;"
          >
            <el-option
              v-for="col in columnMappingOptions"
              :key="col.standardName"
              :label="col.chineseMeaning ? `${col.standardName}（${col.chineseMeaning}）` : col.standardName"
              :value="col.standardName"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="数值条件">
          <div style="width: 100%;">
            <div v-for="col in presetColumnList" :key="col" style="display: flex; align-items: center; gap: 8px; margin-bottom: 8px;">
              <span style="width: 60px; flex-shrink: 0; font-size: 13px;">{{ col }}</span>
              <el-input v-model="presetFilters[col].min" placeholder="最小值" clearable style="width: 120px;" />
              <span style="color: #909399;">—</span>
              <el-input v-model="presetFilters[col].max" placeholder="最大值" clearable style="width: 120px;" />
            </div>
            <span v-if="presetColumnList.length === 0" style="color: var(--el-text-color-placeholder); font-size: 13px;">请先选择包含列</span>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="presetDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSavePreset" :loading="presetSaving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, watch, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { logout as logoutApi } from '@/api/auth'
import { getLoginLogs, getAllLogs, getStorageStats, cleanupCache, getRuntimeLogs } from '@/api/log'
import { updateProfile, updatePwd } from '@/api/user'
import { useUserStore } from '@/store/user'
import { getRefreshToken } from '@/utils/token'
import { getAllColumnMappings } from '@/api/columnMapping'
import { getDefaultColumns, saveDefaultColumns, listPresets, savePreset, deletePreset } from '@/api/filterPreset'

const router = useRouter()
const userStore = useUserStore()
const activeTab = ref('profile')
const loading = ref(false)

const userInfo = reactive({
  username: '',
  realName: '',
  email: '',
  introduction: '',
  avatar: ''
})

watch(
  () => userStore.userInfo,
  (newVal) => {
    if (newVal) {
      Object.assign(userInfo, newVal)
    }
  },
  { immediate: true, deep: true }
)

const saveInfo = async () => {
  if (profileFormRef.value) {
    try { await profileFormRef.value.validate() } catch { return }
  }
  loading.value = true
  try {
    await updateProfile({
      realName: userInfo.realName,
      email: userInfo.email,
      introduction: userInfo.introduction,
      avatar: userInfo.avatar
    })
    ElMessage.success('个人信息更新成功')
    await userStore.fetchUserInfo()
  } catch (error) {
    console.error(error)
  } finally {
    loading.value = false
  }
}

const goToProfile = () => {
  activeTab.value = 'profile'
}

const pwdDialogVisible = ref(false)
const pwdLoading = ref(false)
const pwdFormRef = ref(null)
const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const validatePass2 = (rule, value, callback) => {
  if (value === '') {
    callback(new Error('请再次输入密码'))
  } else if (value !== pwdForm.newPassword) {
    callback(new Error('两次输入密码不一致!'))
  } else {
    callback()
  }
}

const pwdRules = reactive({
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ],
  confirmPassword: [{ required: true, validator: validatePass2, trigger: 'blur' }]
})

const profileFormRef = ref(null)
const profileRules = reactive({
  email: [{ type: 'email', message: '请输入有效的邮箱地址', trigger: 'blur' }]
})

const resetPwdForm = () => {
  pwdFormRef.value?.resetFields()
}

const submitPwdChange = () => {
  if (!pwdFormRef.value) return
  pwdFormRef.value.validate(async (valid) => {
    if (!valid) return
    pwdLoading.value = true
    try {
      await updatePwd({
        oldPassword: pwdForm.oldPassword,
        newPassword: pwdForm.newPassword
      })
      ElMessage.success('密码修改成功，请重新登录')
      pwdDialogVisible.value = false
      try {
        const refreshToken = getRefreshToken()
        if (refreshToken) {
          await logoutApi({ refreshToken })
        }
      } catch (error) {
        console.error('退出接口调用失败', error)
      } finally {
        userStore.logout()
        router.push('/login')
      }
    } catch (error) {
      console.error(error)
      ElMessage.error(error?.message || '密码修改失败，请检查原密码是否正确')
    } finally {
      pwdLoading.value = false
    }
  })
}

const logDialogVisible = ref(false)
const logLoading = ref(false)
const loginLogData = ref([])
const logPage = ref({ current: 1, size: 10, total: 0 })

const fetchLoginLogs = async () => {
  logLoading.value = true
  try {
    const res = await getLoginLogs({ current: logPage.value.current, size: logPage.value.size })
    if (res) {
      loginLogData.value = res.records || []
      logPage.value.total = res.total || 0
    }
  } finally {
    logLoading.value = false
  }
}

const showLoginLogDialog = () => {
  logDialogVisible.value = true
  fetchLoginLogs()
}

// --- 操作日志 ---
const opLogLoading = ref(false)
const opLogData = ref([])
const opLogPage = ref({ current: 1, size: 10, total: 0 })

const fetchOpLogs = async () => {
  opLogLoading.value = true
  try {
    const res = await getAllLogs({ current: opLogPage.value.current, size: opLogPage.value.size })
    if (res) {
      opLogData.value = res.records || []
      opLogPage.value.total = res.total || 0
    }
  } finally {
    opLogLoading.value = false
  }
}

const getModuleTagType = (module) => {
  if (!module) return 'info'
  if (module.includes('登录') || module.includes('注册')) return ''
  if (module.includes('文件') || module.includes('扫描') || module.includes('预载') || module.includes('上传')) return 'success'
  if (module.includes('导出') || module.includes('报表')) return 'warning'
  if (module.includes('删除') || module.includes('清空') || module.includes('扫除')) return 'danger'
  return 'info'
}

// --- 存储用量 ---
const storageDialogVisible = ref(false)
const storageLoading = ref(false)
const storageStats = ref({})

const showStorageDialog = async () => {
  storageDialogVisible.value = true
  storageLoading.value = true
  try {
    const res = await getStorageStats()
    if (res) storageStats.value = res
  } finally {
    storageLoading.value = false
  }
}

const formatNumber = (num) => {
  if (num == null || num === '-') return '-'
  return Number(num).toLocaleString()
}

// --- 清除缓存 ---
const cleanupLoading = ref(false)

const handleCleanup = async () => {
  try {
    await ElMessageBox.confirm(
      '将清理已删除文件的残留数据、脏数据、30天前的操作日志和过期令牌。此操作不可撤销，确定继续吗？',
      '清除缓存',
      { confirmButtonText: '确定清除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return // 用户取消
  }

  cleanupLoading.value = true
  try {
    const res = await cleanupCache()
    if (res) {
      const parts = []
      if (res.deletedFiles > 0) parts.push(`${res.deletedFiles} 个文件`)
      if (res.deletedRecords > 0) parts.push(`${res.deletedRecords.toLocaleString()} 条明细`)
      if (res.deletedDirty > 0) parts.push(`${res.deletedDirty.toLocaleString()} 条脏数据`)
      if (res.deletedLogs > 0) parts.push(`${res.deletedLogs} 条日志`)
      if (res.deletedTokens > 0) parts.push(`${res.deletedTokens} 个令牌`)
      ElMessage.success(parts.length > 0 ? `清理完成：${parts.join('、')}` : '当前无需清理的缓存数据')
    }
  } catch (error) {
    ElMessage.error('缓存清理失败')
  } finally {
    cleanupLoading.value = false
  }
}

// 进入操作日志 tab 时自动加载
watch(activeTab, (val) => {
  if (val === 'oplog' && opLogData.value.length === 0) {
    fetchOpLogs()
  }
  if (val === 'runtime') {
    fetchRuntimeLogs()
    startRuntimePolling()
  } else {
    stopRuntimePolling()
  }
  if (val === 'filterPref') {
    loadColumnMappings()
    loadDefaultColumns()
    loadPresets()
  }
})

onUnmounted(() => {
  stopRuntimePolling()
})

// --- 运行日志 ---
const runtimeLogs = ref([])
const runtimeLoading = ref(false)
const runtimeLevel = ref('ALL')
const runtimeTerminalRef = ref(null)
let runtimeTimer = null

const fetchRuntimeLogs = async () => {
  runtimeLoading.value = true
  try {
    const res = await getRuntimeLogs({ level: runtimeLevel.value, lines: 200 })
    if (res) {
      runtimeLogs.value = res
      await nextTick()
      scrollToBottom()
    }
  } finally {
    runtimeLoading.value = false
  }
}

const scrollToBottom = () => {
  const el = runtimeTerminalRef.value
  if (el) el.scrollTop = el.scrollHeight
}

const startRuntimePolling = () => {
  stopRuntimePolling()
  runtimeTimer = setInterval(() => {
    if (activeTab.value === 'runtime' && !runtimeLoading.value) fetchRuntimeLogs()
  }, 3000)
}

const stopRuntimePolling = () => {
  if (runtimeTimer) {
    clearInterval(runtimeTimer)
    runtimeTimer = null
  }
}

// ==================== 筛选偏好：默认全局筛选列 ====================
const defaultFilterColumns = ref([])
const defaultColumnsSaving = ref(false)
const columnMappingLoading = ref(false)
const columnMappingOptions = ref([])

const loadColumnMappings = async () => {
  columnMappingLoading.value = true
  try {
    const res = await getAllColumnMappings()
    if (res) columnMappingOptions.value = res
  } finally {
    columnMappingLoading.value = false
  }
}

const loadDefaultColumns = async () => {
  try {
    const res = await getDefaultColumns()
    if (res && res.columnsJson) {
      defaultFilterColumns.value = JSON.parse(res.columnsJson)
    }
  } catch (error) {
    console.error('加载默认筛选列失败', error)
  }
}

const handleSaveDefaultColumns = async () => {
  defaultColumnsSaving.value = true
  try {
    await saveDefaultColumns(defaultFilterColumns.value)
    ElMessage.success('默认筛选列保存成功')
  } catch (error) {
    ElMessage.error('保存失败，请重试')
    console.error(error)
  } finally {
    defaultColumnsSaving.value = false
  }
}

// ==================== 筛选偏好：条件预设模板 ====================
const presetList = ref([])
const presetLoading = ref(false)
const presetDialogVisible = ref(false)
const presetSaving = ref(false)
const presetForm = reactive({ id: null, name: '', scope: 'all', columnsJson: '', filtersJson: '' })
const presetColumnList = ref([])
const presetFilters = reactive({})

// 选中/取消列时自动同步 presetFilters 结构
watch(presetColumnList, (newCols) => {
  const newSet = new Set(newCols)
  // 清理已取消的列
  Object.keys(presetFilters).forEach(k => {
    if (!newSet.has(k)) delete presetFilters[k]
  })
  // 为新增列创建默认 { min:'', max:'' }
  newCols.forEach(col => {
    if (!presetFilters[col]) {
      presetFilters[col] = { min: '', max: '' }
    }
  })
})

const loadPresets = async () => {
  presetLoading.value = true
  try {
    const res = await listPresets()
    if (res) presetList.value = res
  } finally {
    presetLoading.value = false
  }
}

const parseJson = (str, fallback) => {
  if (!str) return fallback
  try { return JSON.parse(str) } catch { return fallback }
}

const openPresetDialog = (row) => {
  if (row && row.id) {
    presetForm.id = row.id
    presetForm.name = row.name
    presetForm.scope = row.scope || 'all'
    presetColumnList.value = parseJson(row.columnsJson, [])
    const filters = parseJson(row.filtersJson, {})
    // 先清空再填充
    Object.keys(presetFilters).forEach(k => delete presetFilters[k])
    Object.keys(filters).forEach(k => {
      presetFilters[k] = filters[k] || { min: '', max: '' }
    })
  } else {
    presetForm.id = null
    presetForm.name = ''
    presetForm.scope = 'all'
    presetColumnList.value = []
    Object.keys(presetFilters).forEach(k => delete presetFilters[k])
  }
  presetDialogVisible.value = true
}

const resetPresetForm = () => {
  presetForm.id = null
  presetForm.name = ''
  presetForm.scope = 'all'
  presetColumnList.value = []
  Object.keys(presetFilters).forEach(k => delete presetFilters[k])
}

const handleSavePreset = async () => {
  if (!presetForm.name.trim()) {
    ElMessage.warning('请输入预设名称')
    return
  }
  presetSaving.value = true
  try {
    const payload = {
      id: presetForm.id,
      name: presetForm.name.trim(),
      scope: presetForm.scope,
      columnsJson: JSON.stringify(presetColumnList.value),
      filtersJson: JSON.stringify(presetFilters),
      textFiltersJson: null
    }
    await savePreset(payload)
    ElMessage.success(presetForm.id ? '预设更新成功' : '预设创建成功')
    presetDialogVisible.value = false
    await loadPresets()
  } catch (error) {
    ElMessage.error('保存失败，请重试')
    console.error(error)
  } finally {
    presetSaving.value = false
  }
}

const handleDeletePreset = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除预设「${row.name}」？`, '删除预设', {
      confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning'
    })
  } catch { return }
  try {
    await deletePreset(row.id)
    ElMessage.success('删除成功')
    await loadPresets()
  } catch (error) {
    ElMessage.error('删除失败')
    console.error(error)
  }
}
</script>

<style scoped>
.settings-container {
  height: 100%;
}

.settings-card {
  min-height: calc(100vh - 120px);
  border-radius: 10px;
}

.settings-tabs {
  height: 100%;
}

:deep(.el-tabs--left .el-tabs__item) {
  text-align: left;
  padding: 0 40px 0 20px !important;
  height: 50px;
  line-height: 50px;
  font-size: 15px;
}

.pane-content {
  padding: 10px 40px;
}

.pane-header {
  margin-bottom: 8px;
}

.pane-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.pane-subtitle {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.profile-row {
  align-items: flex-start;
}

.avatar-col {
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding-top: 20px;
}

.avatar-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
}

.avatar-input {
  width: 80%;
  margin-top: 15px;
}

.avatar-tip {
  margin-top: 10px;
  font-size: 12px;
  color: #909399;
}

.security-list {
  max-width: 640px;
}

.security-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 10px 0;
}

.item-title {
  margin-bottom: 5px;
  font-size: 15px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.item-desc {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.log-pagination {
  margin-top: 15px;
  display: flex;
  justify-content: flex-end;
}

.storage-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  padding: 10px 0;
}

.storage-item {
  background: var(--el-fill-color-lighter, #f5f7fa);
  border-radius: 8px;
  padding: 16px;
  text-align: center;
}

.storage-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 8px;
}

.storage-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.storage-unit {
  font-size: 13px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}

@media (max-width: 992px) {
  :deep(.el-tabs--left) {
    display: block;
  }

  :deep(.el-tabs--left .el-tabs__header) {
    margin-right: 0;
  }

  :deep(.el-tabs--left .el-tabs__nav-wrap) {
    width: 100%;
  }

  .pane-content {
    padding: 10px 20px;
  }
}

@media (max-width: 768px) {
  .security-item {
    flex-direction: column;
    align-items: flex-start;
  }

  .avatar-input,
  .settings-select {
    width: 100%;
  }
}

/* ==================== 运行日志终端 ==================== */
.runtime-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding: 14px 16px;
  background: var(--el-fill-color-lighter, #f5f7fa);
  border-radius: 8px;
}

.runtime-count {
  margin-left: auto;
  font-size: 14px;
  color: var(--el-text-color-secondary);
  font-weight: 600;
}

.runtime-terminal {
  background: #1e1e2e;
  border-radius: 8px;
  padding: 16px;
  height: 420px;
  overflow-y: auto;
  font-family: 'Cascadia Code', 'Fira Code', 'Consolas', monospace;
  font-size: 12.5px;
  line-height: 1.7;
}

.runtime-empty {
  color: #6c7086;
  text-align: center;
  padding: 40px 0;
}

.runtime-line {
  white-space: pre-wrap;
  word-break: break-all;
  color: #cdd6f4;
}

.runtime-time {
  color: #6c7086;
  margin-right: 8px;
}

.runtime-level {
  font-weight: 600;
  margin-right: 8px;
}

.runtime-logger {
  color: #89b4fa;
  margin-right: 8px;
}

.runtime-msg {
  color: #cdd6f4;
}

.runtime-line.level-info .runtime-level {
  color: #a6e3a1;
}

.runtime-line.level-warn .runtime-level {
  color: #f9e2af;
}

.runtime-line.level-warn .runtime-msg {
  color: #f9e2af;
}

.runtime-line.level-error .runtime-level {
  color: #f38ba8;
}

.runtime-line.level-error .runtime-msg {
  color: #f38ba8;
}

.runtime-line.level-debug .runtime-level {
  color: #6c7086;
}

.runtime-line.level-debug .runtime-msg {
  color: #6c7086;
}

/* ==================== 筛选偏好 ==================== */
.pref-section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 6px;
}

.pref-section-desc {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 16px;
  line-height: 1.6;
}
</style>
