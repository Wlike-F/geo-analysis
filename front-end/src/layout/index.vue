<template>
  <el-container class="layout-container">
    <el-aside :width="isCollapse ? '64px' : '240px'" class="sidebar" style="transition: width 0.3s">
      <div class="logo-container">
        <el-icon class="logo-icon" :size="24"><DataLine /></el-icon>
        <transition name="el-zoom-in-center">
          <span class="logo-text" v-show="!isCollapse">地质测井分析系统</span>
        </transition>
      </div>
      <el-menu
        :default-active="$route.path"
        router
        class="el-menu-vertical"
        background-color="#001529"
        text-color="#a6adb4"
        active-text-color="#fff"
        :collapse="isCollapse"
        :collapse-transition="false"
      >
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <template #title><span>仪表盘首页</span></template>
        </el-menu-item>
        <el-menu-item index="/datasource">
          <el-icon><FolderOpened /></el-icon>
          <template #title><span>数据源管理</span></template>
        </el-menu-item>
        
        <el-menu-item index="/extract">
          <el-icon><Scissor /></el-icon>
          <template #title><span>异常测段提取</span></template>
        </el-menu-item>
        
        <el-menu-item index="/visualization">
          <el-icon><DataAnalysis /></el-icon>
          <template #title><span>曲线可视化</span></template>
        </el-menu-item>

        <el-menu-item index="/user" v-if="userStore.isAdmin">
          <el-icon><User /></el-icon>
          <template #title><span>用户管理</span></template>
        </el-menu-item>
        
        <el-menu-item index="/instructions">
          <el-icon><Reading /></el-icon>
          <template #title><span>使用说明</span></template>
        </el-menu-item>

        <el-menu-item index="/dictionary">
          <el-icon><Key /></el-icon>
          <template #title><span>字典映射参数</span></template>
        </el-menu-item>

        <el-menu-item index="/settings">
          <el-icon><Setting /></el-icon>
          <template #title><span>系统设置</span></template>
        </el-menu-item>
      </el-menu>
    </el-aside>
    
    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="toggleSidebar">
            <component :is="isCollapse ? 'Expand' : 'Fold'" />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item>{{ $route.meta.title || '工作台' }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <div class="user-info">
            <el-tag v-if="userStore.userInfo.role" :type="userStore.isAdmin ? 'danger' : 'info'" size="small" effect="dark" style="margin-right: 8px">
              {{ userStore.isAdmin ? '管理员' : '普通用户' }}
            </el-tag>
            <el-avatar :size="30" :src="userStore.displayAvatar" />
            <span class="username">{{ userStore.userInfo.username ? userStore.displayName : '数据加载中...' }}</span>
          </div>
          <el-button type="danger" link @click="handleLogout" icon="SwitchButton">退出</el-button>
        </div>
      </el-header>
      
      <el-main class="main-content">
        <div style="height: 100%;">
          <router-view v-slot="{ Component, route }">
            <keep-alive>
              <component :is="Component" :key="route.path" />
            </keep-alive>
          </router-view>
        </div>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { logout as logoutApi } from '@/api/auth'
import { getRefreshToken } from '@/utils/token'

const router = useRouter()
const userStore = useUserStore()
const isCollapse = ref(false)

onMounted(() => {
  userStore.fetchUserInfo()
})

const toggleSidebar = () => {
  isCollapse.value = !isCollapse.value
}

const handleLogout = () => {
  ElMessageBox.confirm('确定要退出当前系统吗?', '提示', {
    confirmButtonText: '确定退出',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
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
  })
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
  width: 100vw;
}
.sidebar {
  background: linear-gradient(180deg, #001529 0%, #000c17 100%);
  box-shadow: 2px 0 10px rgba(0,21,41,0.45);
  display: flex;
  flex-direction: column;
  z-index: 10;
}
.logo-container {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: transparent;
  color: #fff;
  font-size: 18px;
  font-weight: bold;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
}
.logo-icon {
  margin-right: 8px;
  color: #1890ff;
  filter: drop-shadow(0 0 5px rgba(24,144,255,0.5));
}
.el-menu-vertical {
  border-right: none;
  flex: 1;
  background-color: transparent;
}
:deep(.el-menu-item) {
  margin: 4px 0;
}
:deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, rgba(24,144,255,0.2) 0%, transparent 100%) !important;
  border-left: 3px solid #1890ff;
}
.header {
  background-color: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  height: 60px;
  z-index: 9;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 20px;
}
.collapse-btn {
  font-size: 20px;
  cursor: pointer;
  color: #606266;
  transition: color 0.3s;
}
.collapse-btn:hover {
  color: #1890ff;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  font-size: 14px;
  color: #303133;
  font-weight: 500;
}
.main-content {
  background-color: #f0f2f5;
  padding: 24px;
  box-sizing: border-box;
}

/* 路由切换过渡动画 */
.fade-transform-leave-active, .fade-transform-enter-active {
  transition: all .3s;
}
.fade-transform-enter-from {
  opacity: 0;
  transform: translateX(-20px);
}
.fade-transform-leave-to {
  opacity: 0;
  transform: translateX(20px);
}
</style>