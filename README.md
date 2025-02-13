# Deep Claude

一个基于DeepSeek和Claude模型的智能对话系统，通过两个AI模型的协同工作，提供更深入的思考过程和更准确的回答。

## 功能特点

- 双模型协同：结合DeepSeek的思考能力和Claude的表达能力
- 流式响应：实时返回AI的思考和回答过程
- 错误处理：完善的错误处理和用户反馈机制
- SSE通信：采用Server-Sent Events实现实时数据推送

## 技术架构

- 后端框架：Spring Boot
- AI模型：DeepSeek + Claude
- 数据格式：JSON
- 通信方式：SSE (Server-Sent Events)

## 环境要求

- Java 17+
- Maven 3.6+
- Spring Boot 3.x

## 快速开始

### 1. 配置AI模型

在`application.yml`中配置AI模型参数：

```yaml
ai:
  model:
    deepseek:
      name: deepseek-r1
      key: your-api-key
      url: your-api-url
    claude:
      name: claude-3-5-sonnet-20240620
      key: your-api-key
      url: your-api-url
```

### 2. 启动服务

```bash
./mvnw spring-boot:run
```

### 3. 发送对话请求

```bash
curl -X POST http://localhost:8080/chat/chatSend \
  -H "Content-Type: application/json" \
  -d '{"question":"你的问题"}'
```

## 工作流程

1. 用户发送问题
2. DeepSeek模型分析问题并生成思考过程
3. 将思考过程传递给Claude模型
4. Claude模型基于思考过程生成最终答案
5. 以流式方式返回完整的思考和回答过程

## 错误处理

系统实现了完善的错误处理机制：

- 网络异常处理
- 模型调用超时处理
- 客户端断开连接处理
- 格式化的错误响应

## 配置说明

### AI模型配置

- `name`: 模型名称
- `key`: API密钥
- `url`: API接口地址

### 系统配置

- 流式响应延迟：50ms
- 响应格式：Base64编码的JSON
- 字符编码：UTF-8

## 开发计划

- [ ] 添加对话历史记录功能
- [ ] 实现多轮对话上下文管理
- [ ] 优化模型参数配置

## 贡献指南

欢迎提交Issue和Pull Request来帮助改进项目。

## 许可证

[MIT License](LICENSE)