package dev.platform.user.domain.exception;

public class AccountNotFoundException extends DomainException {

  public AccountNotFoundException(String message) {
    super(message);
  }
}
