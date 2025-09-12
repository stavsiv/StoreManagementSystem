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
 * Chat flow: queue → offer → assignee joins → requester joins → active → end.
 * Timers: offer 60s, requester 60s, solo 120s.
 * Commands enter via Server/ClientHandler calling the public API below.
 */
public class ChatService {

    // -------------------- Constants & debug --------------------
    //private static void dbg(String msg) { System.out.println("[CHAT-DBG] " + msg); }
    private final AtomicInteger chatCounter = new AtomicInteger(1000);
    private static final long OFFER_TIMEOUT_MS = 60_000L;
    private static final long REQUESTER_TIMEOUT_MS = 60_000L;
    private static final long SOLO_GRACE_MS = 120_000L;
    private  static final String CHAT_FILE= "Data/chat_history.json";

    public static class ChatSession {
        private final String chatId;
        private final String branchA;                      // fixed at creation
        private final String branchB;                      // fixed at creation
        // null until requester joins
        private volatile String assigneeSessionId;    // set at accept time

        /**
         * Key = sessionId (String)
         */

        private final Set<String> participants = ConcurrentHashMap.newKeySet();

        /**
         * Key = sessionId (String)
         * Value = branchId (String)
         */

        private final Map<String, String> sessionBranch = new ConcurrentHashMap<>();
        private final Map<String, CopyOnWriteArrayList<ListenerReg>> listenersByBranch = new ConcurrentHashMap<>();
        private final List<ChatMessage> messages = Collections.synchronizedList(new ArrayList<>());
        private volatile boolean active = true;
        volatile ScheduledFuture<?> soloTimer;

        public ChatSession(
            String chatId, 
            String branchA, 
            String branchB) {
            this.chatId = chatId;
            this.branchA = branchA;
            this.branchB = branchB;
            // who asked originally
            // who accepted
        }

        public synchronized void addListener(String branchId, String sessionId, Consumer<ChatMessage> listener) {
            participants.add(sessionId);
            sessionBranch.put(sessionId, branchId);
            listenersByBranch.computeIfAbsent(branchId, _ -> new CopyOnWriteArrayList<>())
                    .add(new ListenerReg(sessionId, listener));
        }

        public synchronized void removeListener(String branchId, String sessionId) {
            var list = listenersByBranch.get(branchId);
            if (list != null) {
                list.removeIf(reg -> Objects.equals(reg.sessionId, sessionId));
                if (list.isEmpty()) listenersByBranch.remove(branchId);
            }
            participants.remove(sessionId);
            sessionBranch.remove(sessionId);
        }

//        public void broadcastToOthers(ChatMessage msg, String excludingBranch) {
//            for (String branch : listenersByBranch.keySet()) {
//                if (!branch.equals(excludingBranch)) notifyBranch(branch, msg);
//            }
//        }

        public void addMessage(ChatMessage msg) {
            messages.add(msg);
            for (CopyOnWriteArrayList<ListenerReg> list : listenersByBranch.values()) {
                for (ListenerReg reg : list) reg.callback.accept(msg);
            }
        }

//        public void notifyBranch(String branchId, ChatMessage msg) {
//            var list = listenersByBranch.get(branchId);
//            if (list != null) list.forEach(reg -> reg.callback.accept(msg));
//        }

