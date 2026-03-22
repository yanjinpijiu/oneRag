package com.onerag.chat.service;

import com.onerag.chat.DTO.resp.ChatRespDTO;
import org.apache.xmlbeans.impl.xb.xsdschema.Public;
import org.springframework.stereotype.Service;


public interface ChatService {
    ChatRespDTO sendAChat(String message);


}
