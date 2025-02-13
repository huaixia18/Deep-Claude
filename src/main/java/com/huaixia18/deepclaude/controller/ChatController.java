package com.huaixia18.deepclaude.controller;

import com.huaixia18.deepclaude.service.ChatService;
import com.huaixia18.deepclaude.controller.param.ChatDialogParam;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/chat")
public class ChatController {
    // 添加 @Autowired 注解来进行依赖注入
    @Autowired
    private ChatService chatService;
    
    /**
     * 发起对话
     */
    @PostMapping(value = "/chatSend")
    public void chatSend(HttpServletResponse response,
                         @Validated @RequestBody ChatDialogParam chatDialogParam) throws IOException {
        chatService.chatSend(response, chatDialogParam);
    }
}
