package com.webapp.jobportal.config;

import com.webapp.jobportal.services.CustomUserDetailsService;
import com.webapp.jobportal.services.CustomOAuth2UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class WebSecurityConfig {

        private final CustomUserDetailsService customUserDetailsService;
        private final CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;
        private final CustomAuthenticationFailureHandler customAuthenticationFailureHandler;
        private final RateLimitingFilter rateLimitingFilter;

        @Autowired
        public WebSecurityConfig(CustomUserDetailsService customUserDetailsService,
                        CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler,
                        CustomAuthenticationFailureHandler customAuthenticationFailureHandler,
                        RateLimitingFilter rateLimitingFilter) {
                this.customUserDetailsService = customUserDetailsService;
                this.customAuthenticationSuccessHandler = customAuthenticationSuccessHandler;
                this.customAuthenticationFailureHandler = customAuthenticationFailureHandler;
                this.rateLimitingFilter = rateLimitingFilter;
        }

        private final String[] publicUrl = { "/",
                        "/global-search/**",
                        "/info/**",
                        "/register",
                        "/register/**",
                        "/forgot-password",
                        "/reset-password",
                        "/verify-email",
                        "/webjars/**",
                        "/resources/**",
                        "/assets/**",
                        "/css/**",
                        "/summernote/**",
                        "/js/**",
                        "/*.css",
                        "/*.js",
                        "/*.js.map",
                        "/fonts**", "/favicon.ico", "/resources/**", "/error", "/photos/**" };

        private final String[] websocketHandshakeUrl = { "/ws/**" };

        @Autowired
        private CustomOAuth2UserService oauth2UserService;

        @Autowired
        private OAuth2LoginSuccessHandler oauth2LoginSuccessHandler;

        @Bean
        protected SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

                http.authenticationProvider(authenticationProvider());

                http.authorizeHttpRequests(auth -> {
                        auth.requestMatchers(publicUrl).permitAll();
                        // auth.requestMatchers(websocketHandshakeUrl).permitAll();

                        // Centralized Authorization
                        auth.requestMatchers("/client-dashboard/**").hasAuthority("Client");
                        auth.requestMatchers("/freelancer-dashboard/**").hasAuthority("Freelancer");
                        auth.requestMatchers("/admin/**").hasAuthority("Admin");

                        auth.anyRequest().authenticated();
                }).sessionManagement(session -> session
                                .sessionFixation().migrateSession()
                                .maximumSessions(1)
                                .expiredUrl("/login?expired=true"))
                                .addFilterBefore(rateLimitingFilter,
                                                org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                                .addFilterAfter(new org.springframework.web.filter.OncePerRequestFilter() {
                                        @Override
                                        protected void doFilterInternal(
                                                        @org.springframework.lang.NonNull jakarta.servlet.http.HttpServletRequest request,
                                                        @org.springframework.lang.NonNull jakarta.servlet.http.HttpServletResponse response,
                                                        @org.springframework.lang.NonNull jakarta.servlet.FilterChain filterChain)
                                                        throws jakarta.servlet.ServletException, java.io.IOException {
                                                org.springframework.security.web.csrf.CsrfToken token = (org.springframework.security.web.csrf.CsrfToken) request
                                                                .getAttribute(org.springframework.security.web.csrf.CsrfToken.class
                                                                                .getName());
                                                if (token != null) {
                                                        token.getToken();
                                                }
                                                filterChain.doFilter(request, response);
                                        }
                                }, org.springframework.security.web.csrf.CsrfFilter.class);

                http.formLogin(form -> form.loginPage("/login").permitAll()
                                .successHandler(customAuthenticationSuccessHandler)
                                .failureHandler(customAuthenticationFailureHandler))
                                .logout(logout -> {
                                        logout.logoutUrl("/logout");
                                        logout.logoutSuccessUrl("/");
                                }).cors(Customizer.withDefaults())
                                .csrf(csrf -> csrf
                                                .ignoringRequestMatchers("/payment/webhook"))
                                .headers(headers -> headers
                                                .httpStrictTransportSecurity(hsts -> hsts
                                                                .includeSubDomains(true)
                                                                .maxAgeInSeconds(31536000)
                                                                .requestMatcher(org.springframework.security.web.util.matcher.AntPathRequestMatcher
                                                                                .antMatcher("/**")))
                                                .contentSecurityPolicy(csp -> csp.policyDirectives(
                                                                "default-src 'self'; " +
                                                                                "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://js.stripe.com https://cdn.jsdelivr.net https://cdnjs.cloudflare.com; "
                                                                                +
                                                                                "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://cdn.jsdelivr.net https://cdnjs.cloudflare.com; "
                                                                                +
                                                                                "img-src 'self' data: https:; " +
                                                                                "font-src 'self' data: https://fonts.gstatic.com https://cdnjs.cloudflare.com; "
                                                                                +
                                                                                "frame-src 'self' https://js.stripe.com https://hooks.stripe.com; "
                                                                                +
                                                                                "connect-src 'self' https://api.stripe.com https://checkout.stripe.com;"))
                                                .frameOptions(frame -> frame.sameOrigin()))
                                .oauth2Login(oauth2 -> oauth2
                                                .loginPage("/login")
                                                .failureUrl("/login?error")
                                                .userInfoEndpoint(userInfo -> userInfo
                                                                .userService(oauth2UserService))
                                                .successHandler(oauth2LoginSuccessHandler));

                return http.build();
        }

        @Bean
        public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
                org.springframework.web.cors.CorsConfiguration configuration = new org.springframework.web.cors.CorsConfiguration();
                configuration.setAllowedOrigins(java.util.Arrays.asList("http://localhost:8080", "https://jobify.com")); // Add
                                                                                                                         // production
                                                                                                                         // domain
                configuration.setAllowedMethods(java.util.Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                configuration.setAllowedHeaders(java.util.Arrays.asList("*"));
                configuration.setAllowCredentials(true);
                org.springframework.web.cors.UrlBasedCorsConfigurationSource source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);
                return source;
        }

        @Bean
        public AuthenticationProvider authenticationProvider() {

                DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider();
                authenticationProvider.setPasswordEncoder(passwordEncoder());
                authenticationProvider.setUserDetailsService(customUserDetailsService);
                return authenticationProvider;
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }
}
