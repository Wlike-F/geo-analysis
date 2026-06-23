<template>
  <div class="visualization-container">
    <el-row :gutter="20" class="visualization-layout">
      <el-col :xs="24" :lg="7" :xl="6">
        <el-card class="custom-panel" shadow="never" :body-style="{ padding: '0' }">
          <div class="custom-panel-header">
            <h2 class="custom-panel-title">
              <el-icon><Setting /></el-icon>
              图表分析与异常标定
            </h2>
            <p class="custom-panel-subtitle">选择文件、通道与异常条件后，即可生成测井交汇图与异常段明细。</p>
          </div>

          <div class="custom-panel-body">
            <el-form label-position="top">
              <div class="custom-section-title">
                <el-icon><Document /></el-icon> 数据源文件
              </div>
              <div class="px-1 mt-2" style="margin-bottom: 14px;">
                <div style="display: flex; width: 100%; gap: 8px;">
                  <el-select v-model="selectedFile" placeholder="请选择测井文件" style="flex: 1; height: 30px;" @change="handleFileChange">
                    <el-option
                      v-for="file in fileList"
                      :key="file.id"
                      :label="file.fileName"
                      :value="file.id"
                    />
                  </el-select>
                  <el-button class="custom-btn-blue" @click="fetchFileList" title="刷新文件列表">
                    <el-icon><Refresh /></el-icon> 刷新
                  </el-button>
                </div>
              </div>

              <div class="custom-section-title custom-mt-3 toggle-section" @click="toggleSection('channels')">
                <el-icon class="toggle-arrow" :class="{ expanded: sectionExpanded.channels }"><ArrowRight /></el-icon>
                <el-icon><Connection /></el-icon> 曲线显示通道
                <span class="toggle-hint">{{ sectionExpanded.channels ? '收起' : '展开' }}</span>
              </div>
              <div v-show="sectionExpanded.channels" class="px-1 mt-2" style="margin-bottom: 14px;">
                <el-select
                  v-model="selectedChannels"
                  class="full-width custom-select-multi"
                  multiple
                  :multiple-limit="3"
                  placeholder="请选择绘图通道"
                  style="min-height: 30px;"
                  @change="handleChannelChange"
                >
                  <el-option
                    v-for="col in availableChannels"
                    :key="col"
                    :label="col"
                    :value="col"
                  />
                </el-select>
              </div>

              <div class="custom-section-title custom-mt-3 toggle-section" @click="toggleSection('stats')">
                <el-icon class="toggle-arrow" :class="{ expanded: sectionExpanded.stats }"><ArrowRight /></el-icon>
                <el-icon><DataAnalysis /></el-icon> 统计与特征通道
                <span class="toggle-hint">{{ sectionExpanded.stats ? '收起' : '展开' }}</span>
              </div>
              <div v-show="sectionExpanded.stats" class="px-1 mt-2" style="margin-bottom: 14px;">
                <el-select
                  v-model="selectedStatsChannels"
                  class="full-width custom-select-multi"
                  multiple
                  placeholder="请选择要统计极值、均值的通道"
                  style="min-height: 30px;"
                >
                  <el-option
                    v-for="col in availableChannels"
                    :key="col"
                    :label="col"
                    :value="col"
                  />
                </el-select>
              </div>

              <div class="custom-section-title custom-mt-3 toggle-section" @click="toggleSection('conditions')">
                <el-icon class="toggle-arrow" :class="{ expanded: sectionExpanded.conditions }"><ArrowRight /></el-icon>
                <el-icon><Filter /></el-icon> 异常识别条件
                <span class="toggle-hint">{{ sectionExpanded.conditions ? '收起' : '展开' }}</span>
              </div>
              <section v-show="sectionExpanded.conditions" class="anomaly-controls">
                <div v-for="(cond, idx) in anomalyConditions" :key="idx" class="condition-item">
                  <el-row :gutter="8" class="condition-row" align="middle">
                    <el-col :span="8">
                      <el-select v-model="cond.channel" class="full-width custom-select-sm" placeholder="通道" style="height: 30px;">
                        <el-option v-for="col in availableChannels" :key="col" :label="col" :value="col" />
                      </el-select>
                    </el-col>
                    <el-col :span="7">
                      <el-select v-model="cond.operator" class="full-width custom-select-sm" placeholder="条件" style="height: 30px;">
                        <el-option label="大于 (>)" value=">" />
                        <el-option label="小于 (<)" value="<" />
                      </el-select>
                    </el-col>
                    <el-col :span="6">
                      <el-input-number v-model="cond.threshold" class="full-width custom-input-number" :controls="false" placeholder="阈值" style="height: 30px;" />
                    </el-col>
                    <el-col :span="3" class="condition-delete-col">
                      <el-button
                        v-if="anomalyConditions.length > 1"
                        type="danger"
                        icon="Delete"
                        plain
                        circle
                        class="custom-delete-btn"
                        @click="removeCondition(idx)"
                      />
                    </el-col>
                  </el-row>
                </div>

                <el-button class="full-width mt-10 custom-add-btn" @click="addCondition">
                  <el-icon><Plus /></el-icon> 新增条件
                </el-button>

                <div class="custom-section-title custom-mt-3">
                  <el-icon><Filter /></el-icon> 最短连续测点数 (去除噪点)
                </div>
                <div class="px-1 mt-2">
                  <el-input-number
                    v-model="minContinuousPoints"
                    :min="1"
                    :max="1000"
                    class="full-width"
                    style="width: 100%; height: 30px;"
                  />
                </div>
              </section>

            </el-form>
          </div>
          
          <div class="custom-panel-footer">
            <el-row :gutter="12" class="custom-action-row">
              <el-col :span="12">
                <el-button class="full-width custom-btn-orange" :disabled="anomalySegments.length === 0" @click="showAnomalyDialog = true">
                  <el-icon><List /></el-icon> 明细({{ anomalySegments.length }})
                </el-button>
              </el-col>
              <el-col :span="12">
                <el-button class="full-width custom-btn-emerald" :disabled="anomalySegments.length === 0" @click="exportAnomalyData">
                  <el-icon><Download /></el-icon> 导出 CSV
                </el-button>
              </el-col>
            </el-row>
            <div class="custom-export-link-wrapper">
              <el-button class="full-width custom-btn-indigo custom-export-btn" :disabled="!selectedFile || loading" @click="handleExport">
                <el-icon><Picture /></el-icon> 导出高清图片
              </el-button>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="17" :xl="18">
        <el-card class="chart-panel" shadow="hover">
          <template #header>
            <div class="chart-header">
              <div>
                <div class="panel-title">
                  <el-icon><DataAnalysis /></el-icon>
                  <span>多道测井异常标定交汇图</span>
                </div>
                <p class="panel-subtitle">支持异常段高亮映射、深度缩放与多通道联动浏览。</p>
              </div>
              <el-tag v-if="anomalySegments.length" type="danger" size="small" effect="light" round>
                已发现 {{ anomalySegments.length }} 处异常地层
              </el-tag>
              <el-tag v-else type="info" size="small" effect="plain" round>
                等待分析结果
              </el-tag>
              <el-button type="primary" :disabled="!selectedFile" @click="handleDraw" :loading="loading">
                <el-icon><DataAnalysis /></el-icon> 执行分析
              </el-button>
            </div>
          </template>

          <div class="chart-panel__body">
            <div v-if="loading" class="skeleton-container">
              <el-skeleton :rows="15" animated />
              <div class="loading-text">正在对数据流进行清洗与区间匹配，请稍候...</div>
            </div>

            <template v-else>
              <div v-if="!fileList.length" class="chart-empty-state">
                <el-empty description="暂无可分析文件，请先前往数据源管理上传或扫描文件" />
              </div>
              <div v-else class="chart-stage">
                <div ref="chartRef" class="echarts-container"></div>
                <div v-if="!hasChartData" class="chart-hint">请选择参数后点击“执行分析与渲染”生成图表。</div>
              </div>
            </template>

            <div v-if="hasChartData" class="depth-slider-bar">
              <div class="depth-slider-label">
                <el-icon><Odometer /></el-icon>
                <span>深度区间控制 (m)</span>
              </div>
              <el-slider v-model="depthRange" range :min="minDepth" :max="maxDepth" :step="10"
                class="custom-slider" @change="updateDepthZoom" />
              <div class="custom-range-text" v-if="maxDepth > 0">{{ depthRange[0] }} m &mdash; {{ depthRange[1] }} m</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="showAnomalyDialog" width="60%" destroy-on-close class="anomaly-dialog">
      <template #header>
        <div class="dialog-header">
          <span class="dialog-title">提取到的异常测段明细</span>
        </div>
      </template>

      <el-table :data="anomalySegments" border stripe height="400">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="startDepth" label="顶界深度(m)" align="center" />
        <el-table-column prop="endDepth" label="底界深度(m)" align="center" />
        <el-table-column label="厚度(m)" align="center">
          <template #default="scope">
            <el-tag type="success">{{ (scope.row.endDepth - scope.row.startDepth).toFixed(3) }}</el-tag>
          </template>
        </el-table-column>
        <template v-for="col in selectedStatsChannels" :key="col">
          <el-table-column :prop="`stats_${col}_ext`" :label="`[${col}] 极值`" align="center" />
          <el-table-column :prop="`stats_${col}_avg`" :label="`[${col}] 均值`" align="center" />
        </template>
      </el-table>

      <template #footer>
        <el-button @click="showAnomalyDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick, shallowRef, onBeforeUnmount, onActivated, computed } from 'vue'
