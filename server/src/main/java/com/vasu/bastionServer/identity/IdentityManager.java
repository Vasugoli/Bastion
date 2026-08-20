package com.vasu.bastionServer.identity;

import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

@Component
public class IdentityManager {

	private static final Path BASTION_DIR = Path.of(
		System.getProperty("user.home"), ".bastion"
	);

	private KeyPair identityKeyPair;
	
	private static final Path KEY_FILE = BASTION_DIR.resolve("identity.key");
	private static final Path PUB_FILE = BASTION_DIR.resolve("identity.pub");
	
	@PostConstruct
	public void init() throws Exception {
		if (!Files.exists(BASTION_DIR)) {
			Files.createDirectories(BASTION_DIR);
		}
		
		Security.addProvider(new BouncyCastleProvider());
		
		if (Files.exists(KEY_FILE)) {
			// Load existing identity
			this.identityKeyPair = loadKeyPair();
			System.out.println("[identity] Loaded existing identity");
		} else {
			// First run — generate and save
			KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519", "BC");
			this.identityKeyPair = kpg.generateKeyPair();
			saveKeyPair(this.identityKeyPair);
			System.out.println("[identity] Generated new identity");
		}
		
		System.out.println("[identity] Public key: "
			+ java.util.Base64.getEncoder()
				.encodeToString(identityKeyPair.getPublic().getEncoded()));
	}

	public KeyPair getKeyPair() {
		return identityKeyPair;
	}
	
	private void saveKeyPair(KeyPair keyPair) throws Exception {
		Files.write(KEY_FILE, keyPair.getPrivate().getEncoded());
		Files.write(PUB_FILE, keyPair.getPublic().getEncoded());
		System.out.println("[identity] Keypair saved to ~/.bastion/");
	}
	
	private KeyPair loadKeyPair() throws Exception {
		byte[] privateBytes = Files.readAllBytes(KEY_FILE);
		byte[] publicBytes  = Files.readAllBytes(PUB_FILE);
		
		java.security.KeyFactory keyFactory =
		java.security.KeyFactory.getInstance("Ed25519", "BC");
		
		java.security.PrivateKey privateKey = keyFactory.generatePrivate(
			new java.security.spec.PKCS8EncodedKeySpec(privateBytes)
		);
		java.security.PublicKey publicKey = keyFactory.generatePublic(
			new java.security.spec.X509EncodedKeySpec(publicBytes)
		);
		
		return new java.security.KeyPair(publicKey, privateKey);
	}
	public String getFingerprint() throws Exception {
		byte[] publicKeyBytes = identityKeyPair.getPublic().getEncoded();
		java.security.MessageDigest sha256 =
		java.security.MessageDigest.getInstance("SHA-256");
		byte[] hash = sha256.digest(publicKeyBytes);
		return "SHA256:" + java.util.Base64.getEncoder()
			.encodeToString(hash);
	}
	public java.security.PublicKey getPublicKey() {
		return identityKeyPair.getPublic();
	}
}