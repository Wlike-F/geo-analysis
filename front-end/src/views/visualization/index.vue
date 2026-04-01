<template>
  <div class="visualization-container">
    <el-row :gutter="20" class="full-height">
      <!-- 左侧控制面板 -->
      <el-col :span="6">
        <el-card class="control-panel" shadow="hover">
          <template #header>
            <div class="panel-header">
              <el-icon><Setting /></el-icon>
              <span>图表分析与异常标定</span>
            </div>
          </template>

          <el-form label-position="top" size="small">
            <el-form-item label="数据源文件">
              <el-select v-model="selectedFile" placeholder="请选择测井文件" style="width: 100%" @change="handleFileChange">
                <el-option
                  v-for="file in fileList"
                  :key="file.id"
                  :label="file.fileName"
                  :value="file.id"
                />
              </el-select>
            </el-form-item>

            <el-divider />

            <el-form-item label="曲线显示通道 (最多选3个)">
              <el-select v-model="selectedChannels" multiple :multiple-limit="3" placeholder="请选择绘图通道" style="width: 100%" @change="handleChannelChange">
                <el-option
                  v-for="col in availableChannels"
                  :key="col"
                  :label="col"
                  :value="col"
                />
              </el-select>
            </el-form-item>

            <el-divider />
            
            <div class="anomaly-controls">
                <div style="margin-bottom: 8px; font-weight: bold; font-size: 13px;">异常识别条件筛选</div>
                <div v-for="(cond, idx) in anomalyConditions" :key="idx" style="margin-bottom: 8px;">
                    <el-row :gutter="5">
                        <el-col :span="8">
                            <el-select v-model="cond.channel" placeholder="特征通道" size="small">
                                <el-option v-for="col in availableChannels" :key="col" :label="col" :value="col" />
                            </el-select>
                        </el-col>
                        <el-col :span="7">
                            <el-select v-model="cond.operator" placeholder="条件" size="small">
                                <el-option label="大于 (>)" value=">" />
                                <el-option label="小于 (<)" value="<" />
                            </el-select>
                        </el-col>
                        <el-col :span="6">
                            <el-input-number v-model="cond.threshold" :controls="false" placeholder="阈值" style="width:100%" size="small" />
                        </el-col>
                        <el-col :span="3" style="text-align:right">
                            <el-button type="danger" icon="Delete" circle size="small" @click="removeCondition(idx)" v-if="anomalyConditions.length > 1" />
                        </el-col>
                    </el-row>
                </div>
                <el-button plain type="primary" size="small" style="width: 100%" @click="addCondition">
                    <el-icon><Plus /></el-icon> 新增条件
                </el-button>
            </div>

            <el-divider />

            <el-form-item label="深度区间控制 (m)">
              <el-slider
                v-model="depthRange"
                range
                :min="minDepth"
                :max="maxDepth"
                :step="10"
                style="margin: 0 10px;"
                @change="updateDepthZoom"
              />
              <div class="range-text" v-if="maxDepth > 0">{{ depthRange[0] }}m - {{ depthRange[1] }}m</div>
              <div class="range-text" v-else>暂无数据深度信息</div>
            </el-form-item>

            <el-divider />

            <el-form-item class="action-buttons">
              <el-button type="warning" class="full-width-btn" @click="showAnomalyDialog = true" :disabled="anomalySegments.length === 0">
                <el-icon><List /></el-icon> 查看测井异常段明细 ({{anomalySegments.length}})
              </el-button>
              <el-button type="primary" class="full-width-btn mt-10" @click="handleDraw" :disabled="!selectedFile">
                <el-icon><MagicStick /></el-icon> 执行分析与渲染
              </el-button>
              <el-button class="full-width-btn mt-10" @click="handleExport" :disabled="!selectedFile || loading">
                <el-icon><Download /></el-icon> 导出高清图片
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- 右侧图表区 -->
      <el-col :span="18">
        <el-card class="chart-panel" shadow="hover">
          <template #header>
            <div class="panel-header" style="justify-content: space-between">
              <div style="display:flex; align-items:center; gap: 8px;">
                <el-icon><DataAnalysis /></el-icon>
                <span>多道测井异常标定交汇图</span>
              </div>
              <el-tag type="danger" size="small" effect="light" round v-if="anomalySegments.length">已发现 {{anomalySegments.length}} 处异常地层</el-tag>
              <el-tag type="info" size="small" effect="plain" round v-else>支持异常段高亮映射</el-tag>
            </div>
          </template>

          <div v-if="loading" class="skeleton-container">
            <el-skeleton :rows="15" animated />
            <div style="text-align:center; color:#909399; margin-top:20px;">正在对数据流进行清洗与区间匹配，请稍候...</div>
          </div>
          <div v-show="!loading" ref="chartRef" class="echarts-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 异常明细弹窗 -->
    <el-dialog v-model="showAnomalyDialog" width="60%" destroy-on-close>
        <template #header>
            <div style="display:flex; justify-content:space-between; align-items:center; padding-right:20px;">
                <span style="font-size:16px; font-weight:bold;">提取到的异常测段明细</span>
                <el-button type="primary" size="small" @click="exportAnomalyData">
                    <el-icon><Download /></el-icon> 导出CSV
                </el-button>
            </div>
        </template>
        <el-table :data="anomalySegments" border stripe height="400">
            <el-table-column type="index" label="序号" width="60" align="center" />
            <el-table-column prop="startDepth" label="顶界深度(m)" align="center" />
            <el-table-column prop="endDepth" label="底界深度(m)" align="center" />
            <el-table-column label="厚度(m)" align="center">
                <template #default="scope">
                    <el-tag type="success">{{ (scope.row.endDepth - scope.row.startDepth).toFixed(2) }}</el-tag>
                </template>
            </el-table-column>
            <el-table-column prop="maxVal" label="主特征极大值" align="center" />
            <el-table-column prop="avgVal" label="主特征平均值" align="center" />
        </el-table>
        <template #footer>
            <el-button @click="showAnomalyDialog = false">关闭</el-button>
        </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, shallowRef, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import { Setting, MagicStick, Download, DataAnalysis, Plus, List, Delete } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'

