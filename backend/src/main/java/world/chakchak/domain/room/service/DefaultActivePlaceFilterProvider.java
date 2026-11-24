package world.chakchak.domain.room.service;

import java.util.List;
import org.springframework.stereotype.Component;
import world.chakchak.domain.changelog.entity.ChangeLog;

@Component
public class DefaultActivePlaceFilterProvider implements ActivePlaceFilterProvider {

    @Override
    public List<ChangeLog> filterActivePlaces(List<ChangeLog> placeRegistrations) {
        // TODO: C 담당 장소 도메인과 연결해 삭제된 장소 후보를 제외한다.
        return placeRegistrations;
    }
}