import * as echarts from 'echarts'
import { Setting, Download, DataAnalysis, Plus, List, Delete, Refresh, Filter, Odometer, Picture, Document, Connection, ArrowRight } from '@element-plus/icons-vue'
import { getFileList, getEchartsData } from '@/api/file'
import { ElMessage } from 'element-plus'

const fileList = ref([])
const selectedFile = ref(null)
const minDepth = ref(0)
const maxDepth = ref(3000)
const depthRange = ref([0, 3000])
const availableChannels = ref([])
const selectedChannels = ref([])
const selectedStatsChannels = ref([])
const anomalyConditions = ref([{ channel: '', operator: '>', threshold: 0 }])
const minContinuousPoints = ref(1)
const anomalySegments = ref([])
const showAnomalyDialog = ref(false)

// 折叠面板
const sectionExpanded = reactive({ channels: false, stats: false, conditions: false })
const toggleSection = (key) => { sectionExpanded[key] = !sectionExpanded[key] }
const loading = ref(false)
const chartRef = ref(null)
const chartInstance = shallowRef(null)
const rawChartData = shallowRef({})

const hasChartData = computed(() => Array.isArray(rawChartData.value.DEPTH) && rawChartData.value.DEPTH.length > 0)

const ensureChartInstance = () => {
  if (!chartRef.value) return false

  if (!chartInstance.value) {
    chartInstance.value = echarts.init(chartRef.value)
    return true
  }

  const currentDom = chartInstance.value.getDom?.()
  if (currentDom !== chartRef.value) {
    chartInstance.value.dispose()
    chartInstance.value = echarts.init(chartRef.value)
  }

  return true
}

