package com.motoboutique.seguridad.service;

public interface CorreoRecuperacion {

    void enviar(String destinatario, String enlace, long expiracionMinutos);
}
