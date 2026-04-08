# 前后端接口对应关系与差异对比

本文档汇总了 `front-end` 和 `back-end` 中的接口对应关系，并指出了其中的差异和潜在的不一致问题。

## 环境基础配置

- **前端基础地址 (`baseURL`)**: `http://47.76.162.193/api` (配置于 `src/utils/request.js`)
- **后端上下文路径 (`context-path`)**: `/api` (配置于 `application.yml`)

这意味着前端代码中写的 `url: '/user/info'` 实际发出的请求为 `/api/user/info`，对应后端的 `http://localhost:8081/api/user/info`。

---

## 1. 认证相关 (Auth)

| 接口说明 | 前端请求路径 (代码中) | 后端接收路径 (Controller) | 请求方法 | 匹配状态 |
| :--- | :--- | :--- | :--- | :--- |
| 用户登录 | `/auth/login` | `@RequestMapping("/auth")` + `@PostMapping("/login")` | POST | :white_check_mark: 完全匹配 |
| 用户注册 | `/auth/register` | `@RequestMapping("/auth")` + `@PostMapping("/register")` | POST | :white_check_mark: 完全匹配 |

---

## 2. 仪表盘统计 (Dashboard)

| 接口说明 | 前端请求路径 (代码中) | 后端接收路径 (Controller) | 请求方法 | 匹配状态 |
| :--- | :--- | :--- | :--- | :--- |
| 获取统计数据 | `/dashboard/stats` | `@RequestMapping("/dashboard")` + `@GetMapping("/stats")` | GET | :white_check_mark: 完全匹配 |

---

## 3. 文件管理与导出 (File & Data)

| 接口说明 | 前端请求路径 (代码中) | 后端接收路径 (Controller) | 请求方法 | 匹配状态 |
| :--- | :--- | :--- | :--- | :--- |
| 文本文件上传 | `/file/upload` | `@RequestMapping("/file")` + `@PostMapping("/upload")` | POST | :white_check_mark: 完全匹配 |
| 扫描服务器路径 | `/file/scan` | `@RequestMapping("/file")` + `@GetMapping("/scan")` | GET | :white_check_mark: 完全匹配 |
| 数据分页查询 | `/data/page` | `@RequestMapping("/data")` + `@PostMapping("/page")` | POST | :white_check_mark: 完全匹配 |
| 导出过滤后的Excel | `/data/{fileId}/export` | `@RequestMapping("/data")` + `@PostMapping("/{fileId}/export")` | POST | :white_check_mark: 完全匹配 |
| 导出Excel | `/file/export` | `@RequestMapping("/file")` + `@PostMapping("/export")` | POST | :white_check_mark: 完全匹配 |
| 批量导出Excel | `/file/export-batch` | `@RequestMapping("/file")` + `@PostMapping("/export-batch")` | POST | :white_check_mark: 完全匹配 |
| 获取文件列表(不分页) | `/file/list` | `@RequestMapping("/file")` + `@GetMapping("/list")` | GET | :white_check_mark: 完全匹配 |
| 获取文件列表(分页) | `/file/page` | `@RequestMapping("/file")` + `@GetMapping("/page")` | GET | :white_check_mark: 完全匹配 |
| 批量导出Excel(ZIP) | `/file/export-batch-zip` | `@RequestMapping("/file")` + `@PostMapping("/export-batch-zip")` | POST | :white_check_mark: 完全匹配 |
| 数据批量导出流(ZIP) | `/data/export-batch-zip` | `@RequestMapping("/data")` + `@PostMapping("/export-batch-zip")` | POST | :white_check_mark: 完全匹配 |
| 删除文件 | **前端无对应代码** | `@RequestMapping("/file")` + `@DeleteMapping("/{id}")` | DELETE | :warning: **前端缺失** |
| 清空文件 | **前端无对应代码** | `@RequestMapping("/file")` + `@DeleteMapping("/clear")` | DELETE | :warning: **前端缺失** |
| 获取Echarts数据 | **前端未实现/定义** | `@RequestMapping("/data")` + `@GetMapping("/echarts/{fileId}")` | GET | :warning: **前端缺失** |

---

## 4. 用户管理 (User)