// 获文件列表
const fetchFileList = async () => {
  try {
    const data = await getFileList()
    if (data && Array.isArray(data)) {
      fileList.value = data
      if (fileList.value.length > 0) {
        // 如果当前选中的文件依旧在列表中，则保留选中状态，以免刷新时重置用户的操作
        const isSelectedFileExists = fileList.value.some(f => f.id === selectedFile.value)
        if (!selectedFile.value || !isSelectedFileExists) {
          selectedFile.value = fileList.value[0].id
          handleFileChange()
        }
      } else {
        selectedFile.value = null
      }
      if (!chartInstance.value) initChart()
    } else {
      if (!chartInstance.value) initChart()
    }
  } catch (error) {
    console.error('获取文件列表失败', error)
    ElMessage.error('获取文件列表失败，请检查网络连接')
    if (!chartInstance.value) initChart()
  }
}

// 切换文件，重置绘图
const handleFileChange = () => {
  rawChartData.value = {}
  availableChannels.value = []
  selectedChannels.value = []
  anomalySegments.value = []
  anomalyConditions.value = [{ channel: '', operator: '>', threshold: 0 }]

  const fileMeta = fileList.value.find(file => file.id === selectedFile.value)
  if (fileMeta?.columnsJson) {
    try {
      const cols = JSON.parse(fileMeta.columnsJson)
      availableChannels.value = cols.filter(col => !col.includes('深') && col.toUpperCase() !== 'DEPTH')
      if (availableChannels.value.length > 0) {
        selectedChannels.value = availableChannels.value.slice(0, 3)
        anomalyConditions.value[0].channel = selectedChannels.value[0]
        selectedStatsChannels.value = [...selectedChannels.value]
      }
    } catch (error) {
      console.error(error)
    }
  }

  if (chartInstance.value) {
    try { chartInstance.value.clear() } catch { /* may be disposed */ }
    initChart()
  }
}

const handleChannelChange = () => {
  if (Object.keys(rawChartData.value).length > 0) {
    updateChartData()
  }
}

const addCondition = () => {
  anomalyConditions.value.push({
    channel: availableChannels.value.length > 0 ? availableChannels.value[0] : '',
    operator: '>',
    threshold: 0
  })
}

const removeCondition = (idx) => {
  anomalyConditions.value.splice(idx, 1)
}

const invalidNumericSentinels = [-9999, -999.25, -999]

const parseFiniteNumber = (value) => {
  if (value === null || value === undefined || value === '') return null
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? numberValue : null
}

const isValidStatsNumber = (value) => {
  const numberValue = parseFiniteNumber(value)
  if (numberValue === null) return false
  return !invalidNumericSentinels.some(sentinel => Math.abs(numberValue - sentinel) < 1e-8)
}

