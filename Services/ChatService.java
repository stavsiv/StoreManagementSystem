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

import Models.Role;

/**
 * Chat flow: queue → offer (60s) → assignee joins → requester joins (60s) → active →
 *   solo participant (120s auto-close) → end.
 *
 * All public methods are used by ClientHandler.
 * Naming is explicit, comments are concise, and behavior is deterministic.
 */
public class ChatService {

    // -------------------- Constants --------------------
    private final AtomicInteger chatCounter = new AtomicInteger(1000);
    private static final long OFFER_TIMEOUT_MS = 60_000L;        // assignee has 60s to ACCEPT
    private static final long REQUESTER_TIMEOUT_MS = 60_000L;    // requester has 60s to BEGIN
    private static final long SOLO_GRACE_MS = 120_000L;          // auto-close if only one side remains
    private static final String CHAT_FILE = "Data/chat_history.json";

    // -------------------- Value Types --------------------
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

    public static class ChatSession {
        private final String chatId;
        private final String requesterBranch; // requester branch
        private final String targetBranch; // assignee branch

        private volatile String assigneeSessionId; // session that accepted the offer

        private final Set<String> participants = ConcurrentHashMap.newKeySet();            // sessionIds
        private final Map<String, String> sessionIdToBranch = new ConcurrentHashMap<>();   // sessionId -> branchId
        private final Map<String, CopyOnWriteArrayList<ListenerRegistration>> listenersByBranch = new ConcurrentHashMap<>();
        private final List<ChatMessage> messages = Collections.synchronizedList(new ArrayList<>());

        private volatile boolean active = true;
        volatile ScheduledFuture<?> soloTimer;

        public ChatSession(String chatId, String requesterBranch, String targetBranch) {
            this.chatId = chatId;
            this.requesterBranch = requesterBranch;
            this.targetBranch = targetBranch;
        }

        public synchronized void addListener(String branchId, String sessionId, Consumer<ChatMessage> listener) {
            participants.add(sessionId);
            sessionIdToBranch.put(sessionId, branchId);
            listenersByBranch.computeIfAbsent(branchId, _ -> new CopyOnWriteArrayList<>())
                    .add(new ListenerRegistration(sessionId, listener));
        }

        public synchronized void removeListener(String branchId, String sessionId) {
            var list = listenersByBranch.get(branchId);
            if (list != null) {
                list.removeIf(reg -> Objects.equals(reg.sessionId, sessionId));
                if (list.isEmpty()) listenersByBranch.remove(branchId);
            }
            participants.remove(sessionId);
            sessionIdToBranch.remove(sessionId);
        }

        public void addMessage(ChatMessage msg) {
            messages.add(msg);
            for (CopyOnWriteArrayList<ListenerRegistration> list : listenersByBranch.values()) {
                for (ListenerRegistration reg : list) reg.callback.accept(msg);
            }
        }

        public String getBranchOfSession(String sessionId) { return sessionIdToBranch.get(sessionId); }
        public Set<String> getParticipants() { return participants; }
        public Set<String> getBranchesInvolved() { return listenersByBranch.keySet(); }
        public List<ChatMessage> getMessages() { return messages; }
        public String getChatId() { return chatId; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
        public String getAssigneeSessionId() { return assigneeSessionId; }
        public void setAssigneeSessionId(String sessionId) { this.assigneeSessionId = sessionId; }
        public String getRequesterBranch() { return requesterBranch; }
        public String getTargetBranch() { return targetBranch; }

        static final class ListenerRegistration {
            final String sessionId;
            final Consumer<ChatMessage> callback;
            ListenerRegistration(String sessionId, Consumer<ChatMessage> callback) {
                this.sessionId = sessionId; this.callback = callback;
            }
        }
    }

    public static class ChatRequest {
        final String requestId = UUID.randomUUID().toString();
        final String sourceEmployeeId;
        final String sourceBranch;
        final String targetBranch;
        final String note;
        final Consumer<String> notifyCallback; // direct callback to requester terminal
        int requesterAttachMisses = 0; // counts BEGIN no-shows (max 2)

