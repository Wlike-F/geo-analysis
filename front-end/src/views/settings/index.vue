<template>
  <div class="settings-container">
    <el-card shadow="hover" class="settings-card">
      <el-tabs tab-position="left" class="settings-tabs" v-model="activeTab">
        
        <!-- 标签页：个人信息 -->
        <el-tab-pane label="个人信息" name="profile" icon="User">
          <div class="pane-content">
            <h3 class="pane-title">个人信息设置</h3>
            <el-divider />
            
            <el-row :gutter="40">
              <el-col :span="12">
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
                    <el-input type="textarea" v-model="userInfo.introduction" rows="3" placeholder="地质测井数据分析员..." />
                  </el-form-item>
                  <el-form-item>
                    <el-button type="primary" @click="saveInfo" icon="Check" :loading="loading">保存个人信息</el-button>
                  </el-form-item>
                </el-form>
              </el-col>
              <el-col :span="12" class="avatar-col">
                <div class="avatar-wrapper">
                  <el-avatar :size="120" :src="userInfo.avatar || 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'" />
                  <el-input v-model="userInfo.avatar" placeholder="输入头像外链 URL" style="margin-top: 15px; width: 80%" />
                  <p class="avatar-tip">支持外链图片地址</p>
                </div>
              </el-col>
            </el-row>
          </div>
        </el-tab-pane>

        <!-- 标签页：显示与外观 -->
        <el-tab-pane label="显示与外观" name="appearance" icon="Monitor">
          <div class="pane-content">
            <h3 class="pane-title">系统偏好设置</h3>
            <el-divider />
            <el-form label-width="150px" size="large">
              <el-form-item label="测井数据精度">
                <el-select v-model="sysSettings.precision" style="width: 200px">
                  <el-option label="默认 - 4位小数" value="4" />
                  <el-option label="精度 - 2位小数" value="2" />
                </el-select>
              </el-form-item>
              <el-form-item label="分页数量预设">
                <el-radio-group v-model="sysSettings.pageSize">
                  <el-radio :label="100">100条/页</el-radio>
                  <el-radio :label="300">300条/页</el-radio>
                  <el-radio :label="500">500条/页</el-radio>
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

        <!-- 标签页：安全设置 -->
        <el-tab-pane label="安全隐私" name="security" icon="Lock">
          <div class="pane-content">
            <h3 class="pane-title">账号安全</h3>
            <el-divider />
            <div class="security-list">
              <div class="security-item">
                <div class="item-info">
                  <div class="item-title">账户密码</div>
                  <div class="item-desc">建议定期修改密码以保证账号安全</div>
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
                  <div class="item-desc">查看最近登录系统的记录和IP地址</div>
                </div>
                <el-button type="info" plain>查看详情</el-button>
              </div>
            </div>
          </div>
        </el-tab-pane>

      </el-tabs>
    </el-card>

    <!-- 修改密码弹窗 -->
    <el-dialog title="修改密码" v-model="pwdDialogVisible" width="450px" @close="resetPwdForm">
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="100px">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入当前密码" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="请输入新密码（至少6位）" />
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
  </div>
</template>

<script setup>
import { reactive, watch, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { updateProfile, updatePwd, updateSettings } from '@/api/user'

const router = useRouter()
const userStore = useUserStore()

const activeTab = ref('profile')

const userInfo = reactive({
  username: '',
  realName: '',
  email: '',
  introduction: '',
  avatar: ''
})

const loading = ref(false)

// 监视 Store 数据同步到表单
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
    if (newVal && newVal.sysSettings) {
      try {
        const settings = JSON.parse(newVal.sysSettings);
        Object.assign(sysSettings, settings);
      } catch(e) {}
    }
  },
  { immediate: true, deep: true }
)

const saveInfo = async () => {
  loading.value = true
  try {
    const dataToSave = {
      realName: userInfo.realName,
      email: userInfo.email,
      introduction: userInfo.introduction,
      avatar: userInfo.avatar
    }
    await updateProfile(dataToSave)
    ElMessage.success('个人信息更新成功！')
    await userStore.fetchUserInfo() // 重新获取最新信息
  } catch (error) {
    console.error(error)
  } finally {
    loading.value = false
  }
}

const saveSettings = async () => {
    try {
      await updateSettings({ sysSettings: JSON.stringify(sysSettings) });
      ElMessage.success('系统偏好已成功同步至云端！');
      await userStore.fetchUserInfo();
    } catch(err) {
      ElMessage.error('保存设置失败');
    }
  }

const goToProfile = () => {
  activeTab.value = 'profile'
}

// ==== 修改密码逻辑 ====
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
  if (pwdFormRef.value) {
    pwdFormRef.value.resetFields()
  }
}

const submitPwdChange = () => {
  if (!pwdFormRef.value) return
  pwdFormRef.value.validate(async (valid) => {
    if (valid) {
      pwdLoading.value = true
      try {
        await updatePwd({
          oldPassword: pwdForm.oldPassword,
          newPassword: pwdForm.newPassword
        })
        ElMessage.success('密码修改成功，请重新登录')
        pwdDialogVisible.value = false
        userStore.logout()
        router.push('/login')
      } catch (error) {
        console.error(error)
      } finally {
        pwdLoading.value = false
      }
    }
  })
}
</script>

<style scoped>
.settings-container {
  height: 100%;
}
.settings-card {
  min-height: calc(100vh - 120px);
  border-radius: 8px;
}
.settings-tabs {
  height: 100%;
}
/* 左侧 Tabs 宽度覆盖调整以增强大气感 */
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
.pane-title {
  font-size: 20px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 20px;
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
}
.mt-20 {
  margin-top: 20px;
}
.avatar-tip {
  color: #909399;
  font-size: 12px;
  margin-top: 10px;
}

/* 安全隐私列表 */
.security-list {
  max-width: 600px;
}
.security-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
}
.item-title {
  font-size: 15px;
  font-weight: bold;
  color: #303133;
  margin-bottom: 5px;
}
.item-desc {
  font-size: 13px;
  color: #909399;
}
</style>