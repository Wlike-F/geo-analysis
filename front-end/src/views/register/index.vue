<template>
  <div class="register-container">
    <div class="register-box">
      <div class="register-header">
        <h2 class="title">创建系统账号</h2>
        <p class="subtitle">欢迎注册地质测井分析后台</p>
      </div>

      <el-form :model="regForm" :rules="rules" ref="regFormRef" class="register-form" size="large">
        <el-form-item prop="username">
          <el-input 
            v-model="regForm.username" 
            prefix-icon="User"
            placeholder="请输入账号" 
          />
        </el-form-item>
        
        <el-form-item prop="email">
          <el-input 
            v-model="regForm.email" 
            prefix-icon="Message"
            placeholder="请输入您的常用邮箱" 
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input 
            v-model="regForm.password" 
            prefix-icon="Lock"
            type="password" 
            placeholder="请输入密码"
            show-password
          />
        </el-form-item>

        <el-form-item prop="confirmPassword">
          <el-input 
            v-model="regForm.confirmPassword" 
            prefix-icon="Lock"
            type="password" 
            placeholder="请确认密码"
            show-password
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" class="register-btn" @click="handleRegister" :loading="loading">
            立即注册
          </el-button>
        </el-form-item>
        
        <div class="bottom-links">
          <span>已有账号？</span>
          <el-link type="primary" :underline="false" @click="$router.push('/login')">返回登录</el-link>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { register } from '@/api/auth'

const router = useRouter()
const regFormRef = ref(null)

const regForm = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: ''
})

const validatePass2 = (rule, value, callback) => {
  if (value === '') {
    callback(new Error('请再次输入密码'))
  } else if (value !== regForm.password) {
    callback(new Error('两次输入密码不一致!'))
  } else {
    callback()
  }
}

const rules = reactive({
  username: [
    { required: true, message: '请输入账号名称', trigger: 'blur' },
    { min: 3, max: 15, message: '长度在 3 到 15 个字符', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱地址', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱地址', trigger: ['blur', 'change'] }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, validator: validatePass2, trigger: 'blur' }
  ]
})

const loading = ref(false)

const handleRegister = () => {
  regFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        await register({
          username: regForm.username,
          password: regForm.password,
          email: regForm.email
        })
        ElMessage.success('注册成功！请使用新账密登录')
        router.push('/login')
      } catch (error) {
        // 错误已经在 axios 拦截器中提示
      } finally {
        loading.value = false
      }
    }
  })
}
</script>

<style scoped>
.register-container {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-image: url('@/assets/images/background.png'); 
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  position: relative;
}

.register-container::before {
  content: "";
  position: absolute;
  top: 0; left: 0; width: 100%; height: 100%;
  background-color: rgba(15, 25, 35, 0.65);
  z-index: 1;
}

.register-box {
  width: 420px;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(15px); /* 玻璃拟态效果 */
  -webkit-backdrop-filter: blur(15px);
  border-radius: 12px;
  padding: 40px 45px;
  box-shadow: 0 10px 30px rgba(0,0,0,0.3);
  border: 1px solid rgba(255, 255, 255, 0.3);
  z-index: 2;
  transition: transform 0.3s ease;
}

.register-box:hover {
  transform: translateY(-2px);
}

.register-header {
  text-align: center;
  margin-bottom: 30px;
}

.title {
  font-size: 26px;
  font-weight: 700;
  color: #1f2d3d;
  margin: 0 0 10px 0;
  letter-spacing: 1px;
}

.subtitle {
  font-size: 14px;
  color: #606266;
  margin: 0;
}

.register-btn {
  width: 100%;
  font-size: 16px;
  font-weight: bold;
  letter-spacing: 1px;
  border-radius: 6px;
}

.bottom-links {
  text-align: right;
  font-size: 14px;
  color: #606266;
  margin-top: 10px;
}
</style>