package com.tok.pekko.domain.channel.model.vo;

import com.tok.pekko.domain.channel.model.ChannelPermissionType;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode
public class ChannelManagePermissions {

    private final Set<ChannelPermissionType> permissions;

    public static ChannelManagePermissions ofOwner() {
        return new ChannelManagePermissions(EnumSet.noneOf(ChannelPermissionType.class));
    }

    public static ChannelManagePermissions ofManager() {
        return new ChannelManagePermissions(EnumSet.of(ChannelPermissionType.MESSAGE_EDIT));
    }

    public static ChannelManagePermissions ofManager(Set<ChannelPermissionType> permissions) {
        if (permissions.isEmpty()) {
            return ChannelManagePermissions.ofManager();
        }

        return new ChannelManagePermissions(permissions);
    }

    public static ChannelManagePermissions ofMember() {
        return new ChannelManagePermissions(EnumSet.noneOf(ChannelPermissionType.class));
    }

    private ChannelManagePermissions(Set<ChannelPermissionType> permissions) {
        this.permissions = permissions;
    }

    public boolean has(ChannelPermissionType permission) {
        return permissions.contains(permission);
    }

    public boolean hasAll(Set<ChannelPermissionType> perms) {
        return permissions.containsAll(perms);
    }

    public boolean hasAny(Set<ChannelPermissionType> perms) {
        return perms.stream().anyMatch(permissions::contains);
    }

    public ChannelManagePermissions add(ChannelPermissionType permission) {
        Set<ChannelPermissionType> newPermissions = EnumSet.copyOf(permissions);

        newPermissions.add(permission);
        return new ChannelManagePermissions(newPermissions);
    }

    public ChannelManagePermissions remove(ChannelPermissionType permission) {
        Set<ChannelPermissionType> newPermissions = EnumSet.copyOf(permissions);

        newPermissions.remove(permission);
        return new ChannelManagePermissions(newPermissions);
    }

    public Set<ChannelPermissionType> getAll() {
        return Collections.unmodifiableSet(EnumSet.copyOf(permissions));
    }

    public int size() {
        return permissions.size();
    }

    public boolean isEmpty() {
        return permissions.isEmpty();
    }
}
