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
  DocumentCopy,
  Lightbulb: Bell
}

const fallbackData = [
  {
    id: 'local-1',
    title: '新手入门：首次上传测井文件',
    icon: 'Reading',
    category: 'quickstart',
    summary: '本系统支持 TXT、CSV、Excel 格式的测井文件上传与智能解析。首次使用建议按以下步骤操作。',
    steps: [
      '登录系统后，点击左侧菜单进入"数据源管理"页面。',
      '点击"上传文件"按钮，选择本地的 TXT、CSV 或 Excel 测井文件（单次上传上限 50MB）。',
      '系统自动进入预览阶段，识别表头并生成列名映射建议，请检查每一列的映射是否正确。',
      '确认映射无误后点击"确认上传"，系统将在后台异步解析，状态会显示为"解析中"。',
      '解析完成后状态变为"成功"，此时可进入"测井数据看板"查看解析结果。'
    ],
    note: 'TXT 文件默认使用 GBK 编码读取，CSV 文件自动检测编码，Excel 文件无需关注编码问题。',
    content: '',
    links: [{ label: '去数据源管理', path: '/datasource' }, { label: '去测井数据看板', path: '/files' }]
  },
  {
    id: 'local-2',
    title: '服务器目录批量扫描',
    icon: 'PriceTag',
    category: 'feature',
    summary: '如果测井文件已存放在服务器本地目录中，可以使用批量扫描功能一次性导入多个文件。',
    steps: [
      '在"数据源管理"页面找到"服务器扫描"区域。',
      '输入服务器上存放测井文件的绝对路径（如 E:\\data\\welllog）。',
      '系统自动递归扫描目录下所有 .txt、.csv、.xls、.xlsx 文件。',
      '已存在同名记录的文件会自动跳过，避免重复导入。',
      '扫描完成后所有新文件将自动提交后台异步解析。'
    ],
    note: '请确保路径正确且后端进程对该目录有读取权限。扫描大量文件时请耐心等待。',
    content: '',
    links: [{ label: '去数据源管理', path: '/datasource' }]
  },
  {
    id: 'local-3',
    title: '测井数据看板与筛选',
    icon: 'Lock',
    category: 'feature',
    summary: '数据看板是浏览和筛选测井数据的核心页面，支持动态列展示和范围筛选。',
    steps: [
      '进入"测井数据看板"，系统自动加载已解析的文件列表。',
      '点击某个文件名即可展开该文件的测井数据表格，表格列根据文件实际列名动态生成。',
      '每一列都支持设置最小值和最大值进行范围筛选，输入后点击"提取数据"查看筛选结果。',
      '支持切换每页显示数量（100/200/500/1000 条），翻页浏览全部数据。',
      '筛选完成后可以导出为 Excel 或批量 ZIP 格式。'
    ],
    note: '筛选条件为空时将显示全部数据。如果文件刚上传还在解析中，请稍后再刷新查看。',
    content: '',
    links: [{ label: '去测井数据看板', path: '/files' }]
  },
  {
    id: 'local-4',
    title: '异常测段提取',
    icon: 'Bell',
    category: 'feature',
    summary: '异常测段提取功能用于自动识别满足条件的连续深度区间，支持多文件批量分析。',
    steps: [
      '先在"异常测段提取"页面选择需要分析的文件（支持同时选择多个文件）。',
      '为每个文件设置筛选条件：选择目标列（如 GR），设定最小值和最大值范围。',
      '多文件模式下可以使用统一的全局筛选条件一键应用到所有已选文件。',
      '点击"提取数据"后系统自动分析，识别满足条件的连续测段。',
      '结果展示每个异常段的顶界深度、底界深度、厚度等关键信息。',
      '支持将提取结果导出为 Excel 或 ZIP 格式。'
    ],
    note: '提取功能只做数据分析展示，不会修改原始解析数据。',
    content: '',
    links: [{ label: '去异常测段提取', path: '/extract' }]
  },
  {
    id: 'local-5',
    title: '异常测段可视化（ECharts 曲线图）',
    icon: 'Lightbulb',
    category: 'feature',
    summary: '可视化页面提供多通道测井曲线的交汇图与异常段高亮标注，大数据量自动降采样。',
    steps: [
      '进入"异常测段可视化"页面，从下拉框选择目标测井文件。',
      '选择"曲线显示通道"（最多 3 条），如 AC、GR、DEN。',
      '选择"统计通道"，系统将计算异常段内这些通道的极值和均值。',
      '配置"异常识别条件"：选择通道、运算符（大于/小于）和阈值。支持添加多个条件。',
      '设置"最短连续测点数"以过滤零散噪点，然后点击"执行分析与渲染"。',
      '图表区域会显示多道曲线，异常段以红色半透明区域高亮标注。',
      '可以通过底部滑块或鼠标滚轮缩放深度区间，点击"查看明细"查看异常段列表。'
    ],
    note: '可视化结果仅用于分析展示与导出，不会修改原始数据。大数据量文件后端会自动降采样以保证渲染速度。',
    content: '',
    links: [{ label: '去异常测段可视化', path: '/visualization' }]
  },
  {
    id: 'local-6',
    title: '数据导出指南',
    icon: 'DocumentCopy',
    category: 'feature',
    summary: '系统支持 Excel、CSV、ZIP 等多种导出方式，单文件导出上限为 100 万行。',
    steps: [
      '单文件导出：在看板中对某个文件完成筛选后，点击"导出 Excel"按钮下载 .xlsx 文件。',
      '批量 Excel 导出：选择多个文件后点击"批量导出"，生成包含多个 Sheet 的 Excel 文件。',
      '批量 ZIP 导出：选择多个文件后点击"批量 ZIP"，每个文件的筛选结果分别生成 Excel 并打包下载。',
      '异常段 CSV 导出：在可视化页面点击"导出 CSV"，下载异常测段的明细数据。',
      '图表图片导出：在可视化页面点击"导出高清图片"，下载 PNG 格式的曲线图。'
    ],
    note: 'Excel 单个工作表上限为 1,048,576 行。超大数据建议分批筛选导出或使用 ZIP 格式。',
    content: '',
    links: [{ label: '去测井数据看板', path: '/files' }]
  },
  {
    id: 'local-11',
    title: '地质分层配置',
    icon: 'PriceTag',
    category: 'feature',
    summary: '为测井文件配置地层分段（层名、顶深、底深），导出时自动附加层位列，异常提取时跨层段自动切割。',
    steps: [
      '在"数据源管理"页面找到目标文件，点击操作列的"分层"按钮打开分层配置对话框。',
      '手动输入：在可编辑表格中逐行填写层位名称、顶深（m）、底深（m）和备注，点击"保存分层配置"。',
      '从文件导入：点击"从文件导入"上传 Excel/CSV/TXT 文件，系统自动识别表头中的"层"、"顶"、"底"关键词定位列。',
      '如果导入文件包含多口井的数据，系统会弹出井名选择对话框，选择当前文件对应的井名后再导入。',
      '跨文件复制：配好一口井后点击"复制到其他文件"，勾选同区块其他井，层名结构会被复制，深度值保持不变。',
      '配置完成后，导出 Excel 时会自动追加"层位"列；异常测段提取时如果测段跨越分层边界会自动切割为多段。'
    ],
    note: '分层边界采用左闭右开规则：顶深 <= depth < 底深。建议下一层的顶深 = 上一层的底深，避免重叠或空隙。',
    content: '',
    links: [{ label: '去数据源管理', path: '/datasource' }]
  },
  {
    id: 'local-12',
    title: '运行日志查看',
    icon: 'Bell',
    category: 'feature',
    summary: '在系统设置中实时查看后端服务的运行日志，方便排查报错和监控运行状态。',
    steps: [
      '进入"系统设置"页面，点击左侧导航的"运行日志"选项卡。',
      '日志面板以深色终端风格显示后端实时日志，包括时间戳、日志级别、来源类名和消息内容。',
      '通过顶部下拉框可按级别筛选：ALL（全部）、INFO（普通信息）、WARN（警告）、ERROR（错误）。',
      '日志每 3 秒自动刷新，新日志自动滚动到底部，最新内容始终可见。',
      '点击"刷新日志"可立即手动拉取最新日志，点击"清空显示"清除当前面板内容（不影响后端缓冲）。'
    ],
    note: '运行日志保存在内存环形缓冲中（最多 500 条），重启应用后清空。如需持久化日志请查看后端控制台输出。',
    content: '',
    links: [{ label: '去系统设置', path: '/settings' }]
  },
  {
    id: 'local-7',
    title: '字典映射参数配置',
    icon: 'PriceTag',
    category: 'feature',
    summary: '字典映射用于将不同来源测井文件中的列名归一化为系统标准列名。',
    steps: [
      '进入"字典映射参数"页面，查看当前所有映射规则。',
      '每条规则包含：标准列名（如 DEPTH）、中文含义（如 测量深度）、别名列表（如 dept,tvd,深度）。',
      '上传文件时系统会自动匹配别名，将识别到的列名映射为标准列名。',
      '可以新增自定义映射规则，例如添加 SONIC 列映射到 AC。',
      '可以编辑现有规则的别名列表，增加更多别名以提高匹配率。'
    ],
    note: '核心列（如 DEPTH）受保护无法删除。修改映射规则后只对新上传的文件生效。',
    content: '',
    links: [{ label: '去字典映射参数', path: '/dictionary' }]
  },
  {
    id: 'local-8',
    title: '文件解析失败或数据乱码',
    icon: 'Bell',
    category: 'faq',
    summary: '遇到解析问题时，请按以下步骤排查：检查文件状态、编码、脏数据和列数匹配。',
    steps: [
      '检查文件状态：在数据源管理页面查看文件的解析状态是否为"失败"（红色标记）。',
      '编码问题：TXT 文件默认 GBK 编码读取，如果文件实际是 UTF-8 编码，中文可能乱码。建议另存为 GBK 后重新上传。',
      'CSV 编码：CSV 文件支持自动检测 UTF-8 和 GBK 编码，如果仍有乱码请手动转换编码后重试。',
      '脏数据处理：解析中无法识别的行自动存入脏数据表，不影响正常数据入库。可在"解析报告"中查看脏数据行数。',
      '列数不匹配：如果数据行列数与表头差异过大（多于 3 列或少于一半），该行会被标记为脏数据。'
    ],
    note: '单个文件上传上限为 50MB。如果需要处理更大的文件，建议拆分为多个小文件后分批上传。',
    content: '',
    links: [{ label: '去数据源管理', path: '/datasource' }]
  },
  {
    id: 'local-9',
    title: '导出失败与性能问题',
    icon: 'DocumentCopy',
    category: 'faq',
    summary: '导出失败、加载慢等问题的常见排查方法。',
    steps: [
      'Excel 导出失败：检查数据量是否接近 104 万行上限。如超出请缩小筛选范围后重试，或改用 ZIP 格式。',
      '导出超时：大数据量导出可能需要较长时间，请耐心等待浏览器下载完成，不要关闭页面。',
      '大文件上传慢：50 万行以上的文件解析需要 1~3 分钟，解析期间状态为"解析中"，完成后自动变为"成功"。',
      '看板加载慢：系统采用分页加载，默认每页 100 条，可在页面底部切换每页数量。',
      '可视化页面卡顿：系统对大数据量文件自动降采样至 5000 点以保证渲染速度，图表视觉几乎不受影响。'
    ],
    note: '如果多次尝试仍然失败，请保留浏览器控制台的错误信息并联系管理员排查。',
    content: '',
    links: [{ label: '去异常测段提取', path: '/extract' }]
  },
  {
    id: 'local-10',
    title: '系统设置与账号管理',
    icon: 'Lock',
    category: 'faq',
    summary: '系统设置页面提供个人信息维护、密码管理、操作日志、运行日志和数据用量查看功能。',
    steps: [
      '个人信息：可以修改真实姓名、绑定邮箱、个人简介和头像（支持外链图片 URL）。',
      '修改密码：建议定期更换密码。修改成功后系统自动退出，需要使用新密码重新登录。',
      '存储用量：点击"查看用量"可查看当前账号已上传文件数、解析总行数、脏数据行数和操作日志条数。',
      '操作日志：记录所有文件上传、解析、导出、删除等操作历史，支持分页查看和按模块分类筛选。',
      '运行日志：实时查看后端服务的运行状态和错误信息，支持按级别（INFO/WARN/ERROR）筛选，每 3 秒自动刷新。',
      '清除缓存：一键清理已删除文件的残留数据、脏数据、过期操作日志和过期令牌。'
    ],
    note: '每个用户只能查看和操作自己上传的文件，不同用户之间的数据完全隔离。运行日志保存在内存中，重启后清空。',
    content: '',
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
      // 合并 API 数据与 fallback 数据：API 没有的条目自动补充
      const apiTitles = new Set(data.map(d => (d.title || '').trim()))
      const missingFallback = fallbackData.filter(fb => {
        return !Array.from(apiTitles).some(t => t.includes(fb.title) || fb.title.includes(t))
      })
      instructionsList.value = [...data, ...missingFallback]
    } else {
      instructionsList.value = fallbackData
    }
  } catch (error) {
    instructionsList.value = fallbackData
    ElMessage.warning('说明数据加载失败，已展示本地示例内容')
  } finally {
    loading.value = false
  }
}

