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
    private Map<String, Queue<ChatRequest>> waitingQueue = new ConcurrentHashMap<>(); // targetBranch -> waiting requests
    private int chatCounter = 1000;
    public Collection<ChatSession> listAllChats() { return chatMap.values(); }


    // -------------------- Core Methods --------------------
    public synchronized String startChat(String branch1, String branch2, Consumer<String> notifyCallback)
            throws CustomExceptions.ChatBranchBusyException, CustomExceptions.ChatBranchOfflineException {

        // Check if the initiating branch is busy
        if (branchActiveChat.containsKey(branch1)) {
            throw new CustomExceptions.ChatBranchBusyException("Your branch is already in an active chat.");
        }

        // Check if target branch is busy
        if (branchActiveChat.containsKey(branch2)) {
            throw new CustomExceptions.ChatBranchBusyException("Target branch is currently busy.");
        }

        // Check if target branch is connected/logged in
        if (!connectedUsers.containsKey(branch2)) {  // <-- this map/list should track online branches
            throw new CustomExceptions.ChatBranchOfflineException("Target branch is offline.");
        }

        String chatId = "CHAT-" + chatCounter++;
        ChatSession session = new ChatSession(chatId, branch1, branch2);
        chatMap.put(chatId, session);
        branchActiveChat.put(branch1, chatId);
        branchActiveChat.put(branch2, chatId);
        // Notify both branches
        notifyCallback.accept(chatId + " started between " + branch1 + " and " + branch2);
        // Notify the other branch if it has a listener
        // Notify the other branch
        ChatMessage msgForBranch2 = new ChatMessage(
                "SYSTEM",            // senderName
                branch1,             // senderBranch (who initiated)
                "Branch " + branch1 + " joined chat " + chatId
        );
        session.notifyBranch(branch2, msgForBranch2);


        return chatId;
    }

    private boolean isBranchOnline(String branchId) {
        return branchId != null;
    }

    public void enqueueBranch(String requestingBranch, String targetBranch, Consumer<String> notifyCallback) {
        waitingQueue.putIfAbsent(targetBranch, new ConcurrentLinkedQueue<>());
        waitingQueue.get(targetBranch).add(new ChatRequest(requestingBranch, notifyCallback));
    }

    public void notifyQueuedBranches(String branchId) {
        Queue<ChatRequest> queue = waitingQueue.get(branchId);
        if (queue != null) {
            while (!queue.isEmpty()) {
                ChatRequest req = queue.poll();
                try {
                    String chatId = startChat(req.requestingBranch, branchId, req.notifyCallback);
                    req.notifyCallback.accept("Chat started with " + branchId + ". ChatID: " + chatId);
                } catch (Exception ignored) {}
            }
        }
    }



    public ChatSession getChatById(String chatId) {
        return chatMap.get(chatId);
    }

    // ------------------ Connected Users ------------------
    private final Map<String, String> connectedUsers = new ConcurrentHashMap<>(); // branchId -> sessionId

    /**
     * Adds a branch/user as connected.
     * @param branchId the branch of the logged-in user
     * @param sessionId the current session ID or connection identifier
     */
    public void addConnectedUser(String branchId, String sessionId) {
        connectedUsers.put(branchId, sessionId);
    }

    /**
     * Removes a branch/user from connected list
     */
    public void removeConnectedUser(String branchId) {
        connectedUsers.remove(branchId);
    }

    /**
     * Checks if a branch/user is connected
     */
    public boolean isUserConnected(String branchId) {
        return connectedUsers.containsKey(branchId);
    }

    // -------------------- Core Classes --------------------

    public static class ChatSession {
        private final String chatId;
        private final Set<String> branchesInvolved = Collections.synchronizedSet(new HashSet<>());
        private final List<ChatMessage> messages = Collections.synchronizedList(new ArrayList<>());
        private final Map<String, Consumer<ChatMessage>> branchListeners = new ConcurrentHashMap<>();
        private boolean active = true;

        public ChatSession(String chatId, String branch1, String branch2) {
            this.chatId = chatId;
            branchesInvolved.add(branch1);
            branchesInvolved.add(branch2);
        }

        // Add branch with listener
        public void addBranch(String branchId, Consumer<ChatMessage> listener) {
            branchesInvolved.add(branchId);
            branchListeners.put(branchId, listener);
        }

        // Register listener for an existing branch
        public void registerBranchListener(String branchId, Consumer<ChatMessage> listener) {
            branchListeners.put(branchId, listener);
        }

        public void notifyBranch(String branchId, ChatMessage msg) {
            Consumer<ChatMessage> listener = branchListeners.get(branchId);
            if (listener != null) {
                listener.accept(msg);
            }
        }

        public boolean hasListener(String branchId) {
            return branchListeners.containsKey(branchId);
        }

        public void addMessage(ChatMessage msg) {
            messages.add(msg);
            branchListeners.values().forEach(listener -> listener.accept(msg));
        }

        public Set<String> getBranchesInvolved() { return branchesInvolved; }
        public List<ChatMessage> getMessages() { return messages; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
        public String getChatId() { return chatId; }
    }

    public static class ChatMessage {
        private final String senderName;
        private final String senderBranch;
        private final String content;
        private final LocalDateTime timestamp;

        public ChatMessage(String senderName, String senderBranch, String content) {
            this.senderName = senderName;
            this.senderBranch = senderBranch;
            this.content = content;
            this.timestamp = LocalDateTime.now();
        }

        public String getSenderName()   { return senderName; }
        public String getSenderBranch() { return senderBranch; }
        public String getContent()      { return content; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }


    public static class ChatRequest {
        String requestingBranch;
        Consumer<String> notifyCallback;
        public ChatRequest(String requestingBranch, Consumer<String> notifyCallback) {
            this.requestingBranch = requestingBranch;
            this.notifyCallback = notifyCallback;
        }
    }
}
