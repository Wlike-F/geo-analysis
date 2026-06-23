<template>
  <div class="dashboard-container">
    <section class="welcome-banner mb-6">
      <div class="banner-decor banner-decor--lg"></div>
      <div class="banner-decor banner-decor--sm"></div>
      <div class="banner-content">
        <p class="banner-kicker">Well Log Analytics Workspace</p>
        <h2>欢迎使用地质测井数据分析系统</h2>
        <p>提供专业、高效、高精度的多通道测井序列提取、筛选分析与结果导出能力。</p>
      </div>
    </section>

    <el-row :gutter="24" v-loading="loading" class="mb-6 stats-row">
      <el-col :xs="24" :sm="6">
        <el-card shadow="hover" class="stat-card stat-card--blue">
          <div class="stat-inner">
            <div class="stat-info">
              <div class="stat-title">已加载测井文件</div>
              <div class="stat-value">
                <el-statistic :value="statsData.totalFiles" class="statistic-number" />
                <span class="stat-unit">次</span>
              </div>
            </div>
            <div class="stat-icon-wrapper stat-icon-wrapper--blue">
              <el-icon :size="24"><Document /></el-icon>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="6">
        <el-card shadow="hover" class="stat-card stat-card--green">
          <div class="stat-inner">
            <div class="stat-info">
              <div class="stat-title">累计解析测井长</div>
              <div class="stat-value">
                <el-statistic :value="statsData.totalLines" class="statistic-number" />
                <span class="stat-unit">行</span>
              </div>
            </div>
            <div class="stat-icon-wrapper stat-icon-wrapper--green">
              <el-icon :size="24"><DataLine /></el-icon>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="6">
        <el-card shadow="hover" class="stat-card stat-card--orange">
          <div class="stat-inner">
            <div class="stat-info">
              <div class="stat-title">导出 Excel 报表</div>
              <div class="stat-value">
                <el-statistic :value="statsData.totalExports" class="statistic-number" />
                <span class="stat-unit">次</span>
              </div>
            </div>
            <div class="stat-icon-wrapper stat-icon-wrapper--orange">
              <el-icon :size="24"><Download /></el-icon>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="6">
        <el-card shadow="hover" class="stat-card stat-card--purple">
          <div class="stat-inner">
            <div class="stat-info">
              <div class="stat-title">系统当前状态</div>
              <div class="stat-status">
                <span class="status-dot" :class="statusColorClass"></span>
                <span class="status-text">{{ statsData.systemStatus }}</span>
              </div>
            </div>
            <div class="stat-icon-wrapper stat-icon-wrapper--purple">
              <el-icon :size="24"><Monitor /></el-icon>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="24" class="content-row">
      <el-col :xs="24" :lg="15">
        <el-card shadow="hover" class="dashboard-card workbench-card">
          <div class="section-title">
            <span>我的工作台</span>
            <el-button link type="primary" @click="$router.push('/datasource')">查看全部 →</el-button>
          </div>

          <div class="section-body" v-loading="filesLoading">
            <el-empty v-if="recentFiles.length === 0 && !filesLoading" description="还没有上传文件，去数据源管理上传吧" :image-size="60" />

            <div v-else class="file-list">
              <div
                v-for="file in sortedFiles"
                :key="file.id"
                class="file-row"
                :class="{ 'file-row--pending': file.status === 0 || file.status === -1 }"
              >
                <div class="file-main">
                  <span class="file-name">{{ file.fileName }}</span>
                  <div class="file-meta">
                    <el-tag
                      v-if="file.status === 0"
                      size="small" type="warning" effect="plain"
                    >解析中</el-tag>
                    <el-tag
                      v-else-if="file.status === -1"
                      size="small" type="danger" effect="plain"
                    >解析失败</el-tag>
                    <span v-else class="file-rows">{{ (file.totalRows || 0).toLocaleString() }} 行</span>
                    <span class="file-time">{{ formatRelativeTime(file.createTime) }}</span>
                  </div>
                </div>
                <div class="file-actions" v-if="file.status === 1">
                  <el-button link type="primary" size="small" @click="$router.push({ path: '/extract', query: { fileId: file.id } })">筛选</el-button>
                  <el-button link type="primary" size="small" @click="$router.push({ path: '/visualization', query: { fileId: file.id } })">可视化</el-button>
                </div>
              </div>
            </div>

            <div v-if="pendingHint" class="pending-hint">
              <el-icon><WarningFilled /></el-icon>
              <span>{{ pendingHint }}</span>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="9">
        <el-card shadow="hover" class="dashboard-card side-card">
          <div class="section-title">
            <span>快捷操作</span>
          </div>

          <div class="section-body">
            <div class="shortcut-grid">
              <div class="shortcut-item" @click="$router.push('/datasource')">
                <strong>数据源管理</strong>
                <span>上传与扫描文件</span>
              </div>
              <div class="shortcut-item" @click="$router.push('/settings')">
                <strong>系统设置</strong>
                <span>账号与运行日志</span>
              </div>
              <div class="shortcut-item" @click="$router.push('/extract')">
                <strong>异常测段提取</strong>
                <span>条件筛选与导出</span>
              </div>
              <div class="shortcut-item" @click="$router.push('/visualization')">
                <strong>曲线图表</strong>
                <span>多通道可视化分析</span>
              </div>
            </div>

            <div class="recent-act mt-8">
              <div class="flex-between mb-4 recent-header">
                <h4 class="sub-title">最新活跃记录</h4>
                <el-button link type="primary" @click="fetchStats" class="refresh-btn">
                  <el-icon class="mr-1"><RefreshRight /></el-icon>
                  刷新
                </el-button>
              </div>

              <el-empty v-if="recentLogs.length === 0" description="暂无活跃记录" :image-size="60" />
              <el-timeline v-else class="elegant-timeline">
                <el-timeline-item
                  v-for="(log, idx) in recentLogs"
                  :key="idx"
                  :timestamp="log.createTime"
                  placement="top"
                  :color="getLogColor(log.module)"
                  :hollow="true"
                >
                  <div class="log-module">{{ log.module }}</div>
                  <div class="log-desc">{{ log.description }}</div>
                </el-timeline-item>
              </el-timeline>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, onActivated, ref } from 'vue'
