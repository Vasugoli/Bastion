package com.vasu.bastionServer.ssh.server;

import com.vasu.bastionServer.identity.IdentityManager;
import com.vasu.bastionServer.ssh.server.PeerAuthenticator;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.common.signature.BuiltinSignatures;
import org.apache.sshd.server.keyprovider.AbstractGeneratorHostKeyProvider;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.security.KeyPair;
import java.nio.file.Path;

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
    
        // Let MINA SSHD manage its own Ed25519 host key
        // Saved to ~/.bastion/ssh_host_key (auto-generated on first run)
        SimpleGeneratorHostKeyProvider keyProvider = new SimpleGeneratorHostKeyProvider(
            Path.of(System.getProperty("user.home"), ".bastion", "ssh_host_key")
        );
        keyProvider.setAlgorithm("RSA");
        sshServer.setKeyPairProvider(keyProvider);
    
        sshServer.setPublickeyAuthenticator(peerAuthenticator);
        sshServer.setPasswordAuthenticator((u, p, s) -> false);

        // Register "messenger" subsystem — no shell exposed
        sshServer.setSubsystemFactories(
            java.util.List.of(
                new org.apache.sshd.server.subsystem.SubsystemFactory() {
                    @Override
                    public String getName() { return "messenger"; }
        
                    @Override
                    public Command createSubsystem(ChannelSession channel) {
                        return new MessengerSubsystem();
                    }
                }
            )
        );
        sshServer.setShellFactory(null); // explicitly no shell
        // Explicitly advertise publickey as the only auth method
        sshServer.setUserAuthFactories(java.util.List.of(
            new org.apache.sshd.server.auth.pubkey.UserAuthPublicKeyFactory()
        ));
        
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