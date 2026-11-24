package world.chakchak.domain.room.service;

import java.util.List;
import world.chakchak.domain.changelog.entity.ChangeLog;

public interface ActivePlaceFilterProvider {

    List<ChangeLog> filterActivePlaces(List<ChangeLog> placeRegistrations);
}