import { useUserStore } from '@/store/user'
import { getDashboardStats, getRecentFiles } from '@/api/dashboard'
import {
  DataLine,
  Document,
  Download,
  Monitor,
  RefreshRight,
  WarningFilled
} from '@element-plus/icons-vue'

const userStore = useUserStore()
const loading = ref(true)
const recentLogs = ref([])
const recentFiles = ref([])
const filesLoading = ref(true)
const statsData = ref({
  totalFiles: 0,
  totalLines: 0,
  totalExports: 0,
  systemStatus: '正在加载...'
})

// 文件排序：解析中/失败排前面，成功排后面
const sortedFiles = computed(() => {
  return [...recentFiles.value].sort((a, b) => {
    const priority = { 0: 0, [-1]: 1 } // 0=解析中最前, -1=失败次之
    const pa = priority[a.status] ?? 2
    const pb = priority[b.status] ?? 2
    if (pa !== pb) return pa - pb
    return new Date(b.createTime) - new Date(a.createTime)
  })
})

// 待处理提示
const pendingHint = computed(() => {
  const parsing = recentFiles.value.filter(f => f.status === 0).length
  const failed = recentFiles.value.filter(f => f.status === -1).length
  if (failed > 0) return `有 ${failed} 个文件解析失败，建议检查文件格式后重新上传`
  if (parsing > 0) return `有 ${parsing} 个文件正在解析中，请稍后查看`
  return ''
})

const formatRelativeTime = (timeStr) => {
  if (!timeStr) return ''
  if (typeof timeStr === 'number') timeStr = new Date(timeStr).toISOString()
  const now = new Date()
  const time = new Date(timeStr)
  if (isNaN(time.getTime())) return ''
  const diffMs = now - time
  if (diffMs < 0) return ''
  const diffMin = Math.floor(diffMs / 60000)
  if (diffMin < 1) return '刚刚'
  if (diffMin < 60) return `${diffMin} 分钟前`
  const diffHour = Math.floor(diffMin / 60)
  if (diffHour < 24) return `${diffHour} 小时前`
  const diffDay = Math.floor(diffHour / 24)
  if (diffDay < 30) return `${diffDay} 天前`
  if (typeof timeStr === 'string') return timeStr.slice(0, 10)
  return ''
}

