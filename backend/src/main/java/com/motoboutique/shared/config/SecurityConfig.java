package com.motoboutique.shared.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory;
import org.springframework.boot.web.server.servlet.CookieSameSiteSupplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Configuración de Spring Security para la API REST.
 *
 * <ul>
 *   <li>Sigue usando sesión (cookie JSESSIONID), como antes: la autenticación, los roles y los
 *       permisos no cambian. Lo que cambia es la forma de responder: JSON en lugar de
 *       redirecciones a pantallas.</li>
 *   <li>Todo requiere sesión, salvo el inicio de sesión ({@code POST /api/auth/login}) y /error.</li>
 *   <li>CSRF sigue activo. El token viaja en la cookie XSRF-TOKEN (legible por el frontend) y se
 *       devuelve en la cabecera X-XSRF-TOKEN en cada POST/PUT/PATCH/DELETE.</li>
 *   <li>Sin sesión responde 401 y sin permiso 403, ambos en JSON.</li>
 *   <li>CORS: solo los orígenes de {@code app.cors.allowed-origins}, con credenciales.</li>
 *   <li>@PreAuthorize habilitado para exigir roles y permisos en los métodos.</li>
 *   <li>El usuario sale de {@code UsuarioDetailsService} y la contraseña se compara con el
 *       PasswordEncoder (BCrypt) definido en {@code PasswordEncoderConfig}.</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /** Inicio de sesión: recibe los campos "email" y "password" (formulario). */
    public static final String RUTA_LOGIN = "/api/auth/login";

    /** Cierre de sesión (POST con token CSRF). */
    public static final String RUTA_LOGOUT = "/api/auth/logout";

    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository(
            @Value("${server.servlet.session.cookie.secure:false}") boolean cookiesSeguras) {
        CookieCsrfTokenRepository repositorio = CookieCsrfTokenRepository.withHttpOnlyFalse();
        if (cookiesSeguras) {
            repositorio.setCookieCustomizer(cookie -> cookie.sameSite("None").secure(true));
        }
        return repositorio;
    }

    @Bean
    public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> cookiesSameSite(
            @Value("${server.servlet.session.cookie.secure:false}") boolean cookiesSeguras) {
        return fabrica -> {
            if (cookiesSeguras) {
                fabrica.addCookieSameSiteSuppliers(
                        CookieSameSiteSupplier.ofNone().whenHasName("XSRF-TOKEN"),
                        CookieSameSiteSupplier.ofNone().whenHasName("JSESSIONID"));
            }
        };
    }

    @Bean
    public SecurityFilterChain filtroDeSeguridad(
            HttpSecurity http, CsrfTokenRepository csrfTokenRepository) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .httpBasic(basic -> basic.disable())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        // Manejador sin ofuscación (BREACH): el frontend lee el valor de la cookie
                        // y lo reenvía tal cual en la cabecera.
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .authorizeHttpRequests(autorizacion -> autorizacion
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(RUTA_LOGIN, "/api/auth/recuperar",
                                "/api/auth/restablecer", "/error").permitAll()
                        .anyRequest().authenticated())
                .formLogin(formulario -> formulario
                        // loginPage evita que Spring genere su propia pantalla de login HTML.
                        .loginPage(RUTA_LOGIN)
                        .loginProcessingUrl(RUTA_LOGIN)
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler((request, response, autenticacion) ->
                                response.setStatus(HttpStatus.NO_CONTENT.value()))
                        .failureHandler((request, response, excepcion) ->
                                RespuestasSeguridad.escribir(response, 401, "No se pudo iniciar sesión",
                                        "Usuario o contraseña incorrectos."))
                        .permitAll())
                .logout(salida -> salida
                        .logoutUrl(RUTA_LOGOUT)
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                        .deleteCookies("JSESSIONID"))
                .exceptionHandling(excepciones -> excepciones
                        .authenticationEntryPoint((request, response, excepcion) ->
                                RespuestasSeguridad.escribir(response, 401, "Sesión requerida",
                                        "Inicia sesión para continuar."))
                        .accessDeniedHandler((request, response, excepcion) ->
                                RespuestasSeguridad.escribir(response, 403, "Acceso denegado",
                                        "No tienes permiso para realizar esta acción.")))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") String[] origenesPermitidos) {
        CorsConfiguration configuracion = new CorsConfiguration();
        configuracion.setAllowedOrigins(Arrays.stream(origenesPermitidos).map(String::trim)
                .filter(origen -> !origen.isEmpty()).toList());
        configuracion.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuracion.setAllowedHeaders(List.of("Content-Type", "Accept", "X-XSRF-TOKEN"));
        configuracion.setExposedHeaders(List.of("X-CSRF-TOKEN"));
        configuracion.setAllowCredentials(true);
        configuracion.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/**", configuracion);
        return fuente;
    }
}
