# Bastion — Complete Project Documentation

> A fully decentralized, peer-to-peer encrypted messaging application built on SSH transport,
> with a Web UI (default) and TUI mode (optional). No central server stores your messages.
> Your SSH keypair is your identity.

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Architecture](#2-architecture)
3. [Tech Stack](#3-tech-stack)
4. [Security Model](#4-security-model)
5. [Project Structure](#5-project-structure)
6. [Backend — Java Spring Boot](#6-backend--java-spring-boot)
7. [Frontend — React Web UI](#7-frontend--react-web-ui)
8. [TUI — Ink + React](#8-tui--ink--react)
9. [Shared Package](#9-shared-package)
10. [Database Schema](#10-database-schema)
11. [API Reference](#11-api-reference)
12. [WebSocket Events](#12-websocket-events)
13. [SSH Protocol Design](#13-ssh-protocol-design)
14. [Peer Discovery](#14-peer-discovery)
15. [Message Flow](#15-message-flow)
16. [Group Chat Design](#16-group-chat-design)
17. [Identity System](#17-identity-system)
18. [Configuration](#18-configuration)
19. [Launch Modes](#19-launch-modes)
20. [Build Phases](#20-build-phases)
21. [Gradle Build Setup](#21-gradle-build-setup)
22. [Frontend Package Setup](#22-frontend-package-setup)
23. [TUI Package Setup](#23-tui-package-setup)
24. [Environment Variables](#24-environment-variables)
25. [Deployment](#25-deployment)
26. [Future Roadmap](#26-future-roadmap)

---

## 1. Project Overview

### What is Bastion?

Bastion is a **fully decentralized, end-to-end encrypted** messaging application where:

- Every user runs their own **peer node** (a small server + client)
- Messages travel **directly between peers** over SSH — no relay server
- Your **Ed25519 SSH keypair IS your identity** — no usernames, no passwords, no accounts
- The server only exists for **offline message queuing** — and even those are encrypted blobs it cannot read
- Supports **1-on-1 chat**, **group chats**, **online/offline presence**, and **read receipts**

### Why SSH?

SSH is not just a remote shell protocol — it is a complete secure transport framework:

- **Built-in mutual authentication** via public key cryptography
- **Native encryption** (ChaCha20-Poly1305 / AES-256-CTR) on every byte
- **Custom subsystems** allow arbitrary data channels (not just shells)
- **Ubiquitous** — available on every OS, ports rarely blocked by firewalls
- **Battle-tested** for 30+ years in production environments

### Core Principles

| Principle | Implementation |
|---|---|
| Decentralized | No central server required for online messaging |
| Zero trust | Server never sees plaintext — encrypted at source |
| Identity-first | Ed25519 keypair = your permanent identity |
| Offline-capable | Encrypted message queue for offline peers |
| Cross-platform | Web UI (browser), TUI (terminal), Headless (daemon) |

---

## 2. Architecture

### High-Level Overview

```
┌─────────────────────────────────────────────────────────┐
│                    PEER NODE (Alice)                    │
│                                                         │
│  ┌───────────────────────────────────────────────────┐  │
│  │              Spring Boot Application              │  │
│  │                                                   │  │
│  │  ┌─────────────┐    ┌─────────────────────────┐  │  │
│  │  │ SSH Server  │    │   Spring WebFlux         │  │  │
│  │  │ (port 2222) │    │   WebSocket + REST API  │  │  │
│  │  └──────┬──────┘    └────────────┬────────────┘  │  │
│  │         │                        │                │  │
│  │  ┌──────▼──────────────────────▼────────────┐   │  │
│  │  │              Message Bus                  │   │  │
│  │  │         (in-memory event routing)         │   │  │
│  │  └──────────────────┬────────────────────────┘   │  │
│  │                     │                             │  │
│  │  ┌──────────────────▼────────────────────────┐   │  │
│  │  │           SQLite Store (AES-256)           │   │  │
│  │  └───────────────────────────────────────────┘   │  │
│  └───────────────────────────────────────────────────┘  │
│                         │                               │
│  ┌──────────────────────▼──────────────────────────┐   │
│  │              UI Layer (pick one)                 │   │
│  │                                                  │   │
│  │   [React Web UI]          [Ink TUI]              │   │
│  │   localhost:8080          terminal               │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────┬───────────────────────────────────────┘
                  │  SSH (port 2222) — direct P2P
                  │  No intermediary server
                  ▼
┌─────────────────────────────────────────────────────────┐
│                    PEER NODE (Bob)                      │
│                   (same architecture)                   │
└─────────────────────────────────────────────────────────┘
```

### Connection Lifecycle

```
1. DISCOVERY
   Alice learns Bob's IP:port + Ed25519 pubkey fingerprint
   via mDNS (LAN), DHT (internet), or manual entry

2. AUTHENTICATION
   Alice's SSH client dials Bob:2222
   SSH handshake → both sides prove identity via Ed25519 keypairs
   No password. No token. Pure cryptographic proof.

3. SUBSYSTEM ACTIVATION
   Alice requests "messenger" subsystem (not a shell)
   Bob's server accepts if Alice's pubkey is in authorized_keys

4. MESSAGING
   JSON MessageFrames flow bidirectionally over SSH channel
   Channel stays open for the session duration

5. OFFLINE FALLBACK
   Bob is offline → message queued in Alice's SQLite (encrypted)
   When Bob comes online (mDNS/DHT event) → queue flushes automatically
```

---

## 3. Tech Stack

### Backend

| Layer | Technology | Version | Purpose |
|---|---|---|---|
| Language | Java | 21 LTS | Core runtime with Virtual Threads |
| Framework | Spring Boot | 3.3.x | Application container |
| Web layer | Spring WebFlux | 6.x | Reactive WebSocket + REST |
| SSH | Apache MINA SSHD | 2.13.x | SSH server, client, subsystem |
| ORM | Spring Data JPA | 3.x | Repository pattern over SQLite |
| Database | SQLite (sqlite-jdbc) | 3.46.x | Local encrypted message store |
| Encryption (rest) | Java JCA + BouncyCastle | 1.78 | AES-256-GCM at-rest encryption |
| Discovery (LAN) | jmDNS | 3.5.x | mDNS/Bonjour peer discovery |
| Discovery (P2P) | TomP2P (Kademlia DHT) | 5.0.x | Internet-wide decentralized discovery |
| Shell | Spring Shell | 3.x | CLI commands (--tui, --headless) |
| Build | Gradle + Kotlin DSL | 8.x | Build system (build.gradle.kts) |
| Distribution | GraalVM Native Image | 21 | Optional single native binary |

### Frontend (Web UI)

| Layer | Technology | Version | Purpose |
|---|---|---|---|
| Language | TypeScript | 5.x | Type safety |
| Bundler | Vite | 5.x | Dev server + production build |
| Framework | React | 19 | UI rendering |
| Components | shadcn/ui | latest | Accessible component library |
| Styling | Tailwind CSS | 3.x | Utility-first CSS |
| Routing | React Router | v7 | Client-side navigation |
| Server state | TanStack Query | 5.x | REST + WS data fetching/caching |
| Client state | Zustand | 5.x | In-memory UI state |
| Font | Inter | — | Typography |
| Runtime | Bun | 1.x | Package manager + dev runtime |

### TUI (Terminal UI)

| Layer | Technology | Version | Purpose |
|---|---|---|---|
| Language | TypeScript | 5.x | Type safety |
| Framework | Ink | 5.x | React for terminals |
| Runtime | Bun | 1.x | Execute TUI process |
| Input | ink-text-input | 6.x | Message compose field |
| Select | ink-select-input | 6.x | Contact list navigation |
| Spinner | ink-spinner | 5.x | Loading/connecting states |
| Table | ink-table | 3.x | Peer/group list display |

### Shared Package

| Layer | Technology | Purpose |
|---|---|---|
| Types | TypeScript interfaces | MessageFrame, Contact, Group, etc. |
| State | Zustand stores | chatStore, contactStore shared by Web + TUI |
| API client | fetch wrapper | REST calls to Spring Boot |
| WS hook | useWebSocket | Shared WebSocket logic |

---

## 4. Security Model

### Identity

```
Algorithm:      Ed25519 (Edwards-curve Digital Signature Algorithm)
Key size:       256-bit private key, 32-byte public key
Storage:        ~/.p2pmsg/identity.key (AES-256-GCM encrypted, passphrase-derived)
Your address:   ed25519:<fingerprint>@<ip>:<port>
                e.g. ed25519:SHA256:xK9mP2...@192.168.1.5:2222
```

### Transport Security (SSH)

```
Key exchange:   curve25519-sha256 (ECDH over Curve25519)
Authentication: publickey (Ed25519) — no passwords ever
Encryption:     chacha20-poly1305@openssh.com (preferred)
                aes256-gcm@openssh.com (fallback)
MAC:            Built into AEAD ciphers above (no separate MAC needed)
Compression:    none (messages are small JSON — compression adds overhead)
Forward Secrecy: Yes — ephemeral session keys per connection
                 Past sessions cannot be decrypted even if identity key is stolen
```

### At-Rest Encryption

```
Database:       SQLite database file
Encryption:     AES-256-GCM
Key derivation: HKDF-SHA256 from Ed25519 identity private key
                (no separate password needed — your identity key = your DB key)
IV:             Random 96-bit IV per encryption operation
Auth tag:       128-bit GCM authentication tag
```

### Authentication Flow

```
Alice wants to message Bob:

1. Alice has Bob's Ed25519 public key (shared out-of-band or via DHT)
2. Alice dials Bob:2222 via SSH
3. SSH handshake:
   a. Bob sends host key (Ed25519 public key)
   b. Alice verifies it matches stored fingerprint (TOFU or explicit verify)
   c. Alice presents her Ed25519 public key
   d. Bob checks if Alice's pubkey is in his authorized list
   e. Mutual authentication complete — no passwords exchanged
4. Alice requests "messenger" subsystem
5. Encrypted message channel is open
```

### What the Network Sees

```
Eavesdropper observes:
  ✓  Connection between Alice IP and Bob IP (metadata)
  ✓  Approximate message timing and size
  ✗  Message content (encrypted)
  ✗  Who Alice/Bob are beyond their IPs
  ✗  Contact lists or group membership
  ✗  Message history
```

### Threat Model

| Threat | Mitigation |
|---|---|
| Network eavesdropping | SSH encryption (ChaCha20-Poly1305) |
| Man-in-the-middle | Pubkey pinning (TOFU on first connect) |
| Impersonation | Ed25519 signature verification |
| DB compromise | AES-256-GCM at-rest encryption |
| Shell exposure | Custom subsystem only — no shell spawned |
| Replay attacks | SSH sequence numbers + session keys |
| Key theft | Private key encrypted with user passphrase |

---

## 5. Project Structure

```
bastion/
│
├── backend/                                        Java Spring Boot
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/bastion/
│   │   │   │   ├── BastionApplication.java
│   │   │   │   ├── identity/
│   │   │   │   │   ├── IdentityManager.java
│   │   │   │   │   ├── KeystoreConfig.java
│   │   │   │   │   └── IdentityDTO.java
│   │   │   │   ├── ssh/
│   │   │   │   │   ├── server/
│   │   │   │   │   │   ├── PeerSshServer.java
│   │   │   │   │   │   ├── PeerAuthenticator.java
│   │   │   │   │   │   ├── MessengerSubsystem.java
│   │   │   │   │   │   └── SubsystemFactory.java
│   │   │   │   │   └── client/
│   │   │   │   │       ├── PeerSshClient.java
│   │   │   │   │       └── ConnectionPool.java
│   │   │   │   ├── transport/
│   │   │   │   │   ├── MessageFrame.java
│   │   │   │   │   ├── MessageType.java
│   │   │   │   │   └── MessageCodec.java
│   │   │   │   ├── store/
│   │   │   │   │   ├── entity/
│   │   │   │   │   │   ├── MessageEntity.java
│   │   │   │   │   │   ├── ContactEntity.java
│   │   │   │   │   │   └── GroupEntity.java
│   │   │   │   │   ├── repository/
│   │   │   │   │   │   ├── MessageRepository.java
│   │   │   │   │   │   ├── ContactRepository.java
│   │   │   │   │   │   └── GroupRepository.java
│   │   │   │   │   └── SQLiteConfig.java
│   │   │   │   ├── discovery/
│   │   │   │   │   ├── MdnsDiscovery.java
│   │   │   │   │   ├── DhtDiscovery.java
│   │   │   │   │   └── PeerRegistry.java
│   │   │   │   ├── groups/
│   │   │   │   │   └── GroupChatManager.java
│   │   │   │   ├── queue/
│   │   │   │   │   └── OfflineMessageQueue.java
│   │   │   │   ├── presence/
│   │   │   │   │   └── PresenceService.java
│   │   │   │   ├── web/
│   │   │   │   │   ├── WebSocketHandler.java
│   │   │   │   │   ├── MessageController.java
│   │   │   │   │   ├── ContactController.java
│   │   │   │   │   ├── GroupController.java
│   │   │   │   │   └── StatusController.java
│   │   │   │   └── shell/
│   │   │   │       └── MessengerCommands.java
│   │   │   └── resources/
│   │   │       ├── application.yml
│   │   │       └── db/migration/
│   │   │           └── V1__init.sql
│   │   └── test/
│   │       └── java/com/bastion/
│   │           ├── ssh/SshServerTest.java
│   │           ├── store/MessageRepositoryTest.java
│   │           └── transport/MessageCodecTest.java
│   └── graalvm/
│       ├── reflect-config.json
│       └── resource-config.json
│
├── frontend/                                       Vite + React 19 (Web UI)
│   ├── package.json
│   ├── vite.config.ts
│   ├── tailwind.config.ts
│   ├── tsconfig.json
│   ├── components.json                             shadcn/ui config
│   ├── index.html
│   └── src/
│       ├── main.tsx
│       ├── App.tsx
│       ├── components/
│       │   ├── ui/                                 shadcn/ui components
│       │   ├── layout/
│       │   │   ├── Sidebar.tsx
│       │   │   └── MainLayout.tsx
│       │   ├── chat/
│       │   │   ├── ChatWindow.tsx
│       │   │   ├── MessageBubble.tsx
│       │   │   ├── MessageInput.tsx
│       │   │   └── TypingIndicator.tsx
│       │   ├── contacts/
│       │   │   ├── ContactList.tsx
│       │   │   ├── ContactItem.tsx
│       │   │   └── AddContactModal.tsx
│       │   ├── groups/
│       │   │   ├── GroupList.tsx
│       │   │   └── CreateGroupModal.tsx
│       │   └── common/
│       │       ├── StatusBadge.tsx
│       │       └── Avatar.tsx
│       ├── pages/
│       │   ├── ChatPage.tsx
│       │   ├── SettingsPage.tsx
│       │   └── IdentityPage.tsx
│       ├── hooks/
│       │   ├── useWebSocket.ts
│       │   └── useMessages.ts
│       └── lib/
│           └── utils.ts
│
├── tui/                                            Ink + React 19 (TUI)
│   ├── package.json
│   ├── tsconfig.json
│   └── src/
│       ├── index.tsx                               Entry point (--tui)
│       ├── App.tsx
│       └── components/
│           ├── ContactList.tsx                     ink-select-input
│           ├── ChatWindow.tsx                      scrollable area
│           ├── MessageBubble.tsx                   colored text
│           ├── MessageInput.tsx                    ink-text-input
│           ├── StatusBar.tsx                       bottom info bar
│           └── Spinner.tsx                         ink-spinner
│
├── shared/                                         Shared TS package
│   ├── package.json
│   ├── tsconfig.json
│   └── src/
│       ├── index.ts
│       ├── types.ts                                All shared interfaces
│       ├── stores/
│       │   ├── chatStore.ts                        Zustand
│       │   └── contactStore.ts                     Zustand
│       ├── hooks/
│       │   └── useWebSocket.ts
│       └── lib/
│           └── api.ts                              REST client
│
├── docs/
│   ├── ARCHITECTURE.md
│   ├── SECURITY.md
│   └── API.md
│
├── .gitignore
├── README.md
└── BASTION.md                                This file
```

---

## 6. Backend — Java Spring Boot

### Entry Point

```java
// BastionApplication.java
@SpringBootApplication
public class BastionApplication {
    public static void main(String[] args) {
        List<String> argList = Arrays.asList(args);

        if (argList.contains("--tui")) {
            // Launch Spring Boot headlessly, then start Ink TUI process
            System.setProperty("spring.main.web-application-type", "none");
            SpringApplication.run(BastionApplication.class, args);
            launchInkTui();
        } else if (argList.contains("--headless")) {
            // SSH daemon only — no UI at all
            System.setProperty("spring.main.web-application-type", "none");
            SpringApplication.run(BastionApplication.class, args);
        } else {
            // Default: Spring Boot + Web UI on localhost:8080
            SpringApplication.run(BastionApplication.class, args);
        }
    }

    private static void launchInkTui() {
        // Spawn Ink TUI as a child process
        ProcessBuilder pb = new ProcessBuilder("bun", "run", "tui/src/index.tsx");
        pb.inheritIO();
        pb.start();
    }
}
```

### Identity Manager

```java
// identity/IdentityManager.java
@Component
public class IdentityManager {

    private static final Path IDENTITY_DIR = Path.of(
        System.getProperty("user.home"), ".p2pmsg"
    );

    private KeyPair identityKeyPair;

    @PostConstruct
    public void init() throws Exception {
        Path keyFile = IDENTITY_DIR.resolve("identity.key");

        if (Files.exists(keyFile)) {
            this.identityKeyPair = loadKeyPair(keyFile);
        } else {
            Files.createDirectories(IDENTITY_DIR);
            this.identityKeyPair = generateEd25519KeyPair();
            saveKeyPair(identityKeyPair, keyFile);
            System.out.println("[identity] Generated new Ed25519 identity");
        }

        System.out.println("[identity] Fingerprint: " + getFingerprint());
    }

    private KeyPair generateEd25519KeyPair() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519", "BC");
        return kpg.generateKeyPair();
    }

    public String getFingerprint() {
        // SHA-256 fingerprint of the public key
        byte[] encoded = identityKeyPair.getPublic().getEncoded();
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] hash = sha256.digest(encoded);
        return "SHA256:" + Base64.getEncoder().encodeToString(hash);
    }

    public KeyPair getKeyPair() { return identityKeyPair; }
}
```

### SSH Server

```java
// ssh/server/PeerSshServer.java
@Component
public class PeerSshServer {

    @Value("${messenger.ssh.port:2222}")
    private int port;

    @Autowired
    private IdentityManager identityManager;

    @Autowired
    private PeerAuthenticator peerAuthenticator;

    @Autowired
    private SubsystemFactory subsystemFactory;

    private SshServer sshServer;

    @PostConstruct
    public void start() throws IOException {
        sshServer = SshServer.setUpDefaultServer();
        sshServer.setPort(port);

        // Use our Ed25519 identity as the host key
        sshServer.setKeyPairProvider(
            new SimpleGeneratorHostKeyProvider(identityManager.getKeyPair())
        );

        // Only accept pubkey auth — never passwords
        sshServer.setPublickeyAuthenticator(peerAuthenticator);
        sshServer.setPasswordAuthenticator((u, p, s) -> false); // disable

        // Register "messenger" subsystem — no shell exposed
        sshServer.setSubsystemFactories(List.of(subsystemFactory));
        sshServer.setShellFactory(null); // explicitly no shell

        sshServer.start();
        System.out.println("[ssh-server] Listening on port " + port);
    }

    @PreDestroy
    public void stop() throws IOException {
        if (sshServer != null) sshServer.stop();
    }
}
```

### Messenger Subsystem

```java
// ssh/server/MessengerSubsystem.java
public class MessengerSubsystem extends AbstractCommandSupport {

    private static final Logger log = LoggerFactory.getLogger(MessengerSubsystem.class);

    @Autowired
    private MessageBus messageBus;

    @Autowired
    private MessageCodec codec;

    @Override
    public void run() {
        log.info("[subsystem] Peer connected: {}", getSession().getRemoteAddress());

        try (
            InputStream in = getInputStream();
            OutputStream out = getOutputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in))
        ) {
            String line;
            while ((line = reader.readLine()) != null) {
                MessageFrame frame = codec.decode(line);
                messageBus.publish(frame);  // route to message handler
                sendAck(out, frame.id());   // acknowledge receipt
            }
        } catch (IOException e) {
            log.warn("[subsystem] Connection closed: {}", e.getMessage());
        } finally {
            onExit(0);
        }
    }

    private void sendAck(OutputStream out, String messageId) throws IOException {
        MessageFrame ack = MessageFrame.ack(messageId);
        out.write((codec.encode(ack) + "\n").getBytes());
        out.flush();
    }
}
```

### Message Frame

```java
// transport/MessageFrame.java
public record MessageFrame(
    String id,            // UUID
    MessageType type,     // CHAT, ACK, READ_RECEIPT, PRESENCE, GROUP
    String senderId,      // Ed25519 fingerprint of sender
    String recipientId,   // Ed25519 fingerprint of recipient (or group ID)
    String payload,       // JSON payload (encrypted content or metadata)
    long timestamp        // epoch millis
) {
    public static MessageFrame chat(String from, String to, String content) {
        return new MessageFrame(
            UUID.randomUUID().toString(),
            MessageType.CHAT,
            from, to, content,
            System.currentTimeMillis()
        );
    }

    public static MessageFrame ack(String originalId) {
        return new MessageFrame(
            UUID.randomUUID().toString(),
            MessageType.ACK,
            null, null, originalId,
            System.currentTimeMillis()
        );
    }
}
```

```java
// transport/MessageType.java
public enum MessageType {
    CHAT,           // Regular text message
    ACK,            // Delivery acknowledgment
    READ_RECEIPT,   // Message was read
    PRESENCE,       // Online/offline status update
    GROUP,          // Group chat message
    GROUP_JOIN,     // Peer joined a group
    GROUP_LEAVE,    // Peer left a group
    TYPING,         // Typing indicator
    PING            // Keep-alive
}
```

### WebSocket Handler

```java
// web/WebSocketHandler.java
@Component
public class WebSocketHandler implements WebSocketHandlerAdapter {

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Autowired
    private MessageBus messageBus;

    @PostConstruct
    public void subscribeToMessages() {
        // Push all incoming P2P messages to connected UI clients
        messageBus.subscribe(frame -> {
            String json = objectMapper.writeValueAsString(frame);
            TextMessage msg = new TextMessage(json);
            sessions.forEach(session -> {
                if (session.isOpen()) session.sendMessage(msg);
            });
        });
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        sessions.add(session);
        return session.receive()
            .doOnNext(msg -> handleUiMessage(msg.getPayloadAsText()))
            .doFinally(sig -> sessions.remove(session))
            .then();
    }
}
```

### Presence Service

```java
// presence/PresenceService.java
@Service
public class PresenceService {

    // peerFingerprint → last seen timestamp
    private final Map<String, Long> presenceMap = new ConcurrentHashMap<>();

    // peerFingerprint → online status
    private final Map<String, Boolean> onlineMap = new ConcurrentHashMap<>();

    public void markOnline(String peerFingerprint) {
        onlineMap.put(peerFingerprint, true);
        presenceMap.put(peerFingerprint, System.currentTimeMillis());
        // Trigger offline queue flush for this peer
        offlineQueue.flushFor(peerFingerprint);
    }

    public void markOffline(String peerFingerprint) {
        onlineMap.put(peerFingerprint, false);
        presenceMap.put(peerFingerprint, System.currentTimeMillis());
    }

    public boolean isOnline(String peerFingerprint) {
        return onlineMap.getOrDefault(peerFingerprint, false);
    }

    public Optional<Long> getLastSeen(String peerFingerprint) {
        return Optional.ofNullable(presenceMap.get(peerFingerprint));
    }
}
```

---

## 7. Frontend — React Web UI

### TanStack Query Setup

```typescript
// src/main.tsx
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider, createBrowserRouter } from 'react-router-dom'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 30,     // 30 seconds
      refetchOnWindowFocus: false,
    },
  },
})

const router = createBrowserRouter([
  { path: '/',           element: <ChatPage /> },
  { path: '/settings',  element: <SettingsPage /> },
  { path: '/identity',  element: <IdentityPage /> },
])

ReactDOM.createRoot(document.getElementById('root')!).render(
  <QueryClientProvider client={queryClient}>
    <RouterProvider router={router} />
  </QueryClientProvider>
)
```

### Chat Store (Zustand)

```typescript
// shared/src/stores/chatStore.ts
import { create } from 'zustand'
import type { Message, Contact } from '../types'

interface ChatState {
  messages:      Map<string, Message[]>  // contactId → messages
  activeContact: Contact | null
  typingPeers:   Set<string>

  setActiveContact: (contact: Contact) => void
  addMessage:       (contactId: string, msg: Message) => void
  markRead:         (contactId: string, msgId: string) => void
  setTyping:        (peerId: string, isTyping: boolean) => void
}

export const useChatStore = create<ChatState>((set, get) => ({
  messages:      new Map(),
  activeContact: null,
  typingPeers:   new Set(),

  setActiveContact: (contact) => set({ activeContact: contact }),

  addMessage: (contactId, msg) => set((state) => {
    const existing = state.messages.get(contactId) ?? []
    const updated  = new Map(state.messages)
    updated.set(contactId, [...existing, msg])
    return { messages: updated }
  }),

  markRead: (contactId, msgId) => set((state) => {
    const msgs    = state.messages.get(contactId) ?? []
    const updated = msgs.map(m => m.id === msgId ? { ...m, read: true } : m)
    const map     = new Map(state.messages)
    map.set(contactId, updated)
    return { messages: map }
  }),

  setTyping: (peerId, isTyping) => set((state) => {
    const typingPeers = new Set(state.typingPeers)
    isTyping ? typingPeers.add(peerId) : typingPeers.delete(peerId)
    return { typingPeers }
  }),
}))
```

### WebSocket Hook

```typescript
// shared/src/hooks/useWebSocket.ts
import { useEffect, useRef } from 'react'
import { useChatStore } from '../stores/chatStore'
import { useContactStore } from '../stores/contactStore'
import type { MessageFrame } from '../types'

export const useWebSocket = () => {
  const ws        = useRef<WebSocket | null>(null)
  const addMsg    = useChatStore(s => s.addMessage)
  const setOnline = useContactStore(s => s.setOnline)

  useEffect(() => {
    ws.current = new WebSocket('ws://localhost:8080/ws')

    ws.current.onmessage = (event) => {
      const frame: MessageFrame = JSON.parse(event.data)

      switch (frame.type) {
        case 'CHAT':
          addMsg(frame.senderId, {
            id:        frame.id,
            text:      frame.payload,
            senderId:  frame.senderId,
            timestamp: frame.timestamp,
            read:      false,
          })
          break
        case 'PRESENCE':
          const { peerId, online } = JSON.parse(frame.payload)
          setOnline(peerId, online)
          break
        case 'READ_RECEIPT':
          // handle read receipt
          break
      }
    }

    ws.current.onerror  = (e)  => console.error('[ws] error', e)
    ws.current.onclose  = ()   => console.log('[ws] disconnected')

    return () => ws.current?.close()
  }, [])

  const send = (frame: Partial<MessageFrame>) => {
    ws.current?.send(JSON.stringify(frame))
  }

  return { send }
}
```

### Chat Window Component

```typescript
// src/components/chat/ChatWindow.tsx
import { useEffect, useRef } from 'react'
import { useChatStore } from '@bastion/shared'
import { MessageBubble } from './MessageBubble'
import { MessageInput } from './MessageInput'

export const ChatWindow = () => {
  const { activeContact, messages } = useChatStore()
  const bottomRef = useRef<HTMLDivElement>(null)

  const contactMessages = activeContact
    ? (messages.get(activeContact.id) ?? [])
    : []

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [contactMessages.length])

  if (!activeContact) {
    return (
      <div className="flex-1 flex items-center justify-center text-muted-foreground">
        Select a contact to start messaging
      </div>
    )
  }

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="border-b px-6 py-4 flex items-center gap-3">
        <Avatar name={activeContact.name} />
        <div>
          <p className="font-medium">{activeContact.name}</p>
          <StatusBadge online={activeContact.online} />
        </div>
      </div>

      {/* Messages */}
      <div className="flex-1 overflow-y-auto px-6 py-4 space-y-3">
        {contactMessages.map(msg => (
          <MessageBubble key={msg.id} message={msg} />
        ))}
        <div ref={bottomRef} />
      </div>

      {/* Input */}
      <MessageInput contactId={activeContact.id} />
    </div>
  )
}
```

---

## 8. TUI — Ink + React

### TUI Layout

```
┌─────────────────────────────────────────────────────────┐
│  bastion  ●  alice@ed25519:SHA256:xK9m   online   │  ← StatusBar
├──────────────┬──────────────────────────────────────────┤
│  CONTACTS    │  Bob                                      │
│              │  ──────────────────────────────────────  │
│ ● Bob        │  10:32  hey alice!                        │
│ ○ Charlie    │  10:33  hey bob! you online?              │
│ ● group-1    │  10:33  yeah, connected via SSH ✓✓        │
│              │  10:34  nice, this is fully p2p 🔒        │
│              │                                           │
│  [↑↓] nav   │                                           │
│  [enter] DM  │                                           │
│  [g] group   │  ──────────────────────────────────────  │
│  [a] add     │  > _                                      │  ← MessageInput
│  [q] quit    │                                           │
└──────────────┴──────────────────────────────────────────┘
```

### TUI Entry Point

```typescript
// tui/src/index.tsx
import React from 'react'
import { render } from 'ink'
import { App } from './App'

render(<App />)
```

### TUI App Root

```typescript
// tui/src/App.tsx
import React, { useState } from 'react'
import { Box, useInput, useApp } from 'ink'
import { ContactList } from './components/ContactList'
import { ChatWindow } from './components/ChatWindow'
import { StatusBar } from './components/StatusBar'
import { useWebSocket } from '@bastion/shared'

export const App = () => {
  const { exit } = useApp()

  useWebSocket()  // shared WebSocket logic

  useInput((input) => {
    if (input === 'q') exit()
  })

  return (
    <Box flexDirection="column" height={process.stdout.rows}>
      <StatusBar />
      <Box flexDirection="row" flexGrow={1}>
        <ContactList />
        <ChatWindow />
      </Box>
    </Box>
  )
}
```

### TUI Chat Window

```typescript
// tui/src/components/ChatWindow.tsx
import React, { useState } from 'react'
import { Box, Text } from 'ink'
import TextInput from 'ink-text-input'
import { useChatStore, useContactStore, api } from '@bastion/shared'

export const ChatWindow = () => {
  const [input, setInput]   = useState('')
  const { activeContact }   = useChatStore()
  const messages            = useChatStore(s =>
    activeContact ? (s.messages.get(activeContact.id) ?? []) : []
  )

  const handleSubmit = async (text: string) => {
    if (!text.trim() || !activeContact) return
    await api.sendMessage(activeContact.id, text)
    setInput('')
  }

  return (
    <Box flexDirection="column" flexGrow={1} borderStyle="single" paddingX={1}>
      {/* Header */}
      <Box borderStyle="classic" borderBottom>
        <Text bold>{activeContact?.name ?? 'Select a contact'}</Text>
        {activeContact?.online && <Text color="green">  ●  online</Text>}
      </Box>

      {/* Messages */}
      <Box flexDirection="column" flexGrow={1}>
        {messages.slice(-20).map(msg => (
          <Box key={msg.id}>
            <Text dimColor>{formatTime(msg.timestamp)}  </Text>
            <Text color={msg.isMine ? 'cyan' : 'white'}>{msg.text}</Text>
          </Box>
        ))}
      </Box>

      {/* Input */}
      <Box borderStyle="classic" borderTop>
        <Text>{'> '}</Text>
        <TextInput value={input} onChange={setInput} onSubmit={handleSubmit} />
      </Box>
    </Box>
  )
}
```

---

## 9. Shared Package

### Type Definitions

```typescript
// shared/src/types.ts

export interface Message {
  id:         string
  text:       string
  senderId:   string
  timestamp:  number
  read:       boolean
  delivered:  boolean
  isMine?:    boolean
}

export interface Contact {
  id:          string        // Ed25519 fingerprint
  name:        string        // display name (local alias)
  pubkey:      string        // full Ed25519 public key
  address:     string        // last known IP:port
  online:      boolean
  lastSeen?:   number        // epoch millis
}

export interface Group {
  id:       string
  name:     string
  members:  string[]         // array of Contact fingerprints
  createdBy: string
  createdAt: number
}

export interface MessageFrame {
  id:          string
  type:        MessageType
  senderId:    string
  recipientId: string
  payload:     string
  timestamp:   number
}

export type MessageType =
  | 'CHAT'
  | 'ACK'
  | 'READ_RECEIPT'
  | 'PRESENCE'
  | 'GROUP'
  | 'GROUP_JOIN'
  | 'GROUP_LEAVE'
  | 'TYPING'
  | 'PING'
```

### REST API Client

```typescript
// shared/src/lib/api.ts
const BASE = 'http://localhost:8080/api'

export const api = {
  // Messages
  getMessages:   (contactId: string) =>
    fetch(`${BASE}/messages/${contactId}`).then(r => r.json()),

  sendMessage:   (contactId: string, text: string) =>
    fetch(`${BASE}/messages`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ contactId, text }),
    }).then(r => r.json()),

  // Contacts
  getContacts:   () =>
    fetch(`${BASE}/contacts`).then(r => r.json()),

  addContact:    (pubkey: string, address: string, name: string) =>
    fetch(`${BASE}/contacts`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ pubkey, address, name }),
    }).then(r => r.json()),

  removeContact: (id: string) =>
    fetch(`${BASE}/contacts/${id}`, { method: 'DELETE' }),

  // Groups
  getGroups:     () =>
    fetch(`${BASE}/groups`).then(r => r.json()),

  createGroup:   (name: string, memberIds: string[]) =>
    fetch(`${BASE}/groups`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, memberIds }),
    }).then(r => r.json()),

  // Status
  getIdentity:   () =>
    fetch(`${BASE}/status/identity`).then(r => r.json()),

  getStatus:     () =>
    fetch(`${BASE}/status`).then(r => r.json()),
}
```

---

## 10. Database Schema

```sql
-- V1__init.sql (Flyway migration)

-- Identity (single row — this node's identity)
CREATE TABLE identity (
    id              TEXT PRIMARY KEY DEFAULT 'self',
    fingerprint     TEXT NOT NULL UNIQUE,
    public_key      TEXT NOT NULL,
    display_name    TEXT NOT NULL DEFAULT 'Anonymous',
    created_at      INTEGER NOT NULL
);

-- Contacts (known peers)
CREATE TABLE contacts (
    id              TEXT PRIMARY KEY,      -- Ed25519 fingerprint
    name            TEXT NOT NULL,          -- local display alias
    public_key      TEXT NOT NULL,
    last_address    TEXT,                   -- last seen ip:port
    online          INTEGER DEFAULT 0,      -- 0=offline, 1=online
    last_seen       INTEGER,                -- epoch millis
    authorized      INTEGER DEFAULT 1,      -- 0=blocked, 1=allowed
    created_at      INTEGER NOT NULL
);

-- Groups
CREATE TABLE groups (
    id              TEXT PRIMARY KEY,       -- UUID
    name            TEXT NOT NULL,
    created_by      TEXT NOT NULL,          -- contact fingerprint
    created_at      INTEGER NOT NULL,
    FOREIGN KEY (created_by) REFERENCES contacts(id)
);

-- Group membership
CREATE TABLE group_members (
    group_id        TEXT NOT NULL,
    contact_id      TEXT NOT NULL,
    joined_at       INTEGER NOT NULL,
    PRIMARY KEY (group_id, contact_id),
    FOREIGN KEY (group_id)    REFERENCES groups(id),
    FOREIGN KEY (contact_id)  REFERENCES contacts(id)
);

-- Messages
CREATE TABLE messages (
    id              TEXT PRIMARY KEY,       -- UUID
    type            TEXT NOT NULL,          -- CHAT, GROUP, SYSTEM
    sender_id       TEXT NOT NULL,          -- contact fingerprint or 'self'
    recipient_id    TEXT NOT NULL,          -- contact fingerprint or group ID
    payload         TEXT NOT NULL,          -- message text (stored encrypted)
    delivered       INTEGER DEFAULT 0,      -- 0=pending, 1=delivered
    read            INTEGER DEFAULT 0,      -- 0=unread, 1=read
    queued          INTEGER DEFAULT 0,      -- 1=offline queue
    created_at      INTEGER NOT NULL
);

-- Offline queue (messages to retry when peer comes online)
CREATE TABLE offline_queue (
    id              TEXT PRIMARY KEY,
    message_id      TEXT NOT NULL,
    peer_id         TEXT NOT NULL,          -- target peer fingerprint
    attempts        INTEGER DEFAULT 0,
    next_retry_at   INTEGER NOT NULL,
    created_at      INTEGER NOT NULL,
    FOREIGN KEY (message_id) REFERENCES messages(id)
);

-- Indexes
CREATE INDEX idx_messages_recipient  ON messages (recipient_id);
CREATE INDEX idx_messages_sender     ON messages (sender_id);
CREATE INDEX idx_messages_created    ON messages (created_at);
CREATE INDEX idx_offline_queue_peer  ON offline_queue (peer_id);
CREATE INDEX idx_offline_queue_retry ON offline_queue (next_retry_at);
```

---

## 11. API Reference

### Base URL
```
http://localhost:8080/api
```

### Messages

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/messages/:contactId` | Get message history with a contact |
| `POST` | `/messages` | Send a message to a contact |
| `PATCH` | `/messages/:id/read` | Mark message as read |
| `DELETE` | `/messages/:id` | Delete a message locally |

**POST /messages — Request Body**
```json
{
  "contactId": "SHA256:xK9mP2...",
  "text": "Hey, are you there?"
}
```

**GET /messages/:contactId — Response**
```json
[
  {
    "id": "uuid-1234",
    "type": "CHAT",
    "senderId": "SHA256:abc...",
    "recipientId": "SHA256:xK9mP2...",
    "payload": "Hey, are you there?",
    "delivered": true,
    "read": false,
    "timestamp": 1720000000000
  }
]
```

### Contacts

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/contacts` | List all contacts |
| `POST` | `/contacts` | Add a new contact |
| `PATCH` | `/contacts/:id` | Update contact alias |
| `DELETE` | `/contacts/:id` | Remove a contact |
| `POST` | `/contacts/:id/block` | Block a contact |

**POST /contacts — Request Body**
```json
{
  "pubkey": "ssh-ed25519 AAAAC3NzaC1lZDI1...",
  "address": "192.168.1.10:2222",
  "name": "Bob"
}
```

### Groups

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/groups` | List all groups |
| `POST` | `/groups` | Create a group |
| `GET` | `/groups/:id` | Get group details |
| `POST` | `/groups/:id/members` | Add a member |
| `DELETE` | `/groups/:id/members/:memberId` | Remove a member |
| `POST` | `/groups/:id/messages` | Send a group message |

### Status

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/status` | Node status (port, uptime, peer count) |
| `GET` | `/status/identity` | This node's fingerprint + public key |
| `GET` | `/status/peers` | Connected peer count + addresses |

---

## 12. WebSocket Events

### Connection

```
ws://localhost:8080/ws
```

### Incoming Events (Spring → UI)

```typescript
// New message received from peer
{
  event:      "MESSAGE",
  id:         "uuid-1234",
  type:       "CHAT",
  senderId:   "SHA256:abc...",
  payload:    "Hey!",
  timestamp:  1720000000000
}

// Peer came online
{
  event:   "PRESENCE_ONLINE",
  peerId:  "SHA256:abc..."
}

// Peer went offline
{
  event:   "PRESENCE_OFFLINE",
  peerId:  "SHA256:abc...",
  lastSeen: 1720000000000
}

// Delivery acknowledgment
{
  event:     "ACK",
  messageId: "uuid-1234"
}

// Read receipt
{
  event:     "READ",
  messageId: "uuid-1234",
  peerId:    "SHA256:abc..."
}

// Typing indicator
{
  event:   "TYPING_START",
  peerId:  "SHA256:abc..."
}

{
  event:   "TYPING_STOP",
  peerId:  "SHA256:abc..."
}
```

### Outgoing Events (UI → Spring)

```typescript
// Typing indicator (triggered by keystrokes in UI)
{
  event:       "TYPING",
  contactId:   "SHA256:abc...",
  isTyping:    true
}

// Mark message as read (triggered on message visible in viewport)
{
  event:     "READ",
  messageId: "uuid-1234"
}
```

---

## 13. SSH Protocol Design

### Custom Subsystem: `messenger`

Standard SSH exposes a shell or executes commands. This app defines a **custom subsystem** named `messenger` that replaces that entirely.

```
Client (Alice)                              Server (Bob)
     │                                           │
     │   SSH_MSG_CHANNEL_REQUEST                 │
     │   subsystem: "messenger"  ──────────────► │
     │                                           │  validates Alice's pubkey
     │                           ◄─────────────  │   SSH_MSG_CHANNEL_SUCCESS
     │                                           │
     │  { "type": "CHAT", ... }  ──────────────► │  MessengerSubsystem.run()
     │                                           │  stores message
     │                           ◄─────────────  │  { "type": "ACK", ... }
     │                                           │
     │  { "type": "PING" }       ──────────────► │  (keepalive)
     │                           ◄─────────────  │  { "type": "PING" }
```

### Wire Format

Each message is a **newline-delimited JSON** frame over the SSH channel:

```
{"id":"uuid","type":"CHAT","senderId":"SHA256:...","recipientId":"SHA256:...","payload":"Hello!","timestamp":1720000000000}\n
{"id":"uuid","type":"ACK","payload":"uuid-of-original"}\n
```

- **Newline-delimited** allows streaming without length-prefix framing
- **UTF-8** encoded
- **Max frame size:** 64KB (enforced by codec)
- **Compression:** None (messages are small; SSH already compresses at transport layer if needed)

### SSH Server Hardening

```yaml
# Accepted algorithms only (configured in MINA SSHD)
key-exchange:
  - curve25519-sha256
  - curve25519-sha256@libssh.org

ciphers:
  - chacha20-poly1305@openssh.com
  - aes256-gcm@openssh.com

macs:
  - (built into AEAD — no separate MAC needed)

host-key-algorithms:
  - ssh-ed25519

public-key-algorithms:
  - ssh-ed25519

auth-methods:
  - publickey only (passwords disabled)

max-auth-attempts:   3
login-grace-period:  30s
tcp-keepalive:       true
idle-timeout:        300s
```

---

## 14. Peer Discovery

### mDNS (LAN Discovery)

For peers on the same local network, mDNS (Bonjour/Avahi) announces presence automatically:

```
Service type:  _bastion._tcp.local.
TXT records:
  fingerprint  = SHA256:xK9mP2...
  version      = 1
  port         = 2222
```

On startup, every node:
1. Announces itself via jmDNS
2. Listens for other `_bastion._tcp.local.` services
3. Automatically connects to known peers it discovers

### DHT (Internet-Wide Discovery)

For peers across the internet, TomP2P Kademlia DHT:

```
Node ID:    SHA-256 hash of Ed25519 public key fingerprint
Bootstrap:  List of well-known bootstrap peers (hardcoded or configurable)
Announce:   PUT(nodeId → IP:port) with TTL 1 hour, refreshed every 30 min
Lookup:     GET(peerFingerprint) → IP:port
```

### Manual Peer Addition

Users can add contacts manually by sharing their address string:

```
Format:   ed25519:<fingerprint>@<ip>:<port>
Example:  ed25519:SHA256:xK9mP2abcXYZ@203.0.113.42:2222

QR code support (Phase 6):
  QR encodes the above string for mobile scanning
```

### Trust on First Use (TOFU)

Similar to SSH's known_hosts behavior:

```
First connection to a peer:
  1. Alice receives Bob's host key during SSH handshake
  2. Alice is prompted: "Trust this key? SHA256:abc... fingerprint"
  3. Alice confirms → key saved to ~/.p2pmsg/known_peers
  4. Future connections verify against saved fingerprint
  5. Fingerprint mismatch → connection rejected + user alerted

If Bob's IP changes:
  → Same pubkey, different IP → trusted (key matches)
  → Different pubkey, same IP → rejected (possible MITM)
```

---

## 15. Message Flow

### Online Message (Alice → Bob, both online)

```
1.  Alice types "Hey Bob!" in React UI
2.  React: POST /api/messages { contactId: "SHA256:bob...", text: "Hey Bob!" }
3.  Spring: MessageController receives request
4.  Spring: Creates MessageFrame (UUID, CHAT type, payload, timestamp)
5.  Spring: Persists message to SQLite (delivered=false)
6.  Spring: PresenceService.isOnline("SHA256:bob...") → true
7.  Spring: PeerSshClient dials bob-ip:2222 (or reuses existing connection)
8.  Spring: SSH auth via Alice's Ed25519 keypair
9.  Spring: Activates "messenger" subsystem
10. Spring: Sends JSON MessageFrame over SSH channel
11. Bob's MessengerSubsystem receives frame
12. Bob's Spring: Persists message to Bob's SQLite
13. Bob's Spring: WebSocketHandler pushes to Bob's UI (web or TUI)
14. Bob's Spring: Sends ACK frame back
15. Alice's Spring: Receives ACK, updates SQLite delivered=true
16. Alice's Spring: WebSocketHandler pushes { event: "ACK", messageId } to Alice's UI
17. Alice's UI: Message bubble shows ✓✓ (delivered)
```

### Offline Message (Bob is offline)

```
1-6.  Same as above
7.    Spring: PresenceService.isOnline("SHA256:bob...") → false
8.    Spring: OfflineMessageQueue.enqueue(message, "SHA256:bob...")
9.    Spring: Returns 202 Accepted to UI
10.   UI: Shows message with ◌ (pending)

[Later — Bob comes online]
11.   Bob's mDNS announces presence on LAN
      OR Bob's DHT PUT updates his IP
12.   Alice's MdnsDiscovery / DhtDiscovery fires PeerOnlineEvent
13.   Alice's PresenceService.markOnline("SHA256:bob...")
14.   OfflineMessageQueue.flushFor("SHA256:bob...") triggers
15.   All queued messages sent via SSH (steps 8-16 above)
```

### Read Receipt Flow

```
1. Bob opens chat with Alice (message visible in viewport)
2. Bob's UI: WebSocket { event: "READ", messageId: "uuid-1234" }
3. Bob's Spring: Sends READ_RECEIPT frame to Alice via SSH
4. Alice's Spring: Updates SQLite read=true for message
5. Alice's Spring: WebSocket push { event: "READ", messageId }
6. Alice's UI: Message bubble shows ✓✓✓ (read)
```

---

## 16. Group Chat Design

### Architecture

Group chats are **peer-to-peer fan-out** — no group server, no relay:

```
Alice sends to Group-1 (members: Bob, Charlie, Dave):

Alice ──SSH──► Bob      (direct P2P)
Alice ──SSH──► Charlie  (direct P2P)
Alice ──SSH──► Dave     (direct P2P)

For offline members → OfflineMessageQueue
```

### Group Message Frame

```json
{
  "id":          "uuid-5678",
  "type":        "GROUP",
  "senderId":    "SHA256:alice...",
  "recipientId": "group-uuid-1234",
  "payload":     "{\"text\":\"Hey everyone!\",\"groupName\":\"Group-1\"}",
  "timestamp":   1720000000000
}
```

### Group Management

- Groups are **stored locally** on each member's node
- Creating a group sends `GROUP_JOIN` frames to all members
- Members store the group definition themselves — no central source of truth
- Leaving a group sends a `GROUP_LEAVE` frame to all members
- No admin controls in Phase 5 (simple equality among members)

---

## 17. Identity System

### First-Run Flow

```
1. ~/.p2pmsg/ directory does not exist
2. App generates Ed25519 keypair via Java JCA + BouncyCastle
3. Private key encrypted with AES-256-GCM (passphrase from user OR auto-derived)
4. Saved to ~/.p2pmsg/identity.key
5. Public key fingerprint displayed to user
6. User shares their address with contacts:
   ed25519:SHA256:<fingerprint>@<their-ip>:2222
```

### Identity File Layout

```
~/.p2pmsg/
├── identity.key        Ed25519 keypair (private key encrypted)
├── identity.pub        Ed25519 public key (plaintext, safe to share)
├── known_peers         Trusted peer fingerprints (like SSH known_hosts)
├── messenger.db        SQLite database (AES-256-GCM encrypted)
└── config.yml          User configuration
```

### Key Derivation

```
Master key = Ed25519 private key (256 bits)

DB encryption key:
  HKDF-SHA256(
    ikm  = Ed25519 private key bytes,
    salt = "bastion-db-v1",
    info = user's fingerprint
  ) → 256-bit AES key

This means:
  - No separate password for the DB
  - DB key changes if identity changes (new node = fresh start)
  - Losing identity.key = losing access to all stored messages
```

---

## 18. Configuration

### application.yml (Spring Boot)

```yaml
spring:
  application:
    name: bastion
  datasource:
    url: jdbc:sqlite:${user.home}/.p2pmsg/messenger.db
    driver-class-name: org.sqlite.JDBC
  jpa:
    database-platform: org.hibernate.community.dialect.SQLiteDialect
    hibernate:
      ddl-auto: validate                # use Flyway for migrations
  flyway:
    enabled: true
    locations: classpath:db/migration

server:
  port: 8080
  address: 127.0.0.1                   # localhost only — not exposed to network

messenger:
  ssh:
    port: 2222                          # P2P SSH port (exposed to network)
    host: 0.0.0.0                       # listen on all interfaces
    idle-timeout: 300                   # seconds
    max-auth-attempts: 3
  discovery:
    mdns:
      enabled: true
      service-type: _bastion._tcp.local.
    dht:
      enabled: true
      bootstrap-peers:
        - "bootstrap1.bastion.io:4001"
        - "bootstrap2.bastion.io:4001"
  queue:
    max-attempts: 10
    retry-interval: 60                  # seconds, exponential backoff
```

### ~/.p2pmsg/config.yml (User Config)

```yaml
display-name: "Alice"
ssh-port: 2222
web-ui-port: 8080
theme: "dark"                           # web UI theme
notifications: true
auto-start: false
discovery:
  mdns: true
  dht: true
```

---

## 19. Launch Modes

### Web UI Mode (Default)

```bash
java -jar bastion.jar

# Spring Boot starts on localhost:8080
# SSH daemon starts on 0.0.0.0:2222
# Open browser to http://localhost:8080
```

### TUI Mode

```bash
java -jar bastion.jar --tui

# Spring Boot starts headlessly (no web server)
# SSH daemon starts on 0.0.0.0:2222
# Ink TUI renders in terminal via Bun
```

### Headless / Daemon Mode

```bash
java -jar bastion.jar --headless

# SSH daemon only — no UI
# Useful for background operation or scripting
# Messages queued, synced when peers come online
```

### Combined (Web + Background SSH)

```bash
java -jar bastion.jar --web-port=9090 --ssh-port=2222

# Custom ports
```

---

## 20. Build Phases

### Phase 1 — Identity + SSH Daemon

**Goal:** A running SSH server that accepts connections from known peers.

- Generate Ed25519 keypair on first run
- Save identity to `~/.p2pmsg/identity.key`
- Start Apache MINA SSHD server on port 2222
- Implement `messenger` subsystem (receive MessageFrames, send ACKs)
- Public key authenticator (only known pubkeys allowed)
- CLI: `status`, `identity` commands via Spring Shell

**Done when:** Two instances can SSH-connect to each other on localhost.

---

### Phase 2 — 1-on-1 Messaging

**Goal:** Send and receive encrypted text messages between two peers.

- PeerSshClient: dial out to peers, activate subsystem, send frames
- MessageCodec: serialize/deserialize MessageFrame ↔ JSON
- SQLite store: persist all messages locally
- OfflineMessageQueue: queue messages when peer is offline
- REST API: `POST /messages`, `GET /messages/:contactId`
- ContactRepository: store known peers

**Done when:** Two peers can exchange messages with delivery ACKs.

---

### Phase 3 — React Web UI

**Goal:** A working browser-based chat interface.

- Vite + React 19 project scaffold
- shadcn/ui + Tailwind CSS setup
- ContactList, ChatWindow, MessageInput, MessageBubble components
- Zustand stores (chatStore, contactStore)
- TanStack Query for REST data
- WebSocket connection to Spring WebFlux
- Real-time message updates in UI
- Add Contact modal (paste pubkey + address)

**Done when:** Full 1-on-1 chat works in browser.

---

### Phase 4 — Presence + Offline Queue

**Goal:** Know when peers are online; deliver queued messages automatically.

- jmDNS: announce self, discover peers on LAN
- PresenceService: track online/offline state
- Retry logic: exponential backoff for offline queue
- Online/offline indicators in React UI
- Read receipts: READ_RECEIPT frame + ✓✓✓ UI indicators
- Typing indicators: TYPING frames + animated dots in UI
- Last-seen timestamps

**Done when:** Messages auto-deliver when offline peer reconnects.

---

### Phase 5 — Group Chats

**Goal:** Multi-person conversations without a server.

- GroupChatManager: fan-out send to all members
- GROUP / GROUP_JOIN / GROUP_LEAVE frame types
- Group creation and management UI
- Group message history per group
- Offline fan-out via message queue
- Group list in sidebar

**Done when:** Three or more peers can chat in a group.

---

### Phase 6 — Ink TUI

**Goal:** Full-featured terminal UI as an alternative to Web UI.

- Ink 5 + React 19 TUI project scaffold
- ContactList (ink-select-input navigation)
- ChatWindow (scrollable message area)
- MessageInput (ink-text-input)
- StatusBar (identity fingerprint + online count)
- Spinner (connecting state)
- Shared stores, API client, WS hook with web UI
- `--tui` flag integration in Spring Boot launcher

**Done when:** Complete messaging workflow works in terminal.

---

### Phase 7 — DHT Discovery

**Goal:** Find peers across the internet without manual IP entry.

- TomP2P Kademlia DHT integration
- Announce IP under pubkey hash on startup
- Lookup peer by fingerprint
- Bootstrap peer list (hardcoded + configurable)
- QR code generation for sharing your address

**Done when:** Peers can find each other by fingerprint without knowing IPs.

---

### Phase 8 — Distribution

**Goal:** Single binary that non-technical users can run.

- GraalVM Native Image compilation
  - Reflection config for Spring + MINA SSHD
  - Resource config for static assets
- Fat JAR fallback (`java -jar`)
- Homebrew formula (`brew install bastion`)
- Scoop manifest for Windows
- Auto-update check (GitHub Releases API)
- Installer scripts

**Done when:** Users can install with one command and run with one click.

---

## 21. Gradle Build Setup

```kotlin
// backend/build.gradle.kts
plugins {
    java
    id("org.springframework.boot") version "3.3.0"
    id("io.spring.dependency-management") version "1.1.5"
    id("org.graalvm.buildtools.native") version "0.10.2"
}

group   = "com.bastion"
version = "0.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.shell:spring-shell-starter:3.3.0")

    // SSH
    implementation("org.apache.sshd:sshd-core:2.13.0")
    implementation("org.apache.sshd:sshd-common:2.13.0")

    // SQLite
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")
    implementation("org.hibernate.orm:hibernate-community-dialects:6.5.0.Final")

    // BouncyCastle (crypto)
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")

    // Discovery
    implementation("javax.jmdns:jmdns:3.5.9")
    implementation("net.tomp2p:tomp2p-core:5.0-Beta8")

    // Flyway (DB migrations)
    implementation("org.flywaydb:flyway-core:10.15.0")

    // Utility
    implementation("com.fasterxml.jackson.core:jackson-databind")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("bastion")
            mainClass.set("com.bastion.BastionApplication")
            buildArgs.add("--no-fallback")
            buildArgs.add("-H:ReflectionConfigurationFiles=graalvm/reflect-config.json")
        }
    }
}
```

---

## 22. Frontend Package Setup

```json
// frontend/package.json
{
  "name": "@bastion/frontend",
  "version": "0.1.0",
  "type": "module",
  "scripts": {
    "dev":     "vite",
    "build":   "tsc && vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "@bastion/shared":           "workspace:*",
    "react":                 "^19.0.0",
    "react-dom":             "^19.0.0",
    "react-router-dom":      "^7.0.0",
    "@tanstack/react-query": "^5.0.0",
    "zustand":               "^5.0.0",
    "clsx":                  "^2.1.0",
    "tailwind-merge":        "^2.3.0",
    "lucide-react":          "^0.383.0"
  },
  "devDependencies": {
    "@types/react":          "^19.0.0",
    "@types/react-dom":      "^19.0.0",
    "@vitejs/plugin-react":  "^4.3.0",
    "autoprefixer":          "^10.4.0",
    "tailwindcss":           "^3.4.0",
    "typescript":            "^5.5.0",
    "vite":                  "^5.3.0"
  }
}
```

---

## 23. TUI Package Setup

```json
// tui/package.json
{
  "name": "@bastion/tui",
  "version": "0.1.0",
  "type": "module",
  "scripts": {
    "start": "bun run src/index.tsx",
    "dev":   "bun --watch run src/index.tsx"
  },
  "dependencies": {
    "@bastion/shared":     "workspace:*",
    "react":           "^19.0.0",
    "ink":             "^5.0.1",
    "ink-text-input":  "^6.0.0",
    "ink-select-input":"^6.0.0",
    "ink-spinner":     "^5.0.0",
    "ink-table":       "^3.0.0"
  },
  "devDependencies": {
    "@types/react":  "^19.0.0",
    "typescript":    "^5.5.0"
  }
}
```

---

## 24. Environment Variables

| Variable | Default | Description |
|---|---|---|
| `P2P_SSH_PORT` | `2222` | SSH daemon listening port |
| `P2P_WEB_PORT` | `8080` | Web UI + REST API port |
| `P2P_DATA_DIR` | `~/.p2pmsg` | Data directory for keys + DB |
| `P2P_LOG_LEVEL` | `INFO` | Log verbosity (DEBUG/INFO/WARN) |
| `P2P_IDENTITY_PASSPHRASE` | — | Passphrase to decrypt identity key |
| `P2P_DHT_ENABLED` | `true` | Enable Kademlia DHT discovery |
| `P2P_MDNS_ENABLED` | `true` | Enable mDNS LAN discovery |

---

## 25. Deployment

### Running on a VPS (Headless Mode)

```bash
# Download the binary (Phase 8)
curl -LO https://github.com/you/bastion/releases/latest/bastion-linux-x64
chmod +x bastion-linux-x64

# Run as a daemon
./bastion-linux-x64 --headless --ssh-port=2222

# Open port 2222 in firewall
ufw allow 2222/tcp
```

### Running Locally (Development)

```bash
# Terminal 1: Spring Boot backend
cd backend
./gradlew bootRun

# Terminal 2: Vite frontend (web UI dev server)
cd frontend
bun dev

# Alternative: TUI mode
cd backend
./gradlew bootRun --args="--tui"
```

### Port Requirements

| Port | Protocol | Direction | Purpose |
|---|---|---|---|
| `2222` | TCP | Inbound + Outbound | SSH P2P connections |
| `8080` | TCP | Localhost only | Web UI + REST API |
| `5353` | UDP | LAN | mDNS discovery (jmDNS) |
| `4001` | UDP | Outbound | DHT bootstrap (TomP2P) |

---

## 26. Future Roadmap

| Feature | Notes |
|---|---|
| **File transfer** | Stream files over SSH channel with progress |
| **Voice messages** | Base64-encoded audio blobs as message payloads |
| **Tor integration** | .onion address = your address, hides IP entirely |
| **Mobile app** | React Native + SSH library for iOS/Android |
| **Message search** | Full-text search over local SQLite |
| **Backup/export** | Encrypted backup of identity + messages |
| **Multi-device** | Share identity key across devices securely |
| **Push notifications** | OS-level notifications when new message arrives |
| **QR code sharing** | Scan peer's QR to add them as a contact |
| **Self-hosted bootstrap** | Run your own DHT bootstrap node |
| **Plugins** | Extensible subsystem for bots, webhooks |

---

*Documentation last updated: 2026*
*Stack: Java 21 + Spring Boot 3.3 + Apache MINA SSHD + React 19 + Ink 5 + SQLite*