const fetchStats = async () => {
  try {
    loading.value = true
    const resData = await getDashboardStats()
    if (resData?.stats) {
      statsData.value = {
        totalFiles: resData.stats.totalFiles || 0,
        totalLines: resData.stats.totalLines || 0,
        totalExports: resData.stats.totalExports || 0,
        systemStatus: resData.stats.systemStatus || '未知状态'
      }
    }
    recentLogs.value = resData?.recentLogs || []
  } catch (error) {
    console.error('获取仪表盘统计数据失败', error)
    statsData.value.systemStatus = '加载失败'
    ElMessage.error('统计数据加载失败')
  } finally {
    loading.value = false
  }
}

const getLogColor = (module) => {
  if (!module || typeof module !== 'string') return '#10b981'
  if (module.includes('异常') || module.includes('失败')) return '#ef4444'
  if (module.includes('上传') || module.includes('加载') || module.includes('扫描')) return '#3b82f6'
  if (module.includes('导出')) return '#f59e0b'
  return '#10b981'
}

const statusColorClass = computed(() => {
  const status = statsData.value.systemStatus
  if (status === '正常运行' || status === '正在加载...' || status === '加载失败') return 'status-green'
  if (status === '磁盘告警') return 'status-orange'
  return 'status-green'
})

onMounted(() => {
  fetchStats()
  fetchFiles()
  lastFetchTime.value = Date.now()
})

// 从其他页面切回时自动刷新（keep-alive 触发），30 秒内不重复请求
let lastFetchTime = ref(0)
onActivated(() => {
  if (Date.now() - lastFetchTime.value < 30000) return
  lastFetchTime.value = Date.now()
  fetchStats()
  fetchFiles()
})

const fetchFiles = async () => {
  try {
    filesLoading.value = true
    const data = await getRecentFiles()
    let list = []
    if (Array.isArray(data)) {
      list = data
    } else if (data?.records) {
      list = data.records
    }
    recentFiles.value = list.map(f => ({ ...f, status: Number(f.status) ?? 0 }))
  } catch (error) {
    console.error('获取最近文件失败', error)
    ElMessage.error('文件列表加载失败')
  } finally {
    filesLoading.value = false
  }
}
</script>

<style scoped>
.dashboard-container {
  min-height: 100%;
}

.mb-6 {
  margin-bottom: 24px;
}

.mb-4 {
  margin-bottom: 16px;
}

.mt-4 {
  margin-top: 16px;
}

.mt-8 {
  margin-top: 32px;
}

.mr-1 {
  margin-right: 4px;
}

.mr-2 {
  margin-right: 8px;
}

.flex-between {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

h2,
h4,
p {
  margin: 0;
}

.welcome-banner {
  position: relative;
  overflow: hidden;
  min-height: 160px;
  padding: 28px 32px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  color: #fff;
  background: linear-gradient(135deg, var(--el-color-primary) 0%, #294c7b 100%);
  box-shadow: 0 4px 20px rgba(30, 58, 138, 0.08);
}

.banner-decor {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}

.banner-decor--lg {
  width: 300px;
  height: 300px;
  right: -50px;
  top: -120px;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.12) 0%, rgba(255, 255, 255, 0) 70%);
}

.banner-decor--sm {
  width: 200px;
  height: 200px;
  right: 140px;
  bottom: -80px;
  border: 2px dashed rgba(255, 255, 255, 0.15);
}

.banner-content {
  position: relative;
  z-index: 1;
  max-width: 720px;
}

.banner-kicker {
  margin-bottom: 8px;
  font-size: 12px;
  letter-spacing: 1.6px;
  text-transform: uppercase;
  opacity: 0.85;
}