const addStatsValue = (segment, statsItem, value) => {
  if (!isValidStatsNumber(value)) return
  const numberValue = Number(value)
  const countKey = `stats_${statsItem.col}_count`
  const sumKey = `stats_${statsItem.col}_sum`
  const maxKey = `stats_${statsItem.col}_max`
  const minKey = `stats_${statsItem.col}_min`

  segment[countKey] = (segment[countKey] || 0) + 1
  segment[sumKey] = (segment[sumKey] || 0) + numberValue
  segment[maxKey] = segment[maxKey] === undefined ? numberValue : Math.max(segment[maxKey], numberValue)
  segment[minKey] = segment[minKey] === undefined ? numberValue : Math.min(segment[minKey], numberValue)
}

const finalizeStats = (segment, statsData) => {
  statsData.forEach(s => {
    const count = segment[`stats_${s.col}_count`] || 0
    const extVal = s.op === '<' ? segment[`stats_${s.col}_min`] : segment[`stats_${s.col}_max`]
    segment[`stats_${s.col}_ext`] = count > 0 && Number.isFinite(extVal) ? extVal.toFixed(2) : '-'
    segment[`stats_${s.col}_avg`] = count > 0 ? (segment[`stats_${s.col}_sum`] / count).toFixed(2) : '-'
  })
}

const updateDepthZoom = () => {
  if (chartInstance.value && rawChartData.value.DEPTH?.length > 0) {
    chartInstance.value.dispatchAction({
      type: 'dataZoom',
      startValue: depthRange.value[0],
      endValue: depthRange.value[1]
    })
  }
}

const extractAnomalySegments = () => {
  anomalySegments.value = []
  if (!rawChartData.value?.DEPTH?.length) return

  const depths = rawChartData.value.DEPTH
  const conditionsData = anomalyConditions.value.map(cond => {
    if (!cond.channel) return null
    const targetKey = Object.keys(rawChartData.value).find(key => key.toUpperCase() === cond.channel.toUpperCase())
    return {
      dataArr: targetKey ? rawChartData.value[targetKey] : null,
      operator: cond.operator,
      threshold: cond.threshold
    }
  }).filter(item => item && item.dataArr && item.threshold != null)

  const statsData = selectedStatsChannels.value.map(col => {
    const targetKey = Object.keys(rawChartData.value).find(key => key.toUpperCase() === col.toUpperCase())
    const cond = anomalyConditions.value.find(c => c.channel === col)
    return {
      col,
      arr: targetKey ? rawChartData.value[targetKey] : null,
      op: cond ? cond.operator : '>'
    }
  }).filter(item => item && item.arr)

  if (conditionsData.length === 0) return

  let isAnomaly = false
  let currentSegment = null
  let countVal = 0

  for (let i = 0; i < depths.length; i += 1) {
    const depth = parseFloat(depths[i])
    if (Number.isNaN(depth)) continue

    const inRange = depth >= depthRange.value[0] && depth <= depthRange.value[1]
    let meetsAllConditions = inRange

    if (inRange) {
      for (let j = 0; j < conditionsData.length; j += 1) {
        const cond = conditionsData[j]
        const value = parseFiniteNumber(cond.dataArr[i])
        if (value === null) {
          meetsAllConditions = false
          break
        }

        if (cond.operator === '>' ? !(value > cond.threshold) : !(value < cond.threshold)) {
          meetsAllConditions = false
          break
        }
      }
    }

    if (meetsAllConditions) {
      if (!isAnomaly) {
        isAnomaly = true
        currentSegment = { startDepth: depth, endDepth: depth }
        countVal = 1
        statsData.forEach(s => {
          addStatsValue(currentSegment, s, s.arr[i])
        })
      } else {
        countVal += 1
        statsData.forEach(s => {
          addStatsValue(currentSegment, s, s.arr[i])
        })
      }
    } else if (isAnomaly) {
      currentSegment.endDepth = parseFloat(depths[i - 1 < 0 ? 0 : i - 1])
      if (countVal >= minContinuousPoints.value && (minContinuousPoints.value === 1 || Math.abs(currentSegment.endDepth - currentSegment.startDepth) > 0)) {
        finalizeStats(currentSegment, statsData)
        anomalySegments.value.push(currentSegment)
      }
      isAnomaly = false
    }
  }

  if (isAnomaly) {
    currentSegment.endDepth = parseFloat(depths[depths.length - 1])
    if (countVal >= minContinuousPoints.value && (minContinuousPoints.value === 1 || Math.abs(currentSegment.endDepth - currentSegment.startDepth) > 0)) {
      finalizeStats(currentSegment, statsData)
      anomalySegments.value.push(currentSegment)
    }
  }
}
const handleDraw = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择测井文件')
    return
  }
  if (selectedChannels.value.length === 0) {
    ElMessage.warning('请至少选择 1 个显示通道')
    return
  }

  loading.value = true
  try {
    const data = await getEchartsData(selectedFile.value)
    if (data) {
      rawChartData.value = data
      const keys = Object.keys(data)
      const depthKey = keys.find(key => key.toUpperCase() === 'DEPTH') || 'DEPTH'
      const depths = data[depthKey] || []
      rawChartData.value.DEPTH = depths

      if (depths.length > 0) {
        let minVal = Infinity, maxVal = -Infinity
        for (let i = 0; i < depths.length; i++) {
          const v = Number(depths[i])
          if (!Number.isFinite(v)) continue
          if (v < minVal) minVal = v
          if (v > maxVal) maxVal = v
        }
        if (Number.isFinite(minVal) && Number.isFinite(maxVal)) {
          minDepth.value = Math.floor(minVal)
          maxDepth.value = Math.ceil(maxVal)
          depthRange.value = [minDepth.value, maxDepth.value]
        }
      }

      extractAnomalySegments()
      nextTick(() => {
        ensureChartInstance()
        if (chartInstance.value) chartInstance.value.resize()
        updateChartData()
        updateDepthZoom()
        ElMessage.success(`加载成功，发现 ${anomalySegments.value.length} 处异常`)
      })
    }
  } catch (error) {
    ElMessage.error('获取测井数据失败')
  } finally {
    loading.value = false
  }
}

