package com.motoboutique.shared.exception;

/** Error de entrega de correo que debe responderse sin filtrar detalles del proveedor. */
public class CorreoNoDisponibleException extends RuntimeException {

    public CorreoNoDisponibleException() {
        super("No se pudo enviar el correo.");
    }
}
