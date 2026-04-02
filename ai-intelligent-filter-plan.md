# AI智能筛选提取测井异常数据 - 技术实现方案

## 1. 需求概述

在系统左侧菜单栏新增【AI智能筛选提取】模块。该模块将无缝集成阿里云通义千问大模型（Tongyi LLM），提供一个交互式的聊天界面。用户可以通过自然语言上传测井TXT文件并输入筛选标准（如：“帮我提取深度在1000-1200米之间，且伽马(GR)大于75的所有异常数据”），由大模型自动理解需求、执行测井系统的核心过滤进程，最终为用户生成可直接下载的标准化Excel报表。

## 2. 核心挑战与架构设计

**核心挑战**：测井TXT文件通常数据量极大（几万到几百万行），受限于大模型的上下文Token长度限制和API计费，**绝不能**把整个TXT文件原文直接发给大模型。
**解决方案（Logic-Generation 模式）**：
1. **解析特征**：后端解析TXT文件的前几行（提取出表头字段列名和几行样本数据）。
2. **规则生成**：将表头字段、样本数据以及用户的“自然语言筛选要求”发送给通义千问，让其返回**结构化的过滤规则（JSON格式）**或**表达式**。
3. **本地执行**：后端接收到JSON过滤规则后，在本地Java代码中利用现有的测井提取逻辑（Steam流或规则引擎）对完整内存数据进行筛选。
4. **生成产物**：将筛选后的结果集重用现存的 `EasyExcel` 逻辑生成Excel，并返回下载链接给前端。

---

## 3. 前端实现方案 (Vue 3 + Pinia)

### 3.1 路由与菜单配置
在 `router/index.js` 中新增路由节点，使左侧菜单栏可以渲染：
```javascript
{
  path: '/ai-filter',
  name: 'AIFilter',
  component: () => import('@/views/ai-filter/index.vue'),
  meta: { title: 'AI智能筛选提取', icon: 'eleme' } // 自定义图标
}
```

### 3.2 聊天与交互界面 (`views/ai-filter/index.vue`)
UI 布局分为左、右两部分或采取类似 ChatGPT 的沉浸式对话框：
- **聊天记录区 (Chat History)**：展示用户指令与AI回复（支持 Markdown 渲染和表格预览）。
- **附件列表区 (Attachment)**：显示当前对话上下文中上传的测井 TXT 文件。
- **输入控制区 (Input Box)**：
  - `ElUpload` 按钮用于上传TXT文件。
  - `ElInput` 文本域用于输入对话和筛选提示词。
  - 发送按钮。

**交互逻辑**：
1. 用户上传文件：调用 `POST /api/file/upload`（复用现有接口），返回文件解析的 `WellLogFileDTO`。
2. 用户输入筛选提示词并发送。
3. 前端构造消息对象，将“当前关联的文件名/列名”加上“自然语言指令”发往后端的跨域 AI 聊天接口。
4. AI处理完毕，返回消息包含大模型回复文本、预览数据以及下载 Excel 的 URI。
5. 前端展示回复，提供【生成并下载 Excel】按钮。

### 3.3 API 接口定义 (`api/ai.js`)
```javascript
import request from '@/utils/request'

export function sendAiFilterRequest(data) {
  // data: { fileTitle: 'LT2.txt', columns: ['TVD', 'GR', 'RT'], prompt: 'GR大于70的数据' }
  return request({
    url: '/ai/chat-filter',
    method: 'post',
    data: data
  })
}
```

---

## 4. 后端实现方案 (Spring Boot + Tongyi API)

### 4.1 引入依赖与配置
如果使用阿里云百炼平台（大模型服务），可通过 HTTP Client 或通义千问官方 SDK 接入：
- `application.yml` 中配置 DashScope API-KEY：
  ```yaml
  ai:
    tongyi:
      api-key: your_dashscope_api_key_here
      model: qwen-turbo
  ```

