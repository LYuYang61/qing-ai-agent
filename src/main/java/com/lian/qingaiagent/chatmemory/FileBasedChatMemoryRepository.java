package com.lian.qingaiagent.chatmemory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 基于 Kryo 文件的 ChatMemoryRepository 实现。
 *
 * <p>Spring AI 的 {@code MessageWindowChatMemory} 负责窗口裁剪，
 * 本类只负责把每个会话的消息列表保存到磁盘，因此职责比直接实现 ChatMemory 更清晰。</p>
 */
public class FileBasedChatMemoryRepository implements ChatMemoryRepository {

    private static final String FILE_SUFFIX = ".kryo";
    private static final Pattern SAFE_CONVERSATION_ID =
            Pattern.compile("[\\p{L}\\p{N}._-]{1,128}");

    private final Path baseDirectory;

    public FileBasedChatMemoryRepository(String directory) {
        this(Path.of(directory));
    }

    public FileBasedChatMemoryRepository(Path directory) {
        this.baseDirectory = directory.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.baseDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建对话记忆目录: " + this.baseDirectory, exception);
        }
    }

    @Override
    public synchronized List<String> findConversationIds() {
        try (var paths = Files.list(baseDirectory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(name -> name.endsWith(FILE_SUFFIX))
                    .map(name -> name.substring(0, name.length() - FILE_SUFFIX.length()))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("读取对话记忆目录失败: " + baseDirectory, exception);
        }
    }

    @Override
    public synchronized List<Message> findByConversationId(String conversationId) {
        Path conversationFile = conversationFile(conversationId);
        if (!Files.exists(conversationFile)) {
            return new ArrayList<>();
        }
        try (InputStream inputStream = Files.newInputStream(conversationFile);
             Input input = new Input(inputStream)) {
            ArrayList<?> messages = newKryo().readObject(input, ArrayList.class);
            List<Message> result = new ArrayList<>(messages.size());
            for (Object message : messages) {
                if (!(message instanceof Message)) {
                    throw new IllegalStateException("对话记忆文件包含无法识别的消息类型: "
                            + message.getClass().getName());
                }
                result.add((Message) message);
            }
            return result;
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("读取对话记忆失败: " + conversationId, exception);
        }
    }

    @Override
    public synchronized void saveAll(String conversationId, List<Message> messages) {
        Path conversationFile = conversationFile(conversationId);
        Path temporaryFile;
        try {
            temporaryFile = Files.createTempFile(baseDirectory,
                    conversationFile.getFileName().toString(), ".tmp");
            try (OutputStream outputStream = Files.newOutputStream(temporaryFile);
                 Output output = new Output(outputStream)) {
                newKryo().writeObject(output, new ArrayList<>(messages));
            }
            moveAtomically(temporaryFile, conversationFile);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("保存对话记忆失败: " + conversationId, exception);
        }
    }

    @Override
    public synchronized void deleteByConversationId(String conversationId) {
        try {
            Files.deleteIfExists(conversationFile(conversationId));
        } catch (IOException exception) {
            throw new IllegalStateException("删除对话记忆失败: " + conversationId, exception);
        }
    }

    private Path conversationFile(String conversationId) {
        if (conversationId == null || !SAFE_CONVERSATION_ID.matcher(conversationId).matches()) {
            throw new IllegalArgumentException("会话 ID 只能包含字母、数字、中文、点、下划线和短横线，长度为 1-128");
        }
        Path file = baseDirectory.resolve(conversationId + FILE_SUFFIX).normalize();
        if (!file.getParent().equals(baseDirectory)) {
            throw new IllegalArgumentException("非法的会话 ID");
        }
        return file;
    }

    private void moveAtomically(Path temporaryFile, Path targetFile) throws IOException {
        try {
            Files.move(temporaryFile, targetFile,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Kryo newKryo() {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false);
        kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
        return kryo;
    }
}
