CREATE UNIQUE INDEX IF NOT EXISTS uq_member_resource_scope_global
    ON organization_member_scopes(membership_id, resource_type, access_effect)
    WHERE resource_id IS NULL;
