package dev.platform.board.domain.aggregate;

public final class ProjectRuleViolation extends RuntimeException {
    private final String code;
    private final int httpStatus;

    private ProjectRuleViolation(String code, int httpStatus, String message) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String code() {
        return code;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public static ProjectRuleViolation validation(String message) {
        return new ProjectRuleViolation("VALIDATION_ERROR", 400, message);
    }

    public static ProjectRuleViolation notFound(String message) {
        return new ProjectRuleViolation("RESOURCE_NOT_FOUND", 404, message);
    }

    public static ProjectRuleViolation forbidden(String message) {
        return new ProjectRuleViolation("FORBIDDEN", 403, message);
    }

    public static ProjectRuleViolation unauthorized(String message) {
        return new ProjectRuleViolation("UNAUTHENTICATED", 401, message);
    }

    public static ProjectRuleViolation conflict(String code, String message) {
        return new ProjectRuleViolation(code, 409, message);
    }

    public static ProjectRuleViolation unprocessable(String code, String message) {
        return new ProjectRuleViolation(code, 422, message);
    }

    public static ProjectRuleViolation unavailable(String code, String message) {
        return new ProjectRuleViolation(code, 503, message);
    }

    public static ProjectRuleViolation internal(String code, String message) {
        return new ProjectRuleViolation(code, 500, message);
    }
}
