package world.chakchak.domain.agenda.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 자유 안건 열기 요청 (V-09)
 *
 * @param title 주제 이름(1~50자). 앞뒤 공백을 지우고 저장하며, 같은 방의 자유 안건과 겹치면 안 된다.
 * @param options 선지 내용 2~5개(각 1~50자). 적은 순서대로 보여 주고, 앞뒤 공백을 지운 뒤 서로 달라야 한다.
 */
public record CreateAgendaRequest(
        @NotBlank @Size(max = 50) String title,
        @NotNull @Size(min = 2, max = 5) List<@NotBlank @Size(max = 50) String> options) {}
