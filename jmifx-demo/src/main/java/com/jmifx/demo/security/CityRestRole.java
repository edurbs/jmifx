package com.jmifx.demo.security;

import com.jmifx.demo.entity.City;
import io.jmix.security.model.EntityAttributePolicyAction;
import io.jmix.security.model.EntityPolicyAction;
import io.jmix.security.role.annotation.EntityAttributePolicy;
import io.jmix.security.role.annotation.EntityPolicy;
import io.jmix.security.role.annotation.ResourceRole;

/**
 * API-scope role allowing the client to create and read cities over REST.
 */
@ResourceRole(name = "City REST", code = CityRestRole.CODE, scope = "API")
public interface CityRestRole {

    String CODE = "city-rest";

    @EntityPolicy(entityClass = City.class, actions = {EntityPolicyAction.CREATE, EntityPolicyAction.READ})
    @EntityAttributePolicy(entityClass = City.class, attributes = "name", action = EntityAttributePolicyAction.MODIFY)
    void city();
}
