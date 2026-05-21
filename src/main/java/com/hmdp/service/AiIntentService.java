package com.hmdp.service;


import com.hmdp.enums.AiIntentType;

public interface AiIntentService {
    AiIntentType detectIntent(String message);
}