package com.lian.qingaiagent.chatmemory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileBasedChatMemoryRepositoryTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void savesMessagesAndReadsThemAfterRepositoryRecreation() {
        FileBasedChatMemoryRepository repository = new FileBasedChatMemoryRepository(temporaryDirectory);
        repository.saveAll("conversation-1", messages());

        FileBasedChatMemoryRepository recreatedRepository =
                new FileBasedChatMemoryRepository(temporaryDirectory);
        List<Message> restoredMessages = recreatedRepository.findByConversationId("conversation-1");

        assertEquals(2, restoredMessages.size());
        assertEquals("用户问题", restoredMessages.get(0).getText());
        assertEquals("模型回答", restoredMessages.get(1).getText());
        assertEquals(List.of("conversation-1"), recreatedRepository.findConversationIds());
    }

    @Test
    void deletesConversation() {
        FileBasedChatMemoryRepository repository = new FileBasedChatMemoryRepository(temporaryDirectory);
        repository.saveAll("conversation-1", messages());

        repository.deleteByConversationId("conversation-1");

        assertEquals(List.of(), repository.findConversationIds());
        assertEquals(List.of(), repository.findByConversationId("conversation-1"));
    }

    @Test
    void rejectsPathTraversalConversationId() {
        FileBasedChatMemoryRepository repository = new FileBasedChatMemoryRepository(temporaryDirectory);

        assertThrows(IllegalArgumentException.class,
                () -> repository.findByConversationId("../outside"));
        assertThrows(IllegalArgumentException.class,
                () -> repository.saveAll("conversation/child", messages()));
    }

    private List<Message> messages() {
        return List.of(new UserMessage("用户问题"), new AssistantMessage("模型回答"));
    }
}
