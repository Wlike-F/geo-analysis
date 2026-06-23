<template>
  <div class="instructions-page">
    <section class="hero-section">
      <div class="hero-title-wrap">
        <h1 class="hero-title">系统使用说明</h1>
        <p class="hero-subtitle">覆盖上传、筛选、导出、异常处理全流程</p>
      </div>

      <el-input
        v-model="searchKeyword"
        class="hero-search"
        placeholder="搜索标题或内容关键字"
        clearable
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>

      <div class="quick-entries">
        <el-button
          v-for="entry in quickEntries"
          :key="entry.key"
          plain
          @click="jumpToCategory(entry)"
        >
          {{ entry.label }}
        </el-button>
      </div>
    </section>

    <section class="content-layout">
      <aside class="sidebar desktop-only">
        <el-affix :offset="88">
          <div class="sidebar-card">
            <div class="sidebar-title">目录导航</div>

            <div v-for="group in groupedInstructions" :key="group.key" class="menu-group">
              <div class="menu-group-title">{{ group.label }}</div>
              <button
                v-for="item in group.items"
                :key="item.anchorId"
                type="button"
                class="menu-item"
                :class="{ active: activeAnchor === item.anchorId }"
                @click="scrollToItem(item.anchorId, false)"
              >
                <span class="menu-dot" />
                <span class="menu-label">{{ item.title }}</span>
              </button>
            </div>
          </div>
        </el-affix>
      </aside>

      <section class="main-content">
        <div class="mobile-toolbar">
          <el-button @click="drawerVisible = true">
            <el-icon><Menu /></el-icon>
            目录
          </el-button>
        </div>

        <el-skeleton v-if="loading" :rows="8" animated />

        <div v-else class="instructions-content">
          <el-empty v-if="filteredInstructions.length === 0" description="未找到匹配说明" />

          <el-card
            v-for="item in filteredInstructions"
            :id="item.anchorId"
            :key="item.anchorId"
            class="instruction-card"
            shadow="hover"
          >
            <template #header>
              <div class="card-header">
                <div class="card-title-wrap">
                  <el-icon class="card-title-icon">
                    <component :is="iconMap[item.icon] || Reading" />
                  </el-icon>
                  <span class="card-title">{{ item.title }}</span>
                  <el-tag size="small" type="info">{{ categoryLabels[item.category] }}</el-tag>
                </div>
              </div>
            </template>

            <p class="card-summary">{{ item.summary }}</p>

            <div class="steps-block">
              <h4>操作步骤</h4>
              <ol class="steps-list">
                <li v-for="(step, stepIndex) in item.steps" :key="`${item.anchorId}-step-${stepIndex}`">
                  <span class="step-index">{{ stepIndex + 1 }}</span>
                  <span class="step-text">{{ step }}</span>
                </li>
              </ol>
            </div>

            <el-alert
              v-if="item.note"
              class="note-alert"
              type="warning"
              :closable="false"
              :title="item.note"
              show-icon
            />

            <div class="actions-block">
              <el-button class="copy-btn" size="small" type="primary" @click="copyGuide(item)">
                <el-icon><DocumentCopy /></el-icon>
                复制操作指引
              </el-button>

              <div v-if="item.links.length" class="jump-links">
                <button
                  v-for="link in item.links"
                  :key="`${item.anchorId}-${link.path}`"
                  type="button"
                  class="jump-link"
                  @click="goTo(link.path)"
                >
                  <span>{{ link.label }}</span>
                  <el-icon><ArrowRight /></el-icon>
                </button>
              </div>
            </div>
          </el-card>
        </div>
      </section>
    </section>

    <el-drawer v-model="drawerVisible" title="目录导航" direction="ltr" size="280px">
      <div class="drawer-menu">
        <div v-for="group in groupedInstructions" :key="group.key" class="menu-group">
          <div class="menu-group-title">{{ group.label }}</div>
          <button
            v-for="item in group.items"
            :key="`drawer-${item.anchorId}`"
            type="button"
            class="menu-item"
            :class="{ active: activeAnchor === item.anchorId }"
            @click="scrollToItem(item.anchorId, true)"
          >
            <span class="menu-dot" />
            <span class="menu-label">{{ item.title }}</span>
          </button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, Reading, PriceTag, Lock, Bell, DocumentCopy, Menu, ArrowRight } from '@element-plus/icons-vue'
import { getInstructionList } from '@/api/instruction'

const router = useRouter()

const STORAGE_KEYS = {
  active: 'instructions-active-anchor-v1'
}

const loading = ref(true)
const drawerVisible = ref(false)
const searchKeyword = ref('')
const activeAnchor = ref(readStoredActiveAnchor())
const instructionsList = ref([])

