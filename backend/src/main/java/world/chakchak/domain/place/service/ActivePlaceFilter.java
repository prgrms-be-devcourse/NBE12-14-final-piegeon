package world.chakchak.domain.place.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.chakchak.domain.changelog.entity.ChangeLog;
import world.chakchak.domain.place.repository.PlaceCandidateRepository;
import world.chakchak.domain.room.service.ActivePlaceFilterProvider;

/**
 * 비접속 요약(H-02)의 장소 등록 소식에서, 그 사이 지워진 장소를 뺀다.
 *
 * <p>후보 삭제(P-07)는 행을 완전히 지우고, 같은 장소를 다시 올리면 새 id가 생긴다. 그래서 기록이 가리키는 id가 아직 남아 있는지만 보면 된다. 변경 기록은
 * 지우지 않고 요약을 만들 때만 뺀다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivePlaceFilter implements ActivePlaceFilterProvider {

    private final PlaceCandidateRepository placeCandidateRepository;

    @Override
    public List<ChangeLog> filterActivePlaces(List<ChangeLog> placeRegistrations) {
        if (placeRegistrations.isEmpty()) {
            return placeRegistrations;
        }
        List<Long> placeIds = placeRegistrations.stream().map(ChangeLog::getEntityId).toList();
        Set<Long> existingIds = new HashSet<>(placeCandidateRepository.findExistingIds(placeIds));
        return placeRegistrations.stream()
                .filter(registration -> existingIds.contains(registration.getEntityId()))
                .toList();
    }
}