.banner-content h2 {
  margin-bottom: 10px;
  font-size: 26px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.banner-content p {
  font-size: 14px;
  line-height: 1.7;
  opacity: 0.88;
}

.stats-row {
  row-gap: 24px;
}

.stat-card {
  border-radius: 10px;
  border-left-width: 4px;
  border-left-style: solid;
  border-top: none;
  border-right: none;
  border-bottom: none;
}

.stat-card--blue {
  border-left-color: var(--accent-blue);
}

.stat-card--green {
  border-left-color: var(--accent-green);
}

.stat-card--orange {
  border-left-color: var(--accent-orange);
}

.stat-card--purple {
  border-left-color: var(--accent-purple);
}

.stat-inner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 4px;
}

.stat-title {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-secondary);
}

.stat-value {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.statistic-number :deep(.el-statistic__content) {
  font-family: var(--font-mono);
  font-weight: 700;
  font-size: 28px;
  color: var(--el-text-color-primary);
}

.stat-unit {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.stat-icon-wrapper {
  width: 52px;
  height: 52px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.stat-icon-wrapper--blue {
  background: var(--bg-gradient-blue);
  color: var(--accent-blue);
}

.stat-icon-wrapper--green {
  background: var(--bg-gradient-green);
  color: var(--accent-green);
}

.stat-icon-wrapper--orange {
  background: var(--bg-gradient-orange);
  color: var(--accent-orange);
}

.stat-icon-wrapper--purple {
  background: var(--bg-gradient-purple);
  color: var(--accent-purple);
}

.stat-status {
  display: flex;
  align-items: center;
  min-height: 38px;
}

.status-text {
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
}

.status-green {
  background-color: var(--accent-green);
  box-shadow: 0 0 8px var(--accent-green);
}

.status-orange {
  background-color: var(--accent-orange);
  box-shadow: 0 0 8px var(--accent-orange);
}

.status-red {
  background-color: #ef4444;
  box-shadow: 0 0 8px #ef4444;
}

.content-row {
  row-gap: 24px;
}

.dashboard-card {
  height: 100%;
  border-radius: 10px;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--el-border-color-light);
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.section-body {
  padding-top: 8px;
}

/* --- 工作台文件列表 --- */
.file-list {
  display: flex;
  flex-direction: column;
}

.file-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}

.file-row:last-child {
  border-bottom: none;
}

.file-row--pending {
  background: var(--el-fill-color-lighter);
  margin: 0 -12px;
  padding: 12px;
  border-radius: 6px;
  border-bottom: none;
}

.file-row--pending + .file-row--pending {
  margin-top: 4px;
}

.file-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.file-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.file-rows {
  font-family: var(--font-mono);
}

.file-actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

.pending-hint {
  margin-top: 16px;
  padding: 10px 14px;
  border-radius: 6px;
  background: var(--el-color-warning-light-9);
  color: var(--el-color-warning);
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 6px;
}

/* --- 快捷操作网格 --- */
.shortcut-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.shortcut-item {
  padding: 14px;
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.shortcut-item:hover {
  background: var(--el-color-primary-light-9);
}

.shortcut-item strong {
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.shortcut-item span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.quick-action-area {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.block-btn {
  width: 100%;
  margin-left: 0 !important;
  justify-content: center;
}

.ghost-btn {
  background: var(--el-bg-color-page);
  border-color: transparent;
  color: var(--el-text-color-regular);
}

.ghost-btn:hover {
  background: #e2e8f0;
  color: var(--el-text-color-primary);
}

.recent-header {
  gap: 12px;
}

.sub-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-regular);
}

.refresh-btn {
  font-size: 13px;
}

.elegant-timeline :deep(.el-timeline-item__content) {
  margin-top: -2px;
}

.log-module {
  margin-bottom: 2px;
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.log-desc {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

@media (max-width: 992px) {
  .welcome-banner {
    min-height: 180px;
    padding: 24px;
  }

  .banner-content h2 {
    font-size: 22px;
  }

  .shortcut-grid {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 768px) {
  .welcome-banner {
    padding: 22px 20px;
  }

  .banner-content h2 {
    font-size: 20px;
  }

  .banner-decor--sm {
    display: none;
  }

  .flex-between {
    flex-direction: column;
    align-items: flex-start;
  }

  .stat-inner {
    align-items: flex-start;
  }
}
</style>
