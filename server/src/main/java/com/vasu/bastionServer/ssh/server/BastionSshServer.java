package com.vasu.bastionServer.ssh.server;

import com.vasu.bastionServer.identity.IdentityManager;
import com.vasu.bastionServer.ssh.server.PeerAuthenticator;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.AbstractGeneratorHostKeyProvider;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.security.KeyPair;

@Component
public class BastionSshServer {

    @Value("${app.ssh.port:2222}")
    private int port;

    private final IdentityManager identityManager;
    private final PeerAuthenticator peerAuthenticator;
    private SshServer sshServer;
    private boolean running = false;

    
    public BastionSshServer(IdentityManager identityManager, PeerAuthenticator peerAuthenticator) {
        this.identityManager   = identityManager;
        this.peerAuthenticator = peerAuthenticator;
    }

    @PostConstruct
    public void start() throws Exception {
        sshServer = SshServer.setUpDefaultServer();
        sshServer.setPort(port);

        // Use our Ed25519 identity keypair as the SSH host key
        KeyPair keyPair = identityManager.getKeyPair();
        sshServer.setKeyPairProvider(
            KeyPairProvider.wrap(keyPair)
        );

        // Pubkey auth only — explicitly disable passwords
        sshServer.setPublickeyAuthenticator(peerAuthenticator);
        sshServer.setPasswordAuthenticator((u, p, s) -> false);
        
        sshServer.start();
        running = true;
        System.out.println("[ssh-server] Listening on port " + port);
    }

    @PreDestroy
    public void stop() throws Exception {
        if (sshServer != null) {
            sshServer.stop();
        }
        running = false;
        System.out.println("[ssh-server] Stopped");
    }

    public boolean isRunning() {
        return running;
    }
}