const exportAnomalyData = () => {
  if (anomalySegments.value.length === 0) {
    ElMessage.warning('暂无异常结果可导出')
    return
  }

  let csvContent = '\uFEFF序号,顶界深度(m),底界深度(m),厚度(m)'
  selectedStatsChannels.value.forEach(col => {
    csvContent += `,${col}极值,${col}均值`
  })
  csvContent += '\n'

  anomalySegments.value.forEach((row, idx) => {
    const thickness = (row.endDepth - row.startDepth).toFixed(3)
    let line = `${idx + 1},${row.startDepth},${row.endDepth},${thickness}`
    selectedStatsChannels.value.forEach(col => {
      line += `,${row[`stats_${col}_ext`] || '-'},${row[`stats_${col}_avg`] || '-'}`
    })
    csvContent += line + '\n'
  })

  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `异常测段分析结果_${selectedFile.value || '未知'}.csv`
  link.click()
  setTimeout(() => URL.revokeObjectURL(link.href), 100)
}

const handleExport = () => {
  if (!chartInstance.value) return
  const url = chartInstance.value.getDataURL({
    type: 'png',
    pixelRatio: 2,
    backgroundColor: '#fff'
  })
  const link = document.createElement('a')
  link.download = `测井多道异常标定_${selectedFile.value || '未知'}.png`
  link.href = url
  link.click()
}

const initChart = () => {
  if (!ensureChartInstance()) return

  chartInstance.value.setOption({
    title: {
      text: '配置左侧条件后，点击“执行分析与渲染”',
      left: 'center',
      top: 'middle',
      textStyle: { color: '#c0c4cc', fontWeight: 'normal', fontSize: 16 }
    },
    tooltip: { trigger: 'none' },
    grid: [{ left: '10%', right: '10%', top: '60px', bottom: '20px' }],
    xAxis: [{ type: 'value', position: 'top', name: 'Wait...', nameLocation: 'middle', nameGap: 30 }],
    yAxis: [{ type: 'value', inverse: true, name: 'Depth(m)', nameLocation: 'start' }],
    series: []
  }, true)
}

