package com.huaixia18.deepclaude.service;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.huaixia18.deepclaude.config.AIModelConfig;
import com.huaixia18.deepclaude.controller.param.ChatDialogParam;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * AI对话服务类
 * 负责处理与AI模型的对话交互，包括获取思考过程和回答
 * 
 * 工作流程：
 * 1. 接收用户问题
 * 2. 调用Deepseek模型获取思考过程
 * 3. 将思考过程传递给Claude模型获取最终回答
 * 4. 以流式方式返回结果
 */
@Slf4j
@Service
public class ChatService {
    private static final String THINK_TAG_START = "<think>\n";
    private static final String THINK_TAG_END = "\n</think>\n";
    private static final int STREAM_DELAY = 50;
    private static final String ERROR_MESSAGE = "发起对话失败，请联系管理员";

    @Autowired
    private AIModelConfig aiModelConfig;

    /**
     * 处理对话请求
     * 整个对话流程的入口方法，协调调用Deepseek和Claude模型
     * 
     * @param response HTTP响应对象，用于返回流式响应
     * @param chatDialogParam 对话参数，包含用户问题等信息
     */
    public void chatSend(HttpServletResponse response, ChatDialogParam chatDialogParam) {
        log.info("开始处理对话请求，问题：{}", chatDialogParam.getQuestion());
        
        try {
            // 从 deepseek 获取思考过程
            String reasoning = getDeepseekReasoning(response, chatDialogParam);
            log.debug("获取到思考过程：{}", reasoning);

            // 从 claude 获取回答
            String answer = getClaudeAnswer(response, reasoning, chatDialogParam);
            log.debug("获取到回答：{}", answer);
        } catch (Exception e) {
            log.error("对话处理失败", e);
            handleError(response, e);
        }
    }

    /**
     * 获取Deepseek的思考过程
     * 调用Deepseek模型API获取对问题的分析和思考过程
     * 
     * @param response HTTP响应对象
     * @param param 对话参数
     * @return 思考过程文本
     */
    private String getDeepseekReasoning(HttpServletResponse response, ChatDialogParam param) {
        AIModelConfig.ModelConfig config = aiModelConfig.getDeepseek();
        AtomicReference<String> reasoning = new AtomicReference<>("");
        
        List<Map<String, String>> messages = createMessage("user", param.getQuestion());
        processModelStream(response, messages, "", config, map -> {
            reasoning.set(map.get("reasoning"));
            return null;
        });
        
        return reasoning.get();
    }

    /**
     * 获取Claude的回答
     * 将Deepseek的思考过程传递给Claude模型，获取最终的回答
     * 
     * @param response HTTP响应对象
     * @param reasoning Deepseek的思考过程
     * @param param 对话参数
     * @return Claude的回答文本
     */
    private String getClaudeAnswer(HttpServletResponse response, String reasoning, ChatDialogParam param) {
        AIModelConfig.ModelConfig config = aiModelConfig.getClaude();
        AtomicReference<String> result = new AtomicReference<>("");
        
        List<Map<String, String>> messages = createMessage("user", reasoning);
        processModelStream(response, messages, param.getQuestion(), config, map -> {
            map.put("deepseek_reasoning", reasoning);
            result.set(map.get("reply"));
            return null;
        });
        
        return result.get();
    }

    /**
     * 创建消息对象
     * 构造发送给AI模型的消息格式
     * 
     * @param role 角色标识(user/assistant)
     * @param content 消息内容
     * @return 消息对象列表
     */
    private List<Map<String, String>> createMessage(String role, String content) {
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> message = new HashMap<>();
        message.put("role", role);
        message.put("content", content);
        messages.add(message);
        return messages;
    }

    /**
     * 处理模型流式响应
     * 处理与AI模型的流式交互，包括发送请求和处理响应
     * 
     * @param response HTTP响应对象
     * @param messages 发送给模型的消息列表
     * @param system 系统提示信息
     * @param config 模型配置信息
     * @param callback 处理响应的回调函数
     */
    private void processModelStream(HttpServletResponse response, List<Map<String, String>> messages,
            String system, AIModelConfig.ModelConfig config, Function<Map<String, String>, String> callback) {
        setupResponseHeaders(response);

        Map<String, String> resultMap = new LinkedHashMap<>();
        JSONObject requestBody = createRequestBody(messages, config.getName(), system);
        StringBuilder content = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        Integer tokens = 0;

        try {
            HttpURLConnection connection = setupConnection(config.getUrl(), config.getKey());
            sendRequest(connection, requestBody);
            processResponse(connection, config.getName(), response, content, reasoning, resultMap);
            
            resultMap.put("reply", content.toString().trim());
            resultMap.put("tokens", String.valueOf(tokens));
            resultMap.put("reasoning", reasoning.toString().trim());
            callback.apply(resultMap);
        } catch (ClientAbortException e) {
            handleClientAbort(resultMap, content, messages, callback);
        } catch (Exception e) {
            handleStreamError(response, e);
        }
    }

    private JSONObject createRequestBody(List<Map<String, String>> messages, String model, String system) {
        JSONObject requestBody = new JSONObject();
        requestBody.put("messages", messages);
        requestBody.put("model", model);
        requestBody.put("stream", true);
        requestBody.put("system", system);
        requestBody.put("stream_options", JSONObject.parseObject("{\"include_usage\": true}"));
        return requestBody;
    }