        public String getBranchOfSession(String sessionId) { return sessionBranch.get(sessionId); }
        public Set<String> getParticipants() { return participants; }
        public Set<String> getBranchesInvolved() { return listenersByBranch.keySet(); }
        public List<ChatMessage> getMessages() { return messages; }
        public String getChatId() { return chatId; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
       // public String getRequesterEmployeeId() { return requesterEmployeeId; }
      //  public String getAssigneeEmployeeId()  { return assigneeEmployeeId; }
       // public String getRequesterSessionId()  { return requesterSessionId; }
        public String getAssigneeSessionId()   { return assigneeSessionId; }
        public void setRequesterSessionId() {
        }
        public void setAssigneeSessionId(String sessionId)  { this.assigneeSessionId  = sessionId; }

        static final class ListenerReg {
            final String sessionId;
            final Consumer<ChatMessage> callback;
            ListenerReg(String sessionId, Consumer<ChatMessage> c) { this.sessionId = sessionId; this.callback = c; }
        }
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
        final String requestId = UUID.randomUUID().toString();
        final String sourceEmployeeId, sourceBranch, targetBranch, note;
        final Consumer<String> notifyCallback;
        //LocalDateTime createdAt = LocalDateTime.now();
        int attempts = 0;         // drop after 2 misses

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
        String assigneeSessionId;        // may change on reassignment
        volatile ScheduledFuture<?> timeoutTask;

        // volatile String chatId;
        // volatile long expiresAtMs;
        // volatile boolean sourceAttached = false;
        // volatile boolean targetAttached = false;
        // volatile OfferPhase phase;     // << NEW: which side are we waiting for?

        ChatOffer( String assigneeSessionId,ChatRequest chatRequest) {
            this.assigneeSessionId = assigneeSessionId;
            this.chatRequest = chatRequest;
        }

    }

    // -------------------- Data Structures --------------------
    /**
     * Active chat sessions.
     * Key = chatId (String, format "C####")
     * Value = ChatSession
     * This map only holds chats that are still active; ended chats are always removed.
     */
    private final Map<String, ChatSession> chatMap = new ConcurrentHashMap<>();

    /**
     * One FIFO queue of waiting chat requests for each target branch.
     * Key = branchId (String, e.g. "B001")
     * Value = Queue of ChatRequest objects destined for that branch
     * Only requests that have not yet been assigned are kept here.
     */
    private final Map<String, Queue<ChatRequest>> waitingQueue = new ConcurrentHashMap<>();

    /**
     * Availability pool of idle employees, per branch, for auto-assignment.
     * Key = branchId (String)
     * Value = Deque of sessionIds (String)
     * The deque is used to pick the next available employee fairly (FIFO/LIFO depending on policy).
     */
    private final Map<String, Deque<String>> branchIdleSessions = new ConcurrentHashMap<>();
    
    /**
     * Online presence, grouped by branch.
     * Key = branchId (String)
     * Value = Set of sessionIds (String) currently connected under that branch
     * Updated on login/logout or disconnect.
     */
    private final Map<String, Set<String>> connectedUsers = new ConcurrentHashMap<>();

    /**
     * Tracks active chats per employee.
     * Key = sessionId or employeeId (String)
     * Value = Set of chatIds (String) that this employee is currently participating in
     * Used to enforce capacity (e.g., "max 1 chat per employee").
     */
    private final Map<String, Set<String>> employeeActiveChats = new ConcurrentHashMap<>();
     
    /**
     * In-flight offers while waiting up to 60 seconds for the assignee to accept.
     * Key = requestId (String)
     * Value = ChatOffer object tracking the pending assignment
     * Cleared once accepted, declined, or timed out.
     */
    private final Map<String/*requestId*/, ChatOffer> pendingOffers = new ConcurrentHashMap<>();

    /**
     * Key = assigneeSessionId (String). Value = requestId (String).
     * Fast lookup: maps an assignee sessionId -> active requestId 
     * Used to find the ChatOffer when an assignee accepts/declines an offer.
     * Entries are added when an offer is created and removed when the offer is accepted, declined, reassigned or expires.
     */
    private final Map<String/*assigneeSessionId*/, String/*requestId*/> offerIdByAssignee = new ConcurrentHashMap<>();
    
    /**
     * Direct notification channel per session.
     * Key = sessionId (String)
     * Value = Consumer<String> callback to push messages directly to that session
     * Used for targeted events (offers, system messages).
     */
    private final Map<String, Consumer<String>> sessionNotify = new ConcurrentHashMap<>();
       
    private final Map<String, ScheduledFuture<?>> requesterAttachTimers = new ConcurrentHashMap<>();

    /**
     * Global scheduler for all timeouts and delayed tasks.
     * Single-threaded ScheduledExecutorService.
     * Used for offer timeouts (60s), requester join timeout (60s),
     * and solo-chat auto-close (120s).
     */
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    /**
     * One active attempt per (requester, sourceBranch, targetBranch)
     * key = requesterId|sourceBranch|targetBranch, value = requestId
     */
    private final ConcurrentHashMap<String, String> activeRequests = new ConcurrentHashMap<>();

