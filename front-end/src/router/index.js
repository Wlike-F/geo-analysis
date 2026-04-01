import { createRouter, createWebHistory } from 'vue-router'
import Layout from '@/layout/index.vue'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue')
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/register/index.vue')
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '首页概览', icon: 'Odometer' }
      },
      {
        path: 'datasource',
        name: 'DataSource',
        component: () => import('@/views/datasource/index.vue'),
        meta: { title: '数据源管理', icon: 'FolderOpened' }
      },
      {
        path: 'files',
        name: 'Files',
        component: () => import('@/views/files/index.vue'),
        meta: { title: '测井数据看板', icon: 'Document' }
      },
      {
        path: 'extract',
        name: 'Extract',
        component: () => import('@/views/extract/index.vue'),
        meta: { title: '异常测段提取', icon: 'Scissor' }
      }
      ,
      {
        path: 'user',
        name: 'User',
        component: () => import('@/views/user/index.vue'),
        meta: { title: '用户管理', icon: 'User' }
      },
      {
        path: 'instructions',
        name: 'Instructions',
        component: () => import('@/views/instructions/index.vue'),
        meta: { title: '使用说明', icon: 'Reading' }
      },
      {
        path: 'visualization',
        name: 'Visualization',
        component: () => import('@/views/visualization/index.vue'),
        meta: { title: '曲线图表', icon: 'DataAnalysis' }
      },
      {
        path: 'dictionary',
        name: 'Dictionary',
        component: () => import('@/views/settings/dictionary.vue'),
        meta: { title: '字典映射参数', icon: 'Key' }
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/views/settings/index.vue'),
        meta: { title: '系统设置', icon: 'Setting' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 简单的路由守卫拦截
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (to.path !== '/login' && to.path !== '/register' && !token) {
    next('/login')
  } else {
    next()
  }
})

export default router