const updateChartData = () => {
  if (!chartInstance.value) return
  const depth = rawChartData.value.DEPTH
  if (!depth?.length) return

  const channelCount = selectedChannels.value.length
  const grids = []
  const xAxes = []
  const yAxes = []
  const series = []
  const trackColors = ['#e67c22', '#e74c3c', '#3498db']

  const anomalyMarkArea = {
    itemStyle: {
      color: 'rgba(255, 73, 73, 0.35)',
      borderWidth: 1,
      borderColor: 'rgba(255, 0, 0, 0.8)',
      borderType: 'dashed'
    },
    data: anomalySegments.value.map(seg => [
      { yAxis: seg.startDepth },
      { yAxis: seg.endDepth }
    ])
  }

  const leftMargin = 10
  const rightMargin = 10
  const gap = 8 // percentage gap
  const gridWidth = (100 - leftMargin - rightMargin - gap * (channelCount > 1 ? channelCount - 1 : 0)) / (channelCount || 1)
  let leftOffset = leftMargin

  selectedChannels.value.forEach((colName, index) => {
    grids.push({
      left: `${leftOffset}%`,
      width: `${gridWidth}%`,
      top: '60px',
      bottom: '20px'
    })

    xAxes.push({
      gridIndex: index,
      type: 'value',
      position: 'top',
      name: colName,
      nameLocation: 'middle',
      nameGap: 30,
      axisLine: { lineStyle: { color: trackColors[index % 3] } },
      axisLabel: {
        color: trackColors[index % 3],
        hideOverlap: true,
        formatter: (val) => Number.isInteger(val) ? val : String(val).slice(0, 5)
      },
      splitNumber: 3,
      splitLine: { show: false }
    })

    yAxes.push({
      gridIndex: index,
      type: 'value',
      inverse: true,
      min: minDepth.value,
      max: maxDepth.value,
      name: index === 0 ? 'Depth(m)' : '',
      axisLabel: { show: index === 0 },
      axisTick: { show: index === 0 }
    })

    const key = Object.keys(rawChartData.value).find(item => item.toUpperCase() === colName.toUpperCase())
    const yData = rawChartData.value[key] || []
    const lineData = depth.map((d, i) => [yData[i] || null, d]).filter(item => item[0] !== null)

    series.push({
      name: colName,
      type: 'line',
      xAxisIndex: index,
      yAxisIndex: index,
      showSymbol: false,
      sampling: 'lttb',
      large: true,
      largeThreshold: 2000,
      itemStyle: { color: trackColors[index % 3] },
      lineStyle: { width: 1 },
      data: lineData,
      markArea: anomalyMarkArea
    })

    leftOffset += gridWidth + gap
  })

  chartInstance.value.setOption({
    title: { text: '' },
    grid: grids,
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross', animation: false },
      formatter(params) {
        if (!params?.length) return ''
        let content = `<strong>深度: ${params[0].value[1]} m</strong><br/>`
        params.forEach(item => {
          if (item.value[0] !== undefined && item.value[0] !== null) {
            content += `${item.marker} ${item.seriesName}: ${item.value[0]}<br/>`
          }
        })
        return content
      }
    },
    dataZoom: [{
      type: 'inside',
      yAxisIndex: grids.map((_, i) => i)
    }],
    axisPointer: { link: [{ yAxisIndex: 'all' }] },
    xAxis: xAxes,
    yAxis: yAxes,
    series
  }, true)

  chartInstance.value.off('datazoom')
  if (datazoomHandler) { datazoomHandler.cancel(); datazoomHandler = null }
  datazoomHandler = debounce(() => {
    if (!chartInstance.value) return
    const option = chartInstance.value.getOption()
    if (option?.dataZoom?.length) {
      const dz = option.dataZoom[0]
      let startValue
      let endValue
      if (dz.startValue !== undefined && dz.endValue !== undefined && !Number.isNaN(dz.startValue)) {
        startValue = dz.startValue
        endValue = dz.endValue
      } else if (dz.start !== undefined && dz.end !== undefined) {
        const totalRange = maxDepth.value - minDepth.value
        startValue = minDepth.value + totalRange * (dz.start / 100)
        endValue = minDepth.value + totalRange * (dz.end / 100)
      }
      if (startValue !== undefined && endValue !== undefined) {
        depthRange.value = [
          Math.floor(Math.min(startValue, endValue)),
          Math.ceil(Math.max(startValue, endValue))
        ]
      }
    }
  }, 100)
  chartInstance.value.on('datazoom', datazoomHandler)
}

const debounce = (fn, delay) => {
  let timer = null
  const debounced = function (...args) {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => fn.apply(this, args), delay)
  }
  debounced.cancel = () => { if (timer) { clearTimeout(timer); timer = null } }
  return debounced
}

const handleResize = debounce(() => {
  if (chartInstance.value) chartInstance.value.resize()
}, 200)

let datazoomHandler = null

onMounted(() => {
  fetchFileList()
  window.addEventListener('resize', handleResize)
})

onActivated(() => {
  fetchFileList()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  if (datazoomHandler) { datazoomHandler.cancel(); datazoomHandler = null }
  if (chartInstance.value) {
    chartInstance.value.dispose()
    chartInstance.value = null
  }
})
</script>

<style scoped>
.visualization-container {
  height: 100%;
  overflow: hidden;
}

.visualization-layout {
  height: 100%;
  display: flex;
  align-items: stretch;
  overflow: hidden;
}

