package dev.gavin.dialect.config

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatModel
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class DialectConfig {

    @Bean
    @ConditionalOnMissingBean(ChatClient::class)
    fun chatClient(chatModel: ChatModel): ChatClient =
        ChatClient.create(chatModel)
}
