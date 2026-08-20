package com.vasu.bastionServer.identity;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.vasu.bastionServer.identity.IdentityManager;

@RestController
public class IdentityController {

	private final IdentityManager identityManager;

	public IdentityController(IdentityManager _IdentityManager) {
		this.identityManager = _IdentityManager;
	}

	@GetMapping("/api/identity")
	public Map<String,Object> getIdentity() throws Exception {
		return Map.of(
			"fingerprint", identityManager.getFingerprint(),
			"publicKey",   java.util.Base64.getEncoder().encodeToString(
				identityManager.getPublicKey().getEncoded()
			),
			"algorithm",   "Ed25519"
		);
	}
}