// 状态管理
const fileList = ref([])
const selectedFile = ref(null)

const minDepth = ref(0)
const maxDepth = ref(3000)
const depthRange = ref([0, 3000])

const availableChannels = ref([])
const selectedChannels = ref([])

// 异常判定条件 (支持多个)
const anomalyConditions = ref([
    { channel: '', operator: '>', threshold: 0 }
])

const anomalySegments = ref([])
const showAnomalyDialog = ref(false)

const loading = ref(false)

const chartRef = ref(null)
const chartInstance = shallowRef(null)

// 拿到的大盘数据
const rawChartData = shallowRef({})

// 获文件列表
const fetchFileList = async () => {
  try {
    const data = await request.get('/file/list')
    if (data && Array.isArray(data)) {
      fileList.value = data
      if (fileList.value.length > 0) {
        selectedFile.value = fileList.value[0].id
        handleFileChange()
      }
      initChart()
    } else {
      initChart()
    }
  } catch (error) {
    ElMessage.error('无法连接后端获取文件列表')
    initChart()
  }
}

// 切换文件，重置绘图
const handleFileChange = () => {
  rawChartData.value = {}
  availableChannels.value = []
  selectedChannels.value = []
  anomalySegments.value = []
  anomalyConditions.value = [{ channel: '', operator: '>', threshold: 0 }]
  
  const fileMeta = fileList.value.find(f => f.id === selectedFile.value)
  if (fileMeta && fileMeta.columnsJson) {
      try {
          const cols = JSON.parse(fileMeta.columnsJson)
          // 排除包含深度的列
          availableChannels.value = cols.filter(c => !c.includes('深') && c.toUpperCase() !== 'DEPTH')
          // 默认选中前三个
          if (availableChannels.value.length > 0) {
              selectedChannels.value = availableChannels.value.slice(0, 3)
              anomalyConditions.value[0].channel = selectedChannels.value[0]
          }
      } catch(e) {}
  }

  if (chartInstance.value) {
    chartInstance.value.clear()
    initChart()
  }
}

