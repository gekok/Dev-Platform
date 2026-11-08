package dev.platform.user.application.port.out;

import dev.platform.user.domain.aggregate.UserAggregate;
import java.util.UUID;

public interface TokenPort {
  String issue(UserAggregate user);

  UUID parseToken(String token);
}