    private static String makeRequestKey(String sourceEmployeeId, String sourceBranch, String targetBranch) {
        return sourceEmployeeId + "|" + sourceBranch + "|" + targetBranch;
    }

    // Track which request created a chat, so we can release the dedupe when the chat ends
    private final Map<String, ChatRequest> requestByChat = new ConcurrentHashMap<>();

    // add alongside other maps
    /** 
     * sessionId -> employeeId
     */
    private final Map<String, String> sessionToEmployee = new ConcurrentHashMap<>(); 
    /**
     * chatId -> assigneeEmployeeId
     */
    private final Map<String, String> assigneeEmployeeByChat = new ConcurrentHashMap<>();
    private final Map<String, String> sessionDisplay = new ConcurrentHashMap<>();

    // --------------------- Internal helpers --------------------
    // Matching & offer management
    private synchronized void tryMatch(String targetBranch) {
        Queue<ChatRequest> q = waitingQueue.get(targetBranch);
        if (q == null) return;

        Deque<String> idle = branchIdleSessions.get(targetBranch);
        if (idle == null) return;

        while (!q.isEmpty() && !idle.isEmpty()) {
            ChatRequest chatRequest = q.peek();     // don’t remove until we secure an offer
            String assignee = idle.pollFirst();    // take one idle

            if (assignee == null) break;
            offerToAssignee(chatRequest, assignee);
            q.remove(chatRequest);                  // now take it out of queue (it’s in offer)
        }
    }

