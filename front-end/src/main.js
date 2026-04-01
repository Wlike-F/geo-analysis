import { createApp } from 'vue'
import { createPinia } from 'pinia'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './assets/css/theme.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)

// 注册所有 Element Plus Element 图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router) 
app.use(ElementPlus, { size: 'default', zIndex: 3000 })

app.mount('#app')