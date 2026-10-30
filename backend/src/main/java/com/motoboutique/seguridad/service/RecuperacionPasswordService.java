package com.motoboutique.seguridad.service;

import com.motoboutique.seguridad.domain.LimiteRecuperacionPassword;
import com.motoboutique.seguridad.domain.TokenRecuperacion;
import com.motoboutique.seguridad.domain.Usuario;
import com.motoboutique.seguridad.repository.LimiteRecuperacionRepository;
import com.motoboutique.seguridad.repository.TokenRecuperacionRepository;
import com.motoboutique.seguridad.repository.UsuarioRepository;
import com.motoboutique.shared.exception.NegocioException;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecuperacionPasswordService {

    private static final Duration VENTANA_INTENTOS = Duration.ofMinutes(15);
    private static final Duration BLOQUEO = Duration.ofHours(1);
    private static final int MAX_INTENTOS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TokenRecuperacionRepository tokenRepository;
    private final LimiteRecuperacionRepository limiteRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;
    private final CorreoRecuperacion correoRecuperacion;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.password-reset.token-ttl-minutes:30}")
    private long expiracionMinutos;

    @PostConstruct
    void validarConfiguracion() {
        if (expiracionMinutos < 1) {
            throw new IllegalStateException("PASSWORD_RESET_TOKEN_TTL_MINUTES debe ser mayor que cero.");
        }
    }

    @Transactional
    public void solicitar(String email) {
        String normalizado = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        String clave = sha256(normalizado);
        if (!registrarIntento(clave)) {
            log.warn("Solicitud de recuperación limitada para clave {}", clave);
            return;
        }
        Optional<Usuario> usuario = usuarioRepository.findByEmailIgnoreCaseAndEliminadoFalse(normalizado)
                .filter(Usuario::isActivo);
        if (usuario.isEmpty()) {
            return;
        }
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String tokenPlano = HexFormat.of().formatHex(bytes);
        Instant ahora = Instant.now();
        tokenRepository.invalidarNoUsadosPorUsuario(usuario.get().getId(), ahora);
        TokenRecuperacion token = new TokenRecuperacion();
        token.setUsuario(usuario.get());
        token.setTokenHash(sha256(tokenPlano));
        token.setExpiraEn(ahora.plus(Duration.ofMinutes(expiracionMinutos)));
        tokenRepository.save(token);
        enviarEnlace(normalizado, tokenPlano);
    }

    @Transactional
    public void restablecer(String tokenPlano, String passwordNueva) {
        if (tokenPlano == null || tokenPlano.isBlank()) {
            throw new NegocioException("El enlace de recuperación no es válido o venció.");
        }
        TokenRecuperacion token = tokenRepository.bloquearVigentePorHash(sha256(tokenPlano))
                .orElseThrow(() -> new NegocioException("El enlace de recuperación no es válido o venció."));
        Instant ahora = Instant.now();
        if (!token.estaVigente(ahora) || !token.getUsuario().isActivo()
                || token.getUsuario().isEliminado()) {
            throw new NegocioException("El enlace de recuperación no es válido o venció.");
        }
        usuarioService.cambiarPassword(token.getUsuario().getId(), passwordNueva);
        token.marcarUsado(ahora);
        tokenRepository.save(token);
    }

    private boolean registrarIntento(String clave) {
        Instant ahora = Instant.now();
        LimiteRecuperacionPassword limite = limiteRepository.bloquearPorClave(clave).orElse(null);
        if (limite == null) {
            limite = new LimiteRecuperacionPassword();
            limite.setClave(clave);
            limite.setVentanaInicio(ahora);
        } else if (limite.getBloqueadoHasta() != null && ahora.isBefore(limite.getBloqueadoHasta())) {
            return false;
        } else if (ahora.isAfter(limite.getVentanaInicio().plus(VENTANA_INTENTOS))) {
            limite.setIntentos(0);
            limite.setVentanaInicio(ahora);
            limite.setBloqueadoHasta(null);
        }
        limite.setIntentos(limite.getIntentos() + 1);
        if (limite.getIntentos() > MAX_INTENTOS) {
            limite.setBloqueadoHasta(ahora.plus(BLOQUEO));
            limiteRepository.save(limite);
            return false;
        }
        limiteRepository.save(limite);
        return true;
    }

    private void enviarEnlace(String email, String token) {
        String enlace = frontendUrl.replaceAll("/+$", "") + "/restablecer-password?token=" + token;
        correoRecuperacion.enviar(email, enlace, expiracionMinutos);
    }

    private String sha256(String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no está disponible.", ex);
        }
    }
}
