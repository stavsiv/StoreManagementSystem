package Services;

import Exceptions.CustomExceptions;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public class ChatService {

    private final Map<String, ChatSession> chatMap = new ConcurrentHashMap<>();
    private final Map<String, String> branchActiveChat = new ConcurrentHashMap<>();
    private final Map<String, Queue<ChatRequest>> waitingQueue = new ConcurrentHashMap<>();
    private final Map<String, Queue<String>> pendingChatInvites = new ConcurrentHashMap<>();
    private final Map<String, String> connectedUsers = new ConcurrentHashMap<>();
    private Map<String, Consumer<String>> branchNotifyCallbacks = new ConcurrentHashMap<>();
    private int chatCounter = 1000;

    public Collection<ChatSession> listAllChats() { return chatMap.values(); }

    // -------------------- Core Methods --------------------
    public synchronized String startChat(String branch1, String branch2, Consumer<String> notifyCallback1)
            throws CustomExceptions.ChatBranchBusyException {

        if (branchActiveChat.containsKey(branch1))
            throw new CustomExceptions.ChatBranchBusyException("Your branch is already in an active chat.");

        if (branchActiveChat.containsKey(branch2))
            throw new CustomExceptions.ChatBranchBusyException("Target branch is currently busy.");

        Consumer<String> notifyCallback2 = branchNotifyCallbacks.get(branch2);

        if (!connectedUsers.containsKey(branch2)) {
            pendingChatInvites.computeIfAbsent(branch2, k -> new ConcurrentLinkedQueue<>())
                    .add(branch1 + " invited you to chat.");
            return null;
        }

        return createChat(branch1, branch2, notifyCallback1, notifyCallback2);
    }

    private String createChat(String branch1, String branch2,
                              Consumer<String> notifyCallback1, Consumer<String> notifyCallback2) {
        String chatId = "CHAT-" + chatCounter++;
        ChatSession session = new ChatSession(chatId, branch1, branch2);
        chatMap.put(chatId, session);
        branchActiveChat.put(branch1, chatId);
        branchActiveChat.put(branch2, chatId);

        notifyCallback1.accept("Chat " + chatId + " started with " + branch2);

        if (notifyCallback2 != null) {
            notifyCallback2.accept(
                    "You received a chat request from branch " + branch1 +
                            " on chat: " + chatId +
                            ". Type JOIN_CHAT " + chatId + " to join this chat. " +
                            "Also type SHOW_CHAT to check messages."
            );
        }
        else {
            pendingChatInvites.computeIfAbsent(branch2, k -> new ConcurrentLinkedQueue<>())
                    .add(branch1 + " invited you to chat.");
        }

        return chatId;
    }


    public void leaveChat(String chatId, String branchId) throws CustomExceptions.ChatException {
        ChatSession session = chatMap.get(chatId);
        if (session == null || !session.isActive())
            throw new CustomExceptions.ChatInactiveException("Chat " + chatId + " not found or inactive");

        Set<String> participantsBefore = new HashSet<>(session.getBranchesInvolved());
        boolean removed = session.getBranchesInvolved().remove(branchId);
        if (!removed)
            throw new CustomExceptions.ChatException("Branch " + branchId + " not found in chat " + chatId);

        ChatMessage leaveMsg = new ChatMessage("SYSTEM", "SYSTEM", "Branch " + branchId + " has left chat " + chatId + ". To end the chat, type END_CHAT");
        session.getBranchesInvolved().forEach(b -> session.notifyBranch(b, leaveMsg));

        branchActiveChat.remove(branchId);

        if (session.getBranchesInvolved().isEmpty()) {
            chatMap.remove(chatId);
            ChatMessage endMsg = new ChatMessage("SYSTEM", "SYSTEM", "Chat " + chatId + " has ended (no participants left)");
            participantsBefore.forEach(b -> session.notifyBranch(b, endMsg));
            System.out.println("Chat " + chatId + " closed (no participants left)");
        }
    }

    public synchronized void endChat(String chatId) {
        ChatSession session = chatMap.get(chatId);
        if (session == null || !session.isActive()) return;

        session.setActive(false);
        session.getBranchesInvolved().forEach(branchActiveChat::remove);

        ChatMessage endMsg = new ChatMessage("SYSTEM", "SYSTEM", "Chat " + chatId + " has ended.");
        session.getBranchesInvolved().forEach(b -> session.notifyBranch(b, endMsg));
        session.getBranchesInvolved().forEach(this::notifyQueuedBranches);
    }

    public void enqueueBranch(String requestingBranch, String targetBranch, Consumer<String> notifyCallback) {
        waitingQueue.computeIfAbsent(targetBranch, k -> new ConcurrentLinkedQueue<>())
                .add(new ChatRequest(requestingBranch, notifyCallback));
    }

    public void notifyQueuedBranches(String branchId) {
        Queue<ChatRequest> queue = waitingQueue.get(branchId);
        if (queue == null) return;

        while (!queue.isEmpty()) {
            ChatRequest req = queue.poll();
            try {
                if (connectedUsers.containsKey(branchId)) {
                    String chatId = startChat(req.requestingBranch, branchId, req.notifyCallback);
                    if (chatId != null)
                        req.notifyCallback.accept("Chat started with " + branchId + ". ChatID: " + chatId);
                } else {
                    storeInvite(branchId, req.requestingBranch);
                }
            } catch (Exception ignored) {}
        }
    }

    public Queue<String> getPendingInvites(String branchId) {
        return pendingChatInvites.getOrDefault(branchId, new ConcurrentLinkedQueue<>());
    }

    public void clearPendingInvites(String branchId) {
        pendingChatInvites.remove(branchId);
    }

    public String checkPendingInvites(String branchId) {
        Queue<String> invites = pendingChatInvites.getOrDefault(branchId, new ConcurrentLinkedQueue<>());
        if (invites.isEmpty()) return "No pending chat invites.";

        StringBuilder sb = new StringBuilder("Pending chat invites:\n");
        while (!invites.isEmpty()) sb.append(invites.poll()).append("\n");
        return sb.toString();
    }

    public ChatSession getChatById(String chatId) { return chatMap.get(chatId); }

    public void addConnectedUser(String branchId, String sessionId, Consumer<String> notifyCallback) {
        connectedUsers.put(branchId, sessionId);
        branchNotifyCallbacks.put(branchId, notifyCallback);

        Queue<String> invites = pendingChatInvites.get(branchId);
        if (invites == null || invites.isEmpty()) {
            notifyCallback.accept("No pending chat invites.");
        } else {
            StringBuilder sb = new StringBuilder();
            while (!invites.isEmpty()) sb.append(invites.poll()).append(" ");
            notifyCallback.accept("Pending chat invite from: " + sb.toString().trim());
            pendingChatInvites.remove(branchId);
        }
    }

    public void removeConnectedUser(String branchId) {
        connectedUsers.remove(branchId);
        branchNotifyCallbacks.remove(branchId);
    }
    public void storeInvite(String targetBranch, String fromBranch) {
        pendingChatInvites.computeIfAbsent(targetBranch, k -> new ConcurrentLinkedQueue<>()).add(fromBranch);

        Consumer<String> cb = branchNotifyCallbacks.get(targetBranch);
        if (cb != null) {
            cb.accept("[NOTIFY] Chat invite from " + fromBranch);
        }
    }

    public boolean isUserConnected(String branchId) { return connectedUsers.containsKey(branchId); }

    // -------------------- Core Classes --------------------

    public static class ChatSession {
        private final String chatId;
        private final Set<String> branchesInvolved = Collections.synchronizedSet(new HashSet<>());
        private final List<ChatMessage> messages = Collections.synchronizedList(new ArrayList<>());
        private final Map<String, Consumer<ChatMessage>> branchListeners = new ConcurrentHashMap<>();
        private final Map<String, Queue<ChatMessage>> pendingMessages = new ConcurrentHashMap<>();
        private boolean active = true;

        public ChatSession(String chatId, String branch1, String branch2) {
            this.chatId = chatId;
            branchesInvolved.add(branch1);
            branchesInvolved.add(branch2);
            pendingMessages.put(branch1, new ConcurrentLinkedQueue<>());
            pendingMessages.put(branch2, new ConcurrentLinkedQueue<>());
        }

        public void addBranch(String branchId, Consumer<ChatMessage> listener) {
            branchesInvolved.add(branchId);
            branchListeners.put(branchId, listener);
        }

        public void registerBranchListener(String branchId, Consumer<ChatMessage> listener) {
            branchListeners.put(branchId, listener);
        }

        public void notifyBranch(String branchId, ChatMessage msg) {
            Consumer<ChatMessage> listener = branchListeners.get(branchId);
            if (listener != null) listener.accept(msg);
        }

        public boolean hasListener(String branchId) { return branchListeners.containsKey(branchId); }

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

        public String getSenderName() { return senderName; }
        public String getSenderBranch() { return senderBranch; }
        public String getContent() { return content; }
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
