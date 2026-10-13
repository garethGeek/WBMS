package wbms.tanroads.wbms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // For API development, disable CSRF (otherwise POST/PUT/DELETE will 403)
                .csrf(csrf -> csrf.disable())

                .authorizeHttpRequests(auth -> auth
                        // Allow Swagger/OpenAPI and development endpoints without a browser auth popup.
                        .requestMatchers(
                                "/",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html"
                        ).permitAll()

                        // Allow WBMS API endpoints
                        .requestMatchers("/api/**").permitAll()

                        // Development mode: keep everything open.
                        .anyRequest().permitAll()
                )

                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(form -> form.disable());

        return http.build();
    }
}
