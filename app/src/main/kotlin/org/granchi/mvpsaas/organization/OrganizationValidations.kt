package org.granchi.mvpsaas.organization

import io.konform.validation.Validation
import io.konform.validation.jsonschema.maxLength
import io.konform.validation.jsonschema.minLength
import io.konform.validation.jsonschema.pattern

// Commands — what comes in from the outside world
data class CreateOrganizationCommand(
    val name: String,
    val slug: String,
    val ownerClerkUserId: String
)

data class InviteMemberCommand(
    val zitadelUserId: String,
    val role: Member.Role
)

data class UpdateOrganizationCommand(
    val name: String
)

// Validators — business rules live here, not in controllers
object OrganizationValidations {

    val createOrganization = Validation<CreateOrganizationCommand> {
        CreateOrganizationCommand::name {
            minLength(2) hint "Name must be at least 2 characters"
            maxLength(255) hint "Name must be at most 255 characters"
        }
        CreateOrganizationCommand::slug {
            minLength(2) hint "Slug must be at least 2 characters"
            maxLength(100) hint "Slug must be at most 100 characters"
            pattern(Regex("^[a-z0-9-]+$")) hint "Slug can only contain lowercase letters, numbers and hyphens"
        }
        CreateOrganizationCommand::ownerClerkUserId {
            minLength(1) hint "Owner is required"
        }
    }

    val updateOrganization = Validation<UpdateOrganizationCommand> {
        UpdateOrganizationCommand::name {
            minLength(2) hint "Name must be at least 2 characters"
            maxLength(255) hint "Name must be at most 255 characters"
        }
    }

    val inviteMember = Validation<InviteMemberCommand> {
        InviteMemberCommand::zitadelUserId {
            minLength(1) hint "User ID is required"
        }
    }
}