.visualization-layout > .el-col {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-header,
.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.panel-subtitle {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.control-panel,
.chart-panel {
  border-radius: 10px;
}

/* custom-panel styles based on newui.html */
.custom-panel {
  border-radius: 0.75rem;
  border: 1px solid #e5e7eb;
  background-color: #ffffff;
  box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  height: 100%;
}
.custom-panel :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
  padding: 0 !important;
}
.custom-panel-header {
  padding: 1rem 1.25rem;
  border-bottom: 1px solid #f3f4f6;
  background-color: rgba(249, 250, 251, 0.5);
}
.custom-panel-title {
  font-size: 1rem;
  line-height: 1.5rem;
  font-weight: 600;
  color: #1f2937;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin: 0;
}
.custom-panel-title .el-icon {
  color: #6b7280;
  font-size: 0.875rem;
}
.custom-panel-subtitle {
  font-size: 0.75rem;
  line-height: 1.625;
  color: #6b7280;
  margin: 0.375rem 0 0 0;
}
.custom-panel-body {
  flex: 1 1 0;
  padding: 1.35rem 1.1rem 1rem;
  overflow-y: auto;
  min-height: 0;
}
.depth-slider-container {
  overflow: hidden;
  padding-inline: 12px;
}
.custom-section-title {
  font-size: 0.95rem;
  font-weight: 600;
  color: #1f2937;
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin-bottom: 0.6rem;
}
.custom-section-title .el-icon {
  color: var(--el-color-primary);
  font-size: 1rem;
}
/* 折叠区块 */
.toggle-section {
  cursor: pointer;
  user-select: none;
  padding: 8px 10px;
  margin: 4px -8px;
  border-radius: 8px;
  transition: background 0.2s, box-shadow 0.2s;
  border: 1px solid transparent;
}
.toggle-section:hover {
  background: var(--el-fill-color-light);
  border-color: var(--el-border-color-lighter);
}
.toggle-arrow {
  font-size: 14px !important;
  transition: transform 0.25s;
  color: #4b5563 !important;
  flex-shrink: 0;
}
.toggle-arrow.expanded {
  transform: rotate(90deg);
}
.toggle-hint {
  margin-left: auto;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  font-weight: 400;
}
/* 图表底部深度滑块 */
.depth-slider-bar {
  padding: 12px 16px 4px;
  border-top: 1px solid var(--el-border-color-lighter);
  margin-top: 8px;
  flex-shrink: 0;
}
.depth-slider-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #374151;
  margin-bottom: 8px;
}
.custom-form-item {
  margin-bottom: 0.875rem;
}
.custom-label {
  font-size: 0.875rem;
  font-weight: 500;
  color: #374151;
  line-height: 1;
}
.custom-label-sub {
  color: #9ca3af;
  font-size: 0.75rem;
  font-weight: 400;
}
.custom-select .el-input__inner,
.custom-select-multi .el-select__tags {
  font-size: 0.875rem;
}
/* Ensure el-select and el-input-number respect the 30px height */
:deep(.el-select .el-input__wrapper),
:deep(.el-input-number .el-input__wrapper) {
  height: 30px;
  min-height: 30px;
  line-height: 30px;
}
.custom-select-sm .el-input__wrapper {
  padding: 0 0.5rem;
}
.custom-input-number .el-input__wrapper {
  padding: 0 0.5rem;
}
.custom-input-number .el-input__inner {
  text-align: center;
}
.custom-add-btn {
  width: 100%;
  margin-top: 0.25rem;
  padding: 0;
  border: 1px dashed #d1d5db;
  border-radius: 0.5rem;
  font-size: 0.875rem;
  color: #2563eb;
  background-color: transparent;
  transition: colors 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.25rem;
  height: 30px;
}
.custom-add-btn:hover {
  background-color: #eff6ff;
  border-color: #60a5fa;
  color: #2563eb;
}
.custom-delete-btn {
  margin: 0;
  height: 30px !important;
  width: 30px !important;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.custom-divider {
  border-top: 1px solid #f3f4f6;
  padding-top: 0.5rem;
}
.custom-mt-3 {
  margin-top: 0.75rem;
}
.custom-slider {
  margin: 0.65rem 0 0.9rem;
}
.custom-slider .el-slider__runway {
  height: 0.375rem;
  background-color: #e5e7eb;
}
.custom-slider .el-slider__bar {
  height: 0.375rem;
  background-color: #2563eb;
}
.custom-slider .el-slider__button {
  width: 0.875rem;
  height: 0.875rem;
  border: 2px solid #2563eb;
  box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.1), 0 1px 2px 0 rgba(0, 0, 0, 0.06);
}
.custom-range-text {
  display: flex;
  justify-content: center;
  font-size: 0.75rem;
  color: #6b7280;
}
.custom-panel-footer {
  padding: 0.35rem 1.1rem 1.25rem 1.1rem;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  flex-shrink: 0;
}
.custom-action-row {
  margin-bottom: 0;
  margin-left: 0 !important;
  margin-right: 0 !important;
}
.custom-btn-orange {
  background-color: #fff7ed;
  color: #ea580c;
  border: 1px solid #fed7aa;
  border-radius: 0.5rem;
  font-size: 0.875rem;
  font-weight: 500;
  padding: 0.5rem 0.75rem;
  height: auto;
  transition: colors 0.2s;
}
.custom-btn-orange:hover, .custom-btn-orange:focus {
  background-color: #ffedd5;
  color: #ea580c;
  border-color: #fed7aa;
}
.custom-btn-orange.is-disabled {
  opacity: 0.5;
}
.custom-btn-emerald {
  background-color: #ecfdf5;
  color: #059669;
  border: 1px solid #a7f3d0;
  border-radius: 0.5rem;
  font-size: 0.875rem;
  font-weight: 500;
  padding: 0.5rem 0.75rem;
  height: auto;
  transition: colors 0.2s;
}
.custom-btn-emerald:hover, .custom-btn-emerald:focus {
  background-color: #d1fae5;
  color: #059669;
  border-color: #a7f3d0;
}
.custom-btn-emerald.is-disabled {
  opacity: 0.5;
}
.custom-btn-blue {
  background-color: #eff6ff;
  color: #2563eb;
  border: 1px solid #bfdbfe;
  border-radius: 0.5rem;
  font-size: 0.875rem;
  font-weight: 500;
  padding: 0 1rem;
  height: 30px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}
