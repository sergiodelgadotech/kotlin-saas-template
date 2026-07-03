package tech.sergiodelgado.saastemplate.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.relational.core.mapping.event.AfterConvertCallback
import tech.sergiodelgado.saastemplate.account.AvatarImage

/**
 * Registers Spring Data JDBC lifecycle callbacks for avatar storage entities.
 *
 * [AvatarImage] implements [org.springframework.data.domain.Persistable] with a `_new` flag so
 * that freshly constructed instances are treated as INSERT rather than UPDATE (Spring Data JDBC
 * would otherwise interpret a non-null @Id as "existing"). The [AfterConvertCallback] flips
 * `_new = false` after every DB load so that subsequent saves on a loaded entity are UPDATE.
 *
 * This pattern mirrors the starter's [tech.sergiodelgado.saasstarter.autoconfigure.OrganizationAutoConfiguration]
 * which does the same for Organization and Member.
 */
@Configuration
class AvatarStoreConfig {

    @Bean
    fun avatarImageAfterConvertCallback(): AfterConvertCallback<AvatarImage> =
        AfterConvertCallback { entity ->
            entity._new = false
            entity
        }
}
