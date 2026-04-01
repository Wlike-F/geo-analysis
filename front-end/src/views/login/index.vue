<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-title">地质测井数据分析系统</div>
      <el-form :model="loginForm" :rules="rules" ref="loginFormRef" class="login-form">
        <el-form-item prop="username">
          <el-input 
            v-model="loginForm.username" 
            prefix-icon="User"
            placeholder="请输入用户名" 
            size="large"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input 
            v-model="loginForm.password" 
            prefix-icon="Lock"
            type="password" 
            placeholder="请输入密码"
            show-password
            size="large"
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="login-btn" size="large" @click="handleLogin" :loading="loading">
            登录系统
          </el-button>
        </el-form-item>

        <div class="bottom-links">
          <span>还没有账号？</span>
          <el-link type="primary" :underline="false" @click="$router.push('/register')">立即注册</el-link>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '@/api/auth'

const router = useRouter()
const loginFormRef = ref(null)

const loginForm = reactive({
  username: '',
  password: ''
})

const rules = reactive({
  username: [{ required: true, message: '请输入管理员账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
})

const loading = ref(false)

const handleLogin = () => {
  loginFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        const token = await login({
          username: loginForm.username,
          password: loginForm.password
        })
        
        localStorage.setItem('token', token)
        ElMessage.success('登录成功，欢迎访问测井系统')
        router.push('/')
      } catch (error) {
        // request.js 中已经统一提示错误
      } finally {
        loading.value = false
      }
    }
  })
}
</script>

<style scoped>
.login-container {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  /* 使用地质风貌背景图替换原本纯色 */
  background-image: url('@/assets/images/background.png'); 
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  position: relative;
}
/* 给背景图加一层半透明暗色遮罩以保证表单可见度 */
.login-container::before {
  content: "";
  position: absolute;
  top: 0; left: 0; width: 100%; height: 100%;
  background-color: rgba(30, 40, 50, 0.6);
  z-index: 1;
}
.login-box {
  width: 400px;
  background: rgba(255, 255, 255, 0.85); /* 稍微透明的白色 */
  backdrop-filter: blur(15px);
  -webkit-backdrop-filter: blur(15px);
  border-radius: 12px;
  padding: 45px 40px;
  box-shadow: 0 10px 30px rgba(0,0,0,0.3);
  border: 1px solid rgba(255, 255, 255, 0.3);
  z-index: 2; /* 浮于遮罩之上 */
  transition: transform 0.3s ease;
}
.login-box:hover {
  transform: translateY(-2px);
}
.login-title {
  text-align: center;
  font-size: 26px;
  font-weight: 700;
  color: #1f2d3d;
  margin-bottom: 30px;
  letter-spacing: 1px;
}
.login-btn {
  width: 100%;
  font-size: 16px;
  font-weight: bold;
  letter-spacing: 1px;
  border-radius: 6px;
  margin-top: 5px;
}
.bottom-links {
  text-align: right;
  font-size: 14px;
  color: #606266;
  margin-top: 15px;
}
</style>