const handleChannelChange = () => {
    if (Object.keys(rawChartData.value).length > 0) {
        updateChartData()
    }
}

// 条件操作
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

const updateDepthZoom = () => {
  if (chartInstance.value && rawChartData.value.DEPTH && rawChartData.value.DEPTH.length > 0) {
    chartInstance.value.dispatchAction({
      type: 'dataZoom',
      startValue: depthRange.value[0],
      endValue: depthRange.value[1]
    })
  }
}

// 提取异常分段算法 (支持多条件 &&)
const extractAnomalySegments = () => {
    anomalySegments.value = []
    if (!rawChartData.value || !rawChartData.value.DEPTH || rawChartData.value.DEPTH.length === 0) return
    
    const depths = rawChartData.value.DEPTH
    
    // 映射当前所有的有效条件及其对应的数据集
    const conditionsData = anomalyConditions.value.map(cond => {
        if (!cond.channel) return null
        let tKey = Object.keys(rawChartData.value).find(k => k.toUpperCase() === cond.channel.toUpperCase())
        return {
            dataArr: tKey ? rawChartData.value[tKey] : null,
            operator: cond.operator,
            threshold: cond.threshold || 0
        }
    }).filter(c => c && c.dataArr)

    if (conditionsData.length === 0) return
    
    let isAnomaly = false
    let currentSegment = null
    let maxVal = -Infinity
    let sumVal = 0
    let countVal = 0

    for (let i = 0; i < depths.length; i++) {
        const d = parseFloat(depths[i])
        
        // 过滤深度空值范围
        if (isNaN(d)) continue

        // 检查深度区间范围限制
        let inRange = (d >= depthRange.value[0] && d <= depthRange.value[1])
        
        let meetsAllConditions = inRange
        let primaryVal = 0 // 用于记录最主要(第一个)通道的指标极值

        if (inRange) {
            for (let j = 0; j < conditionsData.length; j++) {
                const cData = conditionsData[j]
                const v = parseFloat(cData.dataArr[i])
                if (isNaN(v)) {
                    meetsAllConditions = false
                    break
                }
                
                if (j === 0) primaryVal = v

                if (cData.operator === '>') {
                    if (!(v > cData.threshold)) {
                        meetsAllConditions = false
                        break
                    }
                } else {
                    if (!(v < cData.threshold)) {
                        meetsAllConditions = false
                        break
                    }
                }
            }
        }

        if (meetsAllConditions) {
            if (!isAnomaly) {
                isAnomaly = true
                currentSegment = { startDepth: d, endDepth: d }
                maxVal = primaryVal
                sumVal = primaryVal
                countVal = 1
            } else {
                if (primaryVal > maxVal) maxVal = primaryVal
                sumVal += primaryVal
                countVal++
            }
        } else {
            if (isAnomaly) {
                currentSegment.endDepth = depths[i - 1 < 0 ? 0 : i - 1]
                currentSegment.maxVal = maxVal.toFixed(2)
                currentSegment.avgVal = (sumVal / countVal).toFixed(2)
                anomalySegments.value.push(currentSegment)
                isAnomaly = false
            }
        }
    }
    
    // 收尾
    if (isAnomaly) {
        currentSegment.endDepth = depths[depths.length - 1]
        currentSegment.maxVal = maxVal.toFixed(2)
        currentSegment.avgVal = (sumVal / countVal).toFixed(2)
        anomalySegments.value.push(currentSegment)
    }
}

