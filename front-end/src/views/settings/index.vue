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
                <el-form label-position="top" :model="userInfo" size="large">
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

        <el-tab-pane label="显示与外观" name="appearance" icon="Monitor">
          <div class="pane-content">
            <div class="pane-header">
              <h3 class="pane-title">系统偏好设置</h3>
              <p class="pane-subtitle">调整展示精度、分页数量与动效体验。</p>
            </div>
            <el-divider />

            <el-form label-width="150px" size="large" class="appearance-form">
              <el-form-item label="测井数据精度">
                <el-select v-model="sysSettings.precision" class="settings-select">
                  <el-option label="默认 - 4 位小数" value="4" />
                  <el-option label="精度 - 2 位小数" value="2" />
                </el-select>
              </el-form-item>
              <el-form-item label="分页数量预设">
                <el-radio-group v-model="sysSettings.pageSize">
                  <el-radio :label="100">100 条/页</el-radio>
                  <el-radio :label="300">300 条/页</el-radio>
                  <el-radio :label="500">500 条/页</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="开启平滑动画">
                <el-switch v-model="sysSettings.smoothAnimation" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" icon="Setting" @click="saveSettings">应用外观设置</el-button>
              </el-form-item>
            </el-form>
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
        />
      </div>
      <template #footer>
        <el-button @click="logDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { logout as logoutApi } from '@/api/auth'
import { getLoginLogs } from '@/api/log'
import { updateProfile, updatePwd, updateSettings } from '@/api/user'
import { useUserStore } from '@/store/user'
import { getRefreshToken } from '@/utils/token'

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

const sysSettings = reactive({
  precision: '4',
  pageSize: 300,
  smoothAnimation: true
})

watch(
  () => userStore.userInfo,
  (newVal) => {
    if (newVal?.sysSettings) {
      try {
        Object.assign(sysSettings, JSON.parse(newVal.sysSettings))
      } catch (error) {
        console.error(error)
      }
    }
  },
  { immediate: true, deep: true }
)

const saveInfo = async () => {
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

const saveSettings = async () => {
  try {
    await updateSettings({ sysSettings: JSON.stringify(sysSettings) })
    ElMessage.success('系统偏好已成功同步至云端')
    await userStore.fetchUserInfo()
  } catch (error) {
    ElMessage.error('保存设置失败')
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

.settings-select {
  width: 220px;
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
</style>