    private void offerToAssignee(ChatRequest chatRequest, String assigneeSessionId) {
        String requestId = chatRequest.requestId;

        // if assignee already holds a different offer, skip (shouldn’t happen with our maps, but safe)
        if (offerIdByAssignee.containsKey(assigneeSessionId)) {
            branchIdleSessions.getOrDefault(chatRequest.targetBranch, new ArrayDeque<>()).addLast(assigneeSessionId);
            return;
        }

        ChatOffer offer = new ChatOffer(assigneeSessionId, chatRequest);
        pendingOffers.put(requestId, offer);
        offerIdByAssignee.put(assigneeSessionId, requestId);

        // notify assignee
        Consumer<String> cb = sessionNotify.get(assigneeSessionId);
        if (cb != null) {
            cb.accept("[OFFER] Incoming chat from " + chatRequest.sourceBranch + ". Use ACCEPT to accept the chat." +
                    (chatRequest.note == null || chatRequest.note.isBlank() ? "" : (" – " + chatRequest.note)));
        }

        // start 60s offer timeout (assignee didn’t click ACCEPT)
        offer.timeoutTask = scheduler.schedule(() -> onOfferTimeout(requestId),
                                            OFFER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private void onOfferTimeout(String requestId) {
        ChatOffer offer = pendingOffers.remove(requestId);
        if (offer == null) return; // accepted/reassigned already

        // clear assignee mapping
        offerIdByAssignee.remove(offer.assigneeSessionId, requestId);

        setIdle(offer.assigneeSessionId, true);

        // DO NOT increment req.attempts here (assignee’s fault, not requester’s)
        // If another idle exists, try match immediately; otherwise requeue the same request
        waitingQueue.computeIfAbsent(offer.chatRequest.targetBranch, _ -> new ConcurrentLinkedQueue<>()).add(offer.chatRequest);
        tryMatch(offer.chatRequest.targetBranch);
    }

    // Requester attach handling
    private void onRequesterAttachTimeout(ChatRequest chatRequest, ChatSession chatSession, String assigneeSessionId) {
        try {
            // free assignee
            Set<String> set = employeeActiveChats.get(assigneeSessionId);
            if (set != null) set.remove(chatSession.getChatId());

            // add a SYSTEM message before ending the chat
            String branch = chatSession.getBranchOfSession(assigneeSessionId);
            if (branch == null) {
                Set<String> branches = chatSession.getBranchesInvolved();
                branch = branches.stream().findFirst().orElse("");
            }
            chatSession.addMessage(new ChatMessage("SYSTEM", branch, "Requester didn't answer, cancelling the chat"));

            setIdle(assigneeSessionId, true);
            // end session
            endSessionOnly(chatSession);

            // this miss counts against requester
            chatRequest.attempts++;
            if (chatRequest.attempts < 2) { // MAX_ATTACH_MISSES
                if (chatRequest.notifyCallback != null)
                    chatRequest.notifyCallback.accept("[INFO] You missed the window. Request re-queued.");
                waitingQueue.computeIfAbsent(chatRequest.targetBranch, _ -> new ConcurrentLinkedQueue<>()).add(chatRequest);
                tryMatch(chatRequest.targetBranch);
            } else {
                if (chatRequest.notifyCallback != null)
                    chatRequest.notifyCallback.accept("[INFO] Chat request for branch" + chatRequest.targetBranch + " was cancelled after" + chatRequest.attempts + " no-shows.");
                releaseActiveRequest(chatRequest); // frees dedupe key
            }
        } finally {
            cancelTimer(requesterAttachTimers.remove(chatSession.getChatId()));
        }
    }

    // Cleanup & utilities
    private void endSessionOnly(ChatSession chatSession) {
        if (chatSession == null || !chatSession.isActive()) return;
        notifyParticipants(chatSession, "[EVENT] CHAT_ENDED " + chatSession.getChatId());
        chatSession.setActive(false);
        cancelTimer(requesterAttachTimers.remove(chatSession.getChatId())); // add this line
        cancelTimer(chatSession.soloTimer);
        chatSession.soloTimer = null;

        ChatRequest request = requestByChat.remove(chatSession.getChatId());
        if (request != null) releaseActiveRequest(request);

        chatMap.remove(chatSession.getChatId());
    }

    private void cancelTimer(ScheduledFuture<?> f) { if (f != null) f.cancel(false); }

    private String newChatId() {
        // e.g., "C" + zero-padded counter; or UUID substring
        return "CHAT-" + chatCounter.getAndIncrement();
    }

    private boolean hasPendingOrOffered(String emp, String src, String tgt) {
        // queue
        Queue<ChatRequest> queue = waitingQueue.getOrDefault(tgt, new ConcurrentLinkedQueue<>());
        boolean inQueue = queue.stream().anyMatch(request ->
            request.sourceEmployeeId.equals(emp) && request.sourceBranch.equals(src));
        if (inQueue) return true;

        // offers
        return pendingOffers.values().stream().anyMatch(of ->
            of.chatRequest.sourceEmployeeId.equals(emp) && of.chatRequest.sourceBranch.equals(src) && of.chatRequest.targetBranch.equals(tgt));
    }

    private void releaseActiveRequest(ChatRequest chatRequest) {
        String key = makeRequestKey(chatRequest.sourceEmployeeId, chatRequest.sourceBranch, chatRequest.targetBranch);
        activeRequests.remove(key, chatRequest.requestId);
    }


    private void notifyParticipants(ChatSession chatSession, String payload) {
        // snapshot first (participants can change during cleanup)
        List<String> snapshot = new ArrayList<>(chatSession.getParticipants());
        for (String sessionId : snapshot) {
            Consumer<String> callback = sessionNotify.get(sessionId);
            if (callback != null) callback.accept(payload);
        }
    }

    // -------------------- Core API Methods --------------------
    public Collection<ChatSession> listAllChats() { return chatMap.values(); }
    public ChatSession getChatById(String chatId) { return chatMap.get(chatId); }
    public String displayOf(String sessionId) { return sessionDisplay.getOrDefault(sessionId, sessionId); }

    // 0) Presence & readiness
    public void connect(String sessionId, String branchId, String employeeId,String display, Consumer<String> directNotify) {
        connectedUsers.computeIfAbsent(branchId, _ -> ConcurrentHashMap.newKeySet()).add(sessionId);
        sessionNotify.put(sessionId, directNotify);
        sessionDisplay.put(sessionId, display);
        sessionToEmployee.put(sessionId, employeeId);
        // optional: auto-idle on connect
        setIdle(sessionId, true);
    }

    public void disconnect(String sessionId) {
        // remove from connected
        connectedUsers.values().forEach(set -> set.remove(sessionId));
        sessionNotify.remove(sessionId);

        // if in a chat, behave like leave
        Set<String> chats = employeeActiveChats.getOrDefault(sessionId, Collections.emptySet());
        for (String chatId : new ArrayList<>(chats)) {
            String branchId = null;
            ChatSession chatSession = chatMap.get(chatId);
            if (chatSession != null) branchId = chatSession.getBranchOfSession(sessionId);
            leaveChatAsUser(chatId, branchId == null ? "" : branchId, sessionId);
        }

        // remove from idle pool
        branchIdleSessions.values().forEach(deque -> deque.remove(sessionId));

        // if had an outstanding offer, let it timeout naturally or clean it
        String reqId = offerIdByAssignee.remove(sessionId);
        if (reqId != null) {
            ChatOffer offer = pendingOffers.get(reqId);
            if (offer != null && offer.assigneeSessionId.equals(sessionId)) {
                // optional: immediately try to reassign
                onOfferTimeout(reqId);
            }
        }
        sessionToEmployee.remove(sessionId); 
    }

    public void setIdle(String sessionId, boolean idle) {
        // find the user's branch via presence maps
        String branchId = connectedUsers.entrySet().stream()
            .filter(e -> e.getValue().contains(sessionId))
            .map(Map.Entry::getKey).findFirst().orElse(null);
        if (branchId == null) return;
        Deque<String> branchIdQueue = branchIdleSessions.computeIfAbsent(branchId, _ -> new ConcurrentLinkedDeque<>());
        boolean isBusy = !employeeActiveChats.getOrDefault(sessionId, Collections.emptySet()).isEmpty();
        if (idle && !isBusy) {
            // only idle if not in any chat
            if (!branchIdQueue.contains(sessionId)) branchIdQueue.addLast(sessionId);
            tryMatch(branchId);
        } else {
            // either explicitly not idle, or busy → must not be in idle pool
            branchIdQueue.remove(sessionId);
        }
    }

    // 1) Request life-cycle
    public void requestChatFromBranch(String sourceBranch, String sourceEmployeeId, String targetBranch, String note, Consumer<String> requesterNotify) {
        final String key = makeRequestKey(sourceEmployeeId, sourceBranch, targetBranch);

        // dedupe (one active attempt per key)
        String existing = activeRequests.putIfAbsent(key, "__PENDING__");
        if (existing != null) {
            if (requesterNotify != null)
                requesterNotify.accept("[ERROR] You already have a pending/active request to " + targetBranch + ".");
            return;
        }

        try {
            // also defensive check against queue/offers
            if (hasPendingOrOffered(sourceEmployeeId, sourceBranch, targetBranch)) {
                if (requesterNotify != null)
                    requesterNotify.accept("[ERROR] You already have a pending/active request to " + targetBranch + ".");
                activeRequests.remove(key);
                return;
            }

            ChatRequest chatRequest = new ChatRequest(sourceBranch, sourceEmployeeId, targetBranch, note, requesterNotify);
            activeRequests.replace(key, chatRequest.requestId);

            waitingQueue.computeIfAbsent(targetBranch, _ -> new ConcurrentLinkedQueue<>()).add(chatRequest);
            tryMatch(targetBranch);

        } catch (RuntimeException e) {
            activeRequests.remove(key);
            throw e;
        }
    }

    public synchronized String acceptOfferByAssignee(
        String sessionId,
        Consumer<ChatMessage> assigneeListener) throws CustomExceptions.ChatException {

        String requestId = offerIdByAssignee.remove(sessionId);
        if (requestId == null) throw new CustomExceptions.ChatException("No active offer for your session.");

        ChatOffer offer = pendingOffers.remove(requestId);
        if (offer == null) throw new CustomExceptions.ChatException("Offer expired or reassigned.");
        if (!Objects.equals(offer.assigneeSessionId, sessionId))
            throw new CustomExceptions.ChatException("You are not the assigned employee for this offer.");

        // capacity: one chat per employee
        if (!employeeActiveChats.getOrDefault(sessionId, Collections.emptySet()).isEmpty())
            throw new CustomExceptions.ChatException("You are already in a chat.");

        // stop offer timeout
        cancelTimer(offer.timeoutTask);

        // remove from idle pool for target branch
        Deque<String> idle = branchIdleSessions.get(offer.chatRequest.targetBranch);
        if (idle != null) idle.remove(sessionId);

        // create chat & attach assignee
        String chatId = newChatId();
        String assigneeEmpId = sessionToEmployee.get(sessionId);           // resolve employee
        ChatSession chatSession = new ChatSession(
            chatId, 
            offer.chatRequest.sourceBranch, 
            offer.chatRequest.targetBranch
        );
        chatMap.put(chatId, chatSession);
        requestByChat.put(chatId, offer.chatRequest); // track which request created this chat

        // remember assignee’s employeeId for later re-joins
        if (assigneeEmpId != null) assigneeEmployeeByChat.put(chatId, assigneeEmpId); 

        
        chatSession.setAssigneeSessionId(sessionId);
        chatSession.addListener(offer.chatRequest.targetBranch, sessionId, assigneeListener);
        employeeActiveChats.computeIfAbsent(sessionId, _ -> ConcurrentHashMap.newKeySet()).add(chatId);
        setIdle(sessionId, false); // assignee is now busy
        String assigneeDisplay = displayOf(sessionId);
        chatSession.addMessage(new ChatMessage("SYSTEM", offer.chatRequest.targetBranch, "Assignee " +assigneeDisplay+ " joined."));

        // tell requester to join
        if (offer.chatRequest.notifyCallback != null) {
            offer.chatRequest.notifyCallback.accept(
                "["+ offer.chatRequest.targetBranch +" ACCEPTED] ChatID: " + chatId + ". Run: BEGIN " + chatId + " within 60 seconds."
            );
        }
        // Consumer<String> assigneeCb = sessionNotify.get(sessionId);
        // if (assigneeCb != null) assigneeCb.accept("[WAITING] Up to 60s for requester to join. ChatID: " + chatId);

        // start requester-attach timer (THIS counts against requester)
        ScheduledFuture<?> t = scheduler.schedule(
            () -> onRequesterAttachTimeout(offer.chatRequest, chatSession, sessionId),
            REQUESTER_TIMEOUT_MS, TimeUnit.MILLISECONDS
        );

        requesterAttachTimers.put(chatId, t);

        return chatId;
    }

    public List<ChatSession> listJoinableChatsForEmployee(
            String employeeId, Role role, String branchId, String currentSessionId) {

        List<ChatSession> result = new ArrayList<>();
        for (ChatSession chatSession : chatMap.values()) {
            if (!chatSession.isActive()) continue;

            // already in this chat from this TCP session? (skip listing)
            if (currentSessionId != null && chatSession.getParticipants().contains(currentSessionId)) continue;

            // who’s allowed to re-join?
            boolean isOriginalRequester = false;
            ChatRequest req = requestByChat.get(chatSession.getChatId());
            if (req != null) isOriginalRequester = Objects.equals(req.sourceEmployeeId, employeeId);

            boolean isOriginalAssignee = Objects.equals(assigneeEmployeeByChat.get(chatSession.getChatId()), employeeId);
            boolean isShiftMgrOfChatBranches =
                    role == Role.SHIFT_MANAGER &&
                    (Objects.equals(branchId, chatSession.branchA) || Objects.equals(branchId, chatSession.branchB));

            if (isOriginalRequester || isOriginalAssignee || isShiftMgrOfChatBranches) {
                result.add(chatSession);
            }
        }
        return result;
    }

    private boolean isInActiveChatOtherThan(String sessionId, String exceptChatId) {
        Set<String> chats = employeeActiveChats.getOrDefault(sessionId, Collections.emptySet());
        if (chats.isEmpty()) return false;
        for (String id : chats) {
            if (!Objects.equals(id, exceptChatId)) {
                ChatSession chatSession = chatMap.get(id);
                if (chatSession != null && chatSession.isActive()) return true;
            }
        }
        return false;
    }

    public void markRequesterAttached(String chatId, String requesterEmployeeId, String requesterSessionId, String requesterBranch, Consumer<ChatMessage> requesterListener)
            throws CustomExceptions.ChatException {

        ChatSession chatSession = chatMap.get(chatId);
        if (chatSession == null || !chatSession.isActive()) throw new CustomExceptions.ChatException("Chat not found or inactive.");

        ChatRequest req = requestByChat.get(chatId);
        if (req == null) throw new CustomExceptions.ChatException("Chat has no originating request.");

        if (!Objects.equals(req.sourceEmployeeId, requesterEmployeeId))
            throw new CustomExceptions.ChatException("Only the original requester can join with BEGIN.");

        // >>> NEW: block requester if busy elsewhere
        if (isInActiveChatOtherThan(requesterSessionId, chatId)) {
            throw new CustomExceptions.ChatException(
                "You’re already connected to another active chat. Please leave or end it before joining this one."
            );
        }

        // Must join from one of the chat’s branches (requester is branchA)
        if (!Objects.equals(requesterBranch, chatSession.branchA) && !Objects.equals(requesterBranch, chatSession.branchB))
            throw new CustomExceptions.ChatException("Branch not allowed to join this chat.");

        // attach + mark busy + ensure not idle
        chatSession.addListener(requesterBranch, requesterSessionId, requesterListener);
        chatSession.setRequesterSessionId();

        employeeActiveChats.computeIfAbsent(requesterSessionId, _ -> ConcurrentHashMap.newKeySet()).add(chatId);
        setIdle(requesterSessionId, false);

        Deque<String> rqIdle = branchIdleSessions.get(requesterBranch);
        if (rqIdle != null) rqIdle.remove(requesterSessionId);

        cancelTimer(requesterAttachTimers.remove(chatId));
        cancelTimer(chatSession.soloTimer);
        chatSession.soloTimer = null;
        // chatSession.addMessage(new ChatMessage("SYSTEM", requesterBranch, "Requester joined. Chat is active."));
    }

    public void joinExistingChatAuthorized(String chatId, String employeeId, Role role, String branchId, String sessionId, Consumer<ChatMessage> listener
    ) throws CustomExceptions.ChatException {

        ChatSession chatSession = chatMap.get(chatId);
        if (chatSession == null || !chatSession.isActive()) throw new CustomExceptions.ChatException("Chat not found or inactive.");

        // Allow if SHIFT_MANAGER from either chat branch
        boolean isMgrOfChatBranches =
                role == Role.SHIFT_MANAGER && (Objects.equals(branchId, chatSession.branchA) || Objects.equals(branchId, chatSession.branchB));

        // Allow if original requester
        ChatRequest request = requestByChat.get(chatId);
        boolean isOriginalRequester = (request != null && Objects.equals(request.sourceEmployeeId, employeeId));

        // Allow if original assignee (by employeeId)
        String assigneeEmp = assigneeEmployeeByChat.get(chatId);
        boolean isOriginalAssignee = Objects.equals(assigneeEmp, employeeId);

        if (!(isMgrOfChatBranches || isOriginalRequester || isOriginalAssignee)) {
            throw new CustomExceptions.ChatException("Not authorized to join this chat.");
        }

        // attach
        chatSession.addListener(branchId, sessionId, listener);
        employeeActiveChats.computeIfAbsent(sessionId, _ -> ConcurrentHashMap.newKeySet()).add(chatId);
        setIdle(sessionId, false); // this participant is now busy

        // If the room was on solo timer, cancel it once someone else returns
        cancelTimer(chatSession.soloTimer);
        chatSession.soloTimer = null;

        // chatSession.addMessage(new ChatMessage("SYSTEM", branchId,
        //         (role == Role.SHIFT_MANAGER ? "Shift manager" : "Participant") + " joined. use SHOW_CHAT to see history."));
    }

    // 2) Messaging (inside an active session)
    public void sendMessage(String chatId, String fromSessionId, String senderName, String text)
            throws CustomExceptions.ChatException {
        ChatSession chatSession = chatMap.get(chatId);
        if (chatSession == null || !chatSession.isActive()) throw new CustomExceptions.ChatException("Chat not found or inactive.");

        String branchId = chatSession.getBranchOfSession(fromSessionId);
        if (branchId == null) throw new CustomExceptions.ChatException("You are not a participant of this chat.");

        ChatMessage msg = new ChatMessage(senderName, branchId, text);
        chatSession.addMessage(msg);
    }
    
    // 3) Leaving & ending
    public void leaveChatAsUser(String chatId, String branchId, String sessionId) {
        ChatSession chatSession = chatMap.get(chatId);
        if (chatSession == null) return;

        chatSession.removeListener(branchId, sessionId);
        Set<String> set = employeeActiveChats.get(sessionId);
        if (set != null) set.remove(chatId);

        // if zero participants -> end
        if (chatSession.getParticipants().isEmpty()) {
            endSessionOnly(chatSession);
            return;
        }

        // if one side remains -> schedule solo auto-close (2 min)
        if (chatSession.getBranchesInvolved().size() == 1) {
            chatSession.addMessage(new ChatMessage("SYSTEM", branchId, "Peer left. Chat will auto-close in 2 minutes unless someone rejoins."));
            cancelTimer(chatSession.soloTimer);
            chatSession.soloTimer = scheduler.schedule(() -> endSessionOnly(chatSession),
                                          SOLO_GRACE_MS, TimeUnit.MILLISECONDS);
            // soloCloseTasks.put(chatId, f);
        }

        // return employee to idle (if still connected)
        setIdle(sessionId, true);
    }

    public void endChat(String chatId) {
        ChatSession chatSession = chatMap.get(chatId);
        if (chatSession == null) return;

        notifyParticipants(chatSession, "[EVENT] CHAT_ENDED " + chatId);
        endSessionOnly(chatSession);

        // free all participants to idle
        for (String sessionId : new ArrayList<>(chatSession.getParticipants())) {
            Set<String> set = employeeActiveChats.get(sessionId);
            if (set != null) set.remove(chatId);
            setIdle(sessionId, true);
        }
    }

    // 4) (Optional) persistence
    public void saveChatHistory(String chatId) {
        ChatSession chatSession = getChatById(chatId);
        if (chatSession == null) return;

        Map<String, Object> chatJson = new LinkedHashMap<>();
        chatJson.put("chatId", chatSession.getChatId());
        chatJson.put("date", LocalDate.now().toString());
        chatJson.put("time", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        List<String> messages = chatSession.getMessages().stream()
                .map(msg -> msg.getSenderName() + " (" + msg.getSenderBranch() + "): " + msg.getContent())
                .toList();
        chatJson.put("messages", messages);

        List<String> allChatsJson = FileUtils.readJsonObjectsFromFile(CHAT_FILE);
        List<String> updatedChats = new ArrayList<>();
        for (String chat : allChatsJson) {
            if (chat != null && !chat.isBlank()) updatedChats.add(chat);
        }

        StringBuilder historySB = new StringBuilder("{\n");
        historySB.append("  \"chatId\": \"").append(chatJson.get("chatId")).append("\",\n");
        historySB.append("  \"date\": \"").append(chatJson.get("date")).append("\",\n");
        historySB.append("  \"time\": \"").append(chatJson.get("time")).append("\",\n");
        historySB.append("  \"messages\": [\n");
        List<String> msgs = (List<String>) chatJson.get("messages");
        for (int index = 0; index < msgs.size(); index++) {
            historySB.append("    \"").append(msgs.get(index).replace("\"", "\\\"")).append("\"");
            if (index < msgs.size() - 1) historySB.append(",");
            historySB.append("\n");
        }
        historySB.append("  ]\n");
        historySB.append("}");
        updatedChats.add(historySB.toString());
        FileUtils.saveToFile(CHAT_FILE, updatedChats, s -> s);
    }

}