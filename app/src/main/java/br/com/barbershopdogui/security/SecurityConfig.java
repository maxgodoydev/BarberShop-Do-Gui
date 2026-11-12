package br.com.barbershopdogui.security;

import br.com.barbershopdogui.service.AdminUserService;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            LoginAttempts attempts,
            AdminUserService users,
            @Value("${app.security.csp:default-src 'self'; img-src 'self' data:; frame-src https://www.google.com; frame-ancestors 'none'; form-action 'self'; base-uri 'self'}") String csp
    ) throws Exception {

        http.csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()));

        // Perfis administrativos.
        String owner = "PROPRIETARIO";
        String desk = "RECEPCAO";
        String tech = "TECNICO";

        http.authorizeHttpRequests(auth -> auth

                // BI e indicadores.
                .requestMatchers(
                        "/api/admin/dashboard",
                        "/api/admin/bi",
                        "/api/admin/export/**"
                ).hasAnyRole(owner, tech)

                .requestMatchers("/api/admin/clientes/resumo")
                .hasAnyRole(owner, tech)

                // Clientes.
                .requestMatchers(HttpMethod.GET, "/api/admin/clientes")
                .hasAnyRole(owner, tech, desk)

                .requestMatchers("/api/admin/clientes/**")
                .hasAnyRole(owner, tech)

                // Serviços e profissionais.
                .requestMatchers(
                        HttpMethod.GET,
                        "/api/admin/servicos",
                        "/api/admin/profissionais"
                ).hasAnyRole(owner, tech, desk)

                // Operação diária.
                .requestMatchers(
                        "/api/admin/agendamentos",
                        "/api/admin/agendamentos/**",
                        "/api/admin/balcao/**",
                        "/api/admin/equipe/**"
                ).hasAnyRole(owner, desk)

                // Estoque.
                .requestMatchers(HttpMethod.GET, "/api/admin/produtos")
                .hasAnyRole(owner, desk)

                .requestMatchers(HttpMethod.PATCH, "/api/admin/produtos/*/estoque")
                .hasAnyRole(owner, desk)

                // Demais APIs administrativas.
                .requestMatchers("/api/admin/**")
                .hasRole(owner)

                // Autenticação.
                .requestMatchers("/api/auth/me", "/api/auth/senha")
                .authenticated()

                // Páginas administrativas.
                .requestMatchers("/admin/painel.html")
                .hasAnyRole(owner, tech, desk)

                .requestMatchers("/admin/bi.html")
                .hasAnyRole(owner, tech)

                .requestMatchers(
                        "/admin/agenda.html",
                        "/admin/equipe.html",
                        "/admin/estoque.html"
                ).hasAnyRole(owner, desk)

                .requestMatchers(
                        "/admin/servicos.html",
                        "/admin/profissionais.html"
                ).hasAnyRole(owner, tech)

                .requestMatchers("/admin/clientes.html")
                .hasAnyRole(owner, tech, desk)

                .requestMatchers(
                        "/admin/usuarios.html",
                        "/admin/horarios.html"
                ).hasRole(owner)

                .requestMatchers("/admin/**")
                .hasAnyRole(owner, tech, desk)

                .anyRequest().permitAll()
        );

        http.formLogin(form -> form
                .loginPage("/admin-login.html")
                .loginProcessingUrl("/api/auth/login")
                .successHandler((req, res, auth) -> {
                    attempts.success(auth.getName());
                    res.setStatus(HttpStatus.OK.value());
                })
                .failureHandler((req, res, ex) -> {
                    if (ex instanceof BadCredentialsException) {
                        attempts.failure(req.getParameter("username"));
                    }

                    res.setStatus(HttpStatus.UNAUTHORIZED.value());
                })
                .permitAll()
        );

        http.logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler(
                        (req, res, auth) ->
                                res.setStatus(HttpStatus.NO_CONTENT.value())
                )
        );

        http.exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) ->
                        redirectOrStatus(
                                req.getRequestURI(),
                                res,
                                HttpStatus.UNAUTHORIZED
                        )
                )
                .accessDeniedHandler((req, res, e) ->
                        redirectOrStatus(
                                req.getRequestURI(),
                                res,
                                HttpStatus.FORBIDDEN
                        )
                )
        );

        http.sessionManagement(s ->
                s.sessionFixation(f -> f.changeSessionId())
        );

        http.headers(h -> h
                .contentSecurityPolicy(c -> c.policyDirectives(csp))
                .referrerPolicy(r -> r.policy(
                        org.springframework.security.web.header.writers
                                .ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN
                ))
        );

        // Impede o acesso administrativo enquanto a senha provisória
        // não for substituída.
        http.addFilterAfter(
                new ForcedPasswordChangeFilter(users),
                UsernamePasswordAuthenticationFilter.class
        );

        return http.build();
    }

    private static void redirectOrStatus(
            String uri,
            jakarta.servlet.http.HttpServletResponse res,
            HttpStatus status
    ) throws IOException {

        if (uri.startsWith("/api/")) {
            res.setStatus(status.value());
        } else {
            res.sendRedirect("/admin-login.html");
        }
    }
}