.custom-btn-blue:hover, .custom-btn-blue:focus {
  background-color: #dbeafe;
  color: #1d4ed8;
  border-color: #93c5fd;
}
.custom-btn-blue.is-disabled {
  opacity: 0.5;
}
.custom-btn-indigo {
  background-color: #4338ca;
  color: #ffffff;
  border: none;
  border-radius: 0.5rem;
  font-size: 0.875rem;
  font-weight: 500;
  padding: 0.625rem 0;
  height: auto;
  box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05);
  transition: background-color 0.2s;
}
.custom-btn-indigo:hover, .custom-btn-indigo:focus {
  background-color: #3730a3;
  color: #ffffff;
}
.custom-btn-indigo.is-disabled {
  background-color: #818cf8;
}
.custom-export-link-wrapper {
  width: 100%;
  margin-top: 0.25rem;
}
.custom-export-btn {
  width: 100%;
  font-size: 0.875rem;
  font-weight: 500;
  color: #4338ca;
  padding: 0.625rem 0;
  border-radius: 0.5rem;
  border: 1px solid #c7d2fe;
  background-color: #eef2ff;
  box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05);
  transition: background-color 0.2s, border-color 0.2s, color 0.2s;
}
.custom-export-btn:hover,
.custom-export-btn:focus {
  color: #3730a3;
  background-color: #e0e7ff;
  border-color: #a5b4fc;
}
.custom-export-btn.is-disabled {
  opacity: 0.5;
  color: #818cf8;
  background-color: #eef2ff;
  border-color: #c7d2fe;
}

.analysis-form {
  padding-top: 4px;
}

.section-mini-desc {
  margin-bottom: 12px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.condition-item + .condition-item {
  margin-top: 8px;
}

.condition-delete-col {
  display: flex;
  justify-content: flex-end;
  align-items: center;
}

.condition-row {
  margin-left: 0 !important;
  margin-right: 0 !important;
}

.depth-slider {
  margin: 0 10px;
}

.range-text {
  margin-top: 6px;
  text-align: center;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.action-buttons {
  margin-top: 24px;
}

.full-width,
.full-width-btn {
  width: 100%;
}

.mt-10 {
  margin-top: 15px;
  margin-left: 0 !important;
}

.chart-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.chart-panel :deep(.el-card__body) {
  flex: 1;
  padding: 12px;
  display: flex;
  flex-direction: column;
}

.chart-panel__body {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.skeleton-container {
  flex: 1;
  padding: 30px;
}

.loading-text {
  margin-top: 20px;
  text-align: center;
  color: #909399;
}

.chart-empty-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.chart-stage {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.echarts-container {
  flex: 1;
  width: 100%;
  min-height: 0;
}

.chart-hint {
  margin-top: 12px;
  text-align: center;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding-right: 20px;
}

.dialog-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

@media (max-width: 1200px) {
  .custom-panel,
  .chart-panel {
    height: auto;
    min-height: 0;
  }
}

@media (max-width: 768px) {
  .chart-header,
  .dialog-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .condition-delete-col {
    justify-content: flex-start;
  }

  .echarts-container {
    min-height: 420px;
  }
}
</style>
