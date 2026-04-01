<template>
  <div class="instructions-container">
    <el-card class="box-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="title">
            <el-icon class="title-icon"><Reading /></el-icon>
            系统使用说明
          </span>
          <span class="subtitle">操作指南与常见问题</span>
        </div>
      </template>

      <!-- 骨架屏加载 -->
      <el-skeleton :rows="5" animated v-if="loading" />

      <!-- 折叠面板 (抽屉折叠样式) -->
      <el-collapse v-model="activeNames" v-else class="custom-collapse">
        <el-collapse-item 
          v-for="(item, index) in instructionsList" 
          :key="item.id || index" 
          :name="index"
        >
          <template #title>
            <div class="collapse-title">
              <el-icon class="item-icon" v-if="item.icon">
                <component :is="iconMap[item.icon] || item.icon" />
              </el-icon>
              {{ item.title }}
            </div>
          </template>
          <div class="collapse-content" v-html="item.content"></div>
        </el-collapse-item>
      </el-collapse>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '@/utils/request'
import { Reading, PriceTag, Lock, Bell, DocumentCopy } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const loading = ref(true)
const activeNames = ref([0]) // 默认展开第一项
const instructionsList = ref([])

// 预定义图标组件映射表，解决从后端获取字符串无法直接渲染动态组件的问题
const iconMap = {
  Reading,
  PriceTag,
  Lock,
  Bell,
  Lightbulb: Bell, // 兼容后端数据库里的 Lightbulb 字符串映射到 Bell 组件
  DocumentCopy
}

// 备用静态数据，以防后端尚未重启编译导致接口无法访问
const fallbackData = [
  {
    id: 1,
    title: '系统概览与核心能力',
    icon: 'PriceTag',
    content: '<p>本系统提供专业的地层分析、多通道并发比对、AI智能提取等功能。</p>'
  },
  {
    id: 2,
    title: '测井数据看板使用提示',
    icon: 'Lock',
    content: '<p>在看板中您可以直观地浏览已上传的测井TXT文件数据。</p>'
  },
  {
    id: 3,
    title: '提示 1：极速并发加载',
    icon: 'Bell',
    content: '<p>当加载超过百万行的TXT文件时，系统会在后台处理。</p><p><img src="https://via.placeholder.com/600x200/efefef/333333?text=Concurrent+Loading" alt="并发加载示意图" style="max-width: 100%; border-radius: 8px;"></p>'
  },
  {
    id: 4,
    title: '提示 2：AI智能提取',
    icon: 'Bell',
    content: '<p>由于调用了阿里云通义千问大模型，自然语言筛选请尽量包含具体的数值条件，例如：深度大于1500，且温度介于50-80之间。</p><p><img src="https://via.placeholder.com/600x200/efefef/333333?text=AI+Query" alt="AI查询示意图" style="max-width: 100%; border-radius: 8px;"></p>'
  },
  {
    id: 5,
    title: '常见问题与解答',
    icon: 'DocumentCopy',
    content: '<div style="background-color: var(--el-color-info-light-9); padding: 15px; border-radius: 4px;"><p style="font-weight: bold; margin-bottom: 8px; color: var(--el-color-primary)">Q：导出Excel失败怎么办？</p><p style="margin-bottom: 0;">A：检查是否超出了Excel单表104万行的限制，如有需要请使用CSV格式。</p></div>'
  }
]

const fetchInstructions = async () => {
  try {
    const data = await request.get('/instruction/list')
    if (data && Array.isArray(data) && data.length > 0) {
      instructionsList.value = data
    } else {
      instructionsList.value = fallbackData
    }
  } catch (error) {
    console.warn('后端服务可能尚未重启，使用本地静态演示数据', error)
    instructionsList.value = fallbackData
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchInstructions()
})
</script>

<style scoped>
.instructions-container {
  padding: 20px;
  max-width: 1000px;
  margin: 0 auto;
}

.box-card {
  border-radius: 12px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.title {
  font-size: 20px;
  font-weight: 600;
  display: flex;
  align-items: center;
  color: var(--el-text-color-primary);
}

.title-icon {
  margin-right: 8px;
  font-size: 24px;
  color: var(--el-color-primary);
}

.subtitle {
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

/* 抽屉/折叠面板自定义样式 */
.custom-collapse {
  border-top: none;
  border-bottom: none;
}

.custom-collapse :deep(.el-collapse-item__header) {
  font-size: 16px;
  font-weight: 500;
  background-color: var(--el-fill-color-light);
  border-radius: 8px;
  margin-bottom: 12px;
  padding: 0 20px;
  border-bottom: none;
  transition: all 0.3s ease;
}

.custom-collapse :deep(.el-collapse-item__header:hover) {
  background-color: var(--el-fill-color);
}

.custom-collapse :deep(.el-collapse-item__header.is-active) {
  background-color: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  border-bottom-left-radius: 0;
  border-bottom-right-radius: 0;
  margin-bottom: 0;
}

.custom-collapse :deep(.el-collapse-item__wrap) {
  background-color: var(--el-bg-color);
  border-bottom: none;
  border-bottom-left-radius: 8px;
  border-bottom-right-radius: 8px;
  margin-bottom: 12px;
  border-left: 1px solid var(--el-color-primary-light-9);
  border-right: 1px solid var(--el-color-primary-light-9);
  border-bottom: 1px solid var(--el-color-primary-light-9);
}

.custom-collapse :deep(.el-collapse-item__content) {
  padding: 20px;
  font-size: 15px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
}

.collapse-title {
  display: flex;
  align-items: center;
}

.item-icon {
  margin-right: 10px;
  font-size: 18px;
}

.collapse-content {
  overflow: hidden;
}

.collapse-content :deep(p) {
  margin-top: 0;
  margin-bottom: 12px;
}

.collapse-content :deep(p:last-child) {
  margin-bottom: 0;
}

.collapse-content :deep(img) {
  display: block;
  margin: 15px 0;
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
}
</style>
