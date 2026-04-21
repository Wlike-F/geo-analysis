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
        <el-card shadow="hover" class="dashboard-card info-card">
          <div class="section-title">
            <div class="icon-block"><el-icon><Operation /></el-icon></div>
            <span>系统概览与核心能力</span>
          </div>

          <div class="section-body">
            <el-alert
              v-if="userStore.isAdmin"
              title="管理员专属工作台"
              type="success"
              description="您已登录最高权限环境，当前所有核心分析节点准备就绪。"
              show-icon
              :closable="false"
              class="mb-6 elegant-alert"
            />

            <p class="intro-text">
              本系统为您提供专业的地质测井数据解析与归档能力，支持从文件加载、筛选提取到图表分析和结果导出的完整工作流。
            </p>

            <div class="feature-grid mt-4">
              <div class="feature-item">
                <div class="f-icon"><el-icon><CopyDocument /></el-icon></div>
                <div class="f-desc">
                  <strong>多通道极速加载</strong>
                  <span>支持大批量 TXT 文件探测、解析与基础预览。</span>
                </div>
              </div>
              <div class="feature-item">
                <div class="f-icon"><el-icon><Reading /></el-icon></div>
                <div class="f-desc">
                  <strong>并发标签页比对</strong>
                  <span>支持多文件并发切换，在同一视图中横向对比关键井曲线属性。</span>
                </div>
              </div>
              <div class="feature-item">
                <div class="f-icon"><el-icon><Filter /></el-icon></div>
                <div class="f-desc">
                  <strong>精细门限矩阵</strong>
                  <span>支持多字段阈值过滤、区间筛选与连续异常测段提取。</span>
                </div>
              </div>
              <div class="feature-item">
                <div class="f-icon"><el-icon><HelpFilled /></el-icon></div>
                <div class="f-desc">
                  <strong>一键导出归档</strong>
                  <span>支持 Excel、CSV 与批量压缩包导出，便于复核与归档。</span>
                </div>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="9">
        <el-card shadow="hover" class="dashboard-card side-card">
          <div class="section-title">
            <div class="icon-block"><el-icon><Lightning /></el-icon></div>
            <span>快捷操作向导</span>
          </div>

          <div class="section-body">
            <div class="quick-action-area">
              <el-button type="primary" size="large" class="quick-btn block-btn" @click="$router.push('/files')">
                <el-icon class="mr-2"><Guide /></el-icon>
                前往业务控制台
              </el-button>
              <el-button size="large" class="quick-btn block-btn ghost-btn" @click="$router.push('/settings')">
                <el-icon class="mr-2"><Setting /></el-icon>
                打开偏好参数配置
              </el-button>
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
import { computed, onMounted, ref } from 'vue'
import { useUserStore } from '@/store/user'
import { getDashboardStats } from '@/api/dashboard'
import {
  CopyDocument,
  DataLine,
  Document,
  Download,
  Filter,
  Guide,
  HelpFilled,
  Lightning,
  Monitor,
  Operation,
  Reading,
  RefreshRight,
  Setting
} from '@element-plus/icons-vue'

const userStore = useUserStore()
const loading = ref(true)
const recentLogs = ref([])
const statsData = ref({
  totalFiles: 0,
  totalLines: 0,
  totalExports: 0,
  systemStatus: '正在加载...'
})

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
      recentLogs.value = resData.recentLogs || []
    }
  } catch (error) {
    console.error('获取仪表盘统计数据失败', error)
  } finally {
    loading.value = false
  }
}

const getLogColor = (module) => {
  if (module.includes('异常') || module.includes('失败')) return '#ef4444'
  if (module.includes('上传') || module.includes('加载') || module.includes('扫描')) return '#3b82f6'
  if (module.includes('导出')) return '#f59e0b'
  return '#10b981'
}

const statusColorClass = computed(() => {
  const status = statsData.value.systemStatus
  if (status === '正常运行') return 'status-green'
  if (status === '磁盘告警') return 'status-orange'
  return 'status-red'
})

onMounted(() => {
  fetchStats()
})
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
  gap: 10px;
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

.icon-block {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-gradient-blue);
  color: var(--accent-blue);
}

.elegant-alert {
  border: 1px solid var(--el-color-success-light-5);
  background-color: #f0fdf4;
}

.intro-text {
  font-size: 14px;
  line-height: 1.7;
  color: var(--el-text-color-regular);
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}

.feature-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  border-radius: 8px;
  background: var(--el-bg-color-page);
  transition: all 0.2s ease;
}

.feature-item:hover {
  background: #fff;
  box-shadow: var(--shadow-sm);
}

.f-icon {
  margin-top: 2px;
  font-size: 20px;
  color: var(--el-color-primary);
}

.f-desc {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
}

.f-desc strong {
  font-size: 14px;
  color: var(--el-text-color-primary);
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

  .feature-grid {
    grid-template-columns: 1fr;
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
