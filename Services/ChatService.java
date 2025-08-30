package Services;

import Exceptions.CustomExceptions;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public class ChatService {

    private Map<String, ChatSession> chatMap = new ConcurrentHashMap<>();
    private Map<String, String> branchActiveChat = new ConcurrentHashMap<>(); // branch -> chatId
    private Queue<String> waitingQueue = new ConcurrentLinkedQueue<>();
    private int chatCounter = 1000;

    // -------------------- Chat Methods --------------------

    /**
     * Start a chat between two branches.
     * If target branch is busy, throws ChatBranchBusyException.
     * If target branch offline, throws ChatBranchOfflineException.
     */
    public synchronized String startChat(String branch1, String branch2) throws CustomExceptions.ChatBranchBusyException, CustomExceptions.ChatBranchOfflineException {
        // Check if branches are busy
        if (branchActiveChat.containsKey(branch1)) {
            throw new CustomExceptions.ChatBranchBusyException("Your branch is already in an active chat.");
        }
        if (branchActiveChat.containsKey(branch2)) {
            throw new CustomExceptions.ChatBranchBusyException("Target branch is currently busy.");
        }

        // Check for offline (simulate offline by absence in active map)
        if (branch1 == null || branch2 == null) {
            throw new CustomExceptions.ChatBranchOfflineException("One of the branches is offline.");
        }

        String chatId = "CHAT-" + (chatCounter++);
        ChatSession session = new ChatSession(chatId, branch1, branch2);
        chatMap.put(chatId, session);
        branchActiveChat.put(branch1, chatId);
        branchActiveChat.put(branch2, chatId);

        return chatId;
    }

    /**
     * Get a chat session by ID
     */
    public ChatSession getChatById(String chatId) {
        return chatMap.get(chatId);
    }

    /**
     * List all active chats
     */
    public Collection<ChatSession> listAllChats() {
        return chatMap.values();
    }

    /**
     * Call this when a branch becomes free to notify queued branches
     */
    public void notifyQueuedBranches() {
        while (!waitingQueue.isEmpty()) {
            String nextBranch = waitingQueue.poll();
            // Here you can implement logic to auto-assign or notify the client via listener
            // Example: fire an event in ClientHandler to notify user
        }
    }

    // -------------------- Chat Session --------------------
    public static class ChatSession {
        private String chatId;
        private Set<String> branchesInvolved;
        private List<ChatMessage> messages;
        private boolean active;

        // Listeners for live message updates
        private Map<String, Consumer<ChatMessage>> branchListeners = new ConcurrentHashMap<>();

        public ChatSession(String chatId, String branch1, String branch2) {
            this.chatId = chatId;
            this.branchesInvolved = Collections.synchronizedSet(new HashSet<>());
            this.branchesInvolved.add(branch1);
            this.branchesInvolved.add(branch2);
            this.messages = Collections.synchronizedList(new ArrayList<>());
            this.active = true;
        }

        public void addBranch(String branch) {
            branchesInvolved.add(branch);
        }

        public void addMessage(ChatMessage msg) {
            messages.add(msg);
            // Notify all listeners
            branchListeners.forEach((_, listener) -> listener.accept(msg));
        }

        public void registerBranchListener(String branchId, Consumer<ChatMessage> listener) {
            branchListeners.put(branchId, listener);
        }

        // Getters and setters
        public String getChatId() { return chatId; }
        public Set<String> getBranchesInvolved() { return branchesInvolved; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
        public List<ChatMessage> getMessages() { return messages; }
    }

    // -------------------- Chat Message --------------------
    public static class ChatMessage {
        private String senderName;
        private String senderBranch;
        private String content;
        private LocalDateTime timestamp;

        public ChatMessage(String senderName, String senderBranch, String content) {
            this.senderName = senderName;
            this.senderBranch = senderBranch;
            this.content = content;
            this.timestamp = LocalDateTime.now();
        }

        // Getters
        public String getSenderName() { return senderName; }
        public String getSenderBranch() { return senderBranch; }
        public String getContent() { return content; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
}
