package com.tok.pekko.domain.channel.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.tok.pekko.domain.channel.model.ChannelPermissionType;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ChannelManagePermissionsTest {

    @Test
    void 채널_오너의_채널_관리_권한을_초기화한다() {
        // when
        ChannelManagePermissions actual = ChannelManagePermissions.ofOwner();

        // then
        assertAll(
                () -> assertThat(actual.isEmpty()).isTrue(),
                () -> assertThat(actual.size()).isZero()
        );
    }

    @Test
    void 채널_매니저의_기본_권한인_메시지_수정을_가진_채널_관리_권한을_초기화한다() {
        // when
        ChannelManagePermissions actual = ChannelManagePermissions.ofManager();

        // then
        assertAll(
                () -> assertThat(actual.has(ChannelPermissionType.MESSAGE_EDIT)).isTrue(),
                () -> assertThat(actual.size()).isEqualTo(1),
                () -> assertThat(actual.isEmpty()).isFalse()
        );
    }

    @Test
    void 여러_권한을_지정해_채널_매니저의_채널_관리_권한을_초기화한다() {
        // when
        ChannelManagePermissions actual = ChannelManagePermissions.ofManager(
                Set.of(
                        ChannelPermissionType.MESSAGE_EDIT,
                        ChannelPermissionType.MESSAGE_DELETE,
                        ChannelPermissionType.MEMBER_INVITE
                )
        );

        // then
        assertAll(
                () -> assertThat(actual.has(ChannelPermissionType.MESSAGE_EDIT)).isTrue(),
                () -> assertThat(actual.has(ChannelPermissionType.MESSAGE_DELETE)).isTrue(),
                () -> assertThat(actual.has(ChannelPermissionType.MEMBER_INVITE)).isTrue(),
                () -> assertThat(actual.size()).isEqualTo(3)
        );
    }

    @Test
    void 채널에_참여한_사용자의_채널_관리_권한을_초기화한다() {
        // when
        ChannelManagePermissions actual = ChannelManagePermissions.ofMember();

        // then
        assertAll(
                () -> assertThat(actual.isEmpty()).isTrue(),
                () -> assertThat(actual.size()).isZero()
        );
    }

    @Test
    void 특정_권한을_가졌는지_확인한다() {
        // given
        ChannelManagePermissions actual = ChannelManagePermissions.ofManager();

        // when & then
        assertAll(
                () -> assertThat(actual.has(ChannelPermissionType.MESSAGE_EDIT)).isTrue(),
                () -> assertThat(actual.has(ChannelPermissionType.MESSAGE_DELETE)).isFalse()
        );
    }

    @Test
    void 지정한_여러_권한을_모두_가졌는지_확인한다() {
        // given
        ChannelPermissionType perm1 = ChannelPermissionType.MESSAGE_EDIT;
        ChannelPermissionType perm2 = ChannelPermissionType.MESSAGE_DELETE;
        ChannelPermissionType perm3 = ChannelPermissionType.MEMBER_INVITE;

        ChannelManagePermissions permissions = ChannelManagePermissions.ofManager(Set.of(perm1, perm2));

        Set<ChannelPermissionType> allExistingPerms = EnumSet.of(perm1, perm2);
        Set<ChannelPermissionType> partialPerms = EnumSet.of(perm1);
        Set<ChannelPermissionType> notExistingPerms = EnumSet.of(perm3);

        // when & then
        assertAll(
                () -> assertThat(permissions.hasAll(allExistingPerms)).isTrue(),
                () -> assertThat(permissions.hasAll(partialPerms)).isTrue(),
                () -> assertThat(permissions.hasAll(notExistingPerms)).isFalse()
        );
    }

    @Test
    void 지정한_여러_권한_중_하나라도_가졌는지_확인한다() {
        // given
        ChannelPermissionType perm1 = ChannelPermissionType.MESSAGE_EDIT;
        ChannelPermissionType perm2 = ChannelPermissionType.MESSAGE_DELETE;
        ChannelPermissionType perm3 = ChannelPermissionType.MEMBER_INVITE;

        ChannelManagePermissions permissions = ChannelManagePermissions.ofManager(Set.of(perm1));

        Set<ChannelPermissionType> withExisting = EnumSet.of(perm1, perm2);
        Set<ChannelPermissionType> allNotExisting = EnumSet.of(perm2, perm3);

        // when & then
        assertAll(
                () -> assertThat(permissions.hasAny(withExisting)).isTrue(),
                () -> assertThat(permissions.hasAny(allNotExisting)).isFalse()
        );
    }

    @Test
    void 권한을_추가한다() {
        // given
        ChannelManagePermissions permissions = ChannelManagePermissions.ofManager();

        // when
        ChannelManagePermissions addedPermissions = permissions.add(ChannelPermissionType.MESSAGE_DELETE);

        // then
        assertAll(
                () -> assertThat(addedPermissions.has(ChannelPermissionType.MESSAGE_EDIT)).isTrue(),
                () -> assertThat(addedPermissions.has(ChannelPermissionType.MESSAGE_DELETE)).isTrue(),
                () -> assertThat(addedPermissions.size()).isEqualTo(2),
                () -> assertThat(permissions.has(ChannelPermissionType.MESSAGE_DELETE)).isFalse(),
                () -> assertThat(permissions.size()).isEqualTo(1)
        );
    }

    @Test
    void 권한을_제거한다() {
        // given
        ChannelPermissionType perm1 = ChannelPermissionType.MESSAGE_EDIT;
        ChannelPermissionType perm2 = ChannelPermissionType.MESSAGE_DELETE;

        ChannelManagePermissions permissions = ChannelManagePermissions.ofManager(Set.of(perm1, perm2));

        // when
        ChannelManagePermissions removedPermissions = permissions.remove(perm1);

        // then
        assertAll(
                () -> assertThat(removedPermissions.has(perm1)).isFalse(),
                () -> assertThat(removedPermissions.has(perm2)).isTrue(),
                () -> assertThat(removedPermissions.size()).isEqualTo(1),
                () -> assertThat(permissions.has(perm1)).isTrue(),
                () -> assertThat(permissions.size()).isEqualTo(2)
        );
    }

    @Test
    void 모든_권한을_조회한다() {
        // given
        ChannelPermissionType perm1 = ChannelPermissionType.MESSAGE_EDIT;
        ChannelPermissionType perm2 = ChannelPermissionType.MESSAGE_DELETE;

        ChannelManagePermissions permissions = ChannelManagePermissions.ofManager(Set.of(perm1, perm2));

        // when
        Set<ChannelPermissionType> allPermissions = permissions.getAll();

        // then
        assertAll(
                () -> assertThat(allPermissions).contains(perm1, perm2),
                () -> assertThat(allPermissions).hasSize(2)
        );
    }

    @Test
    void 권한_개수를_확인한다() {
        // given
        ChannelManagePermissions emptyPermissions = ChannelManagePermissions.ofMember();
        ChannelManagePermissions singlePermissions = ChannelManagePermissions.ofManager();
        ChannelManagePermissions multiplePermissions = ChannelManagePermissions.ofManager(
                Set.of(
                        ChannelPermissionType.MESSAGE_EDIT,
                        ChannelPermissionType.MESSAGE_DELETE,
                        ChannelPermissionType.MEMBER_INVITE
                )
        );

        // when & then
        assertAll(
                () -> assertThat(emptyPermissions.size()).isZero(),
                () -> assertThat(singlePermissions.size()).isEqualTo(1),
                () -> assertThat(multiplePermissions.size()).isEqualTo(3)
        );
    }

    @Test
    void 권한이_비어있는지_확인한다() {
        // given
        ChannelManagePermissions emptyPermissions = ChannelManagePermissions.ofMember();
        ChannelManagePermissions nonEmptyPermissions = ChannelManagePermissions.ofManager();

        // when & then
        assertAll(
                () -> assertThat(emptyPermissions.isEmpty()).isTrue(),
                () -> assertThat(nonEmptyPermissions.isEmpty()).isFalse()
        );
    }

    @Test
    void 같은_권한을_가진_채널_관리_권한은_동등하다() {
        // given
        ChannelPermissionType perm1 = ChannelPermissionType.MESSAGE_EDIT;
        ChannelPermissionType perm2 = ChannelPermissionType.MESSAGE_DELETE;

        ChannelManagePermissions permissions1 = ChannelManagePermissions.ofManager(Set.of(perm1, perm2));
        ChannelManagePermissions permissions2 = ChannelManagePermissions.ofManager(Set.of(perm1, perm2));

        // when & then
        assertAll(
                () -> assertThat(permissions1).isEqualTo(permissions2),
                () -> assertThat(permissions1).hasSameHashCodeAs(permissions2)
        );
    }

    @Test
    void 다른_권한을_가진_채널_관리_권한은_동등하지_않다() {
        // given
        ChannelManagePermissions permissions1 = ChannelManagePermissions.ofManager(Set.of(ChannelPermissionType.MESSAGE_EDIT));
        ChannelManagePermissions permissions2 = ChannelManagePermissions.ofManager(
                Set.of(
                        ChannelPermissionType.MESSAGE_EDIT,
                        ChannelPermissionType.MESSAGE_DELETE
                )
        );

        // when & then
        assertAll(
                () -> assertThat(permissions1).isNotEqualTo(permissions2),
                () -> assertThat(permissions1).doesNotHaveSameHashCodeAs(permissions2)
        );
    }
}