        public ChatRequest(String sourceBranch, String sourceEmployeeId, String targetBranch, String note, Consumer<String> notifyCallback) {
            this.sourceBranch = sourceBranch;
            this.sourceEmployeeId = sourceEmployeeId;
            this.targetBranch = targetBranch;
            this.note = note;
            this.notifyCallback = notifyCallback;
        }
    }

    static final class ChatOffer {
        final ChatRequest chatRequest;
        String assigneeSessionId;
        volatile ScheduledFuture<?> offerTimeoutTask;
        ChatOffer(String assigneeSessionId, ChatRequest chatRequest) {
            this.assigneeSessionId = assigneeSessionId;
            this.chatRequest = chatRequest;
        }
    }

    // -------------------- State --------------------
    private final Map<String, ChatSession> chatIdToSession = new ConcurrentHashMap<>();
    private final Map<String, Queue<ChatRequest>> waitingRequestsByTargetBranch = new ConcurrentHashMap<>();
    private final Map<String, Deque<String>> idleSessionsByBranch = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> connectedSessionsByBranch = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> activeChatsBySession = new ConcurrentHashMap<>();
    private final Map<String, ChatOffer> pendingOffersByRequestId = new ConcurrentHashMap<>();
    private final Map<String, String> requestIdByAssigneeSession = new ConcurrentHashMap<>();
    private final Map<String, Consumer<String>> directNotifyBySession = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> requesterAttachTimersByChat = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private final ConcurrentHashMap<String, String> activeRequestDedupe = new ConcurrentHashMap<>();
    private static String makeRequestKey(String sourceEmployeeId, String sourceBranch, String targetBranch) {
        return sourceEmployeeId + "|" + sourceBranch + "|" + targetBranch;
    }

    private final Map<String, ChatRequest> requestByChatId = new ConcurrentHashMap<>();
    private final Map<String, String> sessionIdToEmployeeId = new ConcurrentHashMap<>();
    private final Map<String, String> assigneeEmployeeIdByChatId = new ConcurrentHashMap<>();
    private final Map<String, String> sessionDisplayBySessionId = new ConcurrentHashMap<>();

    // -------------------- Internal Helpers --------------------
    private String newChatId() { return "CHAT-" + chatCounter.getAndIncrement(); }

    private void cancelTimer(ScheduledFuture<?> future) { if (future != null) future.cancel(false); }
    private static String bold(String string) { return "\u001B[1m" + string + "\u001B[0m"; }

