# JDEC Platform Docker 部署指南

## 快速开始

### 1. 构建镜像

```bash
docker build -t jdec-platform:1.0.0-SNAPSHOT .
```

### 2. 运行容器

#### 方式一：使用 Docker 命令

```bash
docker run -d \
  --name jdec-platform-app \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e JWT_SECRET=your-secret-key \
  jdec-platform:1.0.0-SNAPSHOT
```

#### 方式二：使用 Docker Compose

```bash
# 前台运行
docker-compose up

# 后台运行
docker-compose up -d

# 停止服务
docker-compose down
```

## 环境变量配置

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `SPRING_PROFILES_ACTIVE` | Spring 环境配置 | `prod` |
| `JWT_SECRET` | JWT 密钥 | (见配置文件) |
| `JWT_EXPIRATION` | JWT 过期时间（毫秒） | `604800000` (一周) |
| `JAVA_OPTS` | JVM 参数 | (见 Dockerfile) |

## 多环境部署

### 开发环境

```bash
docker run -d \
  --name jdec-dev \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=dev \
  jdec-platform:1.0.0-SNAPSHOT
```

### 测试环境

```bash
docker run -d \
  --name jdec-test \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=test \
  jdec-platform:1.0.0-SNAPSHOT
```

### 生产环境

```bash
docker run -d \
  --name jdec-prod \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e JWT_SECRET=${YOUR_PROD_SECRET} \
  --restart=unless-stopped \
  jdec-platform:1.0.0-SNAPSHOT
```

## 性能优化

### JVM 参数调优

通过 `JAVA_OPTS` 环境变量覆盖默认配置：

```bash
docker run -d \
  --name jdec-platform-app \
  -p 8080:8080 \
  -e JAVA_OPTS="-Xms1g -Xmx2g -XX:+UseG1GC" \
  jdec-platform:1.0.0-SNAPSHOT
```

### 资源限制

```bash
docker run -d \
  --name jdec-platform-app \
  -p 8080:8080 \
  --memory="2g" \
  --cpus="2" \
  jdec-platform:1.0.0-SNAPSHOT
```

## 健康检查

### 检查容器状态

```bash
docker ps
```

### 查看健康检查日志

```bash
docker inspect --format='{{json .State.Health}}' jdec-platform-app | jq
```

### 手动健康检查

```bash
curl http://localhost:8080/actuator/health
```

## 日志管理

### 查看实时日志

```bash
docker logs -f jdec-platform-app
```

### 查看最近 100 行日志

```bash
docker logs --tail 100 jdec-platform-app
```

## 镜像推送

### 推送到私有仓库

```bash
# 标记镜像
docker tag jdec-platform:1.0.0-SNAPSHOT your-registry.com/jdec-platform:1.0.0-SNAPSHOT

# 登录仓库
docker login your-registry.com

# 推送镜像
docker push your-registry.com/jdec-platform:1.0.0-SNAPSHOT
```

## 故障排查

### 进入容器调试

```bash
# 使用 sh（Alpine 镜像）
docker exec -it jdec-platform-app sh

# 查看应用日志
docker exec jdec-platform-app cat logs/application.log
```

### 查看容器资源使用

```bash
docker stats jdec-platform-app
```

## 注意事项

1. **生产环境必须修改 JWT_SECRET**：默认密钥仅用于开发测试
2. **数据库连接**：确保容器能够访问外部数据库（网络配置）
3. **Redis 连接**：根据实际部署架构配置 Redis 地址
4. **文件上传**：如需持久化上传文件，请挂载 volume
5. **时区设置**：镜像已设置为 `Asia/Shanghai`
6. **非 root 用户**：容器内使用 `spring` 用户运行应用，提升安全性

## 完整 Docker Compose 示例

如果需要一键启动完整环境（包括 MySQL 和 Redis），取消注释 `docker-compose.yml` 中的相关服务配置。
