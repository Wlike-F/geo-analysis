# 测井曲线可视化与异常测段集成方案设计

## 1. 核心目标与需求背景
目前“曲线可视化”模块仅实现了单纯的静态绘图功能，通道写死缺乏灵活性。为了将“绘图”与“异常分析”深度结合，本次优化的核心目标是：实现**通道数据的动态拾取**，以及在测井深度的视图中**直观地用高亮色块标识出异常地层**，辅助地质人员快速定性与定量分析。

---

## 2. 界面与交互优化设计 (UI/UX)

原有界面布局分为“左侧控制区”和“右侧图表区”，我们将基于此结构进行升级扩展：

### 2.1 左侧分析控制面板
1. **新增交互入口**：在“加载并绘制”的按钮上方，紧贴着安排一个 `el-button`（按钮名：“查看测井异常段”），作为唤起详细数据表格的入口。
2. **曲线显示通道拾取**：
   - 彻底废弃前端写死通道的编码方式。
   - 读取被选中文件对象中定义的 `columnsJson` (排除 DEPTH / 深度 字段)。
   - 提供一个 `<el-select multiple :multiple-limit="3">` 下拉多选控件。限制用户自主最多选取 **3个** 数据通道用于绘图显示。
3. **异常测段深度区间筛选**：
   - 提供表单机制，支持用户定义“感兴趣的异常区间”。
   - 增加动态表单组（通过“新增区间”按钮），允许用户输入多段想要限制的深度（如 `[1000m - 1200m]`, `[1500m - 1600m]` ）。
   
### 2.2 "查看测井异常段" 弹窗 (Dialog)
- 点击左侧按钮后，页面中心弹出 `<el-dialog>`。
- 弹窗内渲染一张类似 `extract` 界面的数据表 (`<el-table>`)。
- 直接展示根据所选数据和深度区间抓取出的**异常段合并明细**（包含：顶深、底深、异常厚度、极值/均值 等）。

### 2.3 右侧图表区展示视图 (ECharts)
- **多轨道自适应网格**：根据用户选择的1至3个通道，动态计算画布网栏（Grid），并分配并行的 X 轴。
- **异常地层标识 (`markArea`)**：这是最核心的视觉增强机制。将后端拿到的（或前端计算出的）异常测段顶底深度，映射到纵向 Y 轴中。
- 呈现效果：在对应的异常深度上横跨出带有半透明底色（如浅红或浅黄色）的区块，当用户滚动纵向深度轴时，一目了然就能看到哪里是“异常重灾区”。

---

## 3. 核心技术实现路径

### 3.1 数据通道多选响应
由目前的静态字段改为解析数据库中存的 `columnsJson`：
```javascript
// 获取到文件信息后
const availableChannels = JSON.parse(fileMeta.columnsJson || '[]').filter(c => !c.includes('深') && c.toUpperCase() !== 'DEPTH');
const selectedChannels = ref([]); // template中采用 multiple-limit="3"
```

### 3.2 异常多维度区间控制器
```vue
<div v-for="(range, idx) in depthFilters" :key="idx" class="range-row">
  <el-input-number v-model="range.min" placeholder="起始深度" />
  <span>至</span>
  <el-input-number v-model="range.max" placeholder="结束深度" />
  <el-button icon="Delete" circle @click="removeRange(idx)" />
</div>
<el-button plain type="primary" @click="addRange">增加深度区间</el-button>
```
在此基础上，在提取异常和绘制图表之前，先用这套 `depthFilters` 作为阻断过滤器，如果数据行的深度不在区间内，则忽略该异常段。

### 3.3 核心图表逻辑：高亮色块注入 (`markArea`)
通过 ECharts 提供的 `markArea` 组件跨越坐标系提供异常基底标绘：
```javascript
// 1. 将计算出来的 segments 转换为 ECharts 支持的矩形坐标区域格式
const anomalyMarkArea = {
  itemStyle: { color: 'rgba(255, 73, 73, 0.15)' }, // 半透明警戒红
  data: segments.map(seg => [
    { yAxis: seg.startDepth },
    { yAxis: seg.endDepth }
  ])
};

// 2. 在渲染动态选择的 3 条测井曲线系列时，将色块挂载上去
const echartsSeries = selectedChannels.value.map((colName, index) => {
  return {
    name: colName,
    type: 'line',
    xAxisIndex: index, // 按选择顺序挂载不同的X轴
    yAxisIndex: 0,     // 共享唯一向下深度的Y轴
    data: chartData[colName],
    // 给第一个系列的背后挂一个异常高亮背景即可在全图生效
    markArea: index === 0 ? anomalyMarkArea : undefined 
  };
});
```

---

## 4. 落地实施步骤 (ToDo)

- [ ] **步骤一**：修改 `visualization/index.vue` 的界面左侧，加入多区间数组的状态变量和三通道联动限制的选择器。
- [ ] **步骤二**：抽象和复用“连续异常测段”分析的 JS 逻辑方法，通过用户的区间筛选和条件触发算出正确的 `anomalySegments` 数组。
- [ ] **步骤三**：新增 `el-dialog`，在用户点击 “查看测井异常段” 时，传递 `anomalySegments` 显示数据统计表。
- [ ] **步骤四**：重构 ECharts 的 `initChart` 和 `updateChart` 逻辑：使其从固定的 Series 升级为遍历的配置形态，并引入 `markArea`。
- [ ] **步骤五**：样式微调联动。深度 Y 轴保证平滑支持鼠标漫游缩放，图表颜色背景跟随主题走，交互体验拉满。