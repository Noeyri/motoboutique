package com.motoboutique.seguridad.web;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.motoboutique.seguridad.service.RecuperacionPasswordService;
import com.motoboutique.shared.exception.ApiExceptionHandler;
import com.motoboutique.shared.exception.CorreoNoDisponibleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AuthRecuperacionControllerTest {

    @Mock
    private RecuperacionPasswordService recuperacionService;

    private MockMvc mockMvc;

    @BeforeEach
    void preparar() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(recuperacionService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void solicitudUsaLaRespuestaGenericaYConservaElContrato() throws Exception {
        mockMvc.perform(post("/api/auth/recuperar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@tienda.pe\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mensaje").value(
                        "Si el correo está registrado, recibirás instrucciones para restablecer tu contraseña."));
        verify(recuperacionService).solicitar("ana@tienda.pe");
    }

    @Test
    void errorDeResendSeRespondeComoServicioNoDisponible() throws Exception {
        doThrow(new CorreoNoDisponibleException()).when(recuperacionService).solicitar("ana@tienda.pe");

        mockMvc.perform(post("/api/auth/recuperar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@tienda.pe\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(
                        "No se pudo enviar el correo de recuperación. Inténtalo más tarde."));
    }

    @Test
    void solicitudValidaFormatoDeCorreo() throws Exception {
        mockMvc.perform(post("/api/auth/recuperar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"no-es-correo\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirmacionDevuelveNoContent() throws Exception {
        mockMvc.perform(post("/api/auth/restablecer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"token-de-prueba\",\"password\":\"nueva-clave\"}"))
                .andExpect(status().isNoContent());
        verify(recuperacionService).restablecer("token-de-prueba", "nueva-clave");
    }

    @Test
    void confirmacionValidaLongitudMinimaDeLaContrasena() throws Exception {
        mockMvc.perform(post("/api/auth/restablecer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"token-de-prueba\",\"password\":\"corta\"}"))
                .andExpect(status().isBadRequest());
    }
}