// 模拟绘制动作
const handleDraw = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择测井文件')
    return
  }
  if (selectedChannels.value.length === 0) {
    ElMessage.warning('请至少选择1个显示通道')
    return
  }

  loading.value = true
  try {
    const data = await request.get(`/data/echarts/${selectedFile.value}`)
    if (data) {
      rawChartData.value = data
      
      const keys = Object.keys(data)
      const depthKey = keys.find(k => k.toUpperCase() === 'DEPTH') || 'DEPTH'
      const depths = data[depthKey] || []
      
      // 映射到标准格式供后续提取使用
      rawChartData.value.DEPTH = depths

      if (depths.length > 0) {
         minDepth.value = Math.floor(Math.min(...depths))
         maxDepth.value = Math.ceil(Math.max(...depths))
         // 重置缩放
         depthRange.value = [minDepth.value, maxDepth.value]
      }
      
      // 提取异常数据
      extractAnomalySegments()

      ElMessage.success(`加载成功！发现 ${anomalySegments.value.length} 处异常。`)
      nextTick(() => {
        updateChartData()
        updateDepthZoom()
      })
    }
  } catch (error) {
    ElMessage.error('获取测井数据失败')
  } finally {
    loading.value = false
  }
}

// 导出 CSV 功能
const exportAnomalyData = () => {
    if (anomalySegments.value.length === 0) {
        ElMessage.warning('暂无异常数据可导出')
        return
    }
    let csvContent = '\uFEFF' // 增加 BOM 防止中文乱码
    csvContent += '序号,顶界深度(m),底界深度(m),厚度(m),主特征极值,主特征均值\n'
    anomalySegments.value.forEach((row, idx) => {
        const thickness = (row.endDepth - row.startDepth).toFixed(2)
        csvContent += `${idx + 1},${row.startDepth},${row.endDepth},${thickness},${row.maxVal},${row.avgVal}\n`
    })
    
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = `异常测段分析结果_${selectedFile.value}.csv`
    link.click()
    URL.revokeObjectURL(link.href)
}

// 导出系统截图功能
const handleExport = () => {
  if (!chartInstance.value) return
  const url = chartInstance.value.getDataURL({
    type: 'png',
    pixelRatio: 2,
    backgroundColor: '#fff'
  })
  const link = document.createElement('a')
  link.download = `测井多道异常标定_${selectedFile.value}.png`
  link.href = url
  link.click()
}

// 初始化 ECharts 骨架
const initChart = () => {
  if (!chartRef.value) return
  if (!chartInstance.value) {
    chartInstance.value = echarts.init(chartRef.value)
  }

  const option = {
    title: {
        text: '配置左侧条件后，点击 [执行分析与渲染]',
        left: 'center',
        top: 'middle',
        textStyle: { color: '#ccc', fontWeight: 'normal', fontSize: 16 }
    },
    tooltip: { trigger: 'none' },
    grid: [
      { left: '10%', right: '10%', top: '60px', bottom: '20px' }
    ],
    xAxis: [
      { type: 'value', position: 'top', name: 'Wait...', nameLocation: 'middle', nameGap: 30 }
    ],
    yAxis: [
      { type: 'value', inverse: true, name: 'Depth(m)', nameLocation: 'start' }
    ],
    series: []
  }
  chartInstance.value.setOption(option, true)
}