function normalizeInstruction(item, index) {
  const plainText = htmlToText(item.content || item.summary || '')
  const title = (item.title || `说明 ${index + 1}`).trim()
  if (isLegacyAiExtractionInstruction(title, plainText)) {
    return buildVisualizationInstruction(item, index)
  }

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

function isLegacyAiExtractionInstruction(title, plainText) {
  const source = `${title} ${plainText}`
  return /AI智能提取|AI 智能提取|通义千问|自然语言筛选/.test(source)
}

function buildVisualizationInstruction(item, index) {
  const title = '提示 2：异常测段可视化'
  const summary = '在异常测段可视化页面选择测井文件、显示通道、统计通道和异常识别条件，生成多道曲线与异常测段高亮结果。'
  const steps = [
    '进入“异常测段可视化”页面并选择目标测井文件。',
    '选择需要展示的曲线通道，以及参与极值、均值统计的特征通道。',
    '配置异常识别条件和最短连续测点数，点击执行分析与渲染。',
    '在图表中核对异常段高亮区域，必要时查看明细或导出 CSV 结果。'
  ]
  const note = '异常测段可视化只用于分析展示与结果导出，不会修改原始解析数据。'
  const links = [{ label: '去异常测段可视化', path: '/visualization' }]

  return {
    id: item.id ?? `local-${index + 1}`,
    icon: item.icon || 'Lightbulb',
    title,
    summary,
    steps,
    note,
    category: normalizeCategory(item.category, title, summary),
    links,
    anchorId: `instruction-${item.id ?? index}`,
    searchText: `${title} ${summary} ${steps.join(' ')} ${note}`.toLowerCase()
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
  if (/导出|看板|测井|报表/.test(sourceText)) {
    candidateLinks.push({ label: '去测井数据看板', path: '/files' })
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