| 接口说明 | 前端请求路径 (代码中) | 后端接收路径 (Controller) | 请求方法 | 匹配状态 |
| :--- | :--- | :--- | :--- | :--- |
| 获取个人信息 | `/user/info` | `@RequestMapping("/user")` + `@GetMapping("/info")` | GET | :white_check_mark: 完全匹配 |
| 更新个人配置 | `/user/updateProfile` | `@RequestMapping("/user")` + `@PostMapping("/updateProfile")` | POST | :white_check_mark: 完全匹配 |
| 更新密码 | `/user/updatePwd` | `@RequestMapping("/user")` + `@PostMapping("/updatePwd")` | POST | :white_check_mark: 完全匹配 |
| 用户分页查询 | `/user/page` | `@RequestMapping("/user")` + `@GetMapping("/page")` | GET | :white_check_mark: 完全匹配 |
| 添加用户 | `/user/add` | `@RequestMapping("/user")` + `@PostMapping("/add")` | POST | :white_check_mark: 完全匹配 |
| 修改用户 | `/user/update` | `@RequestMapping("/user")` + `@PostMapping("/update")` | POST | :white_check_mark: 完全匹配 |
| 删除用户 | `/user/delete/{id}` | `@RequestMapping("/user")` + `@DeleteMapping("/delete/{id}")` | DELETE | :white_check_mark: 完全匹配 |
| 更新设置 | `/user/updateSettings` | `@RequestMapping("/user")` + `@PostMapping("/updateSettings")` | POST | :white_check_mark: 完全匹配 |

---

## 5. 字段映射配置 (Column Mapping)

| 接口说明 | 前端请求路径 (代码中) | 后端接收路径 (Controller) | 请求方法 | 匹配状态 |
| :--- | :--- | :--- | :--- | :--- |
| 分页查询 | `/api/column-mapping/page` | `@RequestMapping("/api/column-mapping")` + `@GetMapping("/page")` | GET | :warning: **路径叠加冗余** |
| 列表查询 | `/api/column-mapping/list` | `@RequestMapping("/api/column-mapping")` + `@GetMapping("/list")` | GET | :warning: **路径叠加冗余** |
| 修改/新增 (无ID) | `/api/column-mapping` | `@RequestMapping("/api/column-mapping")` + `@PutMapping`/`@PostMapping` | PUT/POST | :warning: **路径叠加冗余** |
| 删除对应的映射 | `/api/column-mapping/{id}` | `@RequestMapping("/api/column-mapping")` + `@DeleteMapping("/{id}")`| DELETE | :warning: **路径叠加冗余** |
| 批量删除 | `/api/column-mapping/batchDelete` | `@RequestMapping("/api/column-mapping")` + `@PostMapping("/batchDelete")`| POST | :warning: **路径叠加冗余** |

---

## 6. 指令/规则相关 (SysInstruction)

| 接口说明 | 前端请求路径 (代码中) | 后端接收路径 (Controller) | 请求方法 | 匹配状态 |
| :--- | :--- | :--- | :--- | :--- |
| 获取指令列表 | **前端无对应JS方法** | `@RequestMapping("/instruction")` + `@GetMapping("/list")` | GET | :warning: **前端可能有漏配置** |

---

## 差异总结与优化建议

1. **命名规范的差异 (`/api` 重叠):**
   - **问题现象：** 后端服务 `context-path` 已经是 `/api` 时，`SysColumnMappingController` 又加上了一级 `@RequestMapping("/api/column-mapping")`。虽然前端也通过 `url: '/api/column-mapping...'` 跟后端达成了强一致，但这意味着该接口最后在网关层的实际访问地址变成了 `/api/api/column-mapping`。这是一种由于前后端定义重复导致的“冗余URL”。
   - **建议：** 将后端的 `SysColumnMappingController` 改为 `@RequestMapping("/column-mapping")`；并将前端的 `src/api/columnMapping.js` 统一去掉开头的 `/api`。保持和 `user.js` 等接口一样的无后缀风格。

2. **前端缺失的接口实现:**
   - 后端的 `FileController` 和 `LogDataController` 依然保留有 `/{id}` 的删除接口、`clear` 清空接口以及 `echarts` 相关的制图接口请求，但在前端的 `api/file.js` 中没有看到声明，可能是直接写在了页面内（不推荐），或是前端遗漏开发对应功能按钮。如果是冗余废弃接口，建议后端顺手清理，以免生成无关的文档和资源浪费。
   - `SysInstructionController` 内存在的规则列表拉取接口未在独立的 API `.js` 里封装。

3. **HTTP方法混用 (`/batchDelete` 为 `POST`):**
   - 后端的 `SysColumnMappingController` 中批量删除使用的是 `@PostMapping("/batchDelete")` 而不是 `@DeleteMapping` 并附随Body，不过这也是企业里的一种通用妥协做法，建议保持现状。对于标准单体删除使用 `@DeleteMapping("/{id}")`，这点前后端已经一致。
