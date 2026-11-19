package world.chakchak.domain.room.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record SeenRequest(@NotNull LocalDateTime seenUntil) {}