### 4.2 提示词工程 (Prompt Engineering) [核心]
需要在后端预制一个强大的 System Prompt，约束大模型的输出格式。

**System Prompt 示例**：
> "你是一个专业的石油地质测井数据分析助手。用户会提供当前测井数据的列名（如深度TVD、自然伽马GR、电阻率RT等）以及他们的过滤需求。你需要将用户的自然语言需求翻译成如下严格的JSON数组格式，**不要输出任何其他解释性文字**。"
> [
>   {"column": "GR", "operator": ">", "value": 75},
>   {"column": "TVD", "operator": "BETWEEN", "value": [1000, 1200]}
> ]

### 4.3 核心业务逻辑实现 (`AIFilterService.java`)
```java
@Service
public class AIFilterService {
    @Value("${ai.tongyi.api-key}")
    private String apiKey;

    public Result<AiFilterResponseDTO> processChat(AiFilterRequestDTO request) {
        // 1. 获取目标文件的解析缓存结构（可以存在Redis或内存）
        WellLogFileDTO fileData = fileCache.get(request.getFileTitle());
        
        // 2. 组装发给大模型的 Prompt（仅含列名和用户提示，不含几万行原数据）
        String prompt = buildPrompt(fileData.getColumns(), request.getPrompt());
        
        // 3. 调用通义千问 API 获得 JSON 过滤规则
        String llmJsonResult = callTongyiApi(prompt);
        
        // 4. 解析大模型返回的 JSON 为 Java 过滤对象池
        List<FilterRule> rules = JSON.parseArray(llmJsonResult, FilterRule.class);
        
        // 5. 本地执行数据过滤：遍历 fileData.getData()，应用所得到的 rules
        List<Map<String, Object>> filteredData = applyRules(fileData.getData(), rules);
        
        // 6. 构造返回报文 (包含 AI 回复的话术，预览的前10行数据，以及可以直接用于导出 Excel 的临时标识)
        AiFilterResponseDTO response = new AiFilterResponseDTO();
        response.setAiMessage("已经为您成功过滤，共提取到 " + filteredData.size() + " 条异常数据。");
        response.setPreviewData(filteredData.subList(0, Math.min(10, filteredData.size())));
        response.setFilteredDataId(cacheFilteredResultForExport(filteredData)); // 缓存结果集供下载使用
        
        return Result.success(response);
    }
}
```

### 4.4 导出集成
当用户在对话框中点击【下载Excel】时，凭借步骤 4.3 中返回的 `filteredDataId` 给后端：
```java
@GetMapping("/download/{filteredDataId}")
public void downloadAiResult(@PathVariable String filteredDataId, HttpServletResponse response) {
    List<WellLogDataDTO> data = cache.get(filteredDataId);
    // 复用 FileController 的 Excel 导出逻辑
    ExcelUtil.export(response, data, "AI筛选异常数据.xlsx");
}
```

## 5. 项目实施步骤计划

1. **环境准备 (1天)**：注册阿里云DashScope账号，获取API Key，后端集成SDK并打通最基础的文本请求。
2. **AI对话UI开发 (2天)**：前端开发类Chat界面的新Vue组件，对接系统的原有TXT上传组件。
3. **Prompt提取器开发 (2天)**：后端核心引擎编写，测试大模型对含糊自然语言（如：“深度的数值高于100”）生成精准规则对象的稳定性。
4. **规则执行器与Excel导出 (1天)**：后端根据解析出来的动态条件去真实筛选TXT产生的DataList，对接 EasyExcel 输出流。
5. **联调优化 (1天)**：处理异常流（如AI回复格式错误时的重试或兜底返回）、上下文连续多轮对话支持。

## 6. 后续可延展点
- **数据可视化**：大模型除了返回筛选列表，还能返回 Echarts 画曲线图所需的配置项 JSON，前端可直接根据结果画出曲线。
- **动态列猜测**：即便测井TXT的表头是纯英文简称（如 SP, AC），大模型可以猜测出中文含义进行辅助沟通。