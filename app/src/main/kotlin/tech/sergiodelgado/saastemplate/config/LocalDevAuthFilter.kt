package tech.sergiodelgado.saastemplate.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Profile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Profile("local")
@Component
class LocalDevAuthFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        if (SecurityContextHolder.getContext().authentication == null) {
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(
                    LOCAL_DEV_USER_ID,
                    null,
                    listOf(SimpleGrantedAuthority("ROLE_USER"))
                )
        }
        chain.doFilter(request, response)
    }

    companion object {
        const val LOCAL_DEV_USER_ID = "local-dev-user"
    }
}
