package com.joblens.api.config;

import com.joblens.api.security.JwtAuthenticationFilter;
import com.joblens.api.security.RestAccessDeniedHandler;
import com.joblens.api.security.RestAuthenticationEntryPoint;
import com.joblens.api.common.web.ApiRoutes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * The security filter chain.
 *
 * <h2>Stateless</h2>
 *
 * <p>No sessions, ever: {@link SessionCreationPolicy#STATELESS} means Spring
 * never creates or consults an {@code HttpSession}, so identity comes from the
 * token on each request and nowhere else. That is what lets the backend scale to
 * zero and back on a free tier without logging everyone out, and what makes a
 * second instance behave identically to the first.
 *
 * <h2>CSRF disabled -- and why that is correct here</h2>
 *
 * <p>CSRF protection defends against a browser attaching credentials to a
 * request the user did not intend. That only happens with <em>ambient</em>
 * credentials: cookies, or HTTP basic. This API authenticates with an
 * {@code Authorization} header that a client must set deliberately, and a
 * cross-site form post cannot set headers. With no session cookie there is
 * nothing for an attacker's page to ride on.
 *
 * <p>Disabling CSRF would be wrong the moment we put the token in a cookie.
 * That is the line to watch, not the annotation.
 *
 * <h2>What is public</h2>
 *
 * <p>Registration and login have to be reachable without a token, and the health
 * endpoint has to be reachable by the platform's health check. Company
 * <em>reads</em> stay public because discovery is the product -- someone should
 * be able to look at a company before signing up -- but company <em>writes</em>
 * are now authenticated, which tightens what step 3 left open.
 *
 * <p>Everything else falls to {@code anyRequest().authenticated()}. A new
 * endpoint is therefore protected by default and has to be opened deliberately,
 * which is the right way round: forgetting to protect something is silent,
 * forgetting to open something is immediately obvious.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          RestAuthenticationEntryPoint authenticationEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // Uses the CorsConfigurationSource bean from CorsConfig. Declared
                // here as well as there because Spring Security runs its own
                // filter chain: without this line the MVC CORS settings would not
                // apply to requests Security rejects, and a browser would see an
                // opaque CORS error instead of a readable 401.
                .cors(Customizer.withDefaults())

                .csrf(csrf -> csrf.disable())

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Both render ApiError, so authentication failures look like
                // every other failure rather than Spring Security's default HTML.
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))

                .authorizeHttpRequests(requests -> requests
                        // Authentication itself.
                        .requestMatchers(HttpMethod.POST, ApiRoutes.API_V1 + "/auth/register").permitAll()
                        .requestMatchers(HttpMethod.POST, ApiRoutes.API_V1 + "/auth/login").permitAll()

                        // Platform health check and the build identifier.
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, ApiRoutes.API_V1 + "/meta").permitAll()

                        // Company discovery is public; changing a company is not.
                        .requestMatchers(HttpMethod.GET, ApiRoutes.API_V1 + "/companies/**").permitAll()

                        .anyRequest().authenticated())

                // Runs before the form-login filter so that a request carrying a
                // valid token is already authenticated by the time authorization
                // is evaluated.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                .build();
    }

    /**
     * Password hashing.
     *
     * <p>A {@code DelegatingPasswordEncoder} stores the algorithm in the hash
     * itself ({@code {bcrypt}$2a$10$...}) and can verify against any encoder it
     * knows. That means moving to a stronger algorithm later is a change of
     * default plus a re-hash on next login, rather than invalidating every
     * password in the database. Spring Security's factory sets bcrypt as the
     * current default.
     *
     * <p>The bcrypt work factor is left at the library default. It is
     * deliberately slow -- that is the entire point -- and the free tier gives
     * us 0.1 CPU, so raising it would make login painfully slow for a
     * marginal gain. Worth revisiting on real hardware.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
