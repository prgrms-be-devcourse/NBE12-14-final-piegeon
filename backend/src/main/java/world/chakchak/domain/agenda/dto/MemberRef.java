package world.chakchak.domain.agenda.dto;

import world.chakchak.domain.room.entity.RoomMember;

/** 응답에 넣는 멤버 정보. 이름은 user.name이다. */
public record MemberRef(Long memberId, String name) {

    public static MemberRef from(RoomMember member) {
        return new MemberRef(member.getId(), member.getUser().getName());
    }
}