const quickEntries = [
  { key: 'quickstart', label: '新手入门', category: 'quickstart' },
  { key: 'feature-upload', label: '数据上传', category: 'feature', keyword: '上传' },
  { key: 'feature-layer', label: '分层配置', category: 'feature', keyword: '分层' },
  { key: 'feature-export', label: '导出报表', category: 'feature', keyword: '导出' },
  { key: 'faq', label: '常见报错', category: 'faq' }
]

const categoryLabels = {
  quickstart: '快速开始',
  feature: '核心功能',
  faq: '常见问题',
  changelog: '版本更新'
}

const iconMap = {
  Reading,
  PriceTag,
  Lock,
  Bell,
  DocumentCopy
}

const SKELETON_DATA = [
  {
    id: 'skel-1',
    title: '系统使用指引',
    icon: 'Reading',
    category: 'quickstart',
    summary: '本系统提供测井文件上传、解析、筛选、导出、异常分析和可视化功能。',
    steps: [
      '进入”数据源管理”上传测井文件（支持 TXT、CSV、Excel）。',
      '文件解析完成后进入”异常测段提取”进行数据筛选与导出。',
      '进入”异常测段可视化”查看多道曲线与异常段高亮标注。'
    ],
    links: [{ label: '去首页概览', path: '/dashboard' }]
  },
  {
    id: 'skel-2',
    title: '常用功能入口',
    icon: 'PriceTag',
    category: 'feature',
    summary: '数据源管理、异常测段提取、异常测段可视化是三个核心功能页面。',
    steps: [
      '数据源管理：上传文件、管理解析状态、配置地质分层。',
      '异常测段提取：多文件筛选、数值范围过滤、文本列多选、预设模板一键加载。',
      '异常测段可视化：多通道曲线图、异常测段高亮、CSV/PNG 导出。'
    ],
    links: [{ label: '去数据源管理', path: '/datasource' }, { label: '去异常测段提取', path: '/extract' }]
  },
  {
    id: 'skel-3',
    title: '遇到问题？',
    icon: 'Bell',
    category: 'faq',
    summary: '如遇文件解析失败、导出异常或页面报错，请按以下方式排查。',
    steps: [
      '检查文件格式和编码（TXT 默认 GBK，CSV 自动检测）。',
      '查看”系统设置 → 运行日志”获取后端错误详情。',
      '保留浏览器控制台错误信息并联系管理员。'
    ],
    links: [{ label: '去系统设置', path: '/settings' }]
  }
]

const normalizedInstructions = computed(() => {
  return instructionsList.value.map((item, index) => normalizeInstruction(item, index))
})

const filteredInstructions = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  if (!keyword) {
    return normalizedInstructions.value
  }
  return normalizedInstructions.value.filter(item => item.searchText.includes(keyword))
})

const groupedInstructions = computed(() => {
  const order = ['quickstart', 'feature', 'faq', 'changelog']
  return order
    .map(key => ({
      key,
      label: categoryLabels[key],
      items: filteredInstructions.value.filter(item => item.category === key)
    }))
    .filter(group => group.items.length > 0)
})

function readStoredActiveAnchor() {
  try {
    return localStorage.getItem(STORAGE_KEYS.active) || ''
  } catch (error) {
    return ''
  }
}

function saveStoredActiveAnchor(anchorId) {
  try {
    localStorage.setItem(STORAGE_KEYS.active, anchorId || '')
  } catch (error) {
    // ignore
  }
}

const fetchInstructions = async () => {
  try {
    const data = await getInstructionList()
    if (Array.isArray(data) && data.length > 0) {
      instructionsList.value = data
    } else {
      instructionsList.value = SKELETON_DATA
    }
  } catch (error) {
    instructionsList.value = SKELETON_DATA
    ElMessage.warning('说明数据加载失败，已展示基础引导内容')
  } finally {
    loading.value = false
  }
}

function normalizeInstruction(item, index) {
  const plainText = htmlToText(item.content || item.summary || '')
  const title = (item.title || `说明 ${index + 1}`).trim()
  const steps = normalizeSteps(item.steps, plainText, title)
  const summary = (item.summary || buildSummary(plainText, title)).trim()
  const note = (item.note || extractNote(plainText)).trim()
  const category = normalizeCategory(item.category, title, plainText)
  const links = normalizeLinks(item.links, category, `${title} ${plainText}`)

  return {
    id: item.id ?? `local-${index + 1}`,
    icon: item.icon,
    title,
    summary,
    steps,
    note,
    category,
    links,
    anchorId: `instruction-${item.id ?? index}`,
    searchText: `${title} ${summary} ${steps.join(' ')} ${note} ${plainText}`.toLowerCase()
  }
}

