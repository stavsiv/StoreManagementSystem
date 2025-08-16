package Services;

import java.time.LocalDateTime;
import java.util.*;

public class ChatService {

    private static Map<String, ChatSession> chatMap = new HashMap<>();
    private static int chatCounter = 1000; // used to generate chat IDs

    /**
     * Starts a new chat session for the given branches.
     * Returns the unique chatId.
     */
    public synchronized String startChat(String branch1, String branch2) {
        // Generate a unique chat ID
        String chatId = "CHAT-" + (chatCounter++);
        ChatSession session = new ChatSession(chatId, branch1, branch2);
        chatMap.put(chatId, session);
        return chatId;
    }

    /**
     * Finds an existing chat by ID.
     */
    public static ChatSession getChatById(String chatId) {
        return chatMap.get(chatId);
    }

    /**
     * Returns a read-only collection of all active chat sessions.
     */
    public static Collection<ChatSession> listAllChats() {
        return chatMap.values();
    }

    /**
     * Represents a single chat session between two (or more) branches.
     */
    public static class ChatSession {
        private String chatId;
        private Set<String> branchesInvolved;
        private List<ChatMessage> messages;
        private boolean active;

        public ChatSession(String chatId, String branch1, String branch2) {
            this.chatId = chatId;
            this.branchesInvolved = new HashSet<>();
            this.branchesInvolved.add(branch1);
            this.branchesInvolved.add(branch2);
            this.messages = new ArrayList<>();
            this.active = true;
        }

        public void addBranch(String branch) {
            branchesInvolved.add(branch);
        }

        public String getChatId() {
            return chatId;
        }

        public Set<String> getBranchesInvolved() {
            return branchesInvolved;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }

        public List<ChatMessage> getMessages() {
            return messages;
        }

        public void addMessage(ChatMessage msg) {
            messages.add(msg);
        }
    }

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

        public String getSenderName() {
            return senderName;
        }

        public String getSenderBranch() {
            return senderBranch;
        }

        public String getContent() {
            return content;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }
    }
}