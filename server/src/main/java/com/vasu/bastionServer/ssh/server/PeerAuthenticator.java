package com.vasu.bastionServer.ssh.server;

import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;
import org.springframework.stereotype.Component;
import java.security.PublicKey;

@Component
public class PeerAuthenticator implements PublickeyAuthenticator {

	@Override
	public boolean authenticate(String username, PublicKey key, ServerSession session) {
		// For now: log the attempt + accept all known pubkeys
		// Phase 4 will check against the contacts database
		String address = session.getRemoteAddress().toString();
		System.out.println("[auth] Connection attempt from: " + address);
		System.out.println("[auth] Username: " + username);
		System.out.println("[auth] Key type: " + key.getAlgorithm());
		System.out.println("[auth] Address:  " + session.getRemoteAddress());
		// Accept all for now — we'll tighten this in contacts phase
		return true;
	}
}