const updateChartData = () => {
    if (!chartInstance.value) return
    
    const depth = rawChartData.value.DEPTH
    if (!depth || depth.length === 0) return

    const cCount = selectedChannels.value.length
    
    const grids = []
    const xAxes = []
    const yAxes = []
    const series = []
    
    const trackColors = ['#e67c22', '#e74c3c', '#3498db']
    
    // 生成 MarkArea 数据, 更显眼的标识
    const anomalyMarkArea = {
        itemStyle: { 
            color: 'rgba(255, 73, 73, 0.35)', // 更深沉的亮红底色
            borderWidth: 1, 
            borderColor: 'rgba(255, 0, 0, 0.8)', // 外边框让分界线更清晰
            borderType: 'dashed' 
        },
        data: anomalySegments.value.map(seg => [
            { yAxis: seg.startDepth },
            { yAxis: seg.endDepth }
        ])
    }

    const gridWidth = Math.floor(80 / cCount)
    let leftOffset = 8
    
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
            axisLabel: { color: trackColors[index % 3], formatter: (val) => Number.isInteger(val) ? val : String(val).slice(0, 5) },
            splitLine: { show: false }
        })
        
        yAxes.push({
            gridIndex: index,
            type: 'value',
            inverse: true,
            min: 'dataMin',
            max: 'dataMax',
            name: index === 0 ? 'Depth(m)' : '',
            axisLabel: { show: index === 0 },
            axisTick: { show: index === 0 }
        })
        
        // 数据适配
        let key = Object.keys(rawChartData.value).find(k => k.toUpperCase() === colName.toUpperCase())
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
            // 每一个列都统一挂载高亮标识，贯穿全视窗
            markArea: anomalyMarkArea
        })
        
        leftOffset += gridWidth + (index < cCount - 1 ? 2 : 0)
    })

    const option = {
        title: { text: '' },
        grid: grids,
        tooltip: {
            trigger: 'axis',
            axisPointer: { type: 'cross', animation: false },
            formatter: function (params) {
                if (!params || !params.length) return '';
                let res = `<strong>深度: ${params[0].value[1]} m</strong><br/>`
                params.forEach(item => {
                    if(item.value[0] !== undefined && item.value[0] !== null) {
                        res += `${item.marker} ${item.seriesName}: ${item.value[0]}<br/>`
                    }
                })
                return res
            }
        },
        dataZoom: [
             {
                 type: 'inside',
                 xAxisIndex: null, // X轴不缩放
                 yAxisIndex: grids.map((g, i) => i) // 同步缩放所有轨道的 Y 轴
             }
        ],
        axisPointer: {
             link: [{ yAxisIndex: 'all' }]
        },
        xAxis: xAxes,
        yAxis: yAxes,
        series: series
    };

    chartInstance.value.setOption(option, true);
    
    // 同步双向数据流 - 监听滚动时更新侧边栏滑动条 (debounce保护)
    chartInstance.value.off('datazoom');
    chartInstance.value.on('datazoom', debounce(() => {
        if (!chartInstance.value) return;
        const opt = chartInstance.value.getOption();
        if (opt && opt.dataZoom && opt.dataZoom.length > 0) {
            const dz = opt.dataZoom[0];
            let sVal, eVal;
            if (dz.startValue !== undefined && dz.endValue !== undefined && !isNaN(dz.startValue)) {
                sVal = dz.startValue;
                eVal = dz.endValue;
            } else if (dz.start !== undefined && dz.end !== undefined) {        
                const totalRange = maxDepth.value - minDepth.value;
                sVal = minDepth.value + totalRange * (dz.start / 100);
                eVal = minDepth.value + totalRange * (dz.end / 100);
            }
            if (sVal !== undefined && eVal !== undefined) {
                depthRange.value = [
                    Math.floor(Math.min(sVal, eVal)),
                    Math.ceil(Math.max(sVal, eVal))
                ];
            }
        }
    }, 100))
}

onMounted(() => {
  fetchFileList()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  if (chartInstance.value) {
    chartInstance.value.dispose()
    chartInstance.value = null
  }
})

const debounce = (fn, delay) => {
  let timer = null
  return function (...args) {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      fn.apply(this, args)
    }, delay)
  }
}

const handleResize = debounce(() => {
  if (chartInstance.value) {
    chartInstance.value.resize()
  }
}, 200)
</script>

<style scoped>
.visualization-container {
  padding: 20px;
  height: 100%;
}
.full-height {
  height: 100%;
}
.panel-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  font-size: 16px;
}
.control-panel {
  height: calc(100vh - 100px);
  overflow-y: auto;
  border-radius: 8px;
}
.chart-panel {
  height: calc(100vh - 100px);
  display: flex;
  flex-direction: column;
  border-radius: 8px;
}
.chart-panel :deep(.el-card__body) {
  flex: 1;
  padding: 10px;
  display: flex;
  flex-direction: column;
}
.range-row {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}
.anomaly-controls {
  padding: 0px 5px;
}
.action-buttons {
  margin-top: 20px;
}
.full-width-btn {
  width: 100%;
}
.mt-10 {
  margin-top: 15px;
  margin-left: 0 !important;
}
.skeleton-container {
  flex: 1;
  padding: 30px;
}
.echarts-container {
  flex: 1;
  width: 100%;
  min-height: 500px;
}
.range-text {
  text-align: center;
  width: 100%;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  margin-top: 5px;
}
</style>