function htmlToText(html) {
  return String(html || '')
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<\/p>/gi, '\n')
    .replace(/<\/li>/gi, '\n')
    .replace(/<li[^>]*>/gi, '- ')
    .replace(/<[^>]+>/g, '')
    .replace(/&nbsp;/gi, ' ')
    .replace(/&lt;/gi, '<')
    .replace(/&gt;/gi, '>')
    .replace(/&amp;/gi, '&')
    .replace(/\n{2,}/g, '\n')
    .replace(/\s{2,}/g, ' ')
    .trim()
}

function normalizeSteps(steps, plainText, title) {
  if (Array.isArray(steps) && steps.length) {
    return steps.map(text => String(text).trim()).filter(Boolean).slice(0, 6)
  }

  const lineSteps = plainText
    .split(/\n+/)
    .map(line => line.replace(/^\s*\d+[\.、)]\s*/, '').trim())
    .filter(Boolean)

  if (lineSteps.length >= 3) {
    return lineSteps.slice(0, 6)
  }

  const sentenceSteps = plainText
    .split(/[。！？；]/)
    .map(item => item.trim())
    .filter(Boolean)

  if (sentenceSteps.length >= 3) {
    return sentenceSteps.slice(0, 6)
  }

  return buildDefaultSteps(title)
}

function buildDefaultSteps(title) {
  if (/上传|数据源/.test(title)) {
    return [
      '进入数据源管理并选择目标数据源。',
      '上传文件后确认字段映射配置。',
      '解析完成后在看板核对结果。'
    ]
  }

  if (/导出|报表/.test(title)) {
    return [
      '先在看板完成筛选并确认数据范围。',
      '选择导出格式（Excel 或 CSV）。',
      '下载后抽样检查关键字段。'
    ]
  }

  if (/异常|报错|问题/.test(title)) {
    return [
      '记录报错提示并确认复现步骤。',
      '检查文件格式、字段映射和筛选条件。',
      '必要时拆分任务或联系管理员。'
    ]
  }

  return [
    '进入对应功能页面。',
    '按页面指引逐步完成操作。',
    '完成后核对结果是否符合预期。'
  ]
}

function buildSummary(plainText, title) {
  if (!plainText) {
    return `${title}的操作说明。`
  }
  return plainText.length > 72 ? `${plainText.slice(0, 72)}...` : plainText
}

function extractNote(plainText) {
  const match = plainText.match(/(?:注意|提示|风险|异常)[:：]?\s*([^\n。！？]{4,80})/)
  if (match) {
    return `注意：${match[1].trim()}`
  }
  return ''
}

function normalizeCategory(category, title, plainText) {
  if (category && categoryLabels[category]) {
    return category
  }

  const source = `${title} ${plainText}`
  if (/入门|快速开始|首次|概览/.test(source)) {
    return 'quickstart'
  }
  if (/问题|报错|失败|异常|FAQ/.test(source)) {
    return 'faq'
  }
  if (/版本|更新|变更/.test(source)) {
    return 'changelog'
  }
  return 'feature'
}

function normalizeLinks(links, category, sourceText) {
  if (Array.isArray(links) && links.length) {
    return links
      .map(link => ({ label: link.label, path: link.path }))
      .filter(link => link.label && link.path)
  }

  const candidateLinks = []

  if (/上传|数据源/.test(sourceText)) {
    candidateLinks.push({ label: '去数据源管理', path: '/datasource' })
  }
  if (/导出|看板|测井|报表|筛选/.test(sourceText)) {
    candidateLinks.push({ label: '去异常测段提取', path: '/extract' })
  }
  if (/异常.*可视化|可视化.*异常|图表|渲染/.test(sourceText)) {
    candidateLinks.push({ label: '去异常测段可视化', path: '/visualization' })
  } else if (/异常|提取/.test(sourceText)) {
    candidateLinks.push({ label: '去异常测段提取', path: '/extract' })
  }
  if (/字典|映射/.test(sourceText)) {
    candidateLinks.push({ label: '去字典映射参数', path: '/dictionary' })
  }

  if (!candidateLinks.length && category === 'quickstart') {
    candidateLinks.push({ label: '去首页概览', path: '/dashboard' })
  }

  return candidateLinks.filter((item, index, array) => array.findIndex(link => link.path === item.path) === index)
}

function scrollToItem(anchorId, closeDrawer = false) {
  activeAnchor.value = anchorId
  saveStoredActiveAnchor(anchorId)

  nextTick(() => {
    const node = document.getElementById(anchorId)
    if (node) {
      node.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }
  })

  if (closeDrawer) {
    drawerVisible.value = false
  }
}

function jumpToCategory(entry) {
  searchKeyword.value = ''

  nextTick(() => {
    const firstItem = normalizedInstructions.value.find(item => {
      if (item.category !== entry.category) {
        return false
      }
      if (!entry.keyword) {
        return true
      }
      return item.searchText.includes(entry.keyword)
    })

    if (firstItem) {
      scrollToItem(firstItem.anchorId)
      return
    }

    const fallbackItem = normalizedInstructions.value.find(item => item.category === entry.category)
    if (fallbackItem) {
      scrollToItem(fallbackItem.anchorId)
    }
  })
}