    private static String jsonEscape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"'  -> sb.append("\\\"");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }


    private void notifyParticipants(ChatSession chatSession, String payload) {
        List<String> snapshot = new ArrayList<>(chatSession.getParticipants());
        for (String sessionId : snapshot) {
            Consumer<String> callback = directNotifyBySession.get(sessionId);
            if (callback != null) callback.accept(payload);
        }
    }

    private synchronized void tryMatch(String targetBranch) {
        Queue<ChatRequest> queue = waitingRequestsByTargetBranch.get(targetBranch);
        if (queue == null) return;
        Deque<String> idleDeque = idleSessionsByBranch.get(targetBranch);
        if (idleDeque == null) return;

        while (!queue.isEmpty() && !idleDeque.isEmpty()) {
            ChatRequest chatRequest = queue.peek();
            String assigneeSessionId = idleDeque.pollFirst();
            if (assigneeSessionId == null) break;
            offerToAssignee(chatRequest, assigneeSessionId);
            queue.remove(chatRequest);
        }
    }

    private void offerToAssignee(ChatRequest chatRequest, String assigneeSessionId) {
        String requestId = chatRequest.requestId;

        if (requestIdByAssigneeSession.containsKey(assigneeSessionId)) {
            idleSessionsByBranch.getOrDefault(chatRequest.targetBranch, new ArrayDeque<>()).addLast(assigneeSessionId);
            return;
        }

        ChatOffer chatOffer = new ChatOffer(assigneeSessionId, chatRequest);
        pendingOffersByRequestId.put(requestId, chatOffer);
        requestIdByAssigneeSession.put(assigneeSessionId, requestId);

        Consumer<String> assigneeCallback = directNotifyBySession.get(assigneeSessionId);
        if (assigneeCallback != null) {
            String noteSuffix = (chatRequest.note == null || chatRequest.note.isBlank()) ? "" : (bold("(note): ") + chatRequest.note);
            assigneeCallback.accept("[OFFER] Incoming chat from " + chatRequest.sourceBranch + ". Use " + bold("ACCEPT") + " to accept the chat." + noteSuffix);
        }

        chatOffer.offerTimeoutTask = scheduler.schedule(() -> onOfferTimeout(requestId), OFFER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private void onOfferTimeout(String requestId) {
        ChatOffer chatOffer = pendingOffersByRequestId.remove(requestId);
        if (chatOffer == null) return;
        requestIdByAssigneeSession.remove(chatOffer.assigneeSessionId, requestId);
        setIdle(chatOffer.assigneeSessionId, true);
        waitingRequestsByTargetBranch.computeIfAbsent(chatOffer.chatRequest.targetBranch, _ -> new ConcurrentLinkedQueue<>()).add(chatOffer.chatRequest);
        tryMatch(chatOffer.chatRequest.targetBranch);
    }

    private void onRequesterAttachTimeout(ChatRequest chatRequest, ChatSession chatSession, String assigneeSessionId) {
        try {
            Set<String> chatsOfAssignee = activeChatsBySession.get(assigneeSessionId);
            if (chatsOfAssignee != null) chatsOfAssignee.remove(chatSession.getChatId());

            String targetBranch = chatSession.getBranchOfSession(assigneeSessionId);
            if (targetBranch == null) targetBranch = chatSession.getBranchesInvolved().stream().findFirst().orElse("");
            chatSession.addMessage(new ChatMessage("SYSTEM", targetBranch, "Requester didn't answer, cancelling the chat"));

            setIdle(assigneeSessionId, true);
            endSessionOnly(chatSession);

            chatRequest.requesterAttachMisses++;
            if (chatRequest.requesterAttachMisses < 2) {
                if (chatRequest.notifyCallback != null)
                    chatRequest.notifyCallback.accept("[INFO] You missed the window. Request re-queued.");
                waitingRequestsByTargetBranch.computeIfAbsent(chatRequest.targetBranch, _ -> new ConcurrentLinkedQueue<>()).add(chatRequest);
                tryMatch(chatRequest.targetBranch);
            } else {
                if (chatRequest.notifyCallback != null)
                    chatRequest.notifyCallback.accept("[INFO] Chat request for branch " + chatRequest.targetBranch + " was cancelled after " + chatRequest.requesterAttachMisses + " no-shows.");
                releaseActiveRequest(chatRequest);
            }
        } finally {
            cancelTimer(requesterAttachTimersByChat.remove(chatSession.getChatId()));
        }
    }

    private boolean hasPendingOrOffered(String employeeId, String sourceBranch, String targetBranch) {
        Queue<ChatRequest> queue = waitingRequestsByTargetBranch.getOrDefault(targetBranch, new ConcurrentLinkedQueue<>());
        boolean inQueue = queue.stream().anyMatch(req -> req.sourceEmployeeId.equals(employeeId) && req.sourceBranch.equals(sourceBranch));
        if (inQueue) return true;
        return pendingOffersByRequestId.values().stream().anyMatch(of ->
                of.chatRequest.sourceEmployeeId.equals(employeeId)
                        && of.chatRequest.sourceBranch.equals(sourceBranch)
                        && of.chatRequest.targetBranch.equals(targetBranch));
    }

    private void releaseActiveRequest(ChatRequest chatRequest) {
        String key = makeRequestKey(chatRequest.sourceEmployeeId, chatRequest.sourceBranch, chatRequest.targetBranch);
        activeRequestDedupe.remove(key, chatRequest.requestId);
    }

    private void endSessionOnly(ChatSession chatSession) {
        if (chatSession == null || !chatSession.isActive()) return;
        notifyParticipants(chatSession, "[NOTIFY] CHAT_ENDED " + chatSession.getChatId()); // matches ClientHandler callback
        chatSession.setActive(false);
        cancelTimer(requesterAttachTimersByChat.remove(chatSession.getChatId()));
        cancelTimer(chatSession.soloTimer);
        chatSession.soloTimer = null;

        ChatRequest origin = requestByChatId.remove(chatSession.getChatId());
        if (origin != null) releaseActiveRequest(origin);

        chatIdToSession.remove(chatSession.getChatId());
    }

    private boolean isInActiveChatOtherThan(String sessionId, String exceptChatId) {
        Set<String> activeChatSessions = activeChatsBySession.getOrDefault(sessionId, Collections.emptySet());
        if (activeChatSessions.isEmpty()) return false;
        for (String chatId : activeChatSessions) {
            if (!Objects.equals(chatId, exceptChatId)) {
                ChatSession chatSession = chatIdToSession.get(chatId);
                if (chatSession != null && chatSession.isActive()) return true;
            }
        }
        return false;
    }

    // -------------------- Public API (used by ClientHandler) --------------------
    public Collection<ChatSession> listAllChats() { return chatIdToSession.values(); }
    public ChatSession getChatById(String chatId) { return chatIdToSession.get(chatId); }
    public String displayOf(String sessionId) { return sessionDisplayBySessionId.getOrDefault(sessionId, sessionId); }
    


    public boolean hasOnlineInBranch(String branchId) {
    if (branchId == null) return false;
    Set<String> sessionId = connectedSessionsByBranch.get(branchId);
    return sessionId != null && !sessionId.isEmpty();
    }

    // Presence & readiness
    public void connect(String sessionId, String branchId, String employeeId, String display, Consumer<String> directNotify) {
        connectedSessionsByBranch.computeIfAbsent(branchId, _ -> ConcurrentHashMap.newKeySet()).add(sessionId);
        directNotifyBySession.put(sessionId, directNotify);
        sessionDisplayBySessionId.put(sessionId, display);
        sessionIdToEmployeeId.put(sessionId, employeeId);
        setIdle(sessionId, true);
    }

    public void disconnect(String sessionId) {
        connectedSessionsByBranch.values().forEach(set -> set.remove(sessionId));
        directNotifyBySession.remove(sessionId);

        Set<String> chats = activeChatsBySession.getOrDefault(sessionId, Collections.emptySet());
        for (String chatId : new ArrayList<>(chats)) {
            String branchId = null;
            ChatSession chatSession = chatIdToSession.get(chatId);
            if (chatSession != null) branchId = chatSession.getBranchOfSession(sessionId);
            leaveChatAsUser(chatId, branchId == null ? "" : branchId, sessionId);
        }

        idleSessionsByBranch.values().forEach(deque -> deque.remove(sessionId));

        String requestId = requestIdByAssigneeSession.remove(sessionId);
        if (requestId != null) {
            ChatOffer chatOffer = pendingOffersByRequestId.get(requestId);
            if (chatOffer != null && chatOffer.assigneeSessionId.equals(sessionId)) onOfferTimeout(requestId);
        }
        sessionIdToEmployeeId.remove(sessionId);
    }

    public void setIdle(String sessionId, boolean idle) {
        String branchId = connectedSessionsByBranch.entrySet().stream()
                .filter(e -> e.getValue().contains(sessionId))
                .map(Map.Entry::getKey).findFirst().orElse(null);
        if (branchId == null) return;

        Deque<String> idleDeque = idleSessionsByBranch.computeIfAbsent(branchId, _ -> new ConcurrentLinkedDeque<>());
        boolean isBusy = !activeChatsBySession.getOrDefault(sessionId, Collections.emptySet()).isEmpty();

        if (idle && !isBusy) {
            if (!idleDeque.contains(sessionId)) idleDeque.addLast(sessionId);
            tryMatch(branchId);
        } else {
            idleDeque.remove(sessionId);
        }
    }

    // Request life-cycle
    public void requestChatFromBranch(String sourceBranch, String sourceEmployeeId, String targetBranch, String note, Consumer<String> requesterNotify) {
        final String key = makeRequestKey(sourceEmployeeId, sourceBranch, targetBranch);
        String existing = activeRequestDedupe.putIfAbsent(key, "__PENDING__");
        if (existing != null) {
            if (requesterNotify != null) requesterNotify.accept("[ERROR] You already have a pending/active request to " + targetBranch + ".");
            return;
        }
        try {
            if (hasPendingOrOffered(sourceEmployeeId, sourceBranch, targetBranch)) {
                if (requesterNotify != null) requesterNotify.accept("[ERROR] You already have a pending/active request to " + targetBranch + ".");
                activeRequestDedupe.remove(key);
                return;
            }
            ChatRequest chatRequest = new ChatRequest(sourceBranch, sourceEmployeeId, targetBranch, note, requesterNotify);
            activeRequestDedupe.replace(key, chatRequest.requestId);
            waitingRequestsByTargetBranch.computeIfAbsent(targetBranch, _ -> new ConcurrentLinkedQueue<>()).add(chatRequest);
            tryMatch(targetBranch);
        } catch (RuntimeException e) {
            activeRequestDedupe.remove(key);
            throw e;
        }
    }

    public synchronized String acceptOfferByAssignee(String assigneeSessionId, Consumer<ChatMessage> assigneeListener) throws CustomExceptions.ChatException {
        String requestId = requestIdByAssigneeSession.remove(assigneeSessionId);
        if (requestId == null) throw new CustomExceptions.ChatException("No active offer for your session.");

        ChatOffer chatOffer = pendingOffersByRequestId.remove(requestId);
        if (chatOffer == null) throw new CustomExceptions.ChatException("Offer expired or reassigned.");
        if (!Objects.equals(chatOffer.assigneeSessionId, assigneeSessionId))
            throw new CustomExceptions.ChatException("You are not the assigned employee for this offer.");

        if (!activeChatsBySession.getOrDefault(assigneeSessionId, Collections.emptySet()).isEmpty())
            throw new CustomExceptions.ChatException("You are already in a chat.");

        cancelTimer(chatOffer.offerTimeoutTask);
        Deque<String> idleDeque = idleSessionsByBranch.get(chatOffer.chatRequest.targetBranch);
        if (idleDeque != null) idleDeque.remove(assigneeSessionId);

        String chatId = newChatId();
        String assigneeEmployeeId = sessionIdToEmployeeId.get(assigneeSessionId);
        ChatSession chatSession = new ChatSession(chatId, chatOffer.chatRequest.sourceBranch, chatOffer.chatRequest.targetBranch);
        chatIdToSession.put(chatId, chatSession);
        requestByChatId.put(chatId, chatOffer.chatRequest);
        if (assigneeEmployeeId != null) assigneeEmployeeIdByChatId.put(chatId, assigneeEmployeeId);

        chatSession.setAssigneeSessionId(assigneeSessionId);
        chatSession.addListener(chatOffer.chatRequest.targetBranch, assigneeSessionId, assigneeListener);
        activeChatsBySession.computeIfAbsent(assigneeSessionId, _ -> ConcurrentHashMap.newKeySet()).add(chatId);
        setIdle(assigneeSessionId, false);

        String assigneeDisplay = displayOf(assigneeSessionId);
        chatSession.addMessage(new ChatMessage("SYSTEM", chatOffer.chatRequest.targetBranch, "Assignee " + assigneeDisplay + " joined."));

        if (chatOffer.chatRequest.notifyCallback != null) {
            chatOffer.chatRequest.notifyCallback.accept("[" + chatOffer.chatRequest.targetBranch + " ACCEPTED] ChatID: " + chatId + ". Run: " + bold("BEGIN " + chatId) + " within 60 seconds.");
        }

        ScheduledFuture<?> requesterTimer = scheduler.schedule(
                () -> onRequesterAttachTimeout(chatOffer.chatRequest, chatSession, assigneeSessionId),
                REQUESTER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        requesterAttachTimersByChat.put(chatId, requesterTimer);

        return chatId;
    }

    public List<ChatSession> listJoinableChatsForEmployee(String employeeId, Role role, String branchId, String currentSessionId) {
        List<ChatSession> result = new ArrayList<>();
        for (ChatSession chatSession : chatIdToSession.values()) {
            if (!chatSession.isActive()) continue;
            if (currentSessionId != null && chatSession.getParticipants().contains(currentSessionId)) continue;

            boolean isOriginalRequester = false;
            ChatRequest request = requestByChatId.get(chatSession.getChatId());
            if (request != null) isOriginalRequester = Objects.equals(request.sourceEmployeeId, employeeId);

            boolean isOriginalAssignee = Objects.equals(assigneeEmployeeIdByChatId.get(chatSession.getChatId()), employeeId);
            boolean isShiftManagerOfChatBranches = role == Role.SHIFT_MANAGER &&
                    (Objects.equals(branchId, chatSession.requesterBranch) || Objects.equals(branchId, chatSession.targetBranch));

            if (isOriginalRequester || isOriginalAssignee || isShiftManagerOfChatBranches) result.add(chatSession);
        }
        return result;
    }

    public void markRequesterAttached(String chatId, String requesterEmployeeId, String requesterSessionId, String requesterBranch, Consumer<ChatMessage> requesterListener)
            throws CustomExceptions.ChatException {
        ChatSession chatSession = chatIdToSession.get(chatId);
        if (chatSession == null || !chatSession.isActive()) throw new CustomExceptions.ChatException("Chat not found or inactive.");

        ChatRequest request = requestByChatId.get(chatId);
        if (request == null) throw new CustomExceptions.ChatException("Chat has no originating request.");
        if (!Objects.equals(request.sourceEmployeeId, requesterEmployeeId))
            throw new CustomExceptions.ChatException("Only the original requester can join with "+bold("BEGIN"));

        if (isInActiveChatOtherThan(requesterSessionId, chatId))
            throw new CustomExceptions.ChatException("You’re already connected to another active chat. Please leave or end it before joining this one.");

        if (!Objects.equals(requesterBranch, chatSession.requesterBranch) && !Objects.equals(requesterBranch, chatSession.targetBranch))
            throw new CustomExceptions.ChatException("Branch not allowed to join this chat.");

        chatSession.addListener(requesterBranch, requesterSessionId, requesterListener);
        activeChatsBySession.computeIfAbsent(requesterSessionId, _ -> ConcurrentHashMap.newKeySet()).add(chatId);
        setIdle(requesterSessionId, false);

        Deque<String> requesterIdle = idleSessionsByBranch.get(requesterBranch);
        if (requesterIdle != null) requesterIdle.remove(requesterSessionId);

        cancelTimer(requesterAttachTimersByChat.remove(chatId));
        cancelTimer(chatSession.soloTimer);
        chatSession.soloTimer = null;
    }

    public void joinExistingChatAuthorized(String chatId, String employeeId, Role role, String branchId, String sessionId, Consumer<ChatMessage> listener)
            throws CustomExceptions.ChatException {
        ChatSession chatSession = chatIdToSession.get(chatId);
        if (chatSession == null || !chatSession.isActive()) throw new CustomExceptions.ChatException("Chat not found or inactive.");

        boolean isShiftManagerOfChatBranches = role == Role.SHIFT_MANAGER &&
                (Objects.equals(branchId, chatSession.requesterBranch) || Objects.equals(branchId, chatSession.targetBranch));
        ChatRequest request = requestByChatId.get(chatId);
        boolean isOriginalRequester = (request != null && Objects.equals(request.sourceEmployeeId, employeeId));
        String assigneeEmployeeId = assigneeEmployeeIdByChatId.get(chatId);
        boolean isOriginalAssignee = Objects.equals(assigneeEmployeeId, employeeId);

        if (!(isShiftManagerOfChatBranches || isOriginalRequester || isOriginalAssignee))
            throw new CustomExceptions.ChatException("Not authorized to join this chat.");

        chatSession.addListener(branchId, sessionId, listener);
        activeChatsBySession.computeIfAbsent(sessionId, _ -> ConcurrentHashMap.newKeySet()).add(chatId);
        setIdle(sessionId, false);

        cancelTimer(chatSession.soloTimer);
        chatSession.soloTimer = null;
    }

    public void sendMessage(String chatId, String fromSessionId, String senderName, String text) throws CustomExceptions.ChatException {
        ChatSession chatSession = chatIdToSession.get(chatId);
        if (chatSession == null || !chatSession.isActive()) throw new CustomExceptions.ChatException("Chat not found or inactive.");
        String branchId = chatSession.getBranchOfSession(fromSessionId);
        if (branchId == null) throw new CustomExceptions.ChatException("You are not a participant of this chat.");
        chatSession.addMessage(new ChatMessage(senderName, branchId, text));
    }

    public void leaveChatAsUser(String chatId, String branchId, String sessionId) {
        ChatSession chatSession = chatIdToSession.get(chatId);
        if (chatSession == null) return;

        chatSession.removeListener(branchId, sessionId);
        Set<String> chatSet = activeChatsBySession.get(sessionId);
        if (chatSet != null) chatSet.remove(chatId);

        if (chatSession.getParticipants().isEmpty()) {
            endSessionOnly(chatSession);
            return;
        }

        if (chatSession.getBranchesInvolved().size() == 1) {
            chatSession.addMessage(new ChatMessage("SYSTEM", branchId, "Peer left. Chat will auto-close in 2 minutes unless someone rejoins."));
            cancelTimer(chatSession.soloTimer);
            chatSession.soloTimer = scheduler.schedule(() -> {
                saveChatHistory(chatSession.getChatId());
                chatSession.addMessage(new ChatMessage("SYSTEM", branchId, "Chat auto-closed, Chat history saved."));
                endSessionOnly(chatSession);
            }, SOLO_GRACE_MS, TimeUnit.MILLISECONDS);
        }

        setIdle(sessionId, true);
    }

    public void endChat(String chatId) {
        ChatSession chatSession = chatIdToSession.get(chatId);
        if (chatSession == null) return;

        notifyParticipants(chatSession, "[NOTIFY] CHAT_ENDED " + chatId);
        endSessionOnly(chatSession);

        for (String sessionId : new ArrayList<>(chatSession.getParticipants())) {
            Set<String> set = activeChatsBySession.get(sessionId);
            if (set != null) set.remove(chatId);
            setIdle(sessionId, true);
        }
    }

    public void saveChatHistory(String chatId) {
        ChatSession chatSession = getChatById(chatId);
        if (chatSession == null) return;

        String date = LocalDate.now().toString();
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));

        
        List<String> msgLiterals = new ArrayList<>();
        for (ChatMessage m : chatSession.getMessages()) {
            String line = m.getSenderName() + " (" + m.getSenderBranch() + "): " + m.getContent();
            msgLiterals.add("    \"" + jsonEscape(line) + "\"");
        }

        
        String newObject =
            "{\n" +
            "  \"chatId\": \"" + jsonEscape(chatSession.getChatId()) + "\",\n" +
            "  \"date\": \"" + jsonEscape(date) + "\",\n" +
            "  \"time\": \"" + jsonEscape(time) + "\",\n" +
            "  \"messages\": [\n" +
                String.join(",\n", msgLiterals) + "\n" +
            "  ]\n" +
            "}";

        
        List<String> existing = FileUtils.readJsonObjectsFromFile(CHAT_FILE);
        List<String> cleaned = new ArrayList<>();

        for (String obj : existing) {
            if (obj == null) continue;
            String t = obj.trim();
            if (t.isEmpty()) continue;

            
            if (t.equals("[") || t.equals("]") || t.equals(",")) continue;

            
            if (t.startsWith(",")) t = t.substring(1).trim();
            if (t.endsWith(","))   t = t.substring(0, t.length() - 1).trim();

            if (!t.isEmpty()) cleaned.add(t);
        }

        
        cleaned.add(newObject);


        FileUtils.saveToFile(CHAT_FILE, cleaned, s -> s);
    }
}