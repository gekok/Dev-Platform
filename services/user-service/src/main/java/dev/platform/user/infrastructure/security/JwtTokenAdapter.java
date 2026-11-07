package dev.platform.user.infrastructure.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import dev.platform.user.application.port.out.TokenPort;
import dev.platform.user.domain.aggregate.UserAggregate;
import dev.platform.user.domain.exception.AuthenticationException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenAdapter implements TokenPort {

  private static final String DEFAULT_DEV_KEY_FILE = ".keys/dev-rsa-jwt.key";

  private final Algorithm algorithm;
  private final long expirationMillis;
  private final RSAPublicKey publicKey;
  private final RSAPrivateKey privateKey;

  @Autowired
  public JwtTokenAdapter(
      @Value("${app.jwt.expiration-ms:3600000}") long expirationMillis,
      @Value("${app.jwt.private-key:}") String privateKeyPem,
      @Value("${app.jwt.public-key:}") String publicKeyPem,
      @Value("${app.jwt.key-file:}") String keyFilePath) {
    this(resolveKeyPair(privateKeyPem, publicKeyPem, keyFilePath), expirationMillis);
  }

  public JwtTokenAdapter(long expirationMillis) {
    this(generateRsaKeyPair(), expirationMillis);
  }

  public JwtTokenAdapter(KeyPair keyPair, long expirationMillis) {
    Objects.requireNonNull(keyPair, "keyPair cannot be null");
    this.publicKey = (RSAPublicKey) keyPair.getPublic();
    this.privateKey = (RSAPrivateKey) keyPair.getPrivate();
    this.algorithm = Algorithm.RSA256(this.publicKey, this.privateKey);
    this.expirationMillis = expirationMillis;
  }

  @Override
  public String issue(UserAggregate user) {
    Objects.requireNonNull(user, "user cannot be null");
    Date now = new Date();
    Date expiresAt = new Date(now.getTime() + expirationMillis);

    return JWT.create()
        .withSubject(user.getId().toString())
        .withClaim("email", user.getEmail())
        .withClaim("fullName", user.getFullName())
        .withClaim("roles", new ArrayList<>(user.getRoles()))
        .withClaim("authVersion", user.getAuthVersion())
        .withIssuedAt(now)
        .withExpiresAt(expiresAt)
        .sign(algorithm);
  }

  @Override
  public UUID parseAndValidate(String token) {
    if (token == null || token.isBlank()) {
      throw new AuthenticationException("Token cannot be blank");
    }
    try {
      DecodedJWT jwt = JWT.require(algorithm).build().verify(token);

      String sub = jwt.getSubject();
      if (sub == null || sub.isBlank()) {
        throw new AuthenticationException("Token subject is missing");
      }
      return UUID.fromString(sub);
    } catch (JWTVerificationException | IllegalArgumentException e) {
      throw new AuthenticationException("Invalid or expired token: " + e.getMessage(), e);
    }
  }

  public String getPublicKeyPem() {
    String base64 =
        Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(publicKey.getEncoded());
    return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----";
  }

  private static KeyPair resolveKeyPair(
      String privateKeyPem, String publicKeyPem, String keyFilePath) {
    if (privateKeyPem != null
        && !privateKeyPem.isBlank()
        && publicKeyPem != null
        && !publicKeyPem.isBlank()) {
      return parsePemKeyPair(privateKeyPem, publicKeyPem);
    }

    String targetPath =
        (keyFilePath != null && !keyFilePath.isBlank()) ? keyFilePath : DEFAULT_DEV_KEY_FILE;
    try {
      Path path = Paths.get(targetPath);
      if (Files.exists(path) && Files.size(path) > 0) {
        String content = Files.readString(path);
        String[] parts = content.split("---KEY-SPLIT---");
        if (parts.length == 2) {
          return parsePemKeyPair(parts[0], parts[1]);
        }
      }

      KeyPair keyPair = generateRsaKeyPair();
      if (path.getParent() != null) {
        Files.createDirectories(path.getParent());
      }
      String encodedPrivate = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
      String encodedPublic = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
      Files.writeString(path, encodedPrivate + "---KEY-SPLIT---" + encodedPublic);
      return keyPair;
    } catch (Exception e) {
      return generateRsaKeyPair();
    }
  }

  private static KeyPair parsePemKeyPair(String privateKeyStr, String publicKeyStr) {
    try {
      KeyFactory kf = KeyFactory.getInstance("RSA");
      String privClean =
          privateKeyStr
              .replace("-----BEGIN PRIVATE KEY-----", "")
              .replace("-----END PRIVATE KEY-----", "")
              .replaceAll("\\s+", "");
      byte[] privBytes = Base64.getDecoder().decode(privClean);
      RSAPrivateKey privKey =
          (RSAPrivateKey) kf.generatePrivate(new PKCS8EncodedKeySpec(privBytes));

      String pubClean =
          publicKeyStr
              .replace("-----BEGIN PUBLIC KEY-----", "")
              .replace("-----END PUBLIC KEY-----", "")
              .replaceAll("\\s+", "");
      byte[] pubBytes = Base64.getDecoder().decode(pubClean);
      RSAPublicKey pubKey = (RSAPublicKey) kf.generatePublic(new X509EncodedKeySpec(pubBytes));

      return new KeyPair(pubKey, privKey);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to parse RSA key pair", e);
    }
  }

  private static KeyPair generateRsaKeyPair() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Failed to initialize RSA key pair generator", e);
    }
  }
}
