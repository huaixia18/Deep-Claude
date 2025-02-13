package com.huaixia18.deepclaude.controller.param;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * @author biliyu
 * @date 2024/8/5 19:08
 */
public class ChatDialogParam {

    /**
     * 问题
     */
    private String question;


    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }
}
