
<template>
  <div class="dashboard-container">
    
    <!-- 欢迎横幅区：增加背景纹理和几何层次感 -->
    <div class="welcome-banner mb-6">
      <div class="banner-decor-1"></div>
      <div class="banner-decor-2"></div>
      <div class="banner-content">
        <h2>欢迎使用地质测井数据分析系统</h2>
        <p>提供专业、高效、高精度的多通道测井序列提取与门限过滤导出服务。</p>
      </div>
    </div>

    <!-- 静态核心数据卡片 (现代SaaS风格) -->
    <el-row :gutter="24" v-loading="loading" class="mb-6">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card" style="border-left-color: var(--accent-blue);">
          <div class="stat-inner">
            <div class="stat-info">
              <div class="stat-title text-secondary">已加载测井文件</div>
              <div class="stat-value">
                <el-statistic :value="statsData.totalFiles" value-style="font-family: var(--font-mono); font-weight: 700; font-size: 28px; color: var(--el-text-color-primary);" />
                <span class="stat-unit">次</span>
              </div>
            </div>
            <div class="stat-icon-wrapper" style="background: var(--bg-gradient-blue); color: var(--accent-blue);">
              <el-icon :size="24"><Document /></el-icon>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card shadow="hover" class="stat-card" style="border-left-color: var(--accent-green);">
          <div class="stat-inner">
            <div class="stat-info">
              <div class="stat-title text-secondary">累计解析测井长</div>
              <div class="stat-value">
                <el-statistic :value="statsData.totalLines" value-style="font-family: var(--font-mono); font-weight: 700; font-size: 28px; color: var(--el-text-color-primary);" />
                <span class="stat-unit">行</span>
              </div>
            </div>
            <div class="stat-icon-wrapper" style="background: var(--bg-gradient-green); color: var(--accent-green);">
              <el-icon :size="24"><DataLine /></el-icon>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card shadow="hover" class="stat-card" style="border-left-color: var(--accent-orange);">
          <div class="stat-inner">
            <div class="stat-info">
              <div class="stat-title text-secondary">导出 Excel 报表</div>
              <div class="stat-value">
                <el-statistic :value="statsData.totalExports" value-style="font-family: var(--font-mono); font-weight: 700; font-size: 28px; color: var(--el-text-color-primary);" />
                <span class="stat-unit">次</span>
              </div>
            </div>
            <div class="stat-icon-wrapper" style="background: var(--bg-gradient-orange); color: var(--accent-orange);">
              <el-icon :size="24"><Download /></el-icon>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card shadow="hover" class="stat-card" style="border-left-color: var(--accent-purple);">
          <div class="stat-inner">
            <div class="stat-info">
              <div class="stat-title text-secondary">系统当前状态</div>
              <div class="stat-value inline-status">
                <span class="status-dot" :class="statusColorClass"></span>
                <span class="status-text">{{ statsData.systemStatus }}</span>
              </div>
            </div>
            <div class="stat-icon-wrapper" style="background: var(--bg-gradient-purple); color: var(--accent-purple);">
              <el-icon :size="24"><Monitor /></el-icon>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 说明与分析卡片区 (去掉繁杂边缘，增加留白间距) -->
    <el-row :gutter="24" class="content-row">
      <el-col :span="16">
        <el-card shadow="hover" class="sys-summary-card">
          <div class="section-title">
            <div class="icon-block"><el-icon><Operation /></el-icon></div>
            <span>系统概览与核心能力</span>
          </div>
          
          <div class="content pt-4">
            <el-alert
                v-if="userStore.isAdmin"
              title="管理员专属工作台"
              type="success"
              description="您已登录最高权限环境，当前所有核心分析节点准备就绪。"
              show-icon
              :closable="false"
              class="mb-6 elegant-alert"
            />
            <p class="intro-text">本系统为您提供专业的地质测井数据解析与归档能力，支持以下核心功能流：</p>
            <div class="feature-grid mt-4">
              <div class="feature-item">
                <div class="f-icon"><el-icon><CopyDocument /></el-icon></div>
                <div class="f-desc">
                  <strong>多通道极速加载</strong><br/>支持大批量超大 TXT 文件探测与内存并发加载。
                </div>
              </div>
              <div class="feature-item">
                <div class="f-icon"><el-icon><Reading /></el-icon></div>
                <div class="f-desc">
                  <strong>并发标签页比对</strong><br/>随意组合标签页，在同一个视图中横向对比多口井属性。
                </div>
              </div>
              <div class="feature-item">
                <div class="f-icon"><el-icon><Filter /></el-icon></div>
                <div class="f-desc">
                  <strong>精细门限矩阵</strong><br/>支持不同物理属的四位小数强逼近滤除与阈值过滤。
                </div>
              </div>
              <div class="feature-item">
                <div class="f-icon"><el-icon><HelpFilled /></el-icon></div>
                <div class="f-desc">
                  <strong>一键智能报表</strong><br/>通过后端 POI 流将结果输出为高兼容性 Excel/CSV 报表。
                </div>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
      
      <el-col :span="8">
        <el-card shadow="hover" class="sys-summary-card">
          <div class="section-title">
            <div class="icon-block"><el-icon><Lightning /></el-icon></div>
            <span>快捷操作向导</span>
          </div>
          
          <div class="quick-action-area pt-4">
            <el-button type="primary" size="large" class="quick-btn block-btn" @click="$router.push('/files')">
              <el-icon class="mr-2"><Guide /></el-icon>
              前往业务控制台
            </el-button>
            <el-button size="large" class="quick-btn block-btn ghost-btn" @click="$router.push('/settings')">
              <el-icon class="mr-2"><Setting /></el-icon>
              偏好参数配置
            </el-button>
          </div>
          
          <div class="recent-act mt-8">
            <div class="flex-between mb-4">
              <h4 class="sub-title">最新活跃记录</h4>
              <el-button link type="primary" @click="fetchStats" class="refresh-btn">
                <el-icon class="mr-1"><RefreshRight /></el-icon> 刷新
              </el-button>
            </div>
            
            <el-empty v-if="recentLogs.length === 0" description="暂无操作日志" :image-size="60" />
            <el-timeline v-else class="elegant-timeline">
              <el-timeline-item 
                v-for="(log, idx) in recentLogs" 
                :key="idx"
                :timestamp="formatTime(log.createTime)" 
                placement="top" 
                :color="getLogColor(log.module)"
                :hollow="true"
              >
                <div class="log-module">{{ log.module }}</div>
                <div class="log-desc">{{ log.description }}</div>
              </el-timeline-item>
            </el-timeline>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useUserStore } from '@/store/user'
