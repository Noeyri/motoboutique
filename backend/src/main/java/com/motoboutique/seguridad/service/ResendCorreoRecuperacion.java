package com.motoboutique.seguridad.service;

import com.motoboutique.shared.exception.CorreoNoDisponibleException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ResendCorreoRecuperacion implements CorreoRecuperacion {

    private static final URI ENDPOINT = URI.create("https://api.resend.com/emails");
    private static final Duration TIEMPO_MAXIMO_SOLICITUD = Duration.ofSeconds(10);

    private final HttpClient clienteHttp;
    private final String apiKey;
    private final String remitente;

    public ResendCorreoRecuperacion(
            HttpClient clienteHttp,
            @Value("${app.resend.api-key:}") String apiKey,
            @Value("${app.resend.email-from:}") String remitente) {
        this.clienteHttp = clienteHttp;
        this.apiKey = apiKey;
        this.remitente = remitente;
    }

    @Override
    public void enviar(String destinatario, String enlace, long expiracionMinutos) {
        if (apiKey.isBlank() || remitente.isBlank()) {
            log.error("Resend no está configurado; no se pudo enviar el correo de recuperación.");
            throw new CorreoNoDisponibleException();
        }

        String texto = "Solicitaste restablecer tu contraseña. El enlace vence en "
                + expiracionMinutos + " minutos:\n" + enlace;
        String html = "<p>Solicitaste restablecer tu contraseña. El enlace vence en "
                + expiracionMinutos + " minutos.</p><p><a href=\""
                + escaparHtml(enlace) + "\">Restablecer contraseña</a></p>";
        String contenido = "{\"from\":" + jsonString(remitente)
                + ",\"to\":[" + jsonString(destinatario) + "]"
                + ",\"subject\":" + jsonString("Restablecer contraseña - Moto Boutique")
                + ",\"text\":" + jsonString(texto)
                + ",\"html\":" + jsonString(html) + "}";

        HttpRequest solicitud = HttpRequest.newBuilder(ENDPOINT)
                .timeout(TIEMPO_MAXIMO_SOLICITUD)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(contenido, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> respuesta = clienteHttp.send(solicitud, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() < 200 || respuesta.statusCode() >= 300) {
                log.warn("Resend rechazó el correo de recuperación (HTTP {}).", respuesta.statusCode());
                throw new CorreoNoDisponibleException();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Se interrumpió el envío de correo a Resend.");
            throw new CorreoNoDisponibleException();
        } catch (IOException ex) {
            log.warn("Falló la comunicación con Resend ({})", ex.getClass().getSimpleName());
            throw new CorreoNoDisponibleException();
        }
    }

    private String escaparHtml(String valor) {
        return valor.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String jsonString(String valor) {
        StringBuilder json = new StringBuilder("\"");
        for (int indice = 0; indice < valor.length(); indice++) {
            char caracter = valor.charAt(indice);
            switch (caracter) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (caracter < 0x20) {
                        String hex = Integer.toHexString(caracter);
                        json.append("\\u").append("0".repeat(4 - hex.length())).append(hex);
                    } else {
                        json.append(caracter);
                    }
                }
            }
        }
        return json.append('"').toString();
    }
}
