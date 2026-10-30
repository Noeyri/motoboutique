package com.motoboutique.seguridad.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.motoboutique.seguridad.domain.TokenRecuperacion;
import com.motoboutique.seguridad.domain.Usuario;
import com.motoboutique.seguridad.repository.LimiteRecuperacionRepository;
import com.motoboutique.seguridad.repository.TokenRecuperacionRepository;
import com.motoboutique.seguridad.repository.UsuarioRepository;
import com.motoboutique.shared.exception.CorreoNoDisponibleException;
import com.motoboutique.shared.exception.NegocioException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RecuperacionPasswordServiceTest {

    @Mock
    private TokenRecuperacionRepository tokenRepository;

    @Mock
    private LimiteRecuperacionRepository limiteRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private CorreoRecuperacion correoRecuperacion;

    private RecuperacionPasswordService servicio;

    @BeforeEach
    void preparar() {
        servicio = new RecuperacionPasswordService(tokenRepository, limiteRepository,
                usuarioRepository, usuarioService, correoRecuperacion);
        ReflectionTestUtils.setField(servicio, "frontendUrl", "http://localhost:5173/");
        ReflectionTestUtils.setField(servicio, "expiracionMinutos", 45L);
    }

    @Test
    void solicitarGeneraTokenHasheadoInvalidaAnterioresYEnviaEnlace() throws Exception {
        Usuario usuario = usuario();
        when(limiteRepository.bloquearPorClave(anyString())).thenReturn(Optional.empty());
        when(usuarioRepository.findByEmailIgnoreCaseAndEliminadoFalse("ana@tienda.pe"))
                .thenReturn(Optional.of(usuario));
        when(tokenRepository.save(any(TokenRecuperacion.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
        ArgumentCaptor<TokenRecuperacion> captor = ArgumentCaptor.forClass(TokenRecuperacion.class);
        ArgumentCaptor<Instant> ahoraCaptor = ArgumentCaptor.forClass(Instant.class);
        Instant antes = Instant.now();

        servicio.solicitar("  ANA@Tienda.PE ");

        verify(tokenRepository).invalidarNoUsadosPorUsuario(org.mockito.ArgumentMatchers.eq(7L),
                ahoraCaptor.capture());
        verify(tokenRepository).save(captor.capture());
        TokenRecuperacion guardado = captor.getValue();
        assertEquals(64, guardado.getTokenHash().length());
        assertTrue(guardado.getExpiraEn().isAfter(antes.plusSeconds(44 * 60)));
        assertTrue(guardado.getExpiraEn().isBefore(Instant.now().plusSeconds(46 * 60)));
        assertTrue(guardado.getExpiraEn().isAfter(ahoraCaptor.getValue().plusSeconds(44 * 60)));
        ArgumentCaptor<String> enlaceCaptor = ArgumentCaptor.forClass(String.class);
        verify(correoRecuperacion).enviar(org.mockito.ArgumentMatchers.eq("ana@tienda.pe"),
                enlaceCaptor.capture(), org.mockito.ArgumentMatchers.eq(45L));
        Matcher tokenMatcher = Pattern.compile("token=([a-f0-9]{64})$").matcher(enlaceCaptor.getValue());
        assertTrue(tokenMatcher.find());
        String tokenPlano = tokenMatcher.group(1);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(tokenPlano.getBytes(StandardCharsets.UTF_8)));
        assertEquals(hash, guardado.getTokenHash());
        assertFalse(enlaceCaptor.getValue().contains(guardado.getTokenHash()));
    }

    @Test
    void solicitarCorreoNoRegistradoNoCreaTokenNiEnviaCorreo() {
        when(limiteRepository.bloquearPorClave(anyString())).thenReturn(Optional.empty());
        when(usuarioRepository.findByEmailIgnoreCaseAndEliminadoFalse("ausente@tienda.pe"))
                .thenReturn(Optional.empty());

        servicio.solicitar("ausente@tienda.pe");

        verify(tokenRepository, never()).save(any());
        verify(correoRecuperacion, never()).enviar(anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void solicitarPropagaFalloDelProveedorEnLugarDeResponderComoExito() {
        Usuario usuario = usuario();
        when(limiteRepository.bloquearPorClave(anyString())).thenReturn(Optional.empty());
        when(usuarioRepository.findByEmailIgnoreCaseAndEliminadoFalse("ana@tienda.pe"))
                .thenReturn(Optional.of(usuario));
        doThrow(new CorreoNoDisponibleException()).when(correoRecuperacion)
                .enviar(anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong());

        assertThrows(CorreoNoDisponibleException.class, () -> servicio.solicitar("ana@tienda.pe"));
    }

    @Test
    void restablecerCambiaLaContrasenaYConsumeElToken() {
        Usuario usuario = usuario();
        TokenRecuperacion token = tokenVigente(usuario);
        when(tokenRepository.bloquearVigentePorHash(anyString())).thenReturn(Optional.of(token));

        servicio.restablecer("token-plano", "nueva-clave-segura");

        verify(usuarioService).cambiarPassword(7L, "nueva-clave-segura");
        assertTrue(token.getUsadoEn() != null);
        verify(tokenRepository).save(token);
    }

    @Test
    void restablecerRechazaTokenDesconocidoExpiradoYReutilizado() {
        when(tokenRepository.bloquearVigentePorHash(anyString())).thenReturn(Optional.empty());
        assertThrows(NegocioException.class, () -> servicio.restablecer("desconocido", "nueva-clave"));

        Usuario usuario = usuario();
        TokenRecuperacion vencido = tokenVigente(usuario);
        vencido.setExpiraEn(Instant.now().minusSeconds(1));
        when(tokenRepository.bloquearVigentePorHash(anyString())).thenReturn(Optional.of(vencido));
        assertThrows(NegocioException.class, () -> servicio.restablecer("vencido", "nueva-clave"));

        TokenRecuperacion usado = tokenVigente(usuario);
        usado.marcarUsado(Instant.now());
        when(tokenRepository.bloquearVigentePorHash(anyString())).thenReturn(Optional.of(usado));
        assertThrows(NegocioException.class, () -> servicio.restablecer("usado", "nueva-clave"));

        verify(usuarioService, never()).cambiarPassword(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString());
    }

    private Usuario usuario() {
        Usuario usuario = new Usuario();
        ReflectionTestUtils.setField(usuario, "id", 7L);
        usuario.setEmail("ana@tienda.pe");
        return usuario;
    }

    private TokenRecuperacion tokenVigente(Usuario usuario) {
        TokenRecuperacion token = new TokenRecuperacion();
        token.setUsuario(usuario);
        token.setTokenHash("a".repeat(64));
        token.setExpiraEn(Instant.now().plusSeconds(60));
        return token;
    }
}
