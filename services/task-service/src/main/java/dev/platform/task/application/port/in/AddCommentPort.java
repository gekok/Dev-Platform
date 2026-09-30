package dev.platform.task.application.port.in;

import dev.platform.task.application.command.AddCommentCommand;
import dev.platform.task.domain.aggregate.CardAggregate.CommentValue;

// Cổng vào: thêm bình luận vào thẻ.
public interface AddCommentPort {

    CommentValue addComment(AddCommentCommand cmd);
}
