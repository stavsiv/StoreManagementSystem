package Services;

import Exceptions.CustomExceptions;
import Server.Utils.FileUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class ChatService {

    private final Map<String, ChatSession> chatMap = new ConcurrentHashMap<>();
    private final Map<String, String> branchActiveChat = new ConcurrentHashMap<>();
    private final Map<String, Queue<ChatRequest>> waitingQueue = new ConcurrentHashMap<>();
    private final Map<String, Queue<String>> pendingChatInvites = new ConcurrentHashMap<>();
    private final Map<String, String> connectedUsers = new ConcurrentHashMap<>();
    private final Map<String, Consumer<String>> branchNotifyCallbacks = new ConcurrentHashMap<>();
    private final AtomicInteger chatCounter = new AtomicInteger(1000);
    private static final String CHAT_HISTORY_FILE = "Data/chat_history.json";

    // -------------------- Core Methods --------------------
    public Collection<ChatSession> listAllChats() { return chatMap.values(); }

    public synchronized String startChat(String branch1, String branch2, Consumer<String> notifyCallback1)
            throws CustomExceptions.ChatBranchBusyException {
        if (branchActiveChat.containsKey(branch1)) {
            throw new CustomExceptions.ChatBranchBusyException("You're already in another chat. Finish it before starting a new one.");
        }

        if (branchActiveChat.containsKey(branch2)) {
            storeInvite(branch2, branch1);
            String notifyMsg = "[NOTIFY] Branch " + branch2 + " is currently busy. Your chat invitation has been saved and will be delivered later.";
            if (notifyCallback1 != null) notifyCallback1.accept(notifyMsg);
            return notifyMsg;
        }

        if (!connectedUsers.containsKey(branch2)) {
            storeInvite(branch2, branch1);
            String notifyMsg = "[NOTIFY] Branch " + branch2 + " is currently offline. Your chat invitation has been saved and will be delivered later.";
            if (notifyCallback1 != null) notifyCallback1.accept(notifyMsg);
            return notifyMsg;
        }

        Consumer<String> notifyCallback2 = branchNotifyCallbacks.get(branch2);
        String chatId = createChat(branch1, branch2, notifyCallback1, notifyCallback2);
        ChatSession session = getChatById(chatId);
        ChatMessage systemMsg = new ChatMessage("SYSTEM", "SYSTEM",
                "Chat started between " + branch1 + " and " + branch2);
        session.addMessage(systemMsg);

        return "[NOTIFY] Chat started with " + branch2 + ". You can chat now! type SEND_MSG <Message...>. ChatID: " + chatId;
    }

    private String createChat(String branch1, String branch2, Consumer<String> notifyCallback1, Consumer<String> notifyCallback2) {
        String chatId = "CHAT-" + chatCounter.getAndIncrement();
        ChatSession session = new ChatSession(chatId, branch1, branch2);
        chatMap.put(chatId, session);
        branchActiveChat.put(branch1, chatId);
        branchActiveChat.put(branch2, chatId);

        if (notifyCallback1 != null)
            notifyCallback1.accept("You can now send messages to branch " + branch2 + ".");

        if (notifyCallback2 != null)
            notifyCallback2.accept("Branch " + branch1 + " invited you to chat. Type JOIN_CHAT " + chatId + " to join.");

        return chatId;
    }

    public void leaveChat(String chatId, String branchId) throws CustomExceptions.ChatException {
        ChatSession session = chatMap.get(chatId);
        if (session == null || !session.isActive())
            throw new CustomExceptions.ChatInactiveException("Chat " + chatId + " not found or inactive");

        if (!session.getBranchesInvolved().contains(branchId))
            throw new CustomExceptions.ChatException("Branch " + branchId + " not found in chat " + chatId);

        // Notify all others first
        ChatMessage leaveMsg = new ChatMessage(
                "SYSTEM",
                "SYSTEM",
                "Branch " + branchId + " has left chat " + chatId
        );
        session.broadcastToOthers(leaveMsg, branchId);

        // Remove branch
        session.removeBranch(branchId);
        branchActiveChat.remove(branchId);

        // Case 1: no participants left → close chat
        if (session.getBranchesInvolved().isEmpty()) {
            chatMap.remove(chatId);
            System.out.println("Chat " + chatId + " closed (no participants left)");
            return;
        }

        // Case 2: only one participant left → notify them they are alone
        if (session.getBranchesInvolved().size() == 1) {
            String remainingBranch = session.getBranchesInvolved().iterator().next();
            ChatMessage aloneMsg = new ChatMessage(
                    "SYSTEM",
                    "SYSTEM",
                    "You are the only participant left in chat " + chatId +
                            ". Type END_CHAT to close it."
            );
            session.notifyBranch(remainingBranch, aloneMsg);
        }
    }

    public synchronized void endChat(String chatId) {
        ChatSession session = chatMap.get(chatId);
        if (session == null || !session.isActive()) return;

        session.setActive(false);
        // Send end message to all participants
        ChatMessage endMsg = new ChatMessage("SYSTEM", "SYSTEM", "Chat " + chatId + " has ended.");
        session.getBranchesInvolved().forEach(b -> session.notifyBranch(b, endMsg));

        // Remove active chat status
        session.getBranchesInvolved().forEach(branchActiveChat::remove);

        // Remove chat from map
        chatMap.remove(chatId);

        // Notify queued branches
        session.getBranchesInvolved().forEach(this::notifyQueuedBranches);
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
            } catch (Exception e) {
                System.err.println("Failed to notify queued branch: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public Queue<String> getPendingInvites(String branchId) {
        return pendingChatInvites.getOrDefault(branchId, new ConcurrentLinkedQueue<>());
    }

    public void clearPendingInvites(String branchId) {
        pendingChatInvites.remove(branchId);
    }

    public ChatSession getChatById(String chatId) { return chatMap.get(chatId); }

    public void addConnectedUser(String branchId, String sessionId, Consumer<String> notifyCallback) {
        if (connectedUsers.containsKey(branchId)) {
            notifyCallback.accept("Your branch is already connected from another client.");
            return;
        }
        connectedUsers.put(branchId, sessionId);
        branchNotifyCallbacks.put(branchId, notifyCallback);

        Queue<String> invites = pendingChatInvites.get(branchId);
        if (invites != null && !invites.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            while (!invites.isEmpty()) sb.append(invites.poll()).append(" ");
            notifyCallback.accept("Pending chat invite from branch: " + sb.toString().trim());
            pendingChatInvites.remove(branchId);
        } else {
            notifyCallback.accept("No pending chat invites.");
        }
    }

    public void storeInvite(String targetBranch, String fromBranch) {
        pendingChatInvites.computeIfAbsent(targetBranch, _ -> new ConcurrentLinkedQueue<>()).add(fromBranch);
        Consumer<String> cb = branchNotifyCallbacks.get(targetBranch);
        if (cb != null) cb.accept( "Chat invite from " + fromBranch + ". You are currently busy. You can contact them once you’re available.");
    }

public void saveChatHistory(String chatId) {
    ChatSession session = getChatById(chatId);
    if (session == null) return;

    Map<String, Object> chatJson = new LinkedHashMap<>();
    chatJson.put("chatId", session.getChatId());
    chatJson.put("date", LocalDate.now().toString());
    chatJson.put("time", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
    List<String> messages = session.getMessages().stream()
            .map(msg -> msg.getSenderName() + " (" + msg.getSenderBranch() + "): " + msg.getContent())
            .toList();
    chatJson.put("messages", messages);

    List<String> allChatsJson = FileUtils.readJsonObjectsFromFile(CHAT_HISTORY_FILE);
    List<String> updatedChats = new ArrayList<>();

    for (String chat : allChatsJson) {
        if (chat != null && !chat.isBlank()) {
            updatedChats.add(chat);
        }
    }

    StringBuilder sb = new StringBuilder("{\n");
    sb.append("  \"chatId\": \"").append(chatJson.get("chatId")).append("\",\n");
    sb.append("  \"date\": \"").append(chatJson.get("date")).append("\",\n");
    sb.append("  \"time\": \"").append(chatJson.get("time")).append("\",\n");
    sb.append("  \"messages\": [\n");
    List<String> msgs = (List<String>) chatJson.get("messages");
    for (int i = 0; i < msgs.size(); i++) {
        sb.append("    \"").append(msgs.get(i).replace("\"", "\\\"")).append("\"");
        if (i < msgs.size() - 1) sb.append(",");
        sb.append("\n");
    }
    sb.append("  ]\n");
    sb.append("}");

    updatedChats.add(sb.toString());
    FileUtils.saveToFile(CHAT_HISTORY_FILE, updatedChats, s -> s);
}

    // -------------------- ChatSession --------------------
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

        public synchronized void addBranch(String branchId, Consumer<ChatMessage> listener) {
            branchesInvolved.add(branchId);
            branchListeners.put(branchId, listener);
        }

        public synchronized void removeBranch(String branchId) {
            branchesInvolved.remove(branchId);
            branchListeners.remove(branchId);
        }

        public void broadcastToOthers(ChatMessage msg, String excludingBranch) {
            for (String branch : branchesInvolved) {
                if (!branch.equals(excludingBranch)) notifyBranch(branch, msg);
            }
        }

        public void addMessage(ChatMessage msg) {
            messages.add(msg);
            branchListeners.values().forEach(listener -> listener.accept(msg));
        }

        public void notifyBranch(String branchId, ChatMessage msg) {
            Consumer<ChatMessage> listener = branchListeners.get(branchId);
            if (listener != null) listener.accept(msg);
        }

        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
        public Set<String> getBranchesInvolved() { return branchesInvolved; }
        public List<ChatMessage> getMessages() { return messages; }
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