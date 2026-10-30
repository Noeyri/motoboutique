package com.motoboutique.seguridad.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.motoboutique.shared.exception.CorreoNoDisponibleException;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

class ResendCorreoRecuperacionTest {

    private static final String CLAVE_PRUEBA = "re_test_key_not_a_real_secret";

    @Test
    void enviaHttpsConBearerYTiempoMaximo() throws Exception {
        HttpClient cliente = mock(HttpClient.class);
        HttpResponse<String> respuesta = respuesta(200);
        when(cliente.send(any(HttpRequest.class),
                ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(respuesta);
        ResendCorreoRecuperacion correo = new ResendCorreoRecuperacion(
                cliente, CLAVE_PRUEBA, "onboarding@resend.dev");
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);

        correo.enviar("ana@tienda.pe", "http://localhost:5173/restablecer-password?token=abc", 30);

        org.mockito.Mockito.verify(cliente).send(captor.capture(),
                ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        HttpRequest solicitud = captor.getValue();
        assertEquals("https://api.resend.com/emails", solicitud.uri().toString());
        assertEquals("Bearer " + CLAVE_PRUEBA, solicitud.headers().firstValue("Authorization").orElseThrow());
        assertEquals(Duration.ofSeconds(10), solicitud.timeout().orElseThrow());
    }

    @Test
    void errorHttpDelProveedorSePropagaComoErrorDeEntrega() throws Exception {
        HttpClient cliente = mock(HttpClient.class);
        HttpResponse<String> respuesta = respuesta(401);
        when(cliente.send(any(HttpRequest.class),
                ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(respuesta);
        ResendCorreoRecuperacion correo = new ResendCorreoRecuperacion(
                cliente, CLAVE_PRUEBA, "onboarding@resend.dev");

        assertThrows(CorreoNoDisponibleException.class,
                () -> correo.enviar("ana@tienda.pe", "https://frontend.test/reset?token=abc", 30));
    }

    @Test
    void errorDeConexionSePropagaComoErrorDeEntrega() throws Exception {
        HttpClient cliente = mock(HttpClient.class);
        doThrow(new IOException("fallo local de prueba")).when(cliente).send(any(HttpRequest.class),
                ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        ResendCorreoRecuperacion correo = new ResendCorreoRecuperacion(
                cliente, CLAVE_PRUEBA, "onboarding@resend.dev");

        assertThrows(CorreoNoDisponibleException.class,
                () -> correo.enviar("ana@tienda.pe", "https://frontend.test/reset?token=abc", 30));
    }

    @Test
    void faltaDeConfiguracionFallaSinIntentarLaSolicitud() {
        HttpClient cliente = mock(HttpClient.class);
        ResendCorreoRecuperacion correo = new ResendCorreoRecuperacion(
                cliente, "", "");

        assertThrows(CorreoNoDisponibleException.class,
                () -> correo.enviar("ana@tienda.pe", "https://frontend.test/reset?token=abc", 30));
        org.mockito.Mockito.verifyNoInteractions(cliente);
    }

    private HttpResponse<String> respuesta(int status) {
        HttpResponse<String> respuesta = mock(HttpResponse.class);
        when(respuesta.statusCode()).thenReturn(status);
        return respuesta;
    }
}