    private HttpURLConnection setupConnection(String modelUrl, String modelKey) throws IOException {
        URL url = new URL(modelUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + modelKey);
        connection.setUseCaches(false);
        connection.setDoOutput(true);
        return connection;
    }

    private void sendRequest(HttpURLConnection connection, JSONObject requestBody) throws IOException {
        try (OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream())) {
            writer.write(requestBody.toString());
            writer.flush();
        }
    }

    private void processResponse(HttpURLConnection connection, String model, HttpServletResponse response,
            StringBuilder content, StringBuilder reasoning, Map<String, String> resultMap) throws IOException {
        boolean isThink = true;
        boolean isReply = true;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                processResponseLine(line, model, response, content, reasoning, resultMap, isThink, isReply);
            }
        }
    }

    private void processResponseLine(String line, String model, HttpServletResponse response,
            StringBuilder content, StringBuilder reasoning, Map<String, String> resultMap,
            boolean isThink, boolean isReply) throws IOException {
        if (!line.contains("data:")) return;
        
        line = line.replace("data:", "").trim();
        if (line.contains("[DONE]")) return;

        try {
            JSONObject json = JSONObject.parse(line);
            JSONArray choices = json.getJSONArray("choices");
            if (choices.isEmpty()) return;

            JSONObject choice = choices.getJSONObject(0);
            if (model.contains("deepseek")) {
                processDeepseekResponse(choice, response, reasoning, json, isThink);
            } else if (model.contains("claude")) {
                processClaudeResponse(choice, response, content, json, isReply);
            }

            updateTokenCount(json, resultMap);
        } catch (Exception e) {
            log.error("处理响应行失败: {}", line, e);
            throw new IOException("处理响应数据失败", e);
        }
    }

    private void processDeepseekResponse(JSONObject choice, HttpServletResponse response,
            StringBuilder reasoning, JSONObject json, boolean isThink) throws Exception {
        String reason = choice.getJSONObject("delta").getString("reasoning_content");
        if (reason == null) return;

        if (isThink) {
            reasoning.append(THINK_TAG_START);
        }
        reasoning.append(reason);

        if (response != null) {
            sendStreamResponse(response, json, reason);
        }
    }

    private void processClaudeResponse(JSONObject choice, HttpServletResponse response,
            StringBuilder content, JSONObject json, boolean isReply) throws Exception {
        String result = choice.getJSONObject("delta").getString("content");
        if (result == null) return;

        if (isReply) {
            content.append(THINK_TAG_END);
        }
        content.append(result);

        if (response != null) {
            sendStreamResponse(response, json, result);
        }
    }

    private void sendStreamResponse(HttpServletResponse response, JSONObject json, String content) throws Exception {
        JSONObject data = new JSONObject();
        data.put("id", json.get("id"));
        data.put("event", json.getBooleanValue("is_end") ? "finish" : "chat");
        data.put("data", content);
        data.put("model", json.getString("model"));
        data.put("index", 0);

        byte[] bytes = Base64.getEncoder().encodeToString(data.toString().getBytes(StandardCharsets.UTF_8)).getBytes();
        response.getOutputStream().write(bytes);
        response.flushBuffer();
        Thread.sleep(STREAM_DELAY);
    }

    private void updateTokenCount(JSONObject json, Map<String, String> resultMap) {
        JSONObject usage = json.getJSONObject("usage");
        if (usage != null) {
            resultMap.put("tokens", usage.getString("total_tokens"));
        }
    }

    private void handleClientAbort(Map<String, String> resultMap, StringBuilder content,
            List<Map<String, String>> messages, Function<Map<String, String>, String> callback) {
        resultMap.put("reply", content.toString().trim());
        Map<String, String> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", content.toString());
        messages.add(message);
        resultMap.put("tokens", String.valueOf(countTokens(messages.toString())));
        callback.apply(resultMap);
    }

    private void handleStreamError(HttpServletResponse response, Exception e) {
        log.error("流式处理失败", e);
        try {
            sseResponseErrorData(response, 500, ERROR_MESSAGE);
        } catch (IOException ex) {
            log.error("发送错误响应失败", ex);
            throw new RuntimeException("发送错误响应失败", ex);
        }
    }

    /**
     * 设置SSE响应头
     */
    private void setupResponseHeaders(HttpServletResponse response) {
        if (response != null) {
            response.setContentType("text/event-stream;charset=UTF-8");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("Connection", "keep-alive");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("X-Accel-Buffering", "no");
        }
    }

    /**
     * 处理错误响应
     */
    private void handleError(HttpServletResponse response, Exception e) {
        try {
            sseResponseErrorData(response, 500, ERROR_MESSAGE);
        } catch (IOException ex) {
            log.error("发送错误响应失败", ex);
            throw new RuntimeException("发送错误响应失败", ex);
        }
    }

    /**
     * 发送SSE错误数据
     */
    private void sseResponseErrorData(HttpServletResponse response, Integer code, String err) throws IOException {
        if (response == null) return;
        
        String error = String.format("data: {\"error\":{\"errCode\":%d,\"errMsg\":\"%s\"}}\n\n", code, err);
        byte[] bytes = Base64.getEncoder().encodeToString(error.getBytes(StandardCharsets.UTF_8)).getBytes();
        response.getOutputStream().write(bytes);
        response.flushBuffer();
    }

    /**
     * 计算token数量
     */
    private static int countTokens(String text) {
        return text.split("\\s+|(?=\\p{Punct})|(?<=\\p{Punct})").length;
    }
}
