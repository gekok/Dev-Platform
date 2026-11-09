package dev.platform.user.infrastructure.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import dev.platform.user.application.port.out.TokenPort;
import dev.platform.user.domain.aggregate.UserAggregate;
import dev.platform.user.domain.exception.AuthenticationException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.EnumSet;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenAdapter implements TokenPort {

  private static final String DEFAULT_DEV_KEY_FILE = ".keys/dev-rsa-jwt.key";
  private static final String DEFAULT_ISSUER = "dev-platform-user-service";

  private final Algorithm algorithm;
  private final long expirationMillis;
  private final String issuer;
  private final RSAPublicKey publicKey;
  private final RSAPrivateKey privateKey;

  @Autowired
  public JwtTokenAdapter(
      @Value("${app.jwt.expiration-ms:3600000}") long expirationMillis,
      @Value("${app.jwt.private-key:}") String privateKeyPem,
      @Value("${app.jwt.public-key:}") String publicKeyPem,
      @Value("${app.jwt.key-file:}") String keyFilePath,
      @Value("${app.jwt.issuer:dev-platform-user-service}") String issuer) {
    this(resolveKeyPair(privateKeyPem, publicKeyPem, keyFilePath), expirationMillis, issuer);
  }

  public JwtTokenAdapter(
      long expirationMillis, String privateKeyPem, String publicKeyPem, String keyFilePath) {
    this(
        resolveKeyPair(privateKeyPem, publicKeyPem, keyFilePath), expirationMillis, DEFAULT_ISSUER);
  }

  public JwtTokenAdapter(long expirationMillis) {
    this(generateRsaKeyPair(), expirationMillis, DEFAULT_ISSUER);
  }

  public JwtTokenAdapter(KeyPair keyPair, long expirationMillis) {
    this(keyPair, expirationMillis, DEFAULT_ISSUER);
  }

  public JwtTokenAdapter(KeyPair keyPair, long expirationMillis, String issuer) {
    if (expirationMillis <= 0) {
      throw new IllegalArgumentException("expirationMillis must be positive");
    }
    try {
      Math.addExact(System.currentTimeMillis(), expirationMillis);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("expirationMillis is too large", exception);
    }
    Objects.requireNonNull(keyPair, "keyPair cannot be null");
    if (issuer == null || issuer.isBlank()) {
      throw new IllegalArgumentException("issuer cannot be blank");
    }
    validateKeyPair(keyPair);
    this.publicKey = (RSAPublicKey) keyPair.getPublic();
    this.privateKey = (RSAPrivateKey) keyPair.getPrivate();
    this.algorithm = Algorithm.RSA256(this.publicKey, this.privateKey);
    this.expirationMillis = expirationMillis;
    this.issuer = issuer;
  }

  @Override
  public String issue(UserAggregate user) {
    Objects.requireNonNull(user, "user cannot be null");
    Date now = new Date();
    Date expiresAt = new Date(Math.addExact(now.getTime(), expirationMillis));

    return JWT.create()
        .withIssuer(issuer)
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
      DecodedJWT jwt = JWT.require(algorithm).withIssuer(issuer).build().verify(token);
      String sub = jwt.getSubject();
      if (sub == null || sub.isBlank()) {
        throw new AuthenticationException("Token subject is missing");
      }
      return UUID.fromString(sub);
    } catch (JWTVerificationException | IllegalArgumentException e) {
      throw new AuthenticationException("Invalid or expired token: " + e.getMessage());
    }
  }

  public String getPublicKeyPem() {
    String base64 =
        Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(publicKey.getEncoded());
    return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----";
  }

  private static KeyPair resolveKeyPair(
      String privateKeyPem, String publicKeyPem, String keyFilePath) {
    return resolveKeyPair(
        privateKeyPem, publicKeyPem, keyFilePath, Paths.get(DEFAULT_DEV_KEY_FILE));
  }

  static KeyPair resolveKeyPair(
      String privateKeyPem, String publicKeyPem, String keyFilePath, Path defaultKeyFilePath) {
    boolean hasPrivatePem = privateKeyPem != null && !privateKeyPem.isBlank();
    boolean hasPublicPem = publicKeyPem != null && !publicKeyPem.isBlank();

    if (hasPrivatePem != hasPublicPem) {
      throw new IllegalStateException(
          "Invalid JWT RSA configuration: both 'app.jwt.private-key' and 'app.jwt.public-key' must be provided together");
    }
    if (hasPrivatePem && hasPublicPem) {
      return parsePemKeyPair(privateKeyPem, publicKeyPem);
    }

    boolean hasExplicitFile = keyFilePath != null && !keyFilePath.isBlank();
    Path path = hasExplicitFile ? Paths.get(keyFilePath) : defaultKeyFilePath;
    String targetPath = path.toString();

    try {
      if (Files.exists(path) && !Files.isRegularFile(path)) {
        throw new IllegalStateException("JWT RSA key path is not a regular file: " + targetPath);
      }
      if (Files.exists(path) && Files.size(path) > 0) {
        restrictKeyFilePermissions(path);
        String content = Files.readString(path);
        String[] parts = content.split("---KEY-SPLIT---");
        if (parts.length == 2) {
          return parsePemKeyPair(parts[0], parts[1]);
        } else {
          throw new IllegalStateException("Corrupt RSA key file format in: " + targetPath);
        }
      }
      if (hasExplicitFile) {
        throw new IllegalStateException("Configured RSA key file does not exist: " + targetPath);
      }

      KeyPair keyPair = generateRsaKeyPair();
      String encodedPrivate = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
      String encodedPublic = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
      writeKeyFile(path, encodedPrivate + "---KEY-SPLIT---" + encodedPublic);
      return keyPair;
    } catch (Exception e) {
      if (e instanceof IllegalStateException ise) {
        throw ise;
      }
      throw new IllegalStateException("Failed to read/write RSA key file: " + targetPath, e);
    }
  }

  private static void writeKeyFile(Path path, String content) throws java.io.IOException {
    if (path.getParent() != null) {
      Files.createDirectories(path.getParent());
    }
    if (Files.exists(path)) {
      restrictKeyFilePermissions(path);
    } else {
      try {
        Files.createFile(
            path,
            PosixFilePermissions.asFileAttribute(
                EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)));
      } catch (UnsupportedOperationException unsupported) {
        Files.createFile(path);
      }
    }
    Files.writeString(path, content, StandardOpenOption.WRITE);
  }

  private static void restrictKeyFilePermissions(Path path) throws java.io.IOException {
    try {
      Files.setPosixFilePermissions(
          path, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
    } catch (UnsupportedOperationException | SecurityException unsupported) {
      // Keep the platform's default access control on filesystems without POSIX permissions.
    } catch (java.nio.file.FileSystemException fileSystemException) {
      if (!Files.isReadable(path)) {
        throw fileSystemException;
      }
    }
  }

  public static void validateKeyPair(KeyPair keyPair) {
    if (keyPair == null || keyPair.getPublic() == null || keyPair.getPrivate() == null) {
      throw new IllegalStateException("RSA KeyPair must contain both public and private keys");
    }
    if (!(keyPair.getPublic() instanceof RSAPublicKey pubKey)
        || !(keyPair.getPrivate() instanceof RSAPrivateKey privKey)) {
      throw new IllegalStateException("KeyPair must be an RSA key pair");
    }
    if (pubKey.getModulus().bitLength() < 2048) {
      throw new IllegalStateException("RSA keys must be at least 2048 bits");
    }
    if (!pubKey.getModulus().equals(privKey.getModulus())) {
      throw new IllegalStateException(
          "RSA public and private keys do not match (modulus mismatch)");
    }
    try {
      byte[] challenge = "jwt-keypair-validation-challenge".getBytes(StandardCharsets.UTF_8);
      Signature signer = Signature.getInstance("SHA256withRSA");
      signer.initSign(privKey);
      signer.update(challenge);
      byte[] signature = signer.sign();

      Signature verifier = Signature.getInstance("SHA256withRSA");
      verifier.initVerify(pubKey);
      verifier.update(challenge);
      if (!verifier.verify(signature)) {
        throw new IllegalStateException(
            "RSA signature verification failed: public key does not correspond to private key");
      }
    } catch (Exception e) {
      if (e instanceof IllegalStateException ise) {
        throw ise;
      }
      throw new IllegalStateException("RSA key pair validation failed", e);
    }
  }

  private static KeyPair parsePemKeyPair(String privateKeyStr, String publicKeyStr) {
    try {
      KeyFactory kf = KeyFactory.getInstance("RSA");
      String privClean =
          privateKeyStr
              .replace("-----BEGIN PRIVATE KEY-----", "")
              .replace("-----END PRIVATE KEY-----", "")
              .replace("\\n", "")
              .replace("\\r", "")
              .replaceAll("\\s+", "");
      byte[] privBytes = Base64.getDecoder().decode(privClean);
      RSAPrivateKey privKey =
          (RSAPrivateKey) kf.generatePrivate(new PKCS8EncodedKeySpec(privBytes));

      String pubClean =
          publicKeyStr
              .replace("-----BEGIN PUBLIC KEY-----", "")
              .replace("-----END PUBLIC KEY-----", "")
              .replace("\\n", "")
              .replace("\\r", "")
              .replaceAll("\\s+", "");
      byte[] pubBytes = Base64.getDecoder().decode(pubClean);
      RSAPublicKey pubKey = (RSAPublicKey) kf.generatePublic(new X509EncodedKeySpec(pubBytes));

      KeyPair keyPair = new KeyPair(pubKey, privKey);
      validateKeyPair(keyPair);
      return keyPair;
    } catch (Exception e) {
      if (e instanceof IllegalStateException ise) {
        throw ise;
      }
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