import { getDashboardStats } from '@/api/dashboard'
import {
  Document, DataLine, Download, Monitor, Operation, Lightning,
  CopyDocument, Reading, Filter, HelpFilled, Guide, Setting, RefreshRight
} from '@element-plus/icons-vue'

const userStore = useUserStore()
const statsData = ref({
  totalFiles: 0,
  totalLines: 0,
  totalExports: 0,
  systemStatus: '加载中...'
})

const recentLogs = ref([])
const loading = ref(true)

const fetchStats = async () => {
  try {
    loading.value = true
    const resData = await getDashboardStats()
    if (resData && resData.stats) {
      statsData.value = {
        totalFiles: resData.stats.totalFiles || 0,
        totalLines: resData.stats.totalLines || 0,
        systemStatus: resData.stats.systemStatus || '未知状态',
        totalExports: resData.stats.totalExports || 0
      }
      recentLogs.value = resData.recentLogs || []
    }
  } catch (error) {
    console.error('获取仪表盘统计数据失败', error)
  } finally {
    loading.value = false
  }
}

const formatTime = (time) => {
  if (!time) return ''
  const date = new Date(time)
  const pad = (n) => n < 10 ? '0' + n : n
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

const getLogColor = (module) => {
  if (module.includes('异常') || module.includes('失败')) return '#ef4444' // red-500
  if (module.includes('上传') || module.includes('加载') || module.includes('扫描')) return '#3b82f6' // primary blue
  if (module.includes('导出')) return '#f59e0b' // amber-500
  return '#10b981' // emerald-500
}

const statusColorClass = computed(() => {
  const status = statsData.value.systemStatus;
  if (status === '正常运行') return 'status-green';
  if (status === '磁盘告警') return 'status-orange';
  return 'status-red';
})

onMounted(() => {
  fetchStats()
})
</script>

<style scoped>
.dashboard-container {
  min-height: 100%;
}

/* Utilities (Tailwind like) */
.mb-6 { margin-bottom: 24px; }
.mb-4 { margin-bottom: 16px; }
.mt-4 { margin-top: 16px; }
.mt-8 { margin-top: 32px; }
.pt-4 { padding-top: 16px; }
.mr-1 { margin-right: 4px; }
.mr-2 { margin-right: 8px; }
.text-secondary { color: var(--el-text-color-secondary); }
.flex-between { display: flex; justify-content: space-between; align-items: center; }

h2, h4, p { margin: 0; }

/* 欢迎横幅 现代 SaaS 风格 */
.welcome-banner {
  position: relative;
  height: 140px;
  border-radius: var(--radius-xl, 12px);
  overflow: hidden;
  display: flex;
  align-items: center;
  padding-left: 40px;
  box-shadow: 0 4px 20px rgba(30, 58, 138, 0.08);
  background: linear-gradient(135deg, var(--el-color-primary) 0%, #294c7b 100%);
  color: white;
}
.banner-decor-1 {
  position: absolute;
  width: 300px; height: 300px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255,255,255,0.1) 0%, rgba(255,255,255,0) 70%);
  right: -50px; top: -100px;
}
.banner-decor-2 {
  position: absolute;
  width: 200px; height: 200px;
  border: 2px dashed rgba(255,255,255,0.15);
  border-radius: 50%;
  right: 150px; bottom: -80px;
}
.banner-content {
  position: relative; z-index: 5;
}
.banner-content h2 {
  font-size: 24px;
  font-weight: 600;
  letter-spacing: 1px;
  margin-bottom: 8px;
}
.banner-content p {
  font-size: 14px;
  opacity: 0.85;
  font-weight: 300;
}