function copyGuide(item) {
  const lines = [
    `【${item.title}】`,
    '',
    `适用场景：${item.summary}`,
    '',
    '操作步骤：',
    ...item.steps.map((step, index) => `${index + 1}. ${step}`)
  ]

  if (item.note) {
    lines.push('', `注意事项：${item.note}`)
  }

  const linksText = item.links.map(link => link.label).join('、')
  if (linksText) {
    lines.push('', `相关入口：${linksText}`)
  }

  const payload = lines.join('\n')

  if (!navigator.clipboard) {
    ElMessage.error('当前环境不支持一键复制，请手动复制')
    return
  }

  navigator.clipboard.writeText(payload)
    .then(() => {
      ElMessage.success('操作指引已复制')
    })
    .catch(() => {
      ElMessage.error('复制失败，请手动复制')
    })
}

function goTo(path) {
  router.push(path)
}

onMounted(async () => {
  await fetchInstructions()

  if (activeAnchor.value) {
    nextTick(() => {
      const node = document.getElementById(activeAnchor.value)
      if (node) {
        node.scrollIntoView({ behavior: 'auto', block: 'start' })
      }
    })
  }
})
</script>

<style scoped>
.instructions-page {
  max-width: 1280px;
  margin: 0 auto;
  padding: 24px;
}

.hero-section {
  padding: 24px;
  border-radius: 14px;
  background: linear-gradient(120deg, var(--el-color-primary-light-9), #f6f9ff);
  margin-bottom: 20px;
}

.hero-title-wrap {
  margin-bottom: 14px;
}

.hero-title {
  margin: 0;
  font-size: 24px;
  line-height: 1.35;
  color: var(--el-text-color-primary);
}

.hero-subtitle {
  margin: 8px 0 0;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.hero-search {
  max-width: 560px;
}

.quick-entries {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.content-layout {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 16px;
}

.sidebar-card {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 6px 18px rgba(31, 45, 61, 0.06);
}

.sidebar-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 12px;
}

.menu-group + .menu-group {
  margin-top: 14px;
}

.menu-group-title {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 8px;
  letter-spacing: 0.4px;
}

.menu-item {
  width: 100%;
  text-align: left;
  border: none;
  background: transparent;
  color: var(--el-text-color-regular);
  border-radius: 9px;
  padding: 9px 10px;
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  gap: 8px;
}

.menu-dot {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: var(--el-border-color);
  flex-shrink: 0;
}

.menu-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.menu-item:hover {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
}

.menu-item.active {
  background: linear-gradient(90deg, var(--el-color-primary-light-9), rgba(255, 255, 255, 0));
  color: var(--el-color-primary);
  font-weight: 600;
}

.menu-item.active .menu-dot {
  background: var(--el-color-primary);
}

.main-content {
  min-width: 0;
}

.instructions-content {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.mobile-toolbar {
  display: none;
  margin-bottom: 10px;
}

.instruction-card {
  scroll-margin-top: 88px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.card-title-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.card-title-icon {
  color: var(--el-color-primary);
  font-size: 18px;
}

.card-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.card-summary {
  margin: 0;
  line-height: 1.8;
  color: var(--el-text-color-regular);
}

.steps-block {
  margin-top: 14px;
}

.steps-block h4 {
  margin: 0 0 8px;
  font-size: 15px;
}

.steps-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.steps-list li {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  line-height: 1.8;
}

.steps-list li + li {
  margin-top: 8px;
}

.step-index {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--el-color-primary-light-8);
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-top: 3px;
}

.step-text {
  color: var(--el-text-color-regular);
}

.note-alert {
  margin-top: 14px;
}

.actions-block {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed var(--el-border-color-lighter);
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.copy-btn {
  font-weight: 500;
  border-radius: 18px;
  padding-inline: 14px;
}

.copy-btn :deep(.el-icon) {
  margin-right: 4px;
}

.jump-links {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.jump-link {
  border: none;
  background: transparent;
  color: var(--el-text-color-secondary);
  border-radius: 16px;
  padding: 6px 10px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  transition: all 0.2s ease;
}

.jump-link:hover {
  background: var(--el-fill-color-light);
  color: var(--el-color-primary);
}

.jump-link :deep(.el-icon) {
  font-size: 12px;
}

.drawer-menu {
  padding-right: 6px;
}

@media (max-width: 992px) {
  .instructions-page {
    padding: 14px;
  }

  .content-layout {
    grid-template-columns: 1fr;
  }

  .desktop-only {
    display: none;
  }

  .mobile-toolbar {
    display: block;
  }

  .card-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .actions-block {
    align-items: stretch;
  }
}
</style>
