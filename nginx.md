# Nginx 负载均衡配置（适配当前项目）

> 适配你当前代码结构：
> - 后端 Spring Boot `server.port=8081`
> - 后端 `context-path=/api`
> - 前端生产构建 `base=/api/`
> - 前端请求接口前缀 `/api`

## 使用前提

1. 多个后端实例共用同一个 MySQL（refresh token 存库）。
2. 所有后端实例 `jwt.secret` 必须一致（否则 token 跨实例会失效）。
3. 每个实例端口不同（例如 8081/8082/8083）。

---

## nginx.conf 示例

```nginx
worker_processes auto;

events {
    worker_connections 4096;
}

http {
    include       mime.types;
    default_type  application/octet-stream;

    sendfile        on;
    tcp_nopush      on;
    tcp_nodelay     on;
    keepalive_timeout 65;

    # 日志可按需调整
    access_log logs/xy_txt_ui.access.log;
    error_log  logs/xy_txt_ui.error.log warn;

    # 后端负载均衡池（按你的实际机器IP/端口修改）
    upstream xy_txt_ui_backend {
        least_conn;
        server 127.0.0.1:8081 max_fails=3 fail_timeout=30s;
        server 127.0.0.1:8082 max_fails=3 fail_timeout=30s;
        # server 127.0.0.1:8083 max_fails=3 fail_timeout=30s;
    }

    server {
        listen 80;
        server_name _;

        # 适配大文件上传
        client_max_body_size 500m;

        # 打开根路径时跳转到 /api/
        location = / {
            return 302 /api/;
        }

        # 当前项目前后端统一通过 /api 访问
        location /api/ {
            proxy_pass http://xy_txt_ui_backend;

            proxy_http_version 1.1;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;

            # 长任务/导出下载场景
            proxy_connect_timeout 30s;
            proxy_send_timeout    300s;
            proxy_read_timeout    300s;

            # 如后续有 SSE/流式输出可保留
            proxy_buffering off;
        }
    }
}
```

---

## 说明

- 该配置不做路径改写，`/api/**` 会原样转发到后端实例。
- 由于后端配置了 `context-path=/api`，这样转发是正确的。
- 如果后续要上 HTTPS，只需在 `server` 增加 443 + 证书配置，并保留 `location /api/` 逻辑。