/* 核心数据卡片 */
.stat-card {
  border-radius: var(--radius-lg, 10px);
  border-left-width: 4px;
  border-left-style: solid;
  border-top: none; border-right: none; border-bottom: none;
}
.stat-inner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px;
}
.stat-title {
  font-size: 13px;
  margin-bottom: 8px;
  font-weight: 500;
}
.stat-value {
  display: flex;
  align-items: baseline;
  gap: 4px;
}
.stat-unit {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.stat-icon-wrapper {
  width: 52px; height: 52px;
  border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
}
.inline-status {
  height: 38px; /* to align with exact numbers */
  align-items: center;
}
.status-text {
  font-size: 18px; font-weight: 600; color: var(--el-text-color-primary);
}
.status-dot {
  width: 8px; height: 8px; border-radius: 50%;
  margin-right: 6px;
}
.status-green { background-color: var(--accent-green); box-shadow: 0 0 8px var(--accent-green); }
.status-orange { background-color: var(--accent-orange); box-shadow: 0 0 8px var(--accent-orange); }
.status-red { background-color: #ef4444; box-shadow: 0 0 8px #ef4444; }

/* 内容容器 */
.content-row { display: flex; align-items: stretch; }
.sys-summary-card {
  height: 100%; border-radius: var(--radius-lg, 10px);
}
.section-title {
  display: flex; align-items: center; gap: 10px;
  font-size: 16px; font-weight: 600;
  color: var(--el-text-color-primary);
  border-bottom: 1px solid var(--el-border-color-light);
  padding-bottom: 16px; margin-bottom: 8px;
}
.icon-block {
  width: 28px; height: 28px;
  background: var(--bg-gradient-blue);
  color: var(--accent-blue);
  border-radius: 6px;
  display: flex; align-items: center; justify-content: center;
}

/* 警报与列表重构 */
.elegant-alert {
  border: 1px solid var(--el-color-success-light-5);
  background-color: var(--bg-gradient-green);
}
.intro-text {
  font-size: 14px; color: var(--el-text-color-regular); line-height: 1.6;
}
.feature-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}
.feature-item {
  display: flex; align-items: flex-start; gap: 12px;
  padding: 16px;
  background: var(--el-bg-color-page);
  border-radius: 8px;
  transition: all 0.2s ease;
}
.feature-item:hover {
  background: white;
  box-shadow: var(--shadow-sm);
}
.f-icon {
  margin-top: 2px;
  color: var(--el-color-primary);
  font-size: 20px;
}
.f-desc { font-size: 13px; line-height: 1.5; color: var(--el-text-color-regular); }
.f-desc strong { font-size: 14px; color: var(--el-text-color-primary); display: inline-block; margin-bottom: 4px; }

/* 侧边操作 */
.block-btn { width: 100%; margin-left: 0 !important; margin-bottom: 12px; justify-content: center; }
.ghost-btn {
  background: var(--el-bg-color-page); border-color: transparent; color: var(--el-text-color-regular);
}
.ghost-btn:hover { background: #e2e8f0; color: var(--el-text-color-primary); }

.sub-title { font-size: 14px; font-weight: 600; color: var(--el-text-color-regular); }
.refresh-btn { font-size: 13px; }

.elegant-timeline .el-timeline-item__content {
  margin-top: -2px; /* Slight optical adjustment */
}
.log-module { font-size: 13px; font-weight: 500; color: var(--el-text-color-primary); margin-bottom: 2px; }
.log-desc { font-size: 12px; color: var(--el-text-color-secondary); }